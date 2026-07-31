# S07 — Relatorio do saneamento de bootstrap sensivel pre-Docker

> **Estado informado pelo executor:** `IN_PROGRESS`  
> **Data:** `2026-07-28`  
> **CWD principal:** `/home/gregorio/git/baronesa/emporio`

## 1. Resumo do risco

Inicializadores anteriores continham defaults funcionais de credencial,
identificadores e identidade de tenant, alem de logs capazes de registrar
valores de configuracao. Paths fiscais tambem dependiam do host original.

Esta remediacao incremental removeu esses riscos do codigo candidato ao
primeiro commit. Nenhum valor removido e reproduzido neste relatorio.

## 2. Arquivos alterados

- `.env.example`;
- `backend/.env.example`;
- `backend/src/main/java/com/baronesa/emporio/ConfigSeeder.java`;
- `backend/src/main/java/com/baronesa/emporio/config/RootUserInitializer.java`;
- `backend/src/main/resources/application.properties`;
- `backend/src/main/resources/application-dev.properties`;
- `backend/src/test/resources/application-test.properties`;
- `backend/src/test/java/com/baronesa/emporio/config/RootUserInitializerTest.java`;
- `tools/security/bootstrap_contract.py`;
- `tools/security/tests/test_bootstrap_contract.py`;
- `docs/development/CONFIGURACAO_LOCAL_SEGREDOS.md`;
- `docs/development/README.md`;
- `docs/infrastructure/deployment/BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md`;
- este relatorio.

`application-prod.properties` nao precisou ser alterado: os paths de upload ja
estavam portaveis e o contrato de bootstrap e herdado do profile base.

## 3. Contrato final do bootstrap root

- bootstrap desabilitado por default;
- binding exclusivo das quatro variaveis aprovadas;
- nome possui apenas o default tecnico `Root`;
- email e password nao possuem fallback funcional;
- quando desabilitado, nao consulta nem grava usuario;
- quando habilitado, valida nome, formato de email e password minima de 16
  caracteres antes de consultar o repositorio;
- configuracao invalida interrompe a inicializacao com erro sanitizado;
- usuario `SYSTEM` existente e preservado sem alteracao;
- criacao permanece transacional;
- password e entregue ao encoder e somente o hash e persistido;
- logs registram apenas acao e resultado, sem email ou password;
- nao ha endpoint, alias legado ou tratamento que engula a falha.

## 4. Categorias de seeds saneadas

Foram esvaziados os defaults das categorias:

- credenciais, tokens, secrets e chaves;
- identificadores de contas e public keys vinculadas a estabelecimento;
- identidade fiscal, cadastral e de contato do tenant;
- dados de pickup e endpoints especificos de instalacao;
- paths de certificado e artefatos identificadores do estabelecimento.

Defaults tecnicos conservadores permaneceram quando autorizados: enums, flags,
timeouts, limites, URLs publicas oficiais e ambiente fiscal de homologacao.

## 5. Tratamento de logs

`seedConfig` registra somente chave e tipo de acao. Valor default, valor
anterior, valor novo e objetos de configuracao nao sao passados ao logger.

O bootstrap root registra apenas se ficou desabilitado, preservou usuario
existente ou criou o usuario `SYSTEM`. Erros de validacao identificam somente
o campo/categoria invalida.

## 6. Paths portaveis

O contrato foi congelado em:

```text
nfe_schema_path -> /app/nfe/schemas
nfe_xml_path    -> /app/nfe/xmls
uploads         -> /app/uploads
```

O certificado permanece sem path default. O profile de teste usa diretorio
efemero sob o diretorio temporario da JVM para nao gravar no filesystem de
runtime. O gate `BACKEND_FISCAL_SCHEMA_PATH` permanece aberto ate a futura
imagem copiar e validar os schemas.

## 7. Banco existente

O seeder cria somente configuracao ausente. Qualquer linha existente,
inclusive com valor vazio, e preservada. Nao houve migration, limpeza,
consulta externa, sobrescrita ou rotacao.

Valores historicamente persistidos exigem auditoria e rotacao operacional
separadas.

