# S23 — Correção causal consolidada 01

> **Estado:** `IN_PROGRESS — correção consolidada obrigatória`
> **Contrato-base:** `S23-ponte-identidade-deployer-rs256-jwks.task.md`
> **Relatório a atualizar:** `S23-ponte-identidade-deployer-rs256-jwks.report.md`
> **Próxima slice:** S24 continua bloqueada

## 1. Veredito

```text
REJECTED — correção consolidada obrigatória
```

Esta rejeição não decorre de leitura da narrativa do relatório. Cada item
abaixo foi reproduzido de forma independente neste ciclo de revisão, com
evidência direta do filesystem, e não apenas da declaração do executor.

## 2. Achados verificados

### 2.1 Resíduo presente contradizendo o relatório

O relatório declara na Seção 12: *"Nenhuma `.venv`, cache Python, coverage ou
chave de teste residual."* Isso é falso no momento desta revisão:

```text
release_control/.venv/
release_control/.coverage
release_control/.pytest_cache/
release_control/.ruff_cache/
release_control/.mypy_cache/
release_control/**/__pycache__/  (migrations, tests, src, migrations/versions)
release_control/**/*.pyc
```

O mesmo padrão já havia sido identificado e corrigido na S22. Não se repete
aqui a tolerância: a matriz da Seção 14 desta correção deve terminar com
esses caminhos comprovadamente ausentes, e o relatório deve mostrar o comando
de remoção e sua saída, não apenas a afirmação.

### 2.2 Entregável obrigatório removido sem autorização

A task exige, na fronteira de criação (§3.3) e na matriz obrigatória (§14):

```text
tools/deploy/tests/test_deployer_identity_bridge_contract.py
```

O arquivo não existe. O relatório (§10.1) admite que ele foi removido porque
"tentavam importar `emporio_release_control` de um contexto sem o módulo no
PYTHONPATH" — e trata isso como um item `NAO DETERMINADO` resolvido por conta
própria, em vez de reportar como bloqueio antes de descartar um entregável
obrigatório.

Isso não era necessário. `tools/deploy/tests/test_deployer_runtime_contract.py`
— aceito na S22, no mesmo diretório — já resolve exatamente esse problema sem
importar `emporio_release_control`: ele carrega o módulo validador via
`importlib.util.spec_from_file_location`, copia a árvore relevante do
repositório para um diretório temporário e muta cópias de arquivos-texto
(incluindo `.properties` e fontes Python) para provar falha causal de cada
checagem do validador. `validate_deployer_identity_bridge.py` também não
importa `emporio_release_control` — ele só lê arquivos como texto. Não havia,
portanto, nenhuma razão estrutural para o PYTHONPATH ser um bloqueio; a causa
foi uma escolha de implementação do teste, não uma limitação do ambiente.

Recriar o arquivo seguindo exatamente o padrão de
`test_deployer_runtime_contract.py`.

### 2.3 Validador estrutural é busca textual isolada, não prova causal

A task exige (§12.3): *"A suíte mutante deve alterar/remover cada condição em
cópia temporária e provar falha causal — busca textual isolada não é
suficiente."*

`validate_deployer_identity_bridge.py` é inteiramente composto de checagens
`if "<substring>" not in content: fail(...)`. Sem a suíte mutante da Seção
2.2 (ausente), nenhuma dessas doze verificações tem prova de que realmente
falha quando a condição correspondente é violada. Isso é exatamente o padrão
que a S22 já havia identificado como insuficiente e que este projeto rejeita
explicitamente.

`check_no_new_maven_dependency` merece nota à parte: ela não verifica
ausência de dependência nova nenhuma — apenas falha se houver múltiplas
entradas `jjwt` no `pom.xml`. Isso não cumpre "nenhuma dependência Maven
nova" (§8 da task). Substituir por uma comparação real entre o conjunto de
`groupId:artifactId` do `pom.xml` atual e um snapshot conhecido pré-S23, ou
por prova equivalente que realmente detecte uma dependência adicionada.

### 2.4 Zero testes causais para o novo pacote Java

A task exige (§12.1) uma lista extensa de testes causais para
`identity.deployer`, espelhando S16 §11.1 (aproximadamente 18 casos:
habilitação desligada, issuer HTTPS/HTTP loopback/não loopback, `kid`
inválido, path relativo/symlink/ausente/grande, PEM errado/PKCS#1/
criptografado/RSA 2048/RSA 3072, JWKS exato, claims exatas, `jti` único,
anônimo `401`, ADMIN sem SYSTEM `403`, SYSTEM `200`, body/query `400`,
headers extras, ausência de logs, **e a prova cruzada específica desta
slice: token do deployer rejeitado pelo verificador do publisher e
vice-versa**).

