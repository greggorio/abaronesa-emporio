# S22 — Runtime deployer, persistência e reconciliação GitHub

> **Estado:** `IN_PROGRESS`  
> **Tipo:** implementação backend operacional e contratos  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01 a S21 `ACCEPTED`  
> **Relatório de saída:** `S22-runtime-deployer-persistencia-reconciliacao-github.report.md`

## Instrução para delegação

Execute integralmente esta slice. Antes de alterar qualquer arquivo, leia,
nesta ordem:

1. esta task inteira;
2. `docs/infrastructure/deployment/implementation/README.md`;
3. a Seção 17 do relatório da S21;
4. `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
5. `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
6. `docs/infrastructure/deployment/release-control/contracts/state-machines.yml`;
7. `docs/infrastructure/deployment/release-control/contracts/security-matrix.yml`;
8. `docs/infrastructure/deployment/release-control/RELEASES.md`;
9. `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`;
10. `docs/infrastructure/deployment/release-control/WORKFLOW_IMPLANTACAO.md`;
11. `docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md`;
12. `release_control/README.md` e todo o pacote `release_control` existente;
13. `.github/workflows/deploy-production.yml`;
14. `ops/deploy/schemas/deployment-workflow-outcome.schema.json`;
15. `tools/deploy/deployment_plan.py`;
16. `tools/deploy/deployment_transport.py`.

O executor implementa as decisões deste contrato. Não escolhe framework,
banco, nomes de tabelas, rotas, workflow, política de idempotência, regra de
elegibilidade, semântica de resultado incerto, política de rollback ou
fronteira das próximas slices.

Não altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

## 1. Resultado observável

Ao final, o serviço independente `release_control` também executa em modo
imutável `deployer` e:

- registra somente as rotas deployer do OpenAPI;
- autentica JWT RS256 e aplica os scopes de produção;
- sincroniza e valida releases globais publicadas;
- informa a instalação atual reconciliada;
- marca como elegível somente a próxima release forward da cadeia;
- calcula um plano informativo dos seis componentes e das migrations;
- cria uma operação idempotente e exclusiva de implantação;
- dispara somente `.github/workflows/deploy-production.yml` em `main`;
- correlaciona o workflow por `operationId` e reconcilia reinícios;
- valida integralmente o artifact `deployment-workflow-outcome`;
- só confirma `SUCCEEDED` e troca a instalação atual com evidência remota
  canônica;
- mantém resultado remoto incerto em estado ativo e fail-closed;
- não usa Git local, shell, Docker, socket Docker, SSH, `gh` ou URL arbitrária;
- preserva integralmente o modo publisher e suas regressões.

Nenhum GitHub, GHCR, VPS ou ambiente real será acessado nesta slice. Toda
integração remota dos testes usa transporte HTTP injetado e loopback.

## 2. Decisões arquiteturais fechadas

### 2.1 Um pacote, dois processos mutuamente exclusivos

Manter o pacote Python e a stack tecnológica aceitos na S15. O mesmo artefato
de código suporta dois bootstraps:

```text
RELEASE_CONTROL_MODE=publisher
RELEASE_CONTROL_MODE=deployer
```

O modo é lido somente no bootstrap e é imutável. Em cada processo:

- `publisher` registra apenas health, capabilities e rotas publisher;
- `deployer` registra apenas health, capabilities e rotas deployer;
- uma rota de negócio do outro modo deve resultar em `404`;
- nenhum body, query, header ou claim seleciona o modo;
- services, reconcilers e credenciais do modo inativo não são instanciados;
- os dois modos não compartilham operação, idempotência ou lock de negócio.

Não criar um terceiro modo e não executar os dois routers no mesmo processo.

### 2.2 Identidades imutáveis do deployer

Constantes de código:

```text
mode       = deployer
repository = greggorio/abaronesa-emporio
owner      = greggorio
repo       = abaronesa-emporio
ref        = main
workflow   = deploy-production.yml
api        = https://api.github.com
```

Separar as constantes publisher e deployer por nomes inequívocos. Remover os
nomes genéricos `MODE` e `WORKFLOW` e atualizar o código publisher; não manter
aliases ambíguos.

Somente no profile `test` a base GitHub pode apontar para HTTP loopback. Nenhum
cliente escolhe owner, repository, ref, workflow, host, path ou URL.