## 8. Validador e testes mutantes

Comando final, CWD raiz:

```bash
python3 tools/security/bootstrap_contract.py validate
```

Codigo `0`; resultado `bootstrap-contract:valid`.

Comando final, CWD raiz:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/security/tests \
  -p 'test_bootstrap_contract.py' \
  -v
```

Codigo `0`; **20 testes aprovados**, zero falhas e zero erros.

Os mutantes cobrem os 15 itens minimos do contrato, input invalido, path
ausente, preservacao do banco, path de upload e a garantia de que um marcador
sensivel nao aparece na mensagem de erro.

## 9. Testes Java

Comando final, CWD
`/home/gregorio/git/baronesa/emporio/backend`:

```bash
DB_PASSWORD=s07-ephemeral-db-password \
INTEGRATION_SYSTEM_TOKEN_SECRET=s07-ephemeral-integration-token-for-tests \
mvn -B verify
```

Codigo `0`; `BUILD SUCCESS`; **35 testes aprovados**, zero falhas, erros ou
skips. Desse total, 8 testes focados cobrem integralmente o bootstrap root:
desabilitado, configuracoes invalidas, usuario existente, criacao unica e
persistencia somente do hash.

## 10. Comandos, codigos e desvios intermediarios

| Comando | CWD | Codigo | Interpretacao |
|---|---|---:|---|
| primeira validacao do contrato | raiz | 2 | substituicao mecanica dos seeds nao havia sido efetivada; falhou fechado sem imprimir valores |
| primeira suite Python | raiz | 1 | escape sintatico invalido em um teste; nenhum contrato real foi mutado |
| validacao e suite apos correcao | raiz | 0/0 | contrato valido; 20/20 |
| primeiro `mvn -B verify` | `backend/` | 1 | profile de teste tentou gravar no path futuro de container |
| segundo `mvn -B verify` | `backend/` | 0 | fixture temporaria de teste aplicada; 35/35 |
| validacao e suite finais | raiz | 0/0 | contrato valido; 20/20 |
| `mvn -B verify` final | `backend/` | 0 | `BUILD SUCCESS`; 35/35 |

A fixture de storage foi limitada a `application-test.properties`. Nenhum
profile runtime perdeu os paths portaveis.

## 11. Documentacao e itens nao determinados

A documentacao explica uso local via `.env.local` ignorado e o procedimento
futuro de producao: habilitar explicitamente, criar uma vez, verificar acesso
e desabilitar no deploy seguinte.

Permanecem nao determinados:

- secret store e mecanismo de injecao em producao;
- procedimento de recuperacao/reset;
- auditoria e rotacao dos valores historicamente persistidos;
- Dockerfile e copia/validacao dos schemas fiscais, previstos para S08.

## 12. Escopo negativo

Nao houve:

- Dockerfile, Compose, Nginx, workflow, UI ou `release_control`;
- migration, limpeza ou acesso operacional a banco;
- configuracao de dados reais do estabelecimento;
- abertura de PFX, `.env.production`, HPROF, upload ou arquivo sensivel
  ignorado;
- instalacao de dependencia;
- acesso a GitHub, GHCR, DNS ou VPS;
- `git add`, commit, tag ou push;
- alteracao da task S07 ou do tracker;
- teste do `website_back`.

O `mvn verify` executou somente o backend ERP. Uma tentativa existente do
profile de teste contra um endpoint local indisponivel foi tratada pela
aplicacao e nao alterou o resultado; nenhum servico externo foi acessado.

## 13. Estado Git, workflows e caches

Comandos executados na raiz:

| Comando exato | Codigo | Resultado |
|---|---:|---|
| `git ls-files --stage` | 0 | nenhuma entrada; indice real vazio |
| `git rev-parse --verify HEAD` | 128 | `HEAD` inexistente |
| `git tag --list` | 0 | nenhuma tag |
| `git reflog show --all` | 0 | nenhuma entrada |
| `find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -print` | 0 | nenhum workflow YAML ativo |
| `find tools/security \( -type d -name __pycache__ -o -type f -name '*.pyc' \) -print` | 0 | nenhum cache Python |

## 14. Bloqueios e resposta final

Bloqueios para execucao da S07: nenhum.

> **Estado final do executor:** `IN_PROGRESS — aguardando revisao do
> orquestrador`. O executor nao declara `ACCEPTED`.

## 15. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS — CORRECOES REQUERIDAS`  
> **Data:** `2026-07-28`