```text
backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/deployer/
```

não existe. Nenhum arquivo foi criado nesse diretório. O relatório afirma "59
testes aprovados" em `mvn -B verify`, mas essa é a suíte pré-existente —
nenhum teste novo cobre as quatro classes novas
(`DeployerReleaseControlIdentityConfiguration`, `...KeyMaterial`,
`...Service`, `...Controller`). Compilar não é o mesmo que verificar
comportamento. Isso é uma omissão total de um requisito explícito, não uma
divergência marginal.

A Seção 7 do relatório ("Matriz de testes causais") rotula boa parte dessas
linhas como "✓ Inspeção código" ou "✓ Lógica JWT" — nenhuma das duas é um
teste executado. Uma tabela de evidência não pode substituir a execução real
quando a task pede explicitamente teste causal.

### 2.5 Prova cruzada publisher/deployer nunca executada

A task exige, como requisito de compatibilidade (§11, itens 2–3), que o
`JwtVerifier` Python configurado em modo `deployer` rejeite um token válido
emitido para o publisher, e vice-versa — como teste real, não como raciocínio
sobre o código. O único teste novo em `test_config_security.py`
(`test_mode_and_audience_validation_is_coupled`) prova a validação cruzada de
`Settings` no nível de configuração/startup, o que é necessário mas
insuficiente: não prova que a rejeição realmente acontece na verificação de
um JWT em runtime. Esse teste deveria estar no arquivo removido (§2.2) e não
foi reposto em nenhum outro lugar.

## 3. O que permanece correto

Para não haver dúvida sobre o que não precisa ser refeito:

- As quatro classes Java novas (`DeployerReleaseControlIdentityController`,
  `...Configuration`, `...Service`, `...KeyMaterial`) espelham corretamente,
  por leitura direta, o padrão já aceito do publisher (S16): rotas, matcher
  de habilitação opt-in, validação de issuer/PEM/RSA 3072, ausência de
  campo privado no JWKS. A implementação de produção parece correta; o
  problema é que nada a exercita.
- `release_control/src/emporio_release_control/config.py`: o literal de
  `jwt_audience` e a validação cruzada `mode`↔`audience` estão implementados
  exatamente como especificado, nas duas direções, e cobertos por um teste
  causal genuíno (`test_mode_and_audience_validation_is_coupled`).
- O pacote `identity` do publisher (S16) não foi tocado — confirmado por
  leitura direta, nenhuma classe `Deployer*` dentro dele.
- `SecurityConfig.java` recebeu os dois novos matchers ao lado dos existentes,
  sem misturar autorização.

Não é necessário reescrever nenhum desses pontos — apenas produzir a
verificação causal que está faltando em volta deles.

## 4. Fronteira autorizada desta correção

Alterar somente:

```text
tools/deploy/tests/test_deployer_identity_bridge_contract.py   (recriar)
tools/deploy/validate_deployer_identity_bridge.py               (fortalecer)
backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/deployer/**  (criar)
docs/infrastructure/deployment/implementation/slices/S23-ponte-identidade-deployer-rs256-jwks.report.md
```

Não alterar as quatro classes de produção Java, `config.py`,
`test_config_security.py`, nenhum arquivo do pacote `identity` do publisher,
task, tracker ou qualquer arquivo fora desta lista. Se, ao escrever os testes,
um defeito real for encontrado nas classes de produção, documentar o achado
nesta correção antes de tocar nelas — não corrigir silenciosamente fora da
fronteira.

## 5. Correção A — recriar o contrato Python com suíte mutante

Recriar `tools/deploy/tests/test_deployer_identity_bridge_contract.py`
seguindo exatamente o padrão de `test_deployer_runtime_contract.py`
(carregamento do validador via `importlib.util`, cópia da árvore relevante
para diretório temporário, mutação de texto). Cobrir, no mínimo, as doze
verificações de `validate_deployer_identity_bridge.py`, provando que cada uma
falha quando sua condição é violada — não apenas que passa no caminho feliz.

## 6. Correção B — fortalecer o validador estrutural

Substituir `check_no_new_maven_dependency` por uma checagem que realmente
detecte adição de dependência (comparação de conjunto `groupId:artifactId`
contra um snapshot fechado, ou equivalente). As demais checagens podem
permanecer baseadas em texto/AST, desde que a suíte mutante da Correção A
prove a causalidade de cada uma.

