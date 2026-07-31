# S21 — Workflow e transporte autenticado de implantação em produção

## 1. Estado

**IN_PROGRESS — aguardando revisão do orquestrador**

## 13. Revisão do orquestrador — ciclo 1 terminal

### 13.1 Veredito

**S21 NÃO ACEITA — correção causal terminal requerida.**

A arquitetura principal, o helper remoto e a matriz histórica permanecem
válidos, mas ainda existem três defeitos causais na fronteira runner/workflow.
Há também uma incompatibilidade de regressão criada pelo próprio contrato da
S21. Esta última é uma omissão do orquestrador, não uma decisão incorreta do
executor.

Não criar S22. A S21 permanece `IN_PROGRESS` até que todos os itens desta
Seção 13 sejam implementados e a matriz terminal esteja verde.

### 13.2 Evidência independente reproduzida

O orquestrador reproduziu:

1. `253/253` testes de deploy aprovados fora do sandbox em `76,399 s`;
2. `4/4` testes Compose aprovados;
3. os cinco validadores prescritos aprovados;
4. `277` testes de releases executados, com as mesmas três falhas informadas:
   `274` aprovados e três interceptados por `active-workflows`;
5. `git diff --check` aprovado;
6. exatamente os quatro workflows esperados presentes.

A execução da suíte de deploy dentro do sandbox não é evidência de regressão:
o sandbox projeta `/usr/bin/ssh`, `/usr/bin/scp`, `/usr/bin/docker` e
`/usr/bin/curl` com UID `65534`, enquanto os contratos S20/S21 corretamente
exigem binários `root-owned`. A repetição fora do sandbox eliminou essa
interferência e passou integralmente.

### 13.3 Emenda de responsabilidade do orquestrador — allowlist S14

A task S21 exigiu simultaneamente:

- quatro workflows ativos, incluindo `deploy-production.yml`;
- regressão S14 integralmente verde;
- proibição de alterar `tools/releases/validate_release_workflow.py`.

Essas três condições são incompatíveis porque o validador S14 fixa
`EXPECTED` nos três workflows anteriores. A correção fica agora
explicitamente autorizada e determinada:

1. `EXPECTED` deve conter exatamente `ci.yml`, `publish-candidate.yml`,
   `publish-release.yml` e `deploy-production.yml`;
2. o validador S14 continua responsável somente pela semântica de
   `publish-release.yml`; ele não deve duplicar o validador S21;
3. workflow ausente, renomeado ou um quinto workflow continuam falhando com
   `active-workflows`;
4. mutantes internos de `publish-release.yml` devem alcançar sua causa
   específica, sem serem mascarados pela allowlist.

### 13.4 T01 — handoff privado depois do transporte por artifact

O fluxo atual produz `0700/0600` no job `prepare`, mas o transporte padrão do
GitHub Artifact não preserva esses modos: diretórios são restaurados como
`0755` e arquivos como `0644`. `validate_handoff()` aceita hoje esses modos e
também não materializa uma cópia privada antes do uso.

A solução terminal é esta, sem escolha residual:

1. tratar o diretório baixado como **artifact ingress não confiável**;
2. aceitar no ingress somente diretório regular seguro nos modos `0700` ou
   `0755` e os quatro arquivos regulares nos modos `0600` ou `0644`; qualquer
   bit de escrita para grupo/outros, symlink, arquivo especial, entrada extra,
   ausência ou tamanho excedido deve falhar;
3. abrir cada arquivo por descritor com `O_NOFOLLOW`, comparar `lstat/fstat`,
   ler uma única vez e validar sobre esses mesmos bytes: canonicalidade,
   schema, bindings, sidecar e metadata;
4. somente depois dessa validação criar, com operações exclusivas, um novo
   diretório privado `0700` e quatro arquivos `0600`, copiando exatamente os
   bytes já validados;
5. fazer `fsync` dos arquivos e do diretório, reler/revalidar a cópia privada e
   comprovar igualdade byte a byte;
6. `deploy` e `outcome` devem usar somente essa cópia privada; o ingress
   baixado nunca controla paths, SSH ou resultado;
7. no comando `deploy`, nenhum valor `PRODUCTION_SSH_*` pode ser lido ou
   materializado antes de a cópia privada estar confirmada;
8. remover o workspace privado em `finally`, sem persistir token ou material
   SSH.

Não usar `chmod` cego sobre o diretório baixado e não empacotar um formato de
handoff diferente. O artifact lógico continua contendo exatamente os quatro
arquivos congelados na S21.

### 13.5 T02 — falha local pré-remota deve ser confirmada e persistida

Depois de um handoff confiável, uma porta inválida ou falha local ao resolver
ou materializar OpenSSH ocorre antes de qualquer processo SSH. Hoje o comando
termina com exit `3`, não cria `deployment-result` e o job `outcome` converte a
situação em `INDETERMINATE/REMOTE_RESULT_UNAVAILABLE`.

Corrigir de forma determinística:

1. depois de obter o request pela cópia privada, falhas em host/porta,
   resolução dos binários ou materialização da configuração SSH devem produzir
   `CONFIRMED/FAILED` com o código sanitizado real
   (`SSH_CONFIGURATION_INVALID` ou `SSH_UNAVAILABLE`);
2. persistir `result/deployment-result.json` canônico `0600` antes de retornar
   exit `4`;
