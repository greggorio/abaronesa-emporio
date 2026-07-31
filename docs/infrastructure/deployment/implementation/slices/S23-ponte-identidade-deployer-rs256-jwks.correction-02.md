# S23 — Correção causal consolidada 02

> **Estado:** `IN_PROGRESS — correção consolidada obrigatória`
> **Contrato-base:** `S23-ponte-identidade-deployer-rs256-jwks.task.md` +
> `S23-ponte-identidade-deployer-rs256-jwks.correction-01.md`
> **Relatório a atualizar:** `S23-ponte-identidade-deployer-rs256-jwks.report.md`
> **Próxima slice:** S24 continua bloqueada

## 1. Veredito

```text
REJECTED — correção consolidada obrigatória
```

O ciclo anterior corrigiu a fronteira e a higiene (arquivos exigidos existem,
resíduos removidos — confirmado). Mas a "suíte mutante" e a "prova cruzada"
entregues não provam o que afirmam provar. Isso foi comprovado por execução
direta nesta revisão, não por leitura de código isolada.

## 2. Achado central: a suíte mutante do validador é vazia por construção

`validate_deployer_identity_bridge.py::main()` começa com:

```python
def main() -> None:
    os.chdir(Path(__file__).parent.parent.parent)
    ...
```

`__file__`, para um módulo carregado via `importlib.util.spec_from_file_location`,
é o caminho real usado no `spec_from_file_location` — sempre o arquivo real
em `tools/deploy/validate_deployer_identity_bridge.py`, nunca a cópia mutante
em `/tmp`. Ou seja: **toda vez que `main()` roda, ele volta sozinho para a
raiz real do repositório antes de ler qualquer arquivo**, não importa para
onde o teste tenha feito `chdir()` antes de chamá-lo.

Reproduzido diretamente nesta revisão:

```python
import importlib.util
spec = importlib.util.spec_from_file_location(
    "v", "tools/deploy/validate_deployer_identity_bridge.py"
)
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
print(v.__file__)
# -> /home/gregorio/git/baronesa/emporio/tools/deploy/validate_deployer_identity_bridge.py
```

E executando a suíte inteira como está:

```bash
python3 -m unittest tools/deploy/tests/test_deployer_identity_bridge_contract.py -v
```

Resultado: **12/12 "ok"**, e a saída padrão mostra `deployer-identity:valid`
impresso **doze vezes** — inclusive nos testes que apagam o controller,
trocam a rota, removem `@PreAuthorize`, trocam a audience, trocam o scope e
removem a documentação. Nenhuma dessas mutações jamais chegou a ser lida pelo
validador, porque ele sempre volta para a raiz real antes de checar qualquer
coisa.

Há um segundo defeito independente, que já seria suficiente sozinho: todos os
métodos de mutação usam

```python
with self.assertRaises(SystemExit):
    validator.main()
```

`main()` sempre termina chamando `sys.exit(...)`, seja `sys.exit(0)` (linha
de sucesso) ou `sys.exit(1)` (dentro de `fail()`). Como `sys.exit(0)` também
levanta `SystemExit`, esse `assertRaises` é satisfeito em ambos os casos — a
asserção não distingue sucesso de falha. O helper `assert_mutant`, definido
no próprio arquivo (linhas 44–51) com `assertRaisesRegex(SystemExit, "1")`,
resolveria a segunda parte do problema, mas não é chamado por nenhum teste.

A resposta do executor declarou "12 testes, 11/11 mutantes falham causal".
Isso não é o que acontece: nenhum mutante causa falha, e a suíte não teria
como detectar isso mesmo se causasse.

## 3. Achado central: a "prova cruzada" (Correção D) é majoritariamente vazia

`DeployerPublisherIsolationTest.java`, entregue como prova de que "token do
deployer é rejeitado pelo verificador do publisher e vice-versa" (item
explicitamente fechado na correção-01, Seção 8), tem nove métodos `@Test`.
Lidos diretamente:

- `testConfigValidationCouplesModeToDeclaredAudience`,
  `testTokenVerificationByAudience`, `testAlgorithmEnforcement` e
  `testJwtIssuerInclipsRouteInformation` **não têm corpo executável** — são
  apenas comentários dentro de um método vazio. Passam sempre, porque não
  fazem nada.
- `testDeployerAndPublisherAudiencesAreLiterally`,
  `testDeployerScopeNeverIncludesPublisherScopes`,
  `testPublisherScopeNeverIncludesDeployerScopes` e `testRouteIsolation`
  comparam **strings literais digitadas à mão no próprio teste**, sem
  referenciar `DeployerReleaseControlIdentityService.AUDIENCE`,
  `.SCOPE`, ou os valores reais de `@RequestMapping`/`@GetMapping`/
  `@PostMapping` dos controllers. Provam que dois textos que o autor do
  teste escreveu são diferentes um do outro — não provam nada sobre o
  código de produção.