### 2.3 Regra comercial de elegibilidade

O runtime não oferece seleção de componentes. O operador escolhe uma release
global, e o BOM integral dos seis componentes continua sendo a autoridade.

Uma release é elegível para implantação somente quando:

- não há instalação atual e `previousRelease` da release é `null`; ou
- há instalação atual reconciliada, a SemVer alvo é maior e
  `target.previousRelease == current.release`;
- os inventários atuais de migrations são prefixos integrais dos inventários
  alvo, conforme S18;
- a release e toda a cadeia foram sincronizadas sem drift.

Logo, exatamente a primeira release ou a próxima release da cadeia pode ter
`eligible=true`. Não permitir salto, downgrade, release corrente, release
futura com predecessor diferente ou escolha parcial do BOM.

### 2.4 Decisão explícita sobre rollback solicitado

S18 é forward-only e S21 não recebe tipo de operação. Permitir o POST de
rollback disparar o workflow atual produziria uma falsa promessa de downgrade
seguro. Nesta slice:

- a rota `POST /api/deployment-control/v1/rollbacks` existe e exige
  autenticação, scope, header e body válidos;
- depois da validação sintática, retorna sempre `409 RELEASE_NOT_ELIGIBLE`;
- registra exatamente um audit append-only `rollback.rejected`, com ator,
  release alvo e código `RELEASE_NOT_ELIGIBLE`, mas não cria operação,
  idempotência ou dispatch;
- `deployment:rollback` não aparece em `capabilities` nesta versão;
- a matriz de segurança continua reservando o scope e a rota;
- nenhum teste pode simular rollback como implantação normal.

Esta é uma indisponibilidade funcional declarada, não um TODO silencioso. Uma
slice posterior deverá versionar planner/bundle/workflow para rollback antes
de anunciar a capability. Não alterar S18–S21 nesta slice.

No OpenAPI deployer, elevar `info.version` de `1.0.0` para `1.1.0`, retirar
`deployment:rollback` apenas do exemplo de capabilities e declarar
`RELEASE_NOT_ELIGIBLE` como a resposta `409` operacional da rota nesta versão.
O valor continua permitido no enum para a futura ativação; não remover a rota,
o request, o scope nem os demais responses.

### 2.5 Correção contratual de observabilidade

O workflow S21 publica somente outcome terminal. Ele não produz evidência
confiável dos estados internos `PULLING`, `BACKING_UP`, `MIGRATING`,
`UPDATING`, `VERIFYING` ou `ROLLING_BACK`.

Atualizar coordenadamente `state-machines.yml`, documento humano, validador e
testes para:

- elevar `schema_version` da máquina de estados de `1` para `2`;
- manter os estados intermediários reservados para telemetria futura;
- adicionar `QUEUED -> SUCCEEDED` e `QUEUED -> ROLLED_BACK`, ator
  `reconciler`, ambas com `requires_remote_evidence: true`;
- preservar `QUEUED -> FAILED`;
- proibir o runtime S22 de inventar estados intermediários;
- substituir `nonterminal_requires_workflow_run_id: true` por semântica
  machine-readable: o binding é opcional antes da descoberta, obrigatório e
  imutável depois da correlação, salvo aumento de attempt do mesmo run;
- manter estado terminal sem regressão.

Não alterar a máquina publisher.

### 2.6 Correção contratual da instalação incerta

Adicionar resposta `409` ao GET `/api/deployment-control/v1/current`, com
código público `CURRENT_INSTALLATION_UNRECONCILED`. Esse resultado é usado
quando houve execução potencialmente mutável sem evidência terminal suficiente
ou quando `databaseRestoreRequired=true`.

Atualizar OpenAPI, documento humano, erros públicos, validador e testes juntos.
Não devolver uma release como reconciliada nessas condições e não apagar a
evidência de incerteza.

## 3. Fronteira de arquivos

### 3.1 Criar