3. comprovar que nenhum `ProcessRunner`, `ssh`, `scp`, helper remoto ou cleanup
   foi chamado;
4. handoff inválido continua sem produzir resultado inventado, pois não há
   bindings confiáveis;
5. falha ao persistir o próprio resultado continua exit `3` e não autoriza
   construir sucesso ou estado remoto.

### 13.6 T03 — reconciliação integral e artifact de outcome

O comando `outcome` hoje apresenta dois desvios:

- resultado não canônico ou binding divergente retorna exit `3` sem criar o
  artifact obrigatório;
- um JSON canônico com os cinco bindings e campos terminais mínimos, mas
  inválido pelo schema, é aceito como sucesso e termina com exit `0`.

O segundo caso foi reproduzido com um documento sem `schemaVersion`, `kind` e
`databaseRestoreRequired`; ele foi publicado como outcome e promovido a
sucesso.

Aplicar exatamente estas regras:

1. resultado presente deve ser regular, não symlink, limitado, canônico,
   válido pelo schema completo de `deployment-workflow-outcome` e ter os cinco
   bindings idênticos ao request privado;
2. resultado ausente gera
   `INDETERMINATE/REMOTE_RESULT_UNAVAILABLE` com bindings do request;
3. resultado presente ilegível, truncado, não canônico, inválido pelo schema ou
   com binding divergente gera `INDETERMINATE/REMOTE_RESULT_INVALID` com os
   bindings confiáveis do request;
4. nos casos 2 e 3, sempre persistir
   `outcome/deployment-workflow-outcome.json` canônico `0600` e retornar exit
   `4`, permitindo upload antes do gate final;
5. somente um resultado integralmente válido
   `CONFIRMED/SUCCEEDED/errorCode=null` retorna exit `0`;
6. `CONFIRMED/ROLLED_BACK|FAILED` exige `errorCode` string não vazia no schema e
   na validação local do retorno S20;
7. `OpenSshTransport.execute()` deve rejeitar como `REMOTE_RESULT_INVALID`
   qualquer retorno não-SUCCEEDED sem `errorCode` válido, antes de construir o
   outcome.

Nenhum erro pode copiar bytes inválidos, traceback, argv, stderr, secret ou
corpo remoto para o artifact.

### 13.7 Provas causais obrigatórias da correção

Adicionar provas independentes para:

1. output de `prepare` permanece `0700/0600`;
2. ingress simulado como `0755/0644` é validado e rematerializado como
   `0700/0600`, com os quatro conteúdos idênticos;
3. ingress `0777`, arquivo `0666`, symlink, entrada extra e troca entre
   validação/leitura falham sem acesso a `PRODUCTION_SSH_*`;
4. porta inválida depois de handoff válido produz artifact
   `CONFIRMED/FAILED/SSH_CONFIGURATION_INVALID`, exit `4` e zero processo;
5. binário OpenSSH ausente produz artifact
   `CONFIRMED/FAILED/SSH_UNAVAILABLE`, exit `4` e zero processo;
6. result ausente produz e persiste
   `INDETERMINATE/REMOTE_RESULT_UNAVAILABLE`;
7. result não canônico, truncado ou com entrada extra produz e persiste
   `INDETERMINATE/REMOTE_RESULT_INVALID`;
8. binding divergente produz o mesmo estado, preservando no outcome somente os
   bindings do request confiável;
9. o mutante canônico schema-incompleto que antes terminava exit `0` agora
   termina exit `4` e não é promovido;
10. `ROLLED_BACK` e `FAILED` com `errorCode=null`, vazio ou malformado são
    rejeitados pelo schema e pelo transporte local;
11. outcome válido confirmado continua byte a byte preservado e só o sucesso
    limpo retorna exit `0`;
12. allowlist com os quatro workflows passa; ausência do quarto e quinto
    workflow extra falham; o mutante `fetch-depth` S14 volta a falhar por
    `checkout-depth`.

Os testes devem afirmar arquivos, modos, bytes, ordem, exits e ausência de side
effects. Comparar apenas o código de erro não satisfaz esta emenda.

### 13.8 Escopo terminal autorizado

Podem ser alterados somente:

- `tools/deploy/deployment_transport.py`;
- `tools/deploy/validate_deploy_workflow.py`, se necessário para tornar T01–T03
  estruturalmente obrigatórios;
- `tools/deploy/tests/test_deployment_transport.py`;
- `tools/deploy/tests/test_deploy_workflow_contract.py`, se necessário;
- `ops/deploy/schemas/deployment-workflow-outcome.schema.json`;
- `ops/deploy/examples/deployment-workflow-outcome.example.json`, somente se a
  evolução do schema exigir ajuste do exemplo;
- `tools/releases/validate_release_workflow.py`;
- `tools/releases/tests/test_release_publication.py`;
- `docs/infrastructure/deployment/release-control/WORKFLOW_IMPLANTACAO.md`;
- este relatório S21.

Não alterar workflow, helper remoto, planner S18, núcleo S19, adapters/CLI S20,
schemas anteriores, task, tracker ou qualquer slice anterior. Não criar S22.

### 13.9 Revalidação terminal

Executar e registrar:

1. as doze provas da Seção 13.7 isoladamente;
2. todos os testes em `tools/deploy/tests` fora de sandbox quando a projeção de
   UID impedir a prova `root-owned`;
