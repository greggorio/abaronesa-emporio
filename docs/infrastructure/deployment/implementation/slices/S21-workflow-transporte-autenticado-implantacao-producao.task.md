# S21 — Workflow e transporte autenticado de implantação em produção

> **Estado:** `IN_PROGRESS`  
> **Tipo:** implementação local do workflow e protocolo de transporte; sem acesso à VPS  
> **Dependências:** S01 a S20 `ACCEPTED`  
> **Relatório de saída:** `S21-workflow-transporte-autenticado-implantacao-producao.report.md`

## 1. Objetivo fechado

Implementar a fronteira entre o GitHub Actions e o CLI operacional entregue
pela S20.

A S21 deve entregar:

1. o único workflow canônico `.github/workflows/deploy-production.yml`;
2. validação fail-closed do dispatch, ator, release global publicada e
   contexto do workflow;
3. snapshot autenticado e somente leitura do estado instalado na VPS;
4. geração do bundle S18 no runner a partir da release publicada e do estado
   confirmado;
5. empacotamento e transporte autenticado do bundle por OpenSSH;
6. instalação remota atômica e idempotente do bundle;
7. chamada remota do CLI S20 como usuário dedicado `deploy-emporio`;
8. outcome machine-readable que distingue resultado confirmado de resultado
   remoto indeterminado;
9. validadores, testes causais e documentação operacional.

Esta slice materializa código e workflow, mas **não**:

- configura secrets ou variables reais;
- cria usuário, diretório ou arquivo na VPS;
- dispara workflow;
- conecta ao GitHub, GHCR, DNS, VPS ou produção;
- executa deploy real.

## 2. Decisões de arquitetura congeladas

O executor não deve escolher alternativas para os pontos abaixo.

### 2.1 Autoridade

- O operador escolhe somente `operation_id` e uma release global SemVer.
- Imagens, componentes, dependências, bancos, migrations, serviços e ordem
  continuam calculados exclusivamente por S13 e S18.
- O workflow não recebe imagem, digest, tag avulsa, componente, host, porta,
  usuário, path, comando, URL ou action como input.
- O workflow não reconstrói imagens e não altera o BOM publicado.
- O planner S18 permanece a única autoridade para `KEEP`/`UPDATE`, migrations,
  backup e cadeia da release.

### 2.2 Local de planejamento

O bundle é gerado no runner GitHub hospedado:

```text
release global publicada + snapshot confirmado da VPS
                           |
                           v
              deployment_plan.py generate
                           |
                           v
                   bundle S18 validado
```

Não gerar o plano dentro da VPS. A VPS apenas:

1. exporta um snapshot confirmado;
2. recebe e valida um bundle já fechado;
3. instala o bundle atomicamente;
4. chama o CLI S20.

O Compose usado pelo planner é exatamente
`ops/compose/compose.prod.yml` do checkout de controle do próprio run. Não
buscar Compose por input nem aceitar arquivo vindo da VPS.

### 2.3 Transporte

Usar somente os binários OpenSSH `ssh` e `scp`, executados por
`subprocess` com `shell=False`. Não usar action SSH de terceiro, `rsync`,
`sshpass`, agent forwarding, senha, `sudo`, `root`, `eval`, `bash -c`,
`StrictHostKeyChecking=no` ou `UserKnownHostsFile=/dev/null`.

O usuário remoto é literal e imutável:

```text
deploy-emporio
```

Paths remotos literais:

```text
DEPLOY_ROOT=/opt/sistemas/emporio
CONTROL_ROOT=/opt/sistemas/emporio/shared/control
REMOTE_HELPER=/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py
DEPLOY_SCRIPT=/opt/sistemas/emporio/shared/control/ops/deploy/deploy-release.sh
INCOMING_ROOT=/opt/sistemas/emporio/shared/deploy/incoming
SNAPSHOT_ROOT=/opt/sistemas/emporio/shared/deploy/snapshots
```