```text
release_control/migrations/versions/0002_deployer_runtime.py
release_control/src/emporio_release_control/deployer_api.py
release_control/src/emporio_release_control/deployer_schemas.py
release_control/src/emporio_release_control/deployer_service.py
release_control/src/emporio_release_control/deployer_reconciliation.py
release_control/src/emporio_release_control/deployment_artifacts.py
release_control/tests/test_deployer_api.py
release_control/tests/test_deployer_persistence.py
release_control/tests/test_deployer_reconciliation.py
release_control/tests/test_deployer_remote_contract.py
release_control/tests/test_mode_isolation.py
tools/deploy/validate_deployer_runtime.py
tools/deploy/tests/test_deployer_runtime_contract.py
docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md
docs/infrastructure/deployment/implementation/slices/S22-runtime-deployer-persistencia-reconciliacao-github.report.md
```

### 3.2 Alterar somente

```text
release_control/.env.example
release_control/README.md
release_control/src/emporio_release_control/api.py
release_control/src/emporio_release_control/config.py
release_control/src/emporio_release_control/constants.py
release_control/src/emporio_release_control/errors.py
release_control/src/emporio_release_control/github.py
release_control/src/emporio_release_control/main.py
release_control/src/emporio_release_control/persistence.py
release_control/src/emporio_release_control/reconciliation.py
release_control/src/emporio_release_control/service.py
release_control/src/emporio_release_control/sync.py
release_control/tests/conftest.py
release_control/tests/test_api.py
release_control/tests/test_config_security.py
release_control/tests/test_persistence_service.py
release_control/tests/test_reconciliation.py
release_control/tests/test_remote_contract.py
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/api/deployer.openapi.yml
docs/infrastructure/deployment/release-control/contracts/state-machines.yml
docs/infrastructure/deployment/release-control/contracts/security-matrix.yml
tools/releases/release_control_contract.py
tools/releases/tests/test_release_control_contract.py
.gitignore
```

`api.py`, `service.py` e `reconciliation.py` continuam sendo implementações
publisher. Alterações neles são autorizadas somente para nomes explícitos,
interfaces compartilhadas ou compatibilidade com o bootstrap dual; não mover
sem necessidade a lógica publisher para os novos módulos.

Não alterar `pyproject.toml` nem `uv.lock`: a S22 não exige dependência nova.
`.gitignore` só pode receber cache realmente criado pela execução.

### 3.3 Não alterar

- esta task e o tracker;
- tasks/reports anteriores, exceto o relatório S21 já aceito pelo orquestrador;
- quatro workflows GitHub Actions;
- `ops/deploy/**`;
- `tools/deploy/deployment_plan.py`, executor, CLI e adapters S18–S21;
- schemas/examples S18–S21;
- código Java, Node, frontends, Dockerfiles, Compose e gateway;
- migrations dos dois backends comerciais;
- UI publisher ou qualquer UI de produção;
- configuração, secret, environment ou recurso real externo;
- qualquer arquivo S23.

## 4. Configuração e bootstrap

### 4.1 Settings compartilhadas

`Settings.mode` aceita exatamente `publisher | deployer`. Preservar todos os
gates de `runtime`, `development` e `test` aceitos na S15/S16.

Parâmetros existentes continuam iguais. Renomear o limite mutável genérico:

```text
RELEASE_CONTROL_PUBLISH_RATE_PER_MINUTE=5
```

e adicionar:

```text
RELEASE_CONTROL_DEPLOY_RATE_PER_MINUTE=5
RELEASE_CONTROL_ROLLBACK_RATE_PER_MINUTE=2
```

Faixas: deploy `1..30`, rollback `1..10`. No publisher, as duas novas settings
podem existir com default, mas não são usadas. No deployer,
`PUBLISH_RATE_PER_MINUTE` não é usado.

Não adicionar configuração de repository, workflow, ref, owner ou paths.

### 4.2 Factory ASGI

`emporio_release_control.main:app` permanece o entrypoint. Criar uma factory
testável que:

1. carrega settings;
2. cria engine/session, GitHub App client e verificador JWT;
3. instancia apenas o grafo do modo ativo;
4. registra apenas o reconciler ativo;
5. encerra thread e clientes HTTP no shutdown.

Falha de migration, private key, modo, profile ou dependência interrompe o
bootstrap. Não fazer fallback para outro modo.

### 4.3 Readiness deployer

`/health/ready` retorna `200 {"status":"ok"}` somente quando:

- migration Alembic corrente é `0002_deployer_runtime`;
- banco responde;
- private key foi validada;
- o último sync de releases está verde;
- o último ciclo de reconciliação terminou sem drift;
- a instalação está ausente de forma limpa ou reconciliada;
- não existe incerteza operacional que torne o estado atual desconhecido.

