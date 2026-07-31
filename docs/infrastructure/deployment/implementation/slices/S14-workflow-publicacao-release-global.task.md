# S14 — Workflow transacional de publicacao da release global

> **Estado:** `ACCEPTED` — 29/07/2026  
> **Tipo:** GitHub Actions, GitHub Releases, tags e auditoria  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S13 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de releases:** [release-control/RELEASES.md](../../release-control/RELEASES.md)  
> **Relatorio de saida:** `S14-workflow-publicacao-release-global.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a Secao 12 da revisao final da S13;
3. `RELEASES.md`, schemas, exemplos e `global_release.py`;
4. `CANDIDATOS.md`, outcome S12 e transporte seguro de artefatos;
5. `publisher.openapi.yml`, maquinas de estado e matriz de seguranca S06;
6. `.github/workflows/ci.yml`, `publish-candidate.yml` e seus validadores;
7. as secoes `Release — publish-release.yml`, `Credenciais dos dois papeis`,
   `Concorrencia e idempotencia` e `Seguranca de supply chain` da arquitetura.

Nao altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

Esta slice implementa e valida localmente a fronteira mutavel que publicara
uma release global. Como o repositorio ainda nao possui `HEAD` ou primeiro
push, o workflow nao sera executado remotamente. Nenhuma tag ou GitHub Release
real sera criada nesta slice.

## 1. Objetivo observavel

Ao final:

- existe somente um workflow de release global, separado de CI, candidato e
  producao;
- ele e disparado apenas por `workflow_dispatch`;
- recebe candidato, bump, descricao, changelog e operation ID, nunca versao,
  componente, imagem ou digest;
- valida identidade GitHub, allowlist, branch, candidato remoto e cadeia
  completa de releases antes de qualquer mutacao;
- serializa toda publicacao global sem cancelar execucao anterior;
- calcula SemVer reutilizando S13;
- retorna sucesso idempotente quando o candidato ja foi publicado;
- publica primeiro como draft, verifica os tres assets, cria a tag exata,
  torna a release visivel e verifica o estado final;
- remove somente draft/tag criados pelo proprio run se qualquer etapa falhar;
- emite outcome estrito para reconciliacao futura do publisher;
- push em `main` continua incapaz de publicar release global ou producao.

## 2. Fronteira de arquivos

### 2.1 Criar

```text
.github/workflows/publish-release.yml
ops/releases/release-publication-plan.schema.json
ops/releases/release-publication-outcome.schema.json
ops/releases/examples/release-publication-plan.example.json
ops/releases/examples/release-publication-outcome.example.json
tools/releases/release_publication.py
tools/releases/validate_release_workflow.py
tools/releases/tests/test_release_publication.py
docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md
docs/infrastructure/deployment/implementation/slices/S14-workflow-publicacao-release-global.report.md
```

### 2.2 Alterar somente para integrar

```text
.github/workflows/README.md
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RELEASES.md
tools/releases/global_release.py
tools/releases/tests/test_global_release.py
tools/releases/validate_candidate_manifest.py
```

Uma alteracao em `global_release.py` somente pode adicionar a validacao
contextual da cadeia historica ou uma API reutilizavel pelo workflow. Nao
alterar schemas ou semantica S13.

### 2.3 Nao alterar

- `ci.yml` e `publish-candidate.yml`;
- catalogo, candidato, Dockerfiles, Compose e gateway;
- codigo comercial e migrations;
- OpenAPI, state machines e security matrix S06;
- qualquer arquivo do futuro runtime/UI de `release_control`;
- task S14, tracker, S15 ou documento de deploy de producao.

### 2.4 Proibido executar

- `git add`, commit, tag ou push;
- `gh`, `curl`, workflow remoto ou API real do GitHub;
- login/push GHCR, build Docker, Maven, NPM ou Compose up;
- acesso a VPS ou producao;
- instalacao/atualizacao de dependencias;
- criacao da S15.

E permitido somente executar `actionlint` local com a mesma imagem fixada e
efemera aceita na S12, removendo-a ao final.

## 3. Trigger e inputs definitivos

Workflow:

```text
name: Publish Release
arquivo: .github/workflows/publish-release.yml
trigger unico: workflow_dispatch
```

Inputs obrigatorios, exatamente:

```text
operation_id
candidate_id
version_bump
description
changelog
```

Contratos:

- `operation_id`: string de 20 a 128, pattern `^[A-Za-z0-9_-]+$`;
- `candidate_id`: string de 12 a 128;
- `version_bump`: input `choice`, opcoes nesta ordem:
  `PATCH`, `MINOR`, `MAJOR`;
- `description`: string obrigatoria, maximo semantico 500;
- `changelog`: string obrigatoria, maximo semantico 10000.

O workflow nao recebe:

- versao final;
- artifact ID/digest;
- commit SHA;
- componente, imagem, tag OCI ou digest OCI;
- repositorio, owner, URL, path, comando ou environment;
- ator ou actor ID;
- estado desejado.

O helper le os inputs exclusivamente do JSON em `GITHUB_EVENT_PATH`.
Descricao/changelog/operation/candidato nunca podem ser interpolados dentro
de `run:`. Contextos GitHub confiaveis podem ser passados por `env`.

## 4. Identidade e autorizacao

O job inicial valida, antes de download:

- imports de `yaml` e `jsonschema` disponiveis no Python do runner; nao
  instalar dependencia dinamicamente dentro deste workflow;
- repository e owner exatamente `greggorio/abaronesa-emporio`;
- default branch `main`;
- evento `workflow_dispatch`;
- `github.ref == refs/heads/main`;
- run ID e attempt positivos;
- `github.actor`, `github.actor_id` e `event.sender` coerentes;
- current run consultado por ID pertence a `Publish Release`, esta no
  repositorio canonico, usa event `workflow_dispatch`, branch `main` e attempt
  recebido;
- current run `head_sha` e igual a `GITHUB_SHA`; depois do fetch,
  `GITHUB_SHA` deve ser igual ou ancestral de `origin/main`.

Variavel obrigatoria do repositorio:

```text
RELEASE_PUBLISHER_ACTOR_IDS
```

Formato: lista CSV nao vazia de 1 a 20 IDs GitHub decimais positivos, sem
espaco, vazio, duplicidade ou wildcard. `github.actor_id` deve pertencer a
lista. Ausencia ou formato invalido falha fechado.

Essa allowlist autoriza a identidade GitHub que dispara o workflow. O ator de
negocio da futura UI permanece no registro local auditavel do
`release_control`; nao criar input nao autenticado para representa-lo.

## 5. Concorrencia e permissoes

No topo:

```yaml
permissions:
  contents: read
  actions: read