Host e porta vêm somente das variables protegidas do environment GitHub
`production`; nunca de input:

```text
PRODUCTION_SSH_HOST
PRODUCTION_SSH_PORT
```

Materiais secretos do mesmo environment:

```text
PRODUCTION_SSH_PRIVATE_KEY
PRODUCTION_SSH_KNOWN_HOSTS
```

A autenticação GHCR pertence à VPS e ao bootstrap futuro. O workflow não
transporta token GHCR e não executa `docker login`.

### 2.4 Fronteira da VPS

`deployment-remote.py` é um helper versionado, mas não será instalado nesta
slice. A futura slice de bootstrap deverá instalar o control root e preparar
usuário/permissões.

O helper remoto:

- nunca aceita root, host, path ou comando por argumento;
- deriva todos os paths das constantes acima;
- aceita apenas `operationId`, `release`, SHA-256 do archive e subcomando
  allowlisted;
- não lê nem imprime `.env`;
- não executa Docker diretamente;
- invoca somente o wrapper S20 após instalar o bundle;
- emite uma única linha JSON canônica e sanitizada.

### 2.5 Incerteza remota

Perda de SSH depois do início do CLI não autoriza afirmar `FAILED`, porque a
transação pode continuar na VPS.

O outcome separa:

```text
CONFIRMED
INDETERMINATE
```

- `CONFIRMED`: a linha terminal do CLI S20 foi recebida e validada;
- `INDETERMINATE`: não foi possível provar o resultado remoto.

Em `INDETERMINATE`, `deploymentState`, `databaseRestoreRequired` e o resultado
terminal são nulos. Um retry autorizado usa o mesmo `operationId`; o journal
S19 reconcilia sem repetir efeitos já confirmados.

## 3. Ordem de leitura obrigatória

Leia integralmente, nesta ordem:

1. esta task;
2. `docs/infrastructure/deployment/implementation/README.md`;
3. `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`,
   seções “Produção — deploy-production.yml” e “Execução do deploy na VPS”;
4. `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
5. `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`;
6. `docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md`;
7. `docs/infrastructure/deployment/release-control/OPERACAO_LOCAL_IMPLANTACAO.md`;
8. `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
9. `.github/workflows/publish-release.yml`;
10. `tools/releases/release_publication.py`;
11. `tools/releases/global_release.py`;
12. `tools/deploy/deployment_plan.py`;
13. `tools/deploy/deployment_executor.py`;
14. `tools/deploy/deployment_cli.py`;
15. `tools/deploy/production_adapter.py`;
16. `ops/deploy/deploy-release.sh`.

## 4. Fronteira de arquivos

### 4.1 Criar

```text
.github/workflows/deploy-production.yml
tools/deploy/deployment_transport.py
tools/deploy/validate_deploy_workflow.py
tools/deploy/tests/test_deployment_transport.py
tools/deploy/tests/test_deploy_workflow_contract.py
ops/deploy/deployment-remote.py
ops/deploy/schemas/deployment-request.schema.json
ops/deploy/schemas/production-snapshot.schema.json
ops/deploy/schemas/deployment-workflow-outcome.schema.json
ops/deploy/examples/deployment-request.example.json
ops/deploy/examples/production-snapshot.example.json
ops/deploy/examples/deployment-workflow-outcome.example.json
docs/infrastructure/deployment/release-control/WORKFLOW_IMPLANTACAO.md
docs/infrastructure/deployment/implementation/slices/S21-workflow-transporte-autenticado-implantacao-producao.report.md
```

### 4.2 Alterar somente

```text
.github/workflows/README.md
ops/deploy/README.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md
docs/infrastructure/deployment/release-control/OPERACAO_LOCAL_IMPLANTACAO.md
```

Não alterar:

- esta task;
- tracker `docs/infrastructure/deployment/implementation/README.md`;
- S20 ou qualquer slice anterior;
- `deployment_plan.py`, `deployment_executor.py`, `deployment_cli.py` ou
  `production_adapter.py`;