Caso contrário retorna apenas `503 {"status":"unavailable"}`. Liveness não
consulta dependências.

## 5. Persistência PostgreSQL

### 5.1 Migration `0002_deployer_runtime`

`down_revision = "0001_publisher_runtime"`. Criar exatamente:

```text
rc_deployment_operation
rc_deployment_idempotency_key
rc_current_installation
```

Não apagar, renomear ou recriar tabela S15. O downgrade remove apenas as três
tabelas S22, na ordem segura.

### 5.2 `rc_deployment_operation`

Campos obrigatórios:

```text
operation_id                 varchar(36) PK
operation_type               deployment | rollback
mode                         deployer
state                        estado deployer
actor_sub                    varchar(255)
scopes                       jsonb
target_release               varchar(64)
source_release               varchar(64) nullable
rollback_reason              varchar(1000) nullable
request_json                 jsonb
request_hash                 char(64)
idempotency_hash             char(64)
workflow_run_id              bigint nullable
workflow_attempt             integer nullable
workflow_run_url             varchar(512) nullable
control_sha                  char(40) nullable
dispatch_state               NOT_SENT | SENT | UNCERTAIN | CONFIRMED
remote_state                 varchar(30) nullable
transport_status             CONFIRMED | INDETERMINATE nullable
database_restore_required    boolean nullable
outcome_sha256               varchar(71) nullable
error_code                   varchar(100) nullable
error_message                varchar(300) nullable
created_at                   timestamptz
updated_at                   timestamptz
finished_at                  timestamptz nullable
active_slot                  integer nullable
version                      integer
```

IDs:

```text
deployment: dep_ + 32 hex minúsculos
rollback:   rbk_ + 32 hex minúsculos
```

Na S22 somente `dep_` é criado, pois rollback está indisponível. O schema e a
tabela já reservam `rbk_` para a slice posterior.

Índice parcial unique em `active_slot=1`. Toda operação não terminal conserva
slot `1`; terminal libera na mesma transação. Não criar foreign key para
`rc_release_snapshot`, pois o sync substitui snapshots atomicamente.

### 5.3 Idempotência deployer

Tabela `rc_deployment_idempotency_key`, sem reutilizar a FK publisher:

```text
mode | route | actor_sub | key_hmac
```

é unique. `operation_id` referencia `rc_deployment_operation` com RESTRICT.

- HMAC-SHA-256 usa `HASH_PEPPER`;
- request hash usa JSON canônico `{"release":"vX.Y.Z"}\n`;
- chave bruta nunca é persistida ou logada;
- mesma chave, ator, rota e request retorna a mesma operação e
  `Idempotency-Replayed: true`;
- mesma chave com request diferente retorna `409 IDEMPOTENCY_CONFLICT`;
- rotas deployment/rollback não compartilham namespace;
- cleanup remove apenas chaves expiradas de operações terminais;
- operação e audit nunca são removidos nesta slice.

### 5.4 Instalação atual

`rc_current_installation` é singleton com `singleton_id=1` e CHECK. Campos:

```text
release                    varchar(64) nullable
source_commit              char(40) nullable
previous_release           varchar(64) nullable
installed_at               timestamptz nullable
reconciled                 boolean
uncertainty_code           varchar(100) nullable
last_operation_id          varchar(36) nullable
updated_at                 timestamptz
version                    integer
```

Invariantes:

- ausência da linha significa primeira instalação limpa;
- reconciliada exige release, commit, installed_at, last_operation_id e
  `uncertainty_code=null`;
- não reconciliada pode conservar o último release conhecido ou todos os
  campos de release nulos na primeira instalação;
- somente outcome confirmado `SUCCEEDED` troca release/commit/previous e
  marca reconciliada;
- resultado `FAILED`/`ROLLED_BACK` com restore `false` mantém a instalação;
- `databaseRestoreRequired=true` ou resultado indeterminado marca
  `reconciled=false` e preserva evidência;
- nunca inferir instalação corrente somente da release mais recente.

### 5.5 Auditoria

Reutilizar `rc_audit_event`, append-only. Metadata é allowlisted e pode conter
release, state, run ID, attempt, transport status e código estável. Não contém
JWT, idempotency key, token GitHub, private key, body remoto, URL arbitrária,
host, path, stdout, stderr, exception ou stack trace.