A S07 permanece em andamento. O saneamento de valores, logs e identidade de
tenant esta consistente, mas dois contratos operacionais ainda nao foram
atendidos de forma efetiva.

### 15.1 Achados bloqueantes

1. **A transacao do bootstrap root nao esta garantida.**

   `RootUserInitializer.initRootUser()` continua combinando `@PostConstruct`
   e `@Transactional`. A callback de `@PostConstruct` ocorre durante a
   inicializacao do bean, antes da chamada normal atraves do proxy
   transacional. Portanto, a anotacao isolada nao comprova que consulta,
   encode e persistencia ocorram dentro da transacao prometida.

   Substituir o gatilho por mecanismo executado depois da criacao do proxy,
   preferencialmente `ApplicationRunner` ou `CommandLineRunner` com metodo
   publico transacional chamado pelo Spring. Alternativamente, usar
   `TransactionTemplate` explicito. Preservar:

   - opt-in;
   - validacao antes de consultar repositorio;
   - falha de configuracao propagada para interromper startup;
   - consulta e criacao atomicas;
   - usuario existente intocado;
   - nenhum valor sensivel em log.

   O validador deve rejeitar a regressao `@PostConstruct` +
   `@Transactional` e exigir o mecanismo transacional efetivo escolhido.
   Adicionar mutante correspondente. Testes unitarios continuam obrigatorios;
   a garantia de wiring/transacao deve ser documentada e, quando viavel,
   coberta por teste de contexto focado sem banco externo.

2. **Os paths de container foram aplicados ao desenvolvimento local.**

   `application.properties` e `application-dev.properties` agora apontam
   uploads para `/app/uploads`. Isso conflita com o contrato do projeto de
   continuar executando localmente por `mvn spring-boot:run`, fora do
   container, e pode exigir criar/escrever em `/app` no host.

   Corrigir o contrato por profile:

   - desenvolvimento/base usa paths relativos ao projeto, configuraveis por
     ambiente, sem depender de `/app`;
   - testes continuam usando diretorio temporario;
   - producao usa defaults de container sob `/app/uploads`,
     `/app/nfe/schemas` e `/app/nfe/xmls`;
   - o futuro Dockerfile podera sobrescrever pelas mesmas variaveis;
   - nenhum profile depende de `/home/gregorio`;
   - path de certificado permanece vazio/configuravel.

   O mesmo isolamento por profile deve valer para os valores que
   `ConfigSeeder` grava em `nfe_schema_path` e `nfe_xml_path`: uma execucao
   local nao deve persistir paths de container. Use propriedades Spring
   configuraveis em vez de congelar `/app` diretamente no Java.

   Atualizar o validador e adicionar mutantes que provem:

   - base/dev rejeitam `/app`;
   - base/dev rejeitam `/home/gregorio`;
   - prod exige defaults `/app`;
   - teste exige diretorio temporario;
   - seeder usa propriedades injetadas, sem path pessoal ou de container
     hardcoded.

### 15.2 Ajuste da interpretacao do contrato

A task S07 congelou `/app` de forma ampla. Esta revisao refina esse ponto
porque a arquitetura superior e a decisao do usuario preservam o
desenvolvimento local manual. `/app` e contrato de producao/container, nao de
todos os profiles.

A task permanece imutavel. Esta secao e a autoridade corretiva para a nova
execucao.

### 15.3 Preservacoes obrigatorias