concurrency:
  group: emporio-release-publication
  cancel-in-progress: false
```

Jobs de leitura mantem somente `contents: read` e `actions: read`.
Somente o job `publish` recebe:

```yaml
permissions:
  contents: write
  actions: read
```

O job de outcome nao recebe `contents: write`. Nao usar `packages`, `id-token`,
`attestations`, `deployments`, SSH, PAT ou secret customizado. O workflow usa
somente `github.token`.

## 6. Grafo de jobs exato

```text
trust -> prepare -> publish -> outcome
```

- `trust`: identidade, allowlist, request, run e branch;
- `prepare`: candidato, historia, SemVer, bundle e plan;
- `publish`: somente quando `plan.mode == publish`;
- `outcome`: quando `prepare` passou e `publish` passou ou foi corretamente
  skipped em `already_published`.

`publish` usa `if` estrito para `mode == publish`.
`outcome` valida:

- `publish` deve ser `success` em `publish`;
- `publish` deve ser `skipped` em `already_published`;
- qualquer outra combinacao falha.

Timeouts:

```text
trust=10 minutos
prepare=20 minutos
publish=15 minutos
outcome=10 minutos
```

Runner fixo: `ubuntu-24.04`.

Actions permitidas, fixadas pelos mesmos SHAs ja aceitos:

```text
actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd
actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02
actions/download-artifact@634f93cb2916e3fdff6788551b99b062d0335ce0
```

Checkout sempre usa `persist-credentials: false` e `fetch-depth: 0`.

## 7. Resolucao remota do candidato

`candidate_id` deve ter exatamente:

```text
candidate-<40-hex>-<run-id-decimal>-<attempt-decimal>
```

O helper extrai SHA/run/attempt do ID, mas nao confia neles sem validar:

1. consulta o run exato;
2. exige workflow `Publish Candidate`;
3. exige status completed, conclusion success, event `workflow_run`,
   branch `main`, repositorio/owner canonicos;
4. exige head SHA, run ID e attempt iguais ao candidate ID;
5. exige exatamente um artifact `candidate-manifest` e um
   `candidate-outcome`, ambos nao expirados e com digest REST valido;
6. baixa ambos pelo endpoint canonico usando `gh api`;
7. aplica limites, nomes exatos, extracao segura, sidecar e metadata S12;
8. valida manifesto final v2 pelo helper canonico;
9. exige outcome `published`, nunca terminal;
10. liga candidate ID, commit, run, attempt, artifact ID e digest entre run,
    artifacts, outcome e manifesto;
11. exige que o commit exista e seja ancestral de `origin/main`;
12. rejeita duplicidade, artifact extra com nome canonico ou qualquer
    divergencia.

O artifact ID/digest usado por S13 vem somente do REST validado. Nunca e
aceito do input.

## 8. Historia autoritativa de releases

A fonte autoritativa e GitHub Releases, nao artifacts temporarios de Actions.

O helper pagina no maximo 10 paginas de 100. Deve falhar por:

- paginacao esgotada;
- draft ou prerelease preexistente;
- release sem SemVer estrito;
- nome diferente da tag;
- assets ausentes, extras ou duplicados;
- asset em estado diferente de `uploaded`;
- IDs/tamanhos/content types invalidos;
- download excedendo limites;
- bundle, sidecar, metadata, schema ou semantica invalidos;
- tag ausente ou apontando para commit diferente de `sourceCommit`.

Cada release possui exatamente estes assets:

```text
release.json
release.json.sha256
metadata.json
```

Limites:

```text
release.json: 2 MiB
release.json.sha256: 128 bytes
metadata.json: 16 KiB
```

Content types:

```text
release.json=application/json
release.json.sha256=text/plain
metadata.json=application/json
```

Depois de ordenar por SemVer:

- a primeira release exige `previousRelease == null`;
- cada release seguinte exige `previousRelease` igual a release imediatamente
  anterior;
- candidate IDs sao unicos;
- publication workflow run IDs sao unicos;
- conjunto de tags SemVer remotas e conjunto de releases sao identicos;
- nenhuma tag SemVer solta e nenhuma release sem tag e aceita.

Adicionar essa validacao contextual como funcao reutilizavel, sem mudar a
validacao autocontida da S13.

## 9. Idempotencia por candidato

Se nenhum manifesto historico usa o candidate ID:

```text
mode=publish
```

Se exatamente um manifesto historico usa o candidate ID:

```text
mode=already_published
```

Nesse modo:

- o request continua integralmente validado;
- nenhuma nova versao e calculada para publicacao;
- nenhuma tag, draft, asset ou release e criada;
- o plan referencia a release existente;
- outcome termina com sucesso `already_published`.

Mais de uma release usando o candidato falha como historia invalida. O
workflow nao trata `operation_id` como autoridade de idempotencia; essa chave
e correlacao com a operacao persistida pelo futuro publisher. A garantia
material contra dupla release e candidate ID unico mais concorrencia global.

## 10. Snapshot e plano de publicacao

`release-publication-plan.json` usa o schema criado nesta slice, JSON
canonico, sidecar e metadata, e possui exatamente:

```text
schemaVersion
kind
mode
repository
operationId
requestSha256
workflow
candidate
historySnapshotSha256
target
```

Identidade:

```text
schemaVersion=1
kind=release-publication-plan
mode=publish|already_published
repository=greggorio/abaronesa-emporio
```

`workflow`:

```text
runId
attempt
actor
actorId
event=workflow_dispatch
```

`candidate` copia os seis bindings S13.

`requestSha256` e o digest do JSON canonico com exatamente os quatro campos
do request S13, excluindo `operation_id`.

`historySnapshotSha256` e o digest do JSON canonico da lista SemVer ordenada,
com cada item exatamente:

```text
releaseId
tagName
tagCommitSha
manifestSha256
releaseAssetId
sidecarAssetId
metadataAssetId
```

`plan/metadata.json` possui exatamente:

```text
schemaVersion=1
stage=final
kind=release-publication-plan
repository=greggorio/abaronesa-emporio
operationId
workflowRunId
workflowAttempt
planSha256
```

`planSha256` e o digest dos bytes de `plan.json`. O sidecar e a metadata
devem ser validados em todo download do handoff.

Para `publish`, `target` possui exatamente:

```text
release
sourceCommit
previousRelease
manifestSha256
existingReleaseId=null
existingReleaseUrl=null
```

e o artifact de handoff contem:

```text
plan/plan.json
plan/plan.json.sha256
plan/metadata.json
release/release.json
release/release.json.sha256
release/metadata.json
release/notes.md
```

Para `already_published`, `target` usa a release existente, source commit,
previous release, manifest digest, release ID e URL; o handoff nao contem
diretorio `release/`.

Artifact:

```text
name=release-publication-plan
retention-days=1
if-no-files-found=error
overwrite=false
```

## 11. Preparacao da release

No modo `publish`, reutilizar `global_release.build_release` com:

- request extraido do evento;
- candidato e artifact binding remotos;
- releases anteriores validadas;
- `publishedAt` igual ao `created_at` do current workflow run validado;
- current run ID/attempt;
- `github.actor` e `github.actor_id`;
- roots de migration materializados exatamente do `sourceCommit` candidato.

Antes de gerar, exigir que os fingerprints dos roots no checkout atual sejam
compativeis com o commit candidato:

- se o candidato nao e o HEAD atual, criar worktree temporaria read-only ou
  usar `git archive` do candidate SHA para inventariar exatamente os dois
  roots daquele commit;
- nao usar migrations da `main` atual para uma release de candidato antigo;
- nao executar codigo do checkout temporario;
- remover o temporario ao final.

O manifesto global, portanto, sempre liga BOM e migrations ao mesmo
`sourceCommit`.

`notes.md` exato:

```text
# <release>