## 6. Sincronização e plano informativo

### 6.1 Releases

Reutilizar a validação S15 de releases, refs e três assets. No modo deployer:

- executar apenas `sync_releases`; nunca `sync_candidates`;
- validar release/tag lightweight/assets/sidecar/metadata/JSON/BOM/cadeia;
- um ciclo inválido não substitui o último snapshot válido;
- marcar domínio `releases` em drift e readiness indisponível;
- revalidar a release alvo imediatamente antes da transação de criação.

O GET de releases ordena SemVer decrescente, usa o mesmo `CursorCodec` HMAC e
cursor canônico `{"release":"vX.Y.Z"}`, e calcula `eligible` pela Seção 2.3.

### 6.2 Plano HTTP

O GET `/releases/{releaseId}/plan` produz apenas a projeção do OpenAPI:

- seis componentes na ordem canônica;
- `KEEP` quando digest atual e alvo coincidem, senão `UPDATE`;
- primeira instalação usa `currentDigest=null` e seis `UPDATE`;
- `migrationRequired=true` se algum inventário alvo acrescenta migrations;
- `backupRequired == migrationRequired`;
- fonte é a instalação reconciliada ou `null`.

O alvo deve ser elegível pela Seção 2.3. A projeção deve possuir testes de
paridade contra a saída real de S18 para fixtures de primeira instalação,
KEEP/UPDATE e migrations. Ela é informativa; S18 no workflow continua sendo a
única autoridade operacional e recalcula tudo.

Não importar `tools/deploy` no runtime e não copiar lógica de execução,
filesystem, bundle ou Compose para `release_control`.

## 7. API deployer exata

Registrar exatamente:

```text
GET  /health/live
GET  /health/ready
GET  /api/release-control/v1/capabilities
GET  /api/deployment-control/v1/current
GET  /api/deployment-control/v1/releases
GET  /api/deployment-control/v1/releases/{releaseId}/plan
POST /api/deployment-control/v1/deployments
GET  /api/deployment-control/v1/deployments/{deploymentId}
POST /api/deployment-control/v1/rollbacks
```

Nenhuma rota adicional, alias, endpoint admin, cancel, retry, webhook, logs ou
configuração. OpenAPI runtime desabilitado, como no publisher.

### 7.1 Autorização

```text
deployment:read      capabilities, current, releases, plan, status
deployment:execute   POST deployments
deployment:rollback  POST rollbacks
```

Um scope não implica outro. Health é público. Preservar middleware, headers de
segurança, CORS, limite de body, content type e problem details S15.

Capabilities S22 retorna exatamente:

```json
{"apiVersion":"v1","capabilities":["deployment:read","deployment:execute"],"mode":"deployer"}
```

A ordem é a acima. Não anunciar rollback.

### 7.2 Current

- sem linha: `404 NOT_FOUND`;
- linha reconciliada: `200 CurrentInstallation`;
- linha não reconciliada: `409 CURRENT_INSTALLATION_UNRECONCILED`;
- nunca incluir previous release, incerteza ou detalhes internos na resposta
  pública.

### 7.3 POST deployment

Ordem obrigatória:

1. validar bearer, scope, rate limit, content type, bytes, schema e
   `Idempotency-Key`;
2. consultar replay idempotente;
3. exigir instalação ausente limpa ou reconciliada;
4. exigir alvo sincronizado e elegível;
5. revalidar release remotamente fora da transação;
6. iniciar transação, adquirir advisory xact lock do escopo idempotente;
7. repetir replay, estado atual e elegibilidade sob lock;
8. recusar outro `active_slot=1` com `409 PRODUCTION_OPERATION_ACTIVE`;
9. inserir operação `QUEUED`, idempotência, slot e audit atomicamente;
10. commit antes do dispatch;
11. disparar GitHub fora da transação;
12. persistir resultado do dispatch e devolver `202`.

`activeOperationId` aparece no conflito somente porque o chamador já possui
`deployment:execute`; não incluir ator, release ou URL da operação ativa.

Falhas de dispatch:

- falha antes do POST: `FAILED/WORKFLOW_DISPATCH_NOT_SENT`, libera slot;
- resposta `400`, `401`, `403`, `404`, `422` ou `429`:
  `FAILED/WORKFLOW_DISPATCH_REJECTED`, libera slot;