- manter todas as correcoes ja entregues para credenciais, tenant e logs;
- nao reintroduzir nenhum valor removido;
- nao sobrescrever ou acessar banco existente;
- nao criar Dockerfile, Compose, workflow, UI ou `release_control`;
- alterar somente os arquivos ja autorizados na S07 e este relatorio;
- nao alterar a task S07 nem o tracker;
- executar novamente o validador, a suite Python e `mvn -B verify`;
- nao testar `website_back`;
- nao instalar dependencia nem acessar GitHub, GHCR, DNS ou VPS;
- manter indice Git vazio e ausencia de `HEAD`, tag, reflog, workflow e cache
  Python;
- manter estado final do executor como `IN_PROGRESS`.

### 15.4 Resposta esperada

Atualizar este relatorio com:

- resposta individual aos dois achados;
- mecanismo transacional escolhido e sua evidencia;
- matriz final de paths `base/dev/test/prod`;
- novos mutantes;
- comandos, CWD, codigos e totais finais;
- arquivos alterados;
- preservacao de escopo e estado Git.

A S08 nao sera criada enquanto estes dois contratos permanecerem
divergentes.

---

## 16. Resposta as correcoes do ciclo 1

> **Estado do executor:** `IN_PROGRESS`  
> **Data:** `2026-07-28`  
> **CWD principal:** `/home/gregorio/git/baronesa/emporio`

### 16.1 Resposta individual aos dois achados

1. **Gatilho transacional do bootstrap: corrigido.**

   `RootUserInitializer` agora implementa `ApplicationRunner`. O metodo
   publico `run(ApplicationArguments)` possui `@Transactional` e e invocado
   pelo Spring depois da criacao do bean/proxy. `@PostConstruct` foi removido.
   Validacao ocorre antes da consulta; consulta e criacao permanecem dentro da
   mesma chamada transacional; qualquer falha propaga e interrompe o startup.

   O validador exige simultaneamente `ApplicationRunner`, metodo publico
   `run(...)` transacional e ausencia de `@PostConstruct`. O mutante 21
   reconstrói a regressao anterior e e rejeitado. Os 8 testes unitarios
   continuam verdes; o `mvn verify` tambem subiu o contexto Spring e executou
   o runner desabilitado depois da inicializacao do contexto.

2. **Paths por profile: corrigido.**

   Base e desenvolvimento voltaram a usar paths relativos e configuraveis.
   Testes usam o diretorio temporario da JVM. Producao declara defaults de
   container sob `/app`. `ConfigSeeder` recebe os dois paths fiscais por
   propriedades Spring, sem path local ou de container hardcoded em Java.

   O validador e os mutantes 22 a 26 protegem base/dev, host pessoal, prod,
   test e binding do seeder.

### 16.2 Matriz final de paths

| Profile | Uploads | Schemas fiscais | XML fiscal |
|---|---|---|---|
| base | relativo `uploads/...`, sobrescritivel por ambiente | relativo `nfe/schemas`, sobrescritivel | relativo `nfe/xmls`, sobrescritivel |
| dev | relativo `uploads/...`, sobrescritivel por ambiente | herda base relativo | herda base relativo |
| test | diretorio temporario da JVM | diretorio temporario da JVM | diretorio temporario da JVM |
| prod | default `/app/uploads/...`, sobrescritivel | default `/app/nfe/schemas`, sobrescritivel | default `/app/nfe/xmls`, sobrescritivel |

O path do certificado permanece vazio/configuravel no seeder. Nenhum profile
depende de diretorio pessoal.

### 16.3 Arquivos alterados no ciclo

- `backend/src/main/java/com/baronesa/emporio/config/RootUserInitializer.java`;
- `backend/src/main/java/com/baronesa/emporio/ConfigSeeder.java`;
- `backend/src/main/resources/application.properties`;
- `backend/src/main/resources/application-dev.properties`;
- `backend/src/main/resources/application-prod.properties`;
- `backend/src/test/resources/application-test.properties`;
- `backend/src/test/java/com/baronesa/emporio/config/RootUserInitializerTest.java`;
- `tools/security/bootstrap_contract.py`;
- `tools/security/tests/test_bootstrap_contract.py`;
- `docs/infrastructure/deployment/BOOTSTRAP_ADMIN_E_CONFIGURACOES_SENSIVEIS.md`;
- este relatorio.