<description>

## Changelog

<changelog>

## Proveniencia

- Commit: `<sourceCommit>`
- Candidato: `<candidateId>`
- Manifesto: `<manifestSha256>`
- Operacao: `<operationId>`
```

LF final obrigatorio. Conteudo e arquivo sao usados como dados; nunca passam
por avaliacao de shell.

## 12. Publicacao transacional

O job `publish` baixa e valida integralmente o handoff antes de mutar.

Sequencia exata:

1. reconsultar current run, allowlist, candidato e historia;
2. exigir snapshot identico e candidato ainda nao publicado;
3. exigir ausencia da tag e da release alvo;
4. criar uma GitHub Release `draft=true`, `prerelease=false`, com tag/name
   alvo e target commit, guardando seu ID;
5. subir os tres assets com nomes e content types da Secao 8;
6. reconsultar o draft e baixar os tres assets;
7. exigir igualdade byte a byte com o bundle local;
8. reconsultar historia publicada, ignorando somente o draft criado pelo
   proprio run, e exigir snapshot inalterado;
9. criar tag lightweight `refs/tags/<release>` apontando exatamente para
   `sourceCommit`;
10. publicar o draft (`draft=false`);
11. reconsultar release, assets e tag;
12. exigir estado final nao draft, nao prerelease, nome/tag/commit corretos,
    assets exatos e bytes validos.

Nao usar `gh release create`, pois upload parcial nao deve tornar uma release
visivel. Usar endpoints REST explicitos via `gh api`, arrays de argumentos e
arquivos JSON; nunca `shell=True`, `eval` ou comando construido por
concatenacao.

### 12.1 Compensacao

Se qualquer falha ocorrer depois da criacao do draft:

1. apagar somente a release pelo ID retornado diretamente pela chamada de
   criacao deste processo, que estava ausente no snapshot inicial, depois de
   revalidar repository, tag, nome e estado esperados;
2. se a tag tiver sido criada pelo run e ainda apontar para o source commit
   esperado, apagar somente essa ref;
3. provar que release/draft e tag alvo nao permanecem;
4. nunca apagar release/tag preexistente ou com binding divergente;
5. se a compensacao nao puder ser provada, falhar com
   `PUBLICATION_COMPENSATION_FAILED`.

O helper aceita um transport injetavel nos testes. Nenhum teste chama rede
real.

## 13. Outcome de reconciliacao

`release-publication-outcome.json` usa schema estrito e exatamente:

```text
schemaVersion
kind
status
repository
operationId
candidateId
release
sourceCommit
workflow
githubRelease
manifestSha256
recordedAt
```

Identidade:

```text
schemaVersion=1
kind=release-publication-outcome
status=published|already_published
repository=greggorio/abaronesa-emporio
```

`workflow`:

```text
runId
attempt
url
actor
actorId
```

`githubRelease`:

```text
id
url
tagName
```

Bindings:

- operation/candidate vêm do event/plan;
- release, commit e manifest digest vêm do bundle final validado;
- current workflow liga `published`;
- em `already_published`, `githubRelease` e manifesto sao os existentes, mas
  `workflow` continua sendo o current run reconciliado;
- `recordedAt`: UTC RFC 3339 `Z` produzido pelo relogio injetavel do helper no
  momento em que o estado remoto final ja esta comprovado; esse instante nao
  representa a conclusao do workflow, pois o run ainda esta em execucao;
- URL usa somente `https://github.com/greggorio/abaronesa-emporio/releases/tag/<SemVer>`.