3. todos os testes em `tools/releases/tests` — todos devem passar;
4. todos os testes em `tools/compose/tests`;
5. os cinco validadores prescritos;
6. `actionlint` somente se já estiver disponível localmente, sem download;
7. `git diff --check` e a auditoria integral do estado protegido S21.

Não executar Maven, npm, Docker build, Compose operacional, migrations, rede,
GitHub API, GHCR, SSH, VPS, produção, commit, tag ou push.

A devolução deve responder T01, T02 e T03 separadamente, reconhecer a emenda da
allowlist como correção de contrato do orquestrador, mapear as doze provas e
manter:

**IN_PROGRESS — aguardando revisão terminal do orquestrador**

## 15. Revisão do orquestrador — ciclo 2, correção T02-A

### 15.1 Veredito

**S21 NÃO ACEITA — resta exatamente uma correção causal em T02.**

As correções da allowlist, T01 e T03 estão aceitas. A matriz informada foi
reproduzida: 13/13 métodos focados, 264/264 testes de deploy fora do sandbox,
277/277 testes de releases, 4/4 testes Compose e os cinco validadores passaram.
O `actionlint` continua dispensado pelo ramo de indisponibilidade já aprovado.

Não criar S22. Não reabrir os blocos já aceitos.

### 15.2 Evidência causal restante

A Seção 13.5 determinou que falhas de host/porta, resolução **ou
materialização** da configuração SSH, depois de um handoff confiável e antes de
qualquer processo remoto, devem persistir
`CONFIRMED/FAILED/SSH_CONFIGURATION_INVALID|SSH_UNAVAILABLE` antes do exit `4`.

O código atual captura somente `DeploymentTransportError`. Operações reais de
materialização — `tempfile.mkdtemp`, `rmdir`, `directory.mkdir`, `_write_private`
e seus `open/write/fsync/chmod` — podem lançar `OSError` diretamente. A prova
independente injetou `OSError` em `materialize_ssh_configuration()` e obteve:

```text
deployment-transport:INTERNAL_ERROR
exit=3
artifact_exists=false
```

Portanto, a afirmação da Seção 14.4 de que falha de materialização produz
resultado confirmado ainda não é verdadeira para a fronteira operacional real.

### 15.3 Correção fechada T02-A

Aplicar exatamente estas regras:

1. abranger todo o trecho local entre criação do workspace SSH e conclusão de
   `materialize_ssh_configuration()`;
2. preservar sem alteração qualquer `DeploymentTransportError` já tipado;
3. converter `OSError` desse trecho exclusivamente em
   `DeploymentTransportError("SSH_CONFIGURATION_INVALID")`;
4. persistir então `deployment-result.json` canônico `0600` com
   `CONFIRMED/FAILED/SSH_CONFIGURATION_INVALID` e retornar exit `4`;
5. não capturar `Exception` genérica como falha confirmada: erro de programação
   continua fail-closed com exit `3`;
6. remover em `finally` qualquer diretório SSH parcial, inclusive key,
   known_hosts ou config parcialmente materializados;
7. não instanciar `SubprocessRunner` nem chamar SSH, SCP, helper remoto ou
   cleanup nesse caminho;
8. se a persistência do resultado falhar, manter exit `3` e não inventar
   artifact.

### 15.4 Provas causais obrigatórias

Adicionar exatamente estas provas:

1. `tempfile.mkdtemp` lançando `OSError` depois do handoff privado produz
   artifact confirmado com `SSH_CONFIGURATION_INVALID`, exit `4` e zero
   processo;
2. `materialize_ssh_configuration` cria diretório/key parcial e lança
   `OSError`: o mesmo artifact é persistido, o workspace parcial é removido e
   nenhum processo é iniciado;
3. `DeploymentTransportError("SSH_UNAVAILABLE")` vindo da resolução continua
   `SSH_UNAVAILABLE`, sem ser remapeado;
4. falha de `_persist_command_outcome` após o `OSError` continua exit `3`, sem
   artifact e sem processo.

As provas devem afirmar exit, bytes/schema/mode do artifact, ausência do
workspace parcial e zero chamadas operacionais.

### 15.5 Escopo exclusivo

Alterar somente:

- `tools/deploy/deployment_transport.py`;
- `tools/deploy/tests/test_deployment_transport.py`;
- este relatório S21.

Não alterar schema, workflow, allowlist, validador, documentação canônica,
helper remoto, S18–S20, task, tracker ou qualquer slice anterior. Não criar S22.

### 15.6 Revalidação

Executar e registrar:

1. as quatro provas T02-A isoladamente;
2. todos os testes em `tools/deploy/tests` fora do sandbox quando necessário;
3. todos os testes em `tools/releases/tests`;
4. todos os testes Compose;
5. os cinco validadores;
6. `git diff --check` e a auditoria de estado protegido.

Não executar rede, SSH, GitHub, GHCR, VPS, produção, Maven, npm, Docker build,
Compose operacional, commit, tag ou push.

Manter:

**IN_PROGRESS — aguardando revisão terminal do orquestrador**

A fronteira S21 foi implementada localmente. A matriz encontrou um bloqueio
fora da fronteira autorizada: o validador S14 fixa o conjunto anterior de três
workflows e rejeita o quarto workflow obrigatório da S21. O actionlint também
não estava disponível localmente. Nenhum desses pontos foi contornado por
alteração fora do contrato.

## 2. CWD e leitura obrigatória

```text
/home/gregorio/git/baronesa/emporio
```