Credenciais, identidade de tenant, logs e preservacao do banco entregues no
ciclo anterior nao foram relaxados.

### 16.4 Validacoes finais

Na raiz `/home/gregorio/git/baronesa/emporio`:

```bash
python3 tools/security/bootstrap_contract.py validate
```

Codigo `0`; `bootstrap-contract:valid`.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/security/tests \
  -p 'test_bootstrap_contract.py' \
  -v
```

Codigo `0`; **26 testes aprovados**, zero falhas e zero erros. Foram
adicionados 6 mutantes direcionados aos achados do ciclo.

No CWD `/home/gregorio/git/baronesa/emporio/backend`:

```bash
DB_PASSWORD=s07-ephemeral-db-password \
INTEGRATION_SYSTEM_TOKEN_SECRET=s07-ephemeral-integration-token-for-tests \
mvn -B verify
```

Codigo `0`; `BUILD SUCCESS`; **35 testes aprovados**, zero falhas, erros ou
skips. Os 8 testes focados do bootstrap integram esse total.

### 16.5 Estado protegido

| Comando exato, executado na raiz | Codigo | Resultado |
|---|---:|---|
| `git ls-files --stage` | 0 | indice real vazio |
| `git rev-parse --verify HEAD` | 128 | `HEAD` inexistente |
| `git tag --list` | 0 | nenhuma tag |
| `git reflog show --all` | 0 | nenhuma entrada |
| `find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -print` | 0 | nenhum workflow YAML ativo |
| `find tools/security \( -type d -name __pycache__ -o -type f -name '*.pyc' \) -print` | 0 | nenhum cache Python |

Nao houve `git add`, commit, tag, push, instalacao de dependencia ou acesso a
GitHub, GHCR, DNS ou VPS. Nao houve Dockerfile, Compose, workflow, UI,
`release_control`, acesso a banco externo ou teste do `website_back`. A task
S07 e o tracker nao foram alterados.

### 16.6 Estado final

Os dois achados bloqueantes foram corrigidos sem ampliar o escopo. Nao ha
bloqueio tecnico novo registrado pelo executor.

> **Estado final do executor:** `IN_PROGRESS — aguardando revisao do
> orquestrador`. O executor nao declara `ACCEPTED`.

---

## 17. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S07 atende ao contrato apos o ciclo corretivo.

Evidencias aceitas:

- o bootstrap root permanece opt-in e sem credencial default;
- `ApplicationRunner` substituiu `@PostConstruct`;
- o metodo publico `run(ApplicationArguments)` e transacional e chamado pelo
  Spring atraves do bean/proxy;
- configuracao invalida continua interrompendo o startup sem expor valores;
- usuario `SYSTEM` existente permanece intocado;
- seeds sensiveis, identificadores de conta e identidade de tenant continuam
  vazios ou configuraveis;
- logs nao recebem valores de configuracao, email ou password;
- `ConfigSeeder` recebe paths fiscais por propriedades Spring;
- base/dev usam paths relativos e configuraveis;
- testes usam diretorio temporario;
- producao usa defaults sob `/app`;
- nenhum profile depende de diretorio pessoal do host;
- o banco existente nao e sobrescrito ou migrado;
- o validador protege o gatilho transacional e a matriz de profiles;
- os seis novos mutantes cobrem os dois achados do ciclo 1;
- o validador final registrado terminou com codigo `0`;
- a suite Python final registrada passou em 26 de 26 testes;
- o `mvn -B verify` final registrado passou em 35 de 35 testes;
- indice Git, `HEAD`, tags, reflog, ausencia de workflows e caches foram
  preservados;
- nao houve commit, push, instalacao ou acesso externo.

Os estados `IN_PROGRESS` anteriores permanecem como historico. A autoridade
final desta secao altera o estado da S07 para `ACCEPTED`.

A S08 pode agora endurecer e validar exclusivamente as imagens Java do
`backend` e do `website_back`, sem antecipar Compose, gateway, CI ou deploy.