Bundle atomico:

```text
outcome.json
outcome.json.sha256
metadata.json
```

`metadata.json` do outcome possui exatamente:

```text
schemaVersion=1
stage=final
kind=release-publication-outcome
repository=greggorio/abaronesa-emporio
operationId
workflowRunId
workflowAttempt
outcomeSha256
```

`outcomeSha256` e o digest dos bytes canonicos de `outcome.json`.

Artifact:

```text
name=release-publication-outcome
retention-days=90
if-no-files-found=error
overwrite=false
```

O outcome e criado somente depois de validar o estado final remoto ou o
`already_published` historico. Workflow remoto vermelho e ausencia de outcome
sao evidencia de falha para o reconciliador futuro; nao fabricar outcome de
sucesso em excecao.

## 14. Seguranca de transporte

- toda resposta REST tem shape, repository, IDs e limites validados;
- downloads usam arquivo temporario e limite durante streaming, nao somente
  depois;
- ZIP de Actions reutiliza `safe_extract_named`;
- assets de Release nunca sao tratados como ZIP;
- arquivos temporarios usam diretorio exclusivo e sao removidos;
- logs nao imprimem bodies, tokens, description ou changelog;
- erros sao codigos estaveis e mensagens sanitizadas;
- `GH_TOKEN` nunca e passado em argv;
- `GITHUB_OUTPUT` usa LF e valores previamente validados de uma linha;
- nenhum dado remoto controla endpoint fora do repositorio canonico, path
  local, nome de arquivo ou comando.