- código Java/Node/frontends;
- Dockerfiles, Compose, gateway, migrations ou `.env`;
- workflows `ci.yml`, `publish-candidate.yml` e `publish-release.yml`;
- contratos OpenAPI, state machine ou security matrix;
- runtime/UI publisher ou futuro runtime/UI deployer.

## 5. Workflow canônico

Arquivo:

```text
.github/workflows/deploy-production.yml
```

Cabeçalho obrigatório:

```yaml
name: Deploy Production
run-name: deploy-production-${{ inputs.operation_id }}

on:
  workflow_dispatch:
    inputs:
      operation_id:
        required: true
        type: string
      release:
        required: true
        type: string

permissions:
  contents: read
  actions: read

concurrency:
  group: emporio-production
  cancel-in-progress: false
```

Não adicionar `push`, `pull_request`, `schedule`, `workflow_run`,
`repository_dispatch` ou `workflow_call`.

Devem existir exatamente quatro jobs:

```text
trust -> prepare -> deploy -> outcome
```

### 5.1 Job `trust`

- `runs-on: ubuntu-24.04`;
- timeout máximo de 10 minutos;
- sem `environment`;
- sem acesso a secrets de produção;
- checkout do SHA do próprio dispatch, com `persist-credentials: false`;
- valida por `deployment_transport.py trust`;
- produz artifact `deployment-trust`, retenção de 1 dia.

Entradas confiáveis passadas por environment, nunca interpoladas em shell:

```text
TRUSTED_REPOSITORY
TRUSTED_OWNER
TRUSTED_EVENT
TRUSTED_REF
TRUSTED_SHA
TRUSTED_RUN_ID
TRUSTED_RUN_ATTEMPT
TRUSTED_ACTOR_ID
TRUSTED_OPERATION_ID
TRUSTED_RELEASE
DEPLOYER_ACTOR_IDS
```

Regras:

- repository exato `greggorio/abaronesa-emporio`;
- owner exato `greggorio`;
- event exato `workflow_dispatch`;
- ref exata `refs/heads/main`;
- SHA com 40 hex minúsculos;
- run/attempt inteiros positivos;
- actor ID inteiro positivo presente na allowlist numérica
  `DEPLOYER_ACTOR_IDS`;
- `operation_id` conforme S19: `[A-Za-z0-9_-]{20,128}`;
- release conforme SemVer canônica `vMAJOR.MINOR.PATCH`.

Não autorizar por login textual do ator.

### 5.2 Job `prepare`

- depende de `trust`;
- sem `environment`;
- timeout máximo de 15 minutos;
- checkout do mesmo `TRUSTED_SHA`;
- baixa e valida `deployment-trust`;
- usa somente `github.token` com `contents: read`;
- consulta a release GitHub pela tag exata;
- consulta o próprio workflow run para obter `run_started_at`;
- produz artifact `deployment-handoff`, retenção de 1 dia.

Validar a release remota antes de baixar o primeiro asset:

- tag e nome iguais à release solicitada;
- `draft=false`, `prerelease=false`;
- release não deletada e publicada;
- target commit e tag lightweight coerentes com `release.json.sourceCommit`;
- conjunto exato de assets:
  `release.json`, `release.json.sha256`, `metadata.json`;
- IDs positivos, URLs REST derivadas do repository fixo, estado `uploaded`,
  content types e limites idênticos aos da S14;
- nenhum asset duplicado ou extra;
- sidecar, metadata, bytes canônicos e invariantes de
  `global_release.validate_release`;
- seis componentes e dois inventários Flyway completos;
- nenhuma tag mutável ou `latest`.

O helper deve reutilizar `release_publication.ASSETS`,
`validate_release_assets`, a validação de estado remoto e
`global_release.validate_release`; não criar uma segunda interpretação dos
assets S14. O transporte HTTP/CLI é injetável nos testes. Somente `404`
significa ausência; outros status e falha de transporte são erros distintos e
sanitizados.

`deployment-handoff` contém somente:

```text
deployment-request.json
release.json
release.json.sha256
metadata.json
```

Todos são canônicos, modo `0600`; diretório `0700`. Nenhum token é persistido.

### 5.3 Job `deploy`

- depende de `prepare`;
- usa `environment: production`;
- timeout máximo de 45 minutos;
- checkout do mesmo SHA de controle;
- baixa e valida integralmente `deployment-handoff` antes de materializar
  qualquer secret;
- cria arquivos SSH somente depois dessa validação;
- executa `deployment_transport.py deploy`;
- sempre tenta upload do artifact `deployment-result`, retenção de 90 dias;
- depois do upload, falha o job se o resultado não for
  `CONFIRMED/SUCCEEDED`.

Secrets e variables devem entrar apenas por `env`. Nunca interpolar seus
valores em `run`, `run-name`, artifact, path ou output.

Actions oficiais devem estar fixadas por commit SHA. Não usar action de
terceiro.

Usar exatamente os pins já aceitos no repositório:

```text
actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd
actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0
actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02
```

Não atualizar versões ou selecionar outros SHAs nesta slice.

### 5.4 Job `outcome`

- `if: always()`;
- depende de `trust`, `prepare` e `deploy`;
- não usa `environment` nem secrets de produção;
- baixa handoff/result apenas quando os jobs que os produzem chegaram a
  sucesso suficiente;
- valida bindings de operation, release, run ID, attempt e SHA;
- produz `deployment-workflow-outcome`, retenção de 90 dias;
- faz upload antes de aplicar o exit final;
- workflow termina com sucesso somente para
  `transportStatus=CONFIRMED`, `deploymentState=SUCCEEDED` e
  `errorCode=null`;
- `ROLLED_BACK`, `FAILED`, `INDETERMINATE`, artifact ausente ou binding
  divergente terminam o workflow em falha.

## 6. Contratos JSON

Todos usam JSON Schema draft 2020-12, `additionalProperties: false`, exemplos
fictícios e JSON canônico com LF final.

### 6.1 `deployment-request`

Campos exatos:

```json
{
  "schemaVersion": 1,
  "kind": "deployment-request",
  "repository": "greggorio/abaronesa-emporio",
  "operationId": "deployment_0123456789abcdef",
  "targetRelease": "v1.2.3",
  "controlSha": "0123456789abcdef0123456789abcdef01234567",
  "workflowRunId": 123456789,
  "workflowRunAttempt": 1,
  "requestedActorId": 1234567,
  "plannedAt": "2026-07-31T18:00:00Z"
}
```

`plannedAt` é exatamente o `run_started_at` validado do workflow run, reduzido
a UTC com precisão de segundos. Não usar relógio local depois do trust.

### 6.2 `production-snapshot`

Campos comuns:

```text
schemaVersion
kind = production-snapshot
operationId
targetRelease
mode = FIRST_INSTALL | UPDATE
capturedAt
```

Em `FIRST_INSTALL`:

```text
currentRelease = null
installedStateSha256 = null
currentManifestSha256 = null
```

Em `UPDATE`:

```text
currentRelease = SemVer
installedStateSha256 = sha256:<64 hex>
currentManifestSha256 = sha256:<64 hex>
```

O snapshot staging contém:

```text
production-snapshot.json
installed-state.json       # somente UPDATE
current-manifest.json      # somente UPDATE
```

O helper remoto deve reutilizar S18/S19 para validar schema, semântica,
canonicalidade e vínculo integral entre estado e manifesto. Estado parcial,
symlink divergente, release não reconciliada ou arquivos adicionais falham
fechado.

### 6.3 `deployment-workflow-outcome`

Campos exatos:

```json
{
  "schemaVersion": 1,
  "kind": "deployment-workflow-outcome",
  "operationId": "deployment_0123456789abcdef",
  "targetRelease": "v1.2.3",
  "workflowRunId": 123456789,
  "workflowRunAttempt": 1,
  "controlSha": "0123456789abcdef0123456789abcdef01234567",
  "transportStatus": "CONFIRMED",
  "deploymentState": "SUCCEEDED",
  "databaseRestoreRequired": false,
  "errorCode": null
}
```