- qualquer outro status diferente de `204`, inclusive `5xx`, mantém `QUEUED`,
  slot e `dispatch_state=UNCERTAIN`;
- falha de transporte depois de iniciar POST: mantém `QUEUED`, slot e
  `dispatch_state=UNCERTAIN`;
- sucesso `204`: `dispatch_state=SENT`;
- POST nunca é repetido automaticamente.

O endpoint retorna a operação persistida mesmo se o dispatch terminou em
falha após a criação. Não vazar detalhes GitHub.

### 7.4 POST rollback

Aplicar exatamente a Seção 2.4. Body inválido retorna `422`; idempotency header
inválido retorna `400`; autenticação/scope/rate limit mantêm `401/403/429`;
request sintaticamente válido retorna `409 RELEASE_NOT_ELIGIBLE`. Zero
dispatch, zero operação e somente o audit de recusa prescrito.

## 8. Transporte GitHub App

Reutilizar autenticação e GETs S15. Adicionar método explícito
`dispatch_deployment(operation_id, release)` que aceita apenas IDs `dep_` e
SemVer canônica e envia:

```json
{
  "ref": "main",
  "inputs": {
    "operation_id": "dep_<32hex>",
    "release": "vX.Y.Z"
  }
}
```

Endpoint fixo:

```text
/repos/greggorio/abaronesa-emporio/actions/workflows/deploy-production.yml/dispatches
```

Aceitar somente `204`. Não permitir método genérico com workflow recebido por
parâmetro. O método publisher continua restrito a `publish-release.yml`.

GET pode renovar token uma vez após `401`; POST nunca é repetido. Redirect,
payload excessivo, endpoint divergente e resposta inesperada falham fechado.

## 9. Reconciliação remota

### 9.1 Ciclo e lock

O ciclo deployer usa advisory lock próprio, diferente do publisher. Em ordem:

1. sync de releases;
2. cleanup de idempotência terminal;
3. reconciliação de operações deployer não terminais;
4. atualização do estado verde/drift do domínio `deployments`.

Falha em uma operação é auditada e não encerra a thread. Não transformar
inconsistência remota em sucesso ou falha terminal sem evidência.

### 9.2 Descoberta do run

Consultar somente runs do workflow fixo em `main` e evento
`workflow_dispatch`. Um match exige integralmente:

```text
name          = Deploy Production
path          = .github/workflows/deploy-production.yml@main
event         = workflow_dispatch
head_branch   = main
display_title = deploy-production-<operationId>
repository    = greggorio/abaronesa-emporio
headRepository= greggorio/abaronesa-emporio
created_at    >= operation.created_at - 5 segundos
id/attempt    inteiros positivos
head_sha      40 hex minúsculos
html_url      URL canônica derivada do run ID
```

- zero match antes de 600 s: manter `QUEUED`;
- zero match depois de 600 s: manter operação e slot ativos, registrar
  `WORKFLOW_DISPATCH_UNCONFIRMED` e marcar domínio/instalação não reconciliados;
- mais de um match: manter ativo, registrar `WORKFLOW_RUN_AMBIGUOUS`, marcar
  domínio e instalação como não reconciliados;
- após binding, run ID e control SHA são imutáveis;
- rerun do mesmo run pode aumentar `workflow_attempt`;
- attempt menor, igual divergente ou outro run ID falha fechado.

Não inferir estados de negócio pelos nomes/status dos jobs.

### 9.3 Artifact terminal

Quando o run estiver `completed`, consultar artifacts mesmo se a conclusão do
workflow for `failure`, pois S21 falha deliberadamente para `ROLLED_BACK`,
`FAILED` e `INDETERMINATE`.

Exigir exatamente um artifact ativo chamado
`deployment-workflow-outcome`, com:

- ID positivo;
- digest `sha256:<64hex>`;
- URLs REST canônicas do repositório;
- `workflow_run.id` e `head_sha` iguais ao run;
- ZIP máximo 16 MiB;
- exatamente `deployment-workflow-outcome.json` regular;
- sem diretório, link, path traversal, duplicata, entry extra ou bomb;
- arquivo máximo 64 KiB;
- JSON UTF-8 canônico com LF;
- schema S21 válido;
- operation, target release, run ID, attempt e control SHA iguais aos bindings.