## 15. Validador local

`tools/releases/validate_release_workflow.py` deve falhar fechado e validar:

- exatamente tres workflows ativos:
  `ci.yml`, `publish-candidate.yml`, `publish-release.yml`;
- trigger unico `workflow_dispatch`;
- cinco inputs exatos e constraints;
- concorrencia global e `cancel-in-progress: false`;
- grafo, ifs, timeouts e runner exatos;
- permissoes de topo e job;
- actions permitidas e fixadas;
- checkout endurecido;
- ausencia de interpolacao de inputs/untrusted data em `run:`;
- helpers, schemas e exemplos presentes;
- handoffs, retention e nomes canonicos;
- ausencia de push/main, workflow_run, schedule, SSH, Docker, packages,
  environment production ou deploy.

Saidas:

```text
release-workflow:valid
release-workflow:invalid:<erro sanitizado>
```

Exit `0`/`3`.

## 16. Provas causais obrigatorias

Adicionar testes de helpers/transport/workflow reais que cubram:

1. schemas e exemplos de plan/outcome;
2. evento e cinco inputs validos;
3. input ausente/extra e textos/operation invalidos;
4. repository/ref/event/sender/run divergentes;
5. allowlist ausente, vazia, wildcard, duplicada, malformada e ator ausente;
6. candidate ID parsing;
7. candidate run status/name/event/branch/repository/SHA/attempt divergente;
8. artifact candidato ausente, duplicado, expirado, nome/digest/run
   divergente;