A task foi lida integralmente e a ordem da Seção 3 foi seguida: arquitetura,
contratos S06, documentação S18–S20, OpenAPI deployer, workflow/helpers
S13/S14 e código S18–S20. Esses artefatos de autoridade não foram alterados.

## 3. Arquivos criados

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

## 4. Arquivos alterados

```text
.github/workflows/README.md
ops/deploy/README.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md
docs/infrastructure/deployment/release-control/OPERACAO_LOCAL_IMPLANTACAO.md
```

Nenhum arquivo fora das Seções 4.1 e 4.2 foi alterado.

## 5. Arquitetura implementada

### 5.1 Workflow

```text
trust -> prepare -> deploy -> outcome
```

- Trigger exclusivo workflow_dispatch com operation_id e release.
- Permissions contents/actions read.
- Concorrência emporio-production, sem cancelamento.
- Somente deploy usa environment production.
- O planner S18 permanece a única autoridade para BOM, KEEP/UPDATE,
  migrations, backup e cadeia.

Trust vincula repository, owner, evento, ref, SHA, run, attempt, actor ID,
operação e SemVer; autorização usa somente IDs numéricos. Prepare valida o
workflow run e usa run_started_at como plannedAt.

A release é revalidada antes do primeiro download. A implementação reutiliza
release_publication.ASSETS, validate_release_assets, validate_release_state,
validate_tag_ref e global_release.validate_release. O sidecar real S13/S14 é
64 hex + LF.

### 5.2 Snapshot, bundle e archive

O handoff contém somente request, release, sidecar e metadata e é validado
antes da criação dos arquivos SSH.

FIRST_INSTALL exige ausência conjunta de estado/current/previous. UPDATE exige
estado confirmado, manifesto correspondente e current coerente. O runner
revalida schema, bytes canônicos, hashes e vínculo integral.

O bundle S18 é gerado num workspace temporário privado usando somente release,
snapshot e Compose canônico. É validado antes e depois do tar. O artifact
deployment-result contém somente deployment-result.json.

O tar USTAR não comprimido contém seis arquivos regulares ASCII 0600, sem PAX,
duplicata, link, device, FIFO, path ou entrada extra, limitado a 16 MiB.

### 5.3 SSH e helper remoto

ssh/scp são resolvidos em /usr/bin:/bin e exigidos como executáveis root-owned
não graváveis. Subprocessos usam shell=False, stdin nulo, stderr descartado,
ambiente mínimo, stdout incremental limitado, timeout, kill e reap.

A config exige BatchMode, IdentitiesOnly, StrictHostKeyChecking,
UserKnownHostsFile/IdentityFile 0600, ConnectTimeout 15, uma tentativa,
keepalive, nenhum forwarding e nenhuma autenticação por senha. O destino é
sempre deploy-emporio@host-validado.

O helper expõe somente capabilities, snapshot, install, execute e cleanup.
Snapshot usa staging/rename/fsync. Install valida hash, extrai manualmente,
revalida S18 e publica atomicamente. Replay idêntico passa; destino ou staging
divergente é preservado e falha fechado. Execute chama exclusivamente o
wrapper S20 e preserva exits 0/20/21. Cleanup tenta somente o part e os dois
paths de snapshot da operação.

### 5.4 Outcome

CONFIRMED e INDETERMINATE são distintos. Perda durante/depois de execute nunca
inventa estado. Falha de cleanup preserva o estado S20 confirmado e registra
REMOTE_CLEANUP_FAILED. Result/outcome são persistidos e enviados antes do exit.
Somente CONFIRMED/SUCCEEDED/errorCode=null termina verde.

## 6. Actions

```text
actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd
actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0
actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02
```

Não há action de terceiro. Trust/handoff retêm por 1 dia; result/outcome por 90
dias.

## 7. Contratos JSON

Os três schemas são Draft 2020-12, fechados por additionalProperties=false; os
exemplos são fictícios, canônicos e terminam com LF.

- deployment-request: bindings completos e plannedAt do run remoto.
- production-snapshot: FIRST_INSTALL e UPDATE fechados por condicionais.
- deployment-workflow-outcome: estados confirmados/indeterminados; SUCCEEDED
  aceita erro somente para a pendência comprovada de cleanup.

## 8. Matriz das 46 provas causais