Reutilizar os primitives de ZIP/canonicalidade S15; não criar extrator menos
restritivo.

### 9.4 Aplicação do outcome

Aplicar em uma transação com row lock e optimistic version:

- `CONFIRMED/SUCCEEDED`: operação `SUCCEEDED`, instalação atual recebe alvo,
  commit do snapshot validado, `previous_release=source_release`, timestamp do
  processamento, reconciliada; libera slot;
- `CONFIRMED/SUCCEEDED/REMOTE_CLEANUP_FAILED`: mesma confirmação da instalação,
  operação mantém esse `errorCode` informativo e audit; libera slot;
- `CONFIRMED/ROLLED_BACK`: operação `ROLLED_BACK`, instalação anterior é
  mantida; se restore é `true`, marca instalação não reconciliada; libera slot;
- `CONFIRMED/FAILED`: operação `FAILED`, instalação anterior é mantida; se
  restore é `true`, marca instalação não reconciliada; libera slot;
- `INDETERMINATE`: operação permanece `QUEUED`, ativa, com transport status e
  código; instalação vira não reconciliada; não libera slot;
- artifact ausente, inválido, ambíguo ou com binding divergente após possível
  execução: mantém operação ativa, marca drift/instalação incerta e audita.

Uma conclusão GitHub `success` só é coerente com outcome
`CONFIRMED/SUCCEEDED` e erro nulo. As demais combinações falham fechado. O
outcome válido é a autoridade do estado remoto, não a conclusão isolada.

Estado terminal nunca regride. Replay do mesmo outcome produz zero mudança
semântica e zero novo dispatch.

## 10. Segurança e observabilidade

- JWT aceita somente RS256, issuer/audience/exp/sub e scopes exatos;
- credencial GitHub do deployer não chama publication workflow nem escreve
  tag/release/package;
- nunca logar secret, token, JWT, key, idempotency key, body remoto, env,
  stdout/stderr ou exception;
- logs estruturados contêm somente evento, código estável, trace ID e IDs
  opacos;
- responses inesperadas são normalizadas sem traceback;
- CORS sem wildcard e sem credentials;
- limite de payload aplicado antes do parse;
- rate limiter separado para read/deploy/rollback;
- nenhum filesystem operacional, subprocess, Git, Docker ou SSH no runtime.

## 11. Testes causais obrigatórios

Criar no mínimo 60 provas S22, incluindo ao menos:

1. bootstrap publisher/deployer e rejeição de modo desconhecido;
2. isolamento bidirecional de routers, services e reconcilers;
3. migration upgrade/downgrade real em PostgreSQL 16;
4. regressão integral das tabelas publisher após migration 0002;
5. constraints, singleton e unique active slot;
6. primeira release elegível e demais inelegíveis;
7. próxima release elegível sem salto;
8. predecessor divergente e migration não forward inelegíveis;
9. plano de seis componentes em ordem e paridade S18;
10. current ausente, reconciliado e incerto;
11. capabilities sem rollback;
12. autenticação e scopes exatos por rota;
13. body/content type/limite/Idempotency-Key;
14. replay idempotente e conflito por body;
15. corrida simultânea da mesma chave cria uma operação;
16. corrida de chaves distintas cria uma ativa e um conflito;
17. revalidação alvo antes e dentro da transação;
18. dispatch exato para workflow/ref/inputs fixos;
19. zero retry de POST e classificação not-sent/rejected/uncertain;
20. descoberta zero, única, ambígua e timeout;
21. binding integral de run e rerun do mesmo run ID;
22. rejeição de outro run, attempt regressivo e control SHA divergente;
23. artifact REST, digest, URL e vínculo workflow_run;
24. ZIP traversal, link, extra, duplicata, tamanho e bomb;
25. JSON não canônico, schema inválido e cada binding divergente;
26. conclusão GitHub failure com outcome confirmado terminal;
27. `SUCCEEDED`, cleanup failed, `ROLLED_BACK`, `FAILED` e
    `INDETERMINATE`;
28. restore required marca current não reconciliada;
29. terminal não regride e outcome replay não redispara;
30. rollback válido retorna 409, audit de recusa e zero efeito operacional;
31. readiness em migration/sync/reconcile/incerteza;
32. logs e problems não vazam valores sensíveis;
33. erros remotos não encerram a thread;
34. publisher mantém todas as rotas e comportamentos S15/S16.