Regras:

- `CONFIRMED`: state é `SUCCEEDED`, `ROLLED_BACK` ou `FAILED`, booleano de
  restore presente e `errorCode` coerente com o estágio comprovado. Depois de
  o CLI remoto iniciar, state/restore/error devem corresponder exatamente à
  linha S20. Antes do CLI iniciar, uma falha comprovadamente sem mutação usa
  `FAILED`, restore `false` e o código sanitizado do transporte;
- `INDETERMINATE`: state e restore nulos; `errorCode` é um dos códigos
  sanitizados de transporte;
- nunca incluir host, porta, usuário, path, comando, stdout, stderr, exception,
  token, chave ou conteúdo do bundle.

## 7. Protocolo remoto

`ops/deploy/deployment-remote.py` possui somente os subcomandos:

```text
capabilities
snapshot --operation-id <id> --release <semver>
install --operation-id <id> --release <semver> --archive-sha256 <sha256>
execute --operation-id <id> --release <semver>
cleanup --operation-id <id>
```

### 7.1 `capabilities`

Emite uma linha canônica:

```json
{"deployRoot":"/opt/sistemas/emporio","protocol":"emporio-deployment-transport","schemaVersion":1,"user":"deploy-emporio"}
```

Exige UID efetivo não zero e username efetivo exatamente `deploy-emporio`.
Divergência falha antes de qualquer escrita.

### 7.2 `snapshot`

- adquire lock próprio de snapshot sem competir com o lock transacional S19;
- não modifica `current`, `previous`, journal, estado ou bundle;
- `FIRST_INSTALL` somente quando estado, `current` e `previous` estão todos
  ausentes;
- `UPDATE` exige estado confirmado e `current` apontando para sua release;
- copia estado e manifesto atual para staging `0700`, arquivos `0600`;
- calcula hashes sobre bytes canônicos;
- usa staging + `fsync` + `os.replace`;
- replay idêntico reutiliza snapshot válido;
- conflito retorna código sanitizado.

### 7.3 Archive do bundle

O runner gera tar não comprimido, máximo de 16 MiB, com exatamente seis
arquivos regulares:

```text
manifest.json
compose.prod.yml
release.env
deployment-plan.json
installed-state.next.json
bundle.sha256
```

Regras:

- nomes ASCII exatos, sem diretório;
- sem duplicata, PAX header, link, device, FIFO, path absoluto ou `..`;
- arquivos modo `0600`;
- nenhuma entrada extra;
- archive SHA-256 calculado em streaming;
- o bundle é validado por S18 antes e depois do empacotamento.

O `scp` envia somente:

```text
<INCOMING_ROOT>/<operationId>.tar.part
```

com modo final `0600`. Nenhum path remoto vem de input livre.

### 7.4 `install`

- valida usuário, root, parents e incoming;
- abre archive sem seguir link;
- exige SHA-256 informado;
- extrai manualmente para staging irmão `0700`;
- aplica todos os limites da Seção 7.3;
- valida o bundle extraído por S18;
- exige `targetRelease == --release`;
- instala por `os.replace` em `releases/<release>` e faz `fsync`;
- se destino não existir, instala uma vez;
- se destino existir e for integralmente idêntico, replay é sucesso;
- se destino existir diferente, `BUNDLE_CONFLICT`;
- nunca sobrescreve release existente;
- remove somente staging pertencente à mesma operação.

### 7.5 `execute`

Invoca exatamente, sem shell:

```text
<DEPLOY_SCRIPT>
deploy
--operation-id
<operationId>
--release
<release>
```

Ambiente mínimo, stdin nulo, stderr descartado e stdout máximo de 65.536 bytes.
A única linha aceita é o JSON canônico S20 com operation/release coerentes.
Preservar exits `0`, `20` e `21`; outros exits viram erro sanitizado de
execução.