| # | Prova | Evidência |
|---:|---|---|
| 1 | dispatch nominal | transport test 01 |
| 2 | identidade divergente | transport test 02 |
| 3 | actor/allowlist | transport test 03 |
| 4 | operation/release/input extra | transport test 04 |
| 5 | release/tag/estado | transport tests 05 e 18 |
| 6 | asset inventory antes de download | transport test 19 |
| 7 | sidecar/metadata/manifest/source | transport tests 05 e 33 |
| 8 | sanitização | transport test 17 |
| 9 | capabilities/identidade | transport test 44 |
| 10 | first install fechado | transport test 06 + schema |
| 11 | UPDATE vinculado | transport test 20 |
| 12 | snapshot parcial/symlink/replay | transport tests 21, 38 e 43 |
| 13 | entradas do planner | deploy_handoff + transport test 35 |
| 14 | seis componentes | transport test 22 |
| 15 | seis arquivos no archive | transport test 07 |
| 16 | mutantes tar | transport tests 08 e 23–27 |
| 17 | hash antes de install | transport test 39 |
| 18 | install interrompido | transport test 40 |
| 19 | replay/destino divergente | transport test 41 |
| 20 | argv SSH fechado | transport test 11 |
| 21 | host/porta/material inseguro | transport test 10 |
| 22 | PATH hostil | transport test 28 |
| 23 | perda antes de mutação | transport test 13 |
| 24 | perda durante/depois execute | transport test 14 |
| 25 | retry no mesmo journal | install idempotente + S19 preservada |
| 26 | exit 0/SUCCEEDED | transport test 12 |
| 27 | exits 20/21 | transport test 12 |
| 28 | resultado divergente | transport tests 12 e 31 |
| 29 | overflow/timeout/reap | transport tests 08 e 29 |
| 30 | cleanup restrito | transport test 42 |
| 31 | cleanup preserva recursos | transport test 42 |
| 32 | trigger e dois inputs | workflow test de trigger |
| 33 | quatro jobs/grafo | workflow test de jobs |
| 34 | environment só deploy | workflow test de environment |
| 35 | concurrency global | workflow test de concurrency |
| 36 | permissions mínimas | workflow test/validador |
| 37 | actions oficiais pinadas | workflow test de actions |
| 38 | secrets por env após handoff | workflow test de secrets |
| 39 | uploads antes do exit | workflow + transport tests 36/37 |
| 40 | push não dispara | workflow test de trigger |
| 41 | mutantes proibidos | workflow test forbidden_mutants |
| 42 | sem inputs indevidos | workflow test de dois inputs |
| 43 | sem recalcular BOM | planner imutável + transport test 22 |
| 44 | indeterminado não vira sucesso | transport tests 15/31/36 |
| 45 | replay/cache revalidado | snapshot/install/destino existentes |
| 46 | coerência geral | validador + testes contratuais |

Além da matriz mínima, existem provas para sidecar real, bytes não canônicos,
cleanup após falha do planner, conteúdo do artifact e persistência antes do
exit terminal.

## 9. Comandos, exits e resultados

### 9.1 Deploy

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests -p 'test_*.py'
```

Exit 0: 253 testes em 77,109 s, sendo 191 regressões S18–S20 e 62 provas S21.

### 9.2 Releases

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'
```

Exit 1: 277 executados em 5,349 s; 274 passaram. As três ocorrências têm a
mesma causa:

```text
validate_release_workflow.EXPECTED =
  {ci.yml, publish-candidate.yml, publish-release.yml}
observado = conjunto acima + deploy-production.yml
erro = active-workflows
```

Falharam test_44_workflow_validator_valid, test_46_validator_cli_prefixes e o
mutante de checkout, que recebe active-workflows antes da causa esperada.
Corrigir exige alterar tools/releases/validate_release_workflow.py e testes
S14, proibidos pela Seção 4. Nenhuma expansão foi feita.

### 9.3 Compose

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/compose/tests -p 'test_*.py'
```

Exit 0: 4/4 em 0,073 s.

### 9.4 Validadores

| Comando | Exit | Resultado |
|---|---:|---|
| validate_deployment_plan.py | 0 | deployment-plan-contract:valid |
| validate_deployment_executor.py | 0 | deployment-executor-contract:valid |
| validate_production_adapter.py | 0 | production-adapter-contract:valid |
| validate_deploy_workflow.py | 0 | deploy-workflow-contract: ok |
| validate_compose.py | 0 | Compose contract valid |

Todos foram executados com PYTHONDONTWRITEBYTECODE=1 python3 e seus paths
canônicos.

### 9.5 Actionlint

```bash
command -v actionlint
docker image ls --format '{{.Repository}}:{{.Tag}}' |
  rg '(^|/)actionlint(:|$)'