Testes remotos usam `httpx.MockTransport` ou servidor loopback. É proibido
GitHub real, DNS externo, GHCR, SSH, VPS e produção.

Cobertura obrigatória do pacote `emporio_release_control`: ao menos `90%` de
branches, sem omitir os novos módulos.

## 12. Validadores e matriz final

Executar e registrar comandos, exits, contagens e durações:

```bash
cd /home/gregorio/git/baronesa/emporio/release_control
uv sync --frozen --extra test
uv run alembic upgrade head
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90
uv run ruff check .
uv run mypy --strict src tests

cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py'
git diff --check
```

Usar PostgreSQL efêmero local como nas suites existentes. Instalação de
dependências é permitida somente dentro de ambiente virtual/cache efêmero da
S22. Remover `.venv`, caches Python, coverage e recursos efêmeros ao final.

Não executar Maven, npm, Docker build das aplicações, Compose operacional,
workflow remoto, login GHCR, SSH ou deploy.

O validador S22 deve falhar para mutantes que:

- misturam routers ou modos;
- alteram workflow/ref/repository;
- habilitam rollback na capability;
- removem idempotência/slot;
- aceitam salto de release;
- inventam estado intermediário;
- terminalizam outcome indeterminado;
- aceitam artifact/binding incompleto;
- confirmam current sem evidência;
- adicionam rota ou dependência proibida.

## 13. Estado Git protegido

Ao início e ao fim, registrar:

```bash
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -printf '%f\n' | sort
find . -type d -name __pycache__ -print
find . -type f -name '*.pyc' -print
```

Preservar:

- índice real vazio;
- HEAD inexistente;
- zero tags/reflog;
- exatamente quatro workflows ativos;
- nenhum `git add`, commit, tag ou push;
- nenhuma S23;
- nenhum cache ou recurso efêmero residual.

## 14. Relatório obrigatório

Criar somente ao final:

```text
docs/infrastructure/deployment/implementation/slices/S22-runtime-deployer-persistencia-reconciliacao-github.report.md
```

O relatório deve conter:

- CWD e ordem de leitura;
- arquivos criados/alterados;
- decisões implementadas por seção desta task;
- schema e evidência da migration;
- matriz das provas causais;
- comandos exatos, exits, contagens, cobertura e duração;
- estado Git inicial/final;
- ausência de rede/produção;
- divergências reais e itens `NAO DETERMINADO`;
- confirmação de que task/tracker/S23 não foram alterados.

Estado final do executor:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

O executor não declara `ACCEPTED` nem cria a próxima slice.

## 15. Critérios de aceite do orquestrador

A S22 só pode ser aceita se:

- os dois modos forem realmente isolados;
- publisher não regredir;
- deployer expuser exatamente a superfície definida;
- release elegível for determinada automaticamente e sem salto;
- plano HTTP tiver paridade causal com S18;
- operação for idempotente, exclusiva e restart-safe;
- dispatch estiver fixo ao workflow de produção;
- outcome S21 for validado integralmente;
- incerteza nunca virar sucesso/falha inventada;
- instalação atual só mudar com sucesso remoto confirmado;
- rollback permanecer honestamente indisponível e sem dispatch;
- migration e regressões passarem;
- cobertura de branches for ao menos 90%;
- documentação corresponder ao comportamento;
- estado protegido permanecer intacto.

## 16. Fora de escopo e próxima fronteira

Fora da S22:

- rollback comercial/downgrade;
- UI de produção;
- ponte de identidade do ERP para scopes deployer;
- Dockerfile/Compose do `release_control` deployer;
- bootstrap de usuário, paths, systemd e permissões na VPS;
- instalação do control root;
- configuração real de GitHub App, environment, secrets e variables;
- Nginx/TLS do host;
- primeiro commit/push;
- primeiro deploy;
- restore operacional acompanhado, alertas e monitoramento.

Após aceite da S22, o orquestrador decidirá a S23 pela menor dependência
restante. A prioridade prevista é fechar a ponte de identidade e a UI de
produção para atualização forward; rollback comercial continuará separado até
existir contrato de planner/bundle compatível com downgrade.