9. candidate bundle/outcome/metadata/sidecar divergentes;
10. candidate commit inexistente, nao ancestral ou descendente da main;
11. historia vazia;
12. historia valida com tres releases e cadeia imediata;
13. draft, prerelease, tag solta e release sem tag;
14. SemVer/name/tag/commit divergentes;
15. asset ausente, extra, duplicado, size/content type/state invalido;
16. asset bytes/sidecar/metadata/schema invalido;
17. primeira previous nao null e elo intermediario divergente;
18. candidate ID ou publication run historico duplicado;
19. snapshot deterministico e sensivel a qualquer binding;
20. candidato novo gera `publish`;
21. candidato existente gera `already_published` sem transport mutavel;
22. request order/JSON event nao altera hashes;
23. candidato antigo inventaria migrations do proprio commit;
24. bundle/notes/plan canonicos;
25. handoff publish e already possuem shapes exatos;
26. snapshot divergente antes do draft bloqueia sem mutacao;
27. draft criado com tag/release alvo ainda ausentes;
28. tres uploads usam nomes/content types/bytes exatos;
29. verificacao byte a byte antes de tag/publicacao;
30. snapshot divergente depois dos uploads compensa draft;
31. tag criada no commit exato e somente depois de assets verdes;
32. draft torna-se publico somente depois da tag;
33. estado final remoto integral produz `published`;
34. falhas em cada mutacao executam compensacao cumulativa;
35. binding divergente impede delecao de recurso preexistente;
36. falha de compensacao produz codigo especifico;
37. outcome published integral;
38. outcome already published liga release antiga ao current run;
39. nenhuma excecao cria outcome verde;
40. outputs usam LF e nao contem texto livre;
41. mutantes do workflow para trigger, permissao, concurrency, gate,
   interpolation e action nao fixada falham;
42. `validate_release_workflow.py` usa exits/prefixos prescritos;
43. suites S13 continuam verdes.

O executor escolhe a organizacao dos metodos, nao o comportamento.
Mocks substituem somente transporte GitHub e relogio; validadores, parsers,
builders, compensacao e CLIs reais devem ser exercitados.

## 17. Matriz final obrigatoria

Executar:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate \
  --manifest ops/releases/examples/global-release.example.json

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py \
  validate --require-release-ready

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py \
  validate