```

Exit final 1, sem saída: não há binário nem imagem local em cache. Nada foi
baixado e nenhuma rede foi acessada.

### 9.6 Diff

```bash
git diff --check
```

Exit 0, sem saída.

## 10. Falhas intermediárias corrigidas

1. Sidecar inicialmente interpretado diferente do formato S13/S14.
2. Snapshot remoto não limpo quando o planner falhava antes do upload.
3. Snapshot/bundle/tar inicialmente sob o diretório do artifact result.
4. CLI inicialmente retornando zero para terminais não verdes.
5. SUCCEEDED + cleanup failure inicialmente incompatível com o schema.
6. JSON semanticamente igual, mas não canônico, inicialmente aceito no runner.
7. Snapshot staging parcial e install staging divergente sem prova completa.

Todos foram fechados sem alterar a arquitetura congelada.

## 11. Estado protegido final

- Task S21, tracker, planner S18, executor S19, CLI/adapters S20 preservados por
  SHA-256.
- ci.yml, publish-candidate.yml e publish-release.yml preservados por SHA-256.
- Índice Git real vazio; HEAD, tags e reflog inexistentes.
- Exatamente quatro workflows YAML ativos.
- Nenhum __pycache__, pyc, key/known_hosts fora de /tmp ou recurso s21-*.
- S22 ausente.
- Nenhum git add, commit, tag, push, publicação, rede, SSH, GitHub, GHCR, DNS,
  VPS ou produção.
- Nenhum Maven, npm, Docker build ou Compose operacional.

O único comando Docker foi a consulta somente leitura das imagens locais para
detectar actionlint; não criou ou alterou recurso.

## 12. Divergências reais

Há uma divergência causal fora da fronteira: a allowlist de workflows no
validador S14 precisa reconhecer deploy-production.yml. A task exige
simultaneamente o quarto workflow e a suíte S14 verde, mas proíbe alterar o
validador que fixa três workflows. A decisão volta ao orquestrador.

Actionlint não foi executado por indisponibilidade local, conforme o ramo
permitido na Seção 13.

**IN_PROGRESS — aguardando revisão do orquestrador**

## 14. Resposta à revisão do orquestrador — ciclo 1 terminal

### 14.1 CWD, autoridade e escopo

```text
/home/gregorio/git/baronesa/emporio
```

A task S21 e a Seção 13 deste relatório foram relidas integralmente. A correção
foi limitada aos arquivos autorizados pela Seção 13.8:

```text
tools/deploy/deployment_transport.py
tools/deploy/tests/test_deployment_transport.py
ops/deploy/schemas/deployment-workflow-outcome.schema.json
tools/releases/validate_release_workflow.py
tools/releases/tests/test_release_publication.py
docs/infrastructure/deployment/release-control/WORKFLOW_IMPLANTACAO.md
docs/infrastructure/deployment/implementation/slices/S21-workflow-transporte-autenticado-implantacao-producao.report.md
```

Workflow, helper remoto, schemas anteriores, planner S18, núcleo S19,
adapters/CLI S20, task e tracker não foram alterados. Nenhuma S22 foi criada.

### 14.2 Emenda da allowlist S14

A incompatibilidade foi tratada como correção do contrato do orquestrador.
`validate_release_workflow.EXPECTED` agora contém exatamente `ci.yml`,
`publish-candidate.yml`, `publish-release.yml` e `deploy-production.yml`. O
validador continua inspecionando somente a semântica interna de
`publish-release.yml`.

A prova causal usa o conjunto real de quatro workflows, remove somente o
quarto em um root isolado e adiciona um quinto em outro: os dois mutantes
falham com `active-workflows`. O mutante interno de `fetch-depth` alcança e
falha novamente por `checkout-depth`, sem mascaramento pela allowlist.

### 14.3 T01 — ingress e handoff privado

O artifact baixado é agora ingresso não confiável. A leitura:

- aceita somente diretório `0700`/`0755` e os quatro arquivos regulares
  `0600`/`0644`, sem escrita para grupo/outros;
- rejeita symlink, arquivo especial, entrada extra/ausente e tamanho excedido;
- usa descritor do diretório, `O_NOFOLLOW`, `lstat`/`fstat` antes e depois,
  identidade, modos, tamanho e timestamps estáveis;
- valida canonicalidade, schemas, bindings, sidecar e metadata sobre os bytes
  lidos uma única vez.

Somente após essa prova é criado um diretório exclusivo `0700`, com quatro
arquivos exclusivos `0600`. Cada arquivo e o diretório recebem `fsync`; a cópia
é relida, revalidada e comparada byte a byte. `deploy` e `outcome` recebem
somente essa cópia, removida em `finally`. Nenhum `PRODUCTION_SSH_*` é lido
antes dessa materialização.

### 14.4 T02 — falhas locais pré-remotas

Depois do request privado, porta/host inválidos, OpenSSH ausente ou falha de
materialização produzem `CONFIRMED/FAILED` com
`SSH_CONFIGURATION_INVALID`/`SSH_UNAVAILABLE`. O arquivo
`deployment-result.json` canônico `0600` é persistido antes do exit `4`, sem
instanciar `SubprocessRunner` nem chamar SSH, SCP, helper ou cleanup. Handoff
inválido continua sem resultado inventado. Falha simulada na persistência
termina com exit `3`.

### 14.5 T03 — reconciliação fail-closed do outcome

Resultado presente é aceito somente se regular, não symlink, limitado,
canônico, integralmente válido pelo schema e vinculado nos cinco campos ao
request privado. Ausência gera `REMOTE_RESULT_UNAVAILABLE`; qualquer resultado
presente ilegível, truncado, não canônico, schema-inválido ou divergente gera
`REMOTE_RESULT_INVALID`. Ambos persistem outcome canônico `0600` com bindings
confiáveis antes do exit `4`.

O schema e `OpenSshTransport.execute()` agora exigem `errorCode` não vazio e
com formato válido em `ROLLED_BACK`/`FAILED`. Somente
`CONFIRMED/SUCCEEDED/errorCode=null` retorna exit `0`. Um outcome válido é
regravado nos mesmos bytes canônicos; bytes inválidos nunca são promovidos.

### 14.6 Mapeamento das doze provas causais

| Prova | Evidência final |
|---:|---|
| 1 | `test_45_prepare_output_remains_private`: output `0700`, quatro arquivos `0600`. |
| 2 | `test_46_public_artifact_modes_are_rematerialized_private_and_identical`: `0755/0644` vira `0700/0600`, bytes idênticos e cleanup final. |
| 3 | `test_47_hostile_ingress_and_toctou_fail_before_ssh_environment`: `0777`, `0666`, symlink, extra e troca de inode falham antes de qualquer leitura `PRODUCTION_SSH_*`. |
| 4 | `test_48_bad_port_persists_confirmed_local_failure_without_process`: artifact correto, modo `0600`, exit `4`, zero processo; falha de persistência exit `3`. |
| 5 | `test_49_missing_openssh_persists_confirmed_unavailable_without_process`: artifact correto, exit `4`, zero processo. |
| 6 | `test_50_absent_remote_result_persists_indeterminate_unavailable`: outcome persistido com bindings confiáveis. |
| 7 | `test_51_noncanonical_truncated_and_extra_results_persist_invalid`: os três mutantes persistem `REMOTE_RESULT_INVALID`. |
| 8 | `test_52_result_binding_mismatch_uses_trusted_private_bindings`: divergência rejeitada e cinco bindings restaurados do request privado. |
| 9 | `test_53_canonical_schema_incomplete_result_never_promotes_success`: mutante canônico incompleto termina exit `4`. |
| 10 | `test_54_failed_states_require_valid_error_in_schema_and_remote_result`: `null`, vazio e malformado são rejeitados em ambos os estados, pelo schema e transporte. |
| 11 | `test_55_valid_result_is_byte_preserved_and_only_clean_success_exits_zero`: bytes finais idênticos; sucesso limpo exit `0`, falha confirmada exit `4`. |
| 12 | `test_44_workflow_validator_valid` e `test_c02_13_every_checkout_has_depth_zero_and_mutant_fails`: quatro passam, ausente/quinto falham, mutante alcança `checkout-depth`. |

Comando isolado exato:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_45_prepare_output_remains_private \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_46_public_artifact_modes_are_rematerialized_private_and_identical \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_47_hostile_ingress_and_toctou_fail_before_ssh_environment \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_48_bad_port_persists_confirmed_local_failure_without_process \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_49_missing_openssh_persists_confirmed_unavailable_without_process \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_50_absent_remote_result_persists_indeterminate_unavailable \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_51_noncanonical_truncated_and_extra_results_persist_invalid \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_52_result_binding_mismatch_uses_trusted_private_bindings \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_53_canonical_schema_incomplete_result_never_promotes_success \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_54_failed_states_require_valid_error_in_schema_and_remote_result \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_55_valid_result_is_byte_preserved_and_only_clean_success_exits_zero \
  tools.releases.tests.test_release_publication.ReleasePublicationTests.test_44_workflow_validator_valid \
  tools.releases.tests.test_release_publication.ReleasePublicationTests.test_c02_13_every_checkout_has_depth_zero_and_mutant_fails
```