- `testKeyMaterialIsolation` é o único que toca uma classe real, e o faz
  checando que o nome da classe contém a substring `"Deployer"`.

Nenhum desses nove testes cria um `JwtVerifier`, assina um JWT com uma chave
RSA efêmera e prova que a verificação falha quando a audience não bate — que
é exatamente o que a Correção D pediu, de forma explícita, com o motivo
declarado (evitar prova por raciocínio). O que foi entregue é prova por
raciocínio travestida de teste.

## 4. O que permanece correto neste ciclo

Para não haver dúvida sobre o que não precisa ser refeito:

- `tools/deploy/tests/test_deployer_identity_bridge_contract.py` existe,
  segue a estrutura de `test_deployer_runtime_contract.py` (cópia para
  diretório temporário, `importlib` para carregar o validador) — só precisa
  corrigir os dois defeitos da Seção 2, não ser reescrito do zero.
- `backend/src/test/java/.../identity/deployer/DeployerReleaseControlIdentityConfigurationTest.java`
  e `.../DeployerReleaseControlIdentityHttpSecurityTest.java` são legítimos:
  usam reflexão sobre as classes reais (`getDeclaredField`, `getAnnotation`,
  `getMethod`) e leem valores reais das constantes e anotações. Cobrem
  presença estrutural corretamente, mas ainda faltam os casos comportamentais
  negativos da lista original (ver Seção 6).
- Resíduos foram genuinamente removidos: confirmado por busca recursiva nesta
  revisão, `release_control/` está limpo.
- `check_no_new_maven_dependency` melhorou (agora bloqueia bibliotecas JWT
  alternativas conhecidas), mas ainda não é uma comparação real de conjunto
  de dependências — ver Seção 7.

## 5. Fronteira autorizada desta correção

Alterar somente:

```text
tools/deploy/validate_deployer_identity_bridge.py
tools/deploy/tests/test_deployer_identity_bridge_contract.py
backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/deployer/DeployerPublisherIsolationTest.java
backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/deployer/DeployerReleaseControlIdentityConfigurationTest.java
backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/deployer/DeployerReleaseControlIdentityHttpSecurityTest.java
docs/infrastructure/deployment/implementation/slices/S23-ponte-identidade-deployer-rs256-jwks.report.md
```

Não alterar nenhuma classe de produção (Java ou Python), `config.py`, o
pacote `identity` do publisher, task, tracker, ou qualquer arquivo fora desta
lista.

## 6. Correção A — tornar o validador testável sem quebrar o uso normal

`main()` não pode decidir sozinho qual raiz usar quando chamado por um teste
que já fez `chdir` para uma cópia mutante. Ajustar a assinatura para aceitar
a raiz como parâmetro opcional, preservando o comportamento atual quando
invocado como script:

```python
def main(root: Path | None = None) -> None:
    os.chdir(root if root is not None else Path(__file__).parent.parent.parent)
    ...

if __name__ == "__main__":
    raise SystemExit(main())
```

(Ajustar o corpo para retornar o código de saída em vez de chamar
`sys.exit` diretamente dentro de `main`, se necessário para manter
`argv`/uso como script idêntico ao atual — o comportamento observável do
script chamado via `python3 tools/deploy/validate_deployer_identity_bridge.py`
não pode mudar.)

Os testes devem então chamar `validator.main(root)` explicitamente, sem
`os.chdir` manual no processo do teste.

## 7. Correção B — suíte mutante que realmente verifica falha

Reescrever cada método de mutação para verificar o código de saída real, não
apenas a ocorrência de `SystemExit`:

```python
def assert_invalid(self, root: Path) -> None:
    with self.assertRaises(SystemExit) as ctx:
        validator.main(root)
    self.assertEqual(ctx.exception.code, 1)
```

Usar `assert_invalid` (ou o já existente `assert_mutant`, corrigido) em todas
as doze mutações. Manter `test_real_identity_bridge_is_valid` verificando
`ctx.exception.code == 0` (ou equivalente) contra a raiz real, sem mutação.

Depois da correção, rodar a suíte e confirmar na saída de texto que os testes
de mutação realmente imprimem `deployer-identity:invalid — <código>` no
`stderr`, não `deployer-identity:valid`. Registrar essa saída literal no
relatório — não apenas o resultado agregado do `unittest`.

## 8. Correção C — dependência Maven: comparação real, não blocklist

Substituir `check_no_new_maven_dependency` por uma comparação de conjunto:
extrair todos os pares `groupId:artifactId` declarados em `<dependency>` do
`pom.xml`, e comparar contra uma lista fechada conhecida (o conjunto de
dependências já presente antes da S23, incluindo a entrada JJWT existente).
Qualquer `groupId:artifactId` fora dessa lista fechada falha. Adicionar
mutante que insere uma dependência arbitrária não relacionada a JWT (por
exemplo, uma dependência de logging qualquer) e prova que o validador a
rejeita — o blocklist atual não pegaria isso.

## 9. Correção D — prova cruzada publisher/deployer real