### 7.6 `cleanup`

Remove exclusivamente:

```text
incoming/<operationId>.tar.part
snapshots/<operationId>.staging
snapshots/<operationId>
```

Não remove:

- release instalada;
- `current` ou `previous`;
- journal, estado ou backup;
- imagem, container, volume ou network.

O runner somente chama cleanup depois de baixar e validar o snapshot; portanto
o snapshot final já não é necessário ao run. Cleanup ocorre em `finally`.
Falha de cleanup é registrada no outcome como `REMOTE_CLEANUP_FAILED`; mesmo
que o CLI tenha confirmado `SUCCEEDED`, o workflow falha até que a pendência
operacional seja reconciliada. O estado confirmado do CLI não é reescrito.

## 8. SSH e segurança do runner

O helper local deve gerar config OpenSSH fechada com:

```text
BatchMode yes
IdentitiesOnly yes
StrictHostKeyChecking yes
UserKnownHostsFile <arquivo 0600>
IdentityFile <arquivo 0600>
ConnectTimeout 15
ConnectionAttempts 1
ServerAliveInterval 15
ServerAliveCountMax 2
ForwardAgent no
ClearAllForwardings yes
PasswordAuthentication no
KbdInteractiveAuthentication no
LogLevel ERROR
```

Regras:

- diretório SSH `0700`;
- key e known_hosts regulares `0600`, sem symlink e com limites;
- `PRODUCTION_SSH_HOST` aceita hostname DNS ou IPv4 estrito, sem whitespace,
  shell metacharacter, option prefix ou path;
- porta inteira `1..65535`;
- destino sempre construído como `deploy-emporio@<host>`;
- argumentos remotos são tokens já validados, nunca texto livre;
- `ssh` e `scp` resolvidos em PATH mínimo e validados como binários regulares,
  executáveis, root-owned e não graváveis por grupo/outros;
- stdout capturado incrementalmente com limite; stderr nunca capturado;
- timeout termina e recolhe subprocesso;
- nenhum erro público contém argv ou ambiente.

O workflow grava secrets com Python ou `install`, nunca com `echo`, `printf`,
`set -x` ou interpolação direta em comando.

## 9. Sequência integral

Ordem obrigatória:

1. trust do dispatch;
2. validação da release e assets;
3. validação do handoff antes de secrets;
4. materialização segura de SSH;
5. handshake `capabilities`;
6. snapshot remoto;
7. download e validação do snapshot;
8. geração e validação do bundle S18;
9. archive e hash em streaming;
10. upload `.tar.part`;
11. instalação remota atômica;
12. chamada remota do CLI S20;
13. validação do resultado;
14. cleanup restrito;
15. upload do result;
16. reconciliação e upload do workflow outcome;
17. aplicação do exit final.

Nenhuma etapa 5–16 ocorre se uma etapa anterior falhar, exceto cleanup seguro
e produção de outcome sanitizado quando já houver handoff confiável.

## 10. Idempotência e falhas

- Mesma operação + mesma release + mesmos bytes: replay permitido.
- Mesma operação + release diferente: `IDEMPOTENCY_CONFLICT`.
- Release existente com bundle diferente: `BUNDLE_CONFLICT`.
- Snapshot diferente para a mesma operação: `SNAPSHOT_CONFLICT`.
- SSH falha antes de `execute`: `CONFIRMED/FAILED` somente se nenhuma mutação
  remota ocorreu e isso puder ser provado; caso contrário `INDETERMINATE`.
- SSH falha durante/depois de `execute`: sempre `INDETERMINATE`.
- Retry usa a mesma operação e nunca cria journal alternativo.
- Resultado remoto com operation ID, state, exit ou canonicalidade divergente:
  `INDETERMINATE/REMOTE_RESULT_INVALID`.

Códigos públicos permitidos:

```text
INVALID_DISPATCH
ACTOR_NOT_ALLOWED
RELEASE_NOT_FOUND
RELEASE_NOT_ELIGIBLE
RELEASE_ASSETS_INVALID
REMOTE_CAPABILITY_MISMATCH
REMOTE_SNAPSHOT_INVALID
SNAPSHOT_CONFLICT
BUNDLE_GENERATION_FAILED
BUNDLE_INVALID
BUNDLE_CONFLICT
SSH_CONFIGURATION_INVALID
SSH_UNAVAILABLE
REMOTE_RESULT_UNAVAILABLE
REMOTE_RESULT_INVALID
REMOTE_CLEANUP_FAILED
INTERNAL_ERROR
```

Não incluir causa bruta no valor público.

## 11. Testes causais obrigatórios

Criar testes independentes para, no mínimo:

1. dispatch nominal gera request canônico;
2. repository, owner, event, ref ou SHA divergente falha;
3. actor ID ausente/malformado/não allowlisted falha;
4. operation/release inválida ou input adicional é rejeitado;
5. release draft, prerelease, ausente ou tag divergente falha;
6. asset extra, ausente, duplicado, URL/type/size/state divergente falha antes
   do primeiro download;
7. sidecar, metadata, manifesto ou source commit divergente falha;
8. token/response/erro fictício não aparece em arquivo, exception ou outcome;
9. handshake exige protocolo, root, usuário e versão exatos;
10. first install exige ausência conjunta de state/current/previous;
11. snapshot UPDATE exige estado confirmado, manifesto correspondente e link
    current correto;
12. snapshot parcial, symlink ou replay divergente falha sem mutação;
13. planner recebe somente release, snapshot e Compose canônico;
14. primeiro deploy seleciona seis componentes sem input de componente;
15. archive nominal possui exatamente os seis arquivos;
16. archive com `..`, absoluto, extra, duplicata, symlink, device, FIFO, PAX ou
    tamanho excessivo falha antes de extração;
17. hash de archive divergente falha antes de instalar;
18. instalação interrompida preserva destino ausente e staging retomável;
19. destino idêntico é replay; destino diferente nunca é sobrescrito;
20. SSH argv usa usuário/path/opções fixas e `shell=False`;
21. host/porta malformados e known_hosts/key inseguros falham antes de processo;
22. PATH ambiente hostil não altera `ssh`/`scp`;
23. perda antes do execute sem mutação comprovada é falha sanitizada;
24. perda durante/depois do execute produz `INDETERMINATE`;
25. retry da mesma operação reconcilia o mesmo journal;
26. JSON S20 válido com exit 0 produz `CONFIRMED/SUCCEEDED`;
27. exits 20/21 produzem `CONFIRMED/ROLLED_BACK|FAILED`;
28. JSON, operation, release, state ou exit divergente produz
    `INDETERMINATE/REMOTE_RESULT_INVALID`;
29. stdout excessivo e timeout terminam/recolhem o processo;
30. cleanup remove somente `.part` e staging da operação;
31. cleanup nunca remove release, links, journal, estado ou backup;
32. workflow contém somente `workflow_dispatch` e dois inputs;
33. workflow possui quatro jobs e dependências exatas;
34. somente `deploy` usa `environment: production`;
35. concurrency é global, sem cancelamento;
36. permissions são mínimas e não há `packages: write`;
37. todas as actions estão fixadas por SHA e nenhuma é terceira;
38. secrets entram somente por `env` depois do handoff;
39. result e outcome são enviados antes do exit final;
40. push em `main` nunca dispara deploy;
41. mutantes `root`, `sudo`, `docker build`, `down`, `prune`, `latest`,
    `StrictHostKeyChecking=no`, `sshpass`, agent forwarding e shell falham;
42. workflow não aceita componente, digest, host, path, comando ou URL;
43. workflow não recalcula BOM e não altera planner;
44. resultado indeterminado nunca é promovido a sucesso;
45. validação integral ocorre novamente em replay/cache;
46. documentação, schemas, exemplos, workflow e código permanecem coerentes.