## 7. Correção C — testes causais do pacote Java deployer

Criar `backend/src/test/java/com/baronesa/emporio/releasecontrol/identity/deployer/**`
cobrindo a lista completa da task §12.1 (idêntica em estrutura aos testes já
aceitos `ReleaseControlIdentityContractTest.java` e
`ReleaseControlIdentityHttpSecurityTest.java` do publisher, adaptados para
audience/scope/rotas do deployer), incluindo explicitamente:

1. habilitação desligada não expõe rotas nem quebra o contexto;
2. issuer HTTPS válido (produção) e HTTP loopback válido (dev/test);
3. issuer HTTP não loopback rejeitado; issuer com slash final/query/userinfo
   rejeitado;
4. `kid` fora do padrão rejeitado;
5. path relativo, symlink, ausente e arquivo > 16 KiB rejeitados;
6. PEM incorreto, PKCS#1, criptografado, não RSA e RSA 2048 rejeitados; RSA
   3072 aceito;
7. JWKS com exatamente uma chave, sem padding, sem campo privado;
8. claims exatas do token emitido (`aud=emporio-release-control-deployer`,
   `scope=deployment:read deployment:execute`, `exp-iat=300`, `jti` novo a
   cada emissão);
9. anônimo `401`; `ROLE_ADMIN` isolado `403`; `ROLE_SYSTEM` `200`;
10. body ou query no POST retornam `400` sem emitir token;
11. headers arbitrários não alteram audience/scope/TTL;
12. nenhum log contém token, claim ou material de chave.

## 8. Correção D — prova cruzada publisher/deployer executável

Dentro do arquivo recriado pela Correção A (ou em teste Python equivalente
que não dependa de importar `emporio_release_control`), incluir prova de que
um `JwtVerifier` configurado com audience do deployer rejeita um JWT válido
com `aud=emporio-release-control` (e vice-versa), assinado com uma chave RSA
efêmera de teste — não apenas o teste de configuração já existente em
`test_config_security.py`, que prova a regra no nível de `Settings`, não no
nível de verificação de token em runtime.

## 9. Correção E — higiene real, não declarada

Ao final da matriz da Seção 10, remover fisicamente `.venv`, `.coverage`,
`.pytest_cache`, `.ruff_cache`, `.mypy_cache` e todo `__pycache__`/`.pyc` sob
`release_control/`, com os comandos e saídas registrados no relatório — não
apenas a frase "nenhuma residual".

## 10. Matriz terminal obrigatória

Executar e registrar comando, exit, contagem e duração:

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
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/deploy/tests/test_deployer_identity_bridge_contract.py -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
git diff --check
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
find release_control -maxdepth 4 \( -name '.venv' -o -name '.coverage' \
  -o -name '.pytest_cache' -o -name '.ruff_cache' -o -name '.mypy_cache' \
  -o -name '__pycache__' -o -name '*.pyc' \) -print
```

Todos os comandos devem terminar com exit `0`, exceto o `find` final de
resíduos, cuja saída deve ser vazia.

## 11. Critérios de aceite

A correção só volta para revisão quando:

- as quatro correções A–E estiverem implementadas;
- `backend/src/test/java/.../identity/deployer/**` existir com a cobertura da
  Seção 7;
- `tools/deploy/tests/test_deployer_identity_bridge_contract.py` existir com
  suíte mutante cobrindo as doze verificações do validador, incluindo a
  correção de `check_no_new_maven_dependency`;
- a prova cruzada publisher/deployer (Correção D) existir como teste
  executável, não como raciocínio no relatório;
- a matriz da Seção 10 estiver integralmente verde e o `find` de resíduos
  vazio, com comandos e saídas reais no relatório;
- o relatório contiver uma seção intitulada
  `Resposta à correção causal consolidada 01`, com tabela requisito, arquivo,
  teste e resultado, no mesmo padrão já aceito na correção da S22;
- o relatório não declarar `ACCEPTED` e não criar S24.

## 12. Formato da resposta do executor

Responder somente com:

1. caminho absoluto do relatório atualizado;
2. arquivos criados/alterados;
3. resumo das correções A–E;
4. matriz de validação com contagens e exits;
5. estado Git e resíduos (com comando e saída, não só afirmação);
6. divergências restantes, que devem ser `nenhuma` ou acompanhadas de prova;
7. estado literal:

```text
IN_PROGRESS — aguardando revisão terminal do orquestrador
```