```

`actionlint` nos tres workflows com a imagem fixada:

```text
docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9
```

Remover somente essa imagem ao final se ela nao existia antes. Se ja existia,
preserva-la e registrar o estado inicial/final.

Tambem:

```bash
git diff --check
git diff --cached --name-only
git rev-parse --verify HEAD
git tag --list
git reflog
find . -type d -name __pycache__ -o -type f -name '*.pyc'
find .github/workflows -maxdepth 1 -type f \
  \( -name '*.yml' -o -name '*.yaml' \) -print | sort
```

Resultados:

- validadores, testes e actionlint verdes;
- indice vazio;
- `HEAD`, tags e reflog inexistentes;
- zero caches/residuos;
- exatamente tres workflows ativos;
- nenhuma chamada real `gh`, tag, release, artifact ou publicacao.

Nao executar Maven, NPM, Docker build ou Compose; essas superficies nao mudam.

## 18. Documentacao viva

Criar `RELEASE_PUBLICATION.md` e atualizar os READMEs com:

- diferenca candidato/release/outcome;
- inputs da UI futura e campos que ela nao controla;
- allowlist e privilegio minimo;
- idempotencia por candidato e concorrencia;
- fonte autoritativa GitHub Releases;
- cadeia `previousRelease`;
- draft, assets, tag, publicacao e compensacao;
- outcome usado pelo futuro reconciliador;
- configuracao futura da repository variable;
- comandos locais de validacao;
- fronteira: workflow ainda nao executado remotamente.

Nao documentar actor ID real nem criar valor default para allowlist.

## 19. Relatorio obrigatorio

Criar o relatorio indicado com:

- CWD;
- arquivos criados/alterados;
- grafo, triggers, inputs e permissoes finais;
- shapes de plan/outcome;
- endpoints REST e limites;
- algoritmo de historia/snapshot/idempotencia;
- ordem mutavel e compensacao;
- tabela dos 43 casos causais;
- comandos, exits, contagens e interpretacao;
- actionlint e estado da imagem;
- estado Git/workflows/caches;
- confirmacao de zero acesso GitHub real;
- qualquer divergencia factual.

Estado final:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao declarar `ACCEPTED`.

## 20. Criterios de aceite do orquestrador

A S14 somente sera aceita se:

- S13 estiver aceita e suas suites continuarem verdes;
- workflow, schemas, helpers, exemplos, testes e docs coincidirem;
- nenhum input fornecer versao ou autoridade tecnica;
- allowlist, repository, actor, ref e run falharem fechado;
- candidato remoto completo for validado antes de mutacao;
- historia de Releases/tags/assets for integral e encadeada;
- candidato ja publicado for sucesso sem mutacao;
- SemVer for calculado somente depois da historia;
- snapshot for revalidado imediatamente antes das mutacoes irreversiveis;
- draft e assets forem verificados antes de tag/publicacao;
- compensacao nunca atingir recurso preexistente;
- outcome somente representar sucesso comprovado;
- permissoes e concorrencia forem minimas;
- testes positivos, negativos, mutantes e actionlint passarem;
- nenhum estado remoto real for criado;
- estado Git protegido for preservado.

A proxima slice prevista, apos aceite, sera a S15: fundacao executavel do
modulo `release_control` no modo publisher, persistencia transacional,
integracao GitHub autenticada e reconciliacao, ainda sem UI.

## 21. Condicoes de bloqueio

Parar e documentar se:

- GitHub REST nao permitir draft/assets/tag na sequencia especificada;
- a identidade do candidate artifact nao puder ser ligada ao run S12;
- a historia nao puder ser validada sem afrouxar S13;
- o workflow exigir PAT, SSH ou permissao fora do contrato;
- actionlint revelar sintaxe impossivel para o grafo;
- algum teste tentar usar rede real;
- for necessario alterar codigo comercial, migrations ou producao;
- o indice Git deixar de estar vazio;
- qualquer comando criar tag, release ou artifact remoto.