Exit `0`: 13 métodos cobrindo as 12 provas, executados em `0,547 s`.

### 14.7 Matriz terminal completa

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests -p 'test_*.py'
```

Exit `0`: `264/264` testes em `78,789 s`: 191 regressões S18–S20 e 73
provas S21 após a correção terminal.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'
```

Exit `0`: `277/277` testes em `5,527 s`. As três falhas históricas de allowlist
foram fechadas sem reduzir a semântica S14.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/compose/tests -p 'test_*.py'
```

Exit `0`: `4/4` testes em `0,081 s`.

| Comando exato | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py` | 0 | `deployment-plan-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py` | 0 | `deployment-executor-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py` | 0 | `production-adapter-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py` | 0 | `Compose contract valid` |

`command -v actionlint` terminou com exit `1` e sem saída. A ferramenta não
está instalada localmente; nada foi baixado e actionlint não foi executado,
conforme o ramo permitido. Nenhum comando Docker foi necessário neste ciclo.

### 14.8 Falhas intermediárias corrigidas

1. A primeira execução focada expôs o fixture histórico `errorCode="X"`, agora
   corretamente inválido; ele foi substituído por um código causal válido.
2. A primeira sentinela de ambiente do mutante T01 interceptava também leituras
   internas não relacionadas. Ela foi restringida a `PRODUCTION_SSH_*`, e a
   prova final passou com `INVALID_DISPATCH` antes desses acessos.
3. A comparação de identidade foi endurecida para incluir link count, mtime e
   ctime, cobrindo mutação de mesmo inode além da troca de inode já testada.

### 14.9 Estado protegido final

```bash
git diff --check
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -name '*.yml' -printf '%f\n' | sort
find docs/infrastructure/deployment/implementation/slices -maxdepth 1 \
  -type f -name 'S22*' -print
find . -type d -name __pycache__ -print
find . -type f -name '*.pyc' -print
```

- `git diff --check`: exit `0`, sem saída;
- índice real vazio: exit `0`, sem entradas;
- HEAD inexistente: exit `128`, `fatal: Needed a single revision`;
- tags e reflog: exit `0`, sem entradas;
- exatamente `ci.yml`, `deploy-production.yml`, `publish-candidate.yml` e
  `publish-release.yml` ativos;
- nenhuma S22, `__pycache__` ou `.pyc` encontrada.

Os SHA-256 de task, tracker, quatro workflows, helper remoto, planner S18,
executor S19 e CLI/adapters S20 coincidem com os valores capturados antes da
correção. Não houve `git add`, commit, tag, push, rede, SSH, GitHub, GHCR, DNS,
VPS, produção, Maven, npm, Docker build, Compose operacional ou instalação.

**IN_PROGRESS — aguardando revisão terminal do orquestrador**

## 16. Resposta à correção T02-A

### 16.1 Escopo e comportamento final

CWD obrigatório e utilizado:

```text
/home/gregorio/git/baronesa/emporio
```

A Seção 15 foi lida integralmente. Foram alterados somente:

```text
tools/deploy/deployment_transport.py
tools/deploy/tests/test_deployment_transport.py
docs/infrastructure/deployment/implementation/slices/S21-workflow-transporte-autenticado-implantacao-producao.report.md
```