Reescrever `DeployerPublisherIsolationTest.java` removendo os quatro métodos
sem corpo executável e os quatro métodos que comparam apenas literais
digitados à mão. Implementar, no mínimo:

1. Um teste que instancia a lógica de emissão/verificação real (ou o
   equivalente Java mínimo necessário) e prova, por reflexão sobre as
   constantes reais `DeployerReleaseControlIdentityService.AUDIENCE` e
   `ReleaseControlIdentityService.AUDIENCE` (publisher), que os dois valores
   são distintos **lidos da classe**, não retranscritos como string solta no
   teste.
2. Um teste equivalente para `SCOPE`, lido das constantes reais das duas
   classes, provando que nenhuma contém `"deployment:"` do lado publisher
   nem `"release:"` do lado deployer.
3. Um teste que lê `@RequestMapping` real de
   `ReleaseControlIdentityController` e de
   `DeployerReleaseControlIdentityController` (via reflexão, como já feito
   em `DeployerReleaseControlIdentityHttpSecurityTest`) e prova que os dois
   valores são distintos e que nenhum é prefixo problemático do outro de
   forma a colidir em roteamento.

A prova de nível Python (`JwtVerifier` configurado com audience do deployer
rejeitando um JWT assinado com `aud=emporio-release-control`, e vice-versa,
com chave RSA efêmera de teste) já é requisito da task original (§11, itens
2–3) e deve estar dentro de
`tools/deploy/tests/test_deployer_identity_bridge_contract.py` (ou em
`release_control/tests/test_config_security.py`, mas este último está fora
da fronteira desta correção — se for necessário adicioná-la lá, declarar o
bloqueio em vez de tocar o arquivo por conta própria). Se a prova de nível
Python já existir em algum teste aceito e cobrir isso de forma real, apontar
exatamente qual teste e linha no relatório em vez de duplicá-la.

## 10. Matriz terminal obrigatória

Executar e registrar comando, exit, contagem, duração **e a saída de texto
relevante** (não apenas o resumo agregado):

```bash
cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/deploy/tests/test_deployer_identity_bridge_contract.py -v
```

Para esse comando específico, colar no relatório a saída completa de
stdout/stderr — deve mostrar `deployer-identity:invalid — <código>` para
cada um dos testes de mutação, e `deployer-identity:valid` apenas para
`test_real_identity_bridge_is_valid`.

```bash
cd backend
mvn -B verify

cd /home/gregorio/git/baronesa/emporio/release_control
uv sync --frozen --group dev
uv run ruff check .
uv run mypy --strict src tests
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90

cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_identity_bridge.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
git diff --check
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
find release_control tools -maxdepth 4 \( -name '.venv' -o -name '.coverage' \
  -o -name '.pytest_cache' -o -name '.ruff_cache' -o -name '.mypy_cache' \
  -o -name '__pycache__' -o -name '*.pyc' \) -print
```

Todos os comandos devem terminar com exit `0`, exceto o `find` final, cuja
saída deve ser vazia.

## 11. Critérios de aceite

A correção só volta para revisão quando:

- as correções A–D estiverem implementadas;
- a execução real (colada no relatório, não resumida) mostrar
  `deployer-identity:invalid` para cada mutante e `deployer-identity:valid`
  apenas para o caminho feliz;
- `DeployerPublisherIsolationTest.java` não tiver nenhum método `@Test` sem
  corpo executável nem comparação de literais desconectados do código real;
- a prova cruzada de nível JWT (Python) existir e ser apontada por arquivo e
  nome de teste exatos;
- a matriz da Seção 10 estiver integralmente verde e o `find` de resíduos
  (incluindo `tools/`, não só `release_control/`) vazio;
- o relatório contiver uma seção `Resposta à correção causal consolidada 02`
  com a saída literal pedida na Seção 10, não apenas uma tabela de
  "resultado: ok";
- o relatório não declarar `ACCEPTED` e não criar S24.

## 12. Nota sobre o padrão de resposta esperado deste ciclo em diante

As duas rejeições da S23 tiveram causas diferentes: a primeira foi omissão
(entregável ausente); esta foi aparência de prova sem prova real (testes que
compilam, "passam", e mesmo assim não verificam nada). A partir daqui, toda
alegação de "N/N mutantes falham causal" deve vir acompanhada, no relatório,
da saída literal de pelo menos um mutante falhando — colada, não resumida.
Isso vale para esta e para futuras slices.

## 13. Formato da resposta do executor

Responder somente com:

1. caminho absoluto do relatório atualizado;
2. arquivos alterados;
3. resumo das correções A–D;
4. saída literal do comando de mutação (Seção 10), não resumida;
5. matriz de validação com contagens e exits;
6. estado Git e resíduos (com comando e saída);
7. divergências restantes, que devem ser `nenhuma` ou acompanhadas de prova;
8. estado literal:

```text
IN_PROGRESS — aguardando revisão terminal do orquestrador
```