Mocks devem registrar método HTTP, endpoint, subprocess argv, ordem, arquivos
e side effects. Um teste que apenas compara código de erro sem provar a causa
não satisfaz o critério.

## 12. Validadores

`validate_deploy_workflow.py` deve falhar fechado e validar:

- schemas e exemplos;
- tokens obrigatórios no workflow/código/docs;
- triggers, jobs, dependencies, environment e concurrency;
- actions por SHA;
- permissions;
- ausência dos mutantes proibidos;
- protocolo remoto e paths literais;
- bindings request/handoff/snapshot/outcome;
- import/CLI básico dos helpers.

Não acessar rede.

## 13. Validação obrigatória

Executar:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/compose/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deploy_workflow.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py

git diff --check
```

Executar `actionlint` local contra os quatro workflows. Se a imagem/ferramenta
não estiver disponível localmente, registrar bloqueio objetivo sem baixar ou
ampliar rede.

Não executar Maven, npm, Docker build, Compose operacional, SSH real,
workflow, GitHub API, GHCR, DNS, VPS ou produção.

## 14. Estado protegido

Ao final:

- índice Git real vazio;
- `HEAD`, tags e reflog inexistentes;
- exatamente quatro workflows YAML ativos:
  `ci.yml`, `publish-candidate.yml`, `publish-release.yml`,
  `deploy-production.yml`;
- nenhum cache Python ou artefato temporário;
- nenhum arquivo contendo key/known_hosts fictício fora de `/tmp`;
- nenhum recurso `s21-*`;
- nenhum `git add`, commit, tag ou push;
- nenhum acesso externo;
- S22 ausente.

## 15. Critérios de aceite

A S21 somente poderá ser aceita se:

1. dispatch possuir somente operation e release;
2. trust vincular repository/ref/SHA/run/attempt/actor;
3. release global publicada e assets forem integralmente revalidados;
4. handoff for validado antes de secrets;
5. planejamento continuar exclusivamente em S18;
6. snapshot remoto não mutar estado;
7. first install e update forem inequívocos;
8. archive e instalação forem seguros, atômicos e idempotentes;
9. SSH autenticar host e usuário sem root;
10. CLI S20 for a única fronteira de execução;
11. perda de conexão não inventar estado terminal;
12. outcome possuir bindings completos e nenhuma informação sensível;
13. workflow global não cancelar deploy em andamento;
14. nenhum build, escolha de componente ou tag mutável existir;
15. provas causais e regressões permanecerem verdes;
16. documentação refletir exatamente o protocolo;
17. nenhum acesso real ou mutação externa ocorrer.

## 16. Relatório obrigatório

O relatório deve registrar:

- CWD;
- arquivos criados e alterados;
- arquitetura efetivamente implementada;
- grafo do workflow;
- inputs, permissions, environment, concurrency e actions;
- contratos JSON;
- protocolo de snapshot/archive/install/execute/cleanup;
- segurança SSH;
- matriz das 46 provas causais;
- comandos, exits, contagens e duração;
- actionlint;
- auditoria de estado protegido;
- divergências reais e itens não determinados.

Mantenha:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED`.

## 17. Fora de escopo e próxima fronteira

Fora da S21:

- runtime HTTP do modo deployer;
- UI de produção;
- criação de usuário/diretórios/permissões/systemd na VPS;
- instalação do control root;
- secrets/variables/environments reais do GitHub;
- chave/token GHCR real;
- Nginx/TLS do host;
- primeiro deploy;
- rollback solicitado pela UI;
- retenção/cópia externa/restore de backup;
- monitoramento e alertas;
- primeiro commit/push do repositório.

Depois do aceite da S21, a S22 prevista implementará o runtime HTTP do modo
`deployer`, persistência/idempotência/reconciliação do workflow e os endpoints
do OpenAPI já aceito. Bootstrap da VPS, UI de produção e primeiro deploy
continuarão em slices posteriores.