No comando `deploy`, todo `OSError` ocorrido entre a criação do workspace SSH e
a conclusão da materialização local passa a ser tratado como
`SSH_CONFIGURATION_INVALID`. Um `DeploymentTransportError` já tipado continua
preservado sem remapeamento. Nenhuma captura genérica foi convertida em falha
confirmada.

Depois do handoff privado, o ramo `OSError` constrói e persiste
`deployment-result.json` canônico `0600` com
`CONFIRMED/FAILED/SSH_CONFIGURATION_INVALID` antes do exit `4`. O `finally`
remove workspace SSH parcial. `SubprocessRunner`, `deploy_handoff`, SSH, SCP,
helper e cleanup remoto não são alcançados. Se a própria persistência lança
`OSError`, a exceção não é recapturada pelo ramo local: o comando termina exit
`3`, sem artifact inventado, e ainda executa a limpeza do workspace.

Os blocos já aceitos de allowlist, T01 e T03 não foram modificados.

### 16.2 Quatro provas causais T02-A

| Prova | Evidência |
|---:|---|
| 1 | `test_56_t02a_ssh_mkdtemp_oserror_persists_confirmed_failure`: `mkdtemp` falha depois do handoff privado; artifact canônico/schema-válido `0600`, bindings confiáveis, exit `4` e zero processo. |
| 2 | `test_57_t02a_partial_ssh_materialization_oserror_is_removed`: materializador cria diretório/key e lança `OSError`; artifact confirmado, workspace parcial removido e zero processo/operação remota. |
| 3 | `test_58_t02a_typed_ssh_unavailable_is_preserved`: `DeploymentTransportError("SSH_UNAVAILABLE")` permanece `SSH_UNAVAILABLE`, exit `4`, sem remapeamento nem processo. |
| 4 | `test_59_t02a_persistence_failure_after_oserror_stays_exit_three`: falha de `_persist_command_outcome` termina exit `3`, sem output/artifact, sem processo e sem workspace SSH residual. |

Comando exato:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_56_t02a_ssh_mkdtemp_oserror_persists_confirmed_failure \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_57_t02a_partial_ssh_materialization_oserror_is_removed \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_58_t02a_typed_ssh_unavailable_is_preserved \
  tools.deploy.tests.test_deployment_transport.DeploymentTransportTest.test_59_t02a_persistence_failure_after_oserror_stays_exit_three
```

Exit `0`: `4/4` provas aprovadas em `0,169 s`. A linha sanitizada
`deployment-transport:INTERNAL_ERROR` na prova 4 é a evidência esperada do exit
`3` quando a persistência do resultado falha.

### 16.3 Matriz completa da Seção 15.6

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests -p 'test_*.py'
```

Exit `0`: `268/268` testes em `76,106 s`.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'
```

Exit `0`: `277/277` testes em `5,383 s`.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/compose/tests -p 'test_*.py'
```

Exit `0`: `4/4` testes em `0,071 s`.

| Comando exato | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py` | 0 | `deployment-plan-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py` | 0 | `deployment-executor-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_production_adapter.py` | 0 | `production-adapter-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py` | 0 | `Compose contract valid` |

### 16.4 Estado protegido final

Comandos de auditoria:

```bash
git diff --check
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -name '*.yml' -printf '%f\n' | sort
find docs/infrastructure/deployment/implementation/slices -maxdepth 1 \
  -type f -name 'S22*' -print
find . -type d -name __pycache__ -print
find . -type f -name '*.pyc' -print
```

- `git diff --check`: exit `0`, sem saída;
- índice Git real vazio;
- HEAD inexistente: exit `128`, `fatal: Needed a single revision`;
- nenhuma tag ou reflog;
- exatamente os quatro workflows esperados;
- nenhuma S22, `__pycache__` ou `.pyc`.

Os hashes de task, tracker, workflows, schema de outcome, allowlist, validadores,
helper remoto e código S18–S20 coincidem antes e depois da T02-A. Não houve
rede, SSH, subprocesso operacional, GitHub, GHCR, VPS, produção, Maven, npm,
Docker build, Compose operacional, `git add`, commit, tag ou push.

**IN_PROGRESS — aguardando revisão terminal do orquestrador**

## 17. Aceite terminal do orquestrador

> **Decisão:** `ACCEPTED` — 31/07/2026

A correção T02-A fecha o último bloqueio da S21. A revisão independente
confirmou que:

- `OSError` durante a preparação local do workspace SSH produz somente a
  falha confirmada e sanitizada `SSH_CONFIGURATION_INVALID`;
- `DeploymentTransportError` preserva seu código tipado, inclusive
  `SSH_UNAVAILABLE`;
- nenhum runner, SSH, SCP, helper ou efeito remoto é alcançado nesses ramos;
- o workspace parcial é removido em `finally`;
- falha ao persistir o próprio resultado continua sendo erro interno exit `3`,
  sem artifact inventado;
- as quatro provas T02-A passaram de forma independente;
- as regressões passaram com `268/268` testes de deploy, `277/277` de releases
  e `4/4` de Compose;
- os cinco validadores passaram e `git diff --check` permaneceu limpo;
- índice, HEAD, tags, reflog, workflows e resíduos permaneceram dentro do
  estado protegido prescrito.

Não há divergência conhecida ou critério pendente da S21. O workflow, o
transporte autenticado, a classificação de incerteza e o outcome persistente
estão aceitos para serem consumidos pelo runtime deployer da S22.
