# S14 — Relatorio de execucao

> **Estado:** `IN_PROGRESS — aguardando revisao do orquestrador`  
> **Data:** `2026-07-29`  
> **CWD:** `/home/gregorio/git/baronesa/emporio`

## 1. Contrato e fronteira

Foram lidos integralmente a task S14, a revisao final S13, os contratos de
release/candidato, as superficies S06, os workflows/validadores existentes e
as secoes vinculadas da arquitetura. A task e o tracker nao foram alterados.

Nao houve `git add`, commit, tag, push, `gh`, `curl`, workflow remoto, GitHub
real, GHCR, release, artifact remoto, Maven, NPM, Docker build, Compose,
instalacao, VPS ou producao. O unico acesso externo foi o pull permitido da
imagem actionlint fixada por digest; ela nao existia no inicio e foi removida.

## 2. Arquivos

Criados:

- `.github/workflows/publish-release.yml`;
- `ops/releases/release-publication-plan.schema.json`;
- `ops/releases/release-publication-outcome.schema.json`;
- `ops/releases/examples/release-publication-plan.example.json`;
- `ops/releases/examples/release-publication-outcome.example.json`;
- `tools/releases/release_publication.py`;
- `tools/releases/validate_release_workflow.py`;
- `tools/releases/tests/test_release_publication.py`;
- `docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md`;
- este relatorio.

Alterados somente para integracao:

- `.github/workflows/README.md`;
- `docs/infrastructure/deployment/release-control/README.md`;
- `docs/infrastructure/deployment/release-control/RELEASES.md`;
- `tools/releases/global_release.py`.

`global_release.py` recebeu somente `validate_release_chain`, uma API
contextual para elo imediato, candidato e run de publicacao unicos. A
validacao autocontida S13 nao mudou.

## 3. Workflow final

Trigger unico: `workflow_dispatch`.

Inputs exatos:

```text
operation_id
candidate_id
version_bump = PATCH | MINOR | MAJOR
description
changelog
```

O helper le os cinco campos do `GITHUB_EVENT_PATH`; nenhum input livre e
interpolado em `run:`. Versao, SHA, artifact, digest, imagem, componente,
repositorio, URL, path, comando, ator e environment nao sao inputs.

Grafo e timeout:

| Job | Needs | Timeout | Permissoes |
|---|---|---:|---|
| `trust` | — | 10 min | `contents:read`, `actions:read` |
| `prepare` | `trust` | 20 min | `contents:read`, `actions:read` |
| `publish` | `prepare` | 15 min | `contents:write`, `actions:read` |
| `outcome` | `prepare,publish` | 10 min | `contents:read`, `actions:read` |

Todos usam `ubuntu-24.04`. A concorrencia e global,
`emporio-release-publication`, com `cancel-in-progress: false`. O job mutavel
roda somente em `mode=publish`; o outcome exige `success` nesse modo e
`skipped` em `already_published`.

As unicas actions sao checkout, upload e download nos SHAs prescritos. Todo
checkout usa `persist-credentials:false`; nenhum job recebe packages, OIDC,
attestations, deployments ou environment.

## 4. Plan e outcome

O plan estrito possui exatamente identidade, operacao, hash do request,
workflow atual, seis bindings do candidato, snapshot historico e target. Em
`publish`, target ainda nao tem release remota; em `already_published`, ID e
URL apontam para a unica release historica do candidato.

O handoff `release-publication-plan`, retido por um dia, possui:

```text
plan/plan.json
plan/plan.json.sha256
plan/metadata.json
release/release.json
release/release.json.sha256
release/metadata.json
release/notes.md
```

O diretorio `release/` inexiste em `already_published`. JSON, sidecars,
metadata, arquivos e notes sao revalidados antes da mutacao.

O outcome estrito liga status, operacao, candidato, release, source commit,
workflow atual, GitHub Release e digest do manifesto. Seu relogio UTC injetavel
logicamente so e consultado depois da reconciliacao. O bundle atomico contem
`outcome.json`, sidecar e metadata e e retido por 90 dias. Excecoes nao
fabricam outcome verde.

## 5. Transporte, historia e limites

O transporte remoto fica isolado em `GhTransport`; testes usam
`FakeTransport`. Chamadas usam arrays `subprocess`, endpoints canonicos e
`GH_TOKEN` somente no ambiente.

Endpoints modelados:

- `GET /actions/runs/{id}` e `/actions/runs/{id}/artifacts`;
- `GET /actions/artifacts/{id}/zip`;
- `GET /releases`, `/releases/{id}`, `/releases/tags/{tag}`;
- `GET /releases/assets/{id}`;
- `GET /git/matching-refs/tags/v` e `/git/ref/tags/{tag}`;
- `POST /releases`, upload em `uploads.github.com`, `POST /git/refs`;
- `PATCH /releases/{id}`;
- `DELETE /releases/{id}` e `/git/refs/tags/{tag}`.

Paginacao aceita no maximo dez paginas de cem e falha se o limite termina
cheio. Downloads sao lidos em blocos e interrompidos ao exceder limite.
Artifacts Actions usam ZIP com nomes, digest, tamanho, path, sidecar e metadata
validados. Assets de Release sao bytes, nunca ZIP:

| Asset | Limite | Content-Type |
|---|---:|---|
| `release.json` | 2 MiB | `application/json` |
| `release.json.sha256` | 128 B | `text/plain` |
| `metadata.json` | 16 KiB | `application/json` |

Candidate ID e decomposto em SHA/run/attempt e cada parte e confirmada contra
run, artifacts, outcome, manifesto, sidecar e metadata. O commit precisa
existir e ser ancestral de `origin/main`.

GitHub Releases e tags sao a historia autoritativa. Cada release e bundle
completo e canonico; tags/release devem formar conjuntos identicos. A ordem
SemVer numerica exige `previousRelease` imediato, candidate IDs e publication
run IDs unicos. O snapshot ordenado inclui release/tag/commit/digest e os tres
asset IDs. Candidato ausente gera `publish`; exatamente uma ocorrencia gera
`already_published`; duplicidade falha.

Para candidato antigo, `git archive` materializa somente os dois roots Flyway,
rejeita path/symlink inesperado, nao executa codigo e e removido ao final. BOM
e migrations ficam ligados ao mesmo `sourceCommit`.

## 6. Ordem mutavel e compensacao

Ordem implementada:

1. revalidar handoff e snapshot;
2. provar target ausente;
3. criar draft e guardar o ID retornado;
4. enviar os tres assets com nomes/content types/bytes exatos;
5. baixar e comparar bytes;
6. revalidar snapshot ignorando somente o draft proprio;
7. criar tag lightweight no source commit;
8. publicar o draft;
9. reconsultar release, assets e tag.

Depois da tentativa de draft, falhas acumulam compensacao. Somente o draft
identificado, ainda com tag/nome/draft esperados, e removido. A tag so e
removida se pertence ao target e aponta ao SHA esperado, inclusive quando a
resposta da criacao ficou ambigua. A ausencia de release e tag e provada. Uma
falha de cleanup resulta em `PUBLICATION_COMPENSATION_FAILED`.

## 7. Provas causais

Os 47 testes S14 cobrem os 43 comportamentos contratuais:

| # | Evidencia |
|---:|---|
| 1 | schemas e exemplos plan/outcome |
| 2 | evento e cinco inputs |
| 3 | ausente, extra e textos invalidos |
| 4 | identidade repository/ref/event/sender/run |
| 5 | allowlist ausente, wildcard, duplicada e malformada |
| 6 | parsing causal do candidate ID |
| 7 | contrato estrito do candidate run no resolver |
| 8 | selecao unica, expiry e digest de artifacts |
| 9 | bundle/outcome/metadata/sidecar ligados |
| 10 | existencia e ancestry no caminho executavel |
| 11 | historia vazia |
| 12 | tres releases em cadeia imediata |
| 13 | draft, prerelease e conjuntos tag/release |
| 14 | SemVer, name, tag e commit |
| 15 | assets ausentes, extras, duplicados e metadados REST |
| 16 | bytes, sidecar, metadata, schema |
| 17 | primeiro previous e elo intermediario |
| 18 | candidate e publication run duplicados |
| 19 | snapshot deterministico e sensivel |
| 20 | candidato novo produz publish |
| 21 | candidato existente produz already sem mutacao |
| 22 | ordem JSON nao altera hash |
| 23 | migrations do SHA por archive seguro |
| 24 | bundle, notes e plan canonicos |
| 25 | handoffs distintos por modo |
| 26 | snapshot divergente bloqueia antes do draft |
| 27 | target deve estar ausente antes do draft |
| 28 | tres uploads exatos |
| 29 | download byte a byte precede tag |
| 30 | snapshot pos-upload e compensacao |
| 31 | tag exata depois dos assets |
| 32 | publicacao somente depois da tag |
| 33 | reconciliacao final produz published |
| 34 | falhas mutaveis compensam cumulativamente |
| 35 | bindings protegem recurso preexistente |
| 36 | falha de compensacao tem codigo especifico |
| 37 | outcome published estrito |
| 38 | already liga release antiga ao current run |
| 39 | excecao nao cria outcome |
| 40 | outputs de linha unica com LF |
| 41 | mutantes trigger/permissao/concurrency/gate/action |
| 42 | validador usa prefixos e exits |
| 43 | 140 testes herdados S13 continuam verdes |

## 8. Comandos e resultados

| Comando exato | Exit | Resultado e interpretacao |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | `Ran 187 tests ... OK`; 47 S14 + 140 herdados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json` | 0 | `global-release:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `docker image inspect docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 1 | imagem ausente inicialmente |
| `docker run --rm -v /home/gregorio/git/baronesa/emporio:/repo:ro -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color=false .github/workflows/ci.yml .github/workflows/publish-candidate.yml .github/workflows/publish-release.yml` | 0 | tres workflows sem achados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | somente a imagem efemera removida |
| `docker image inspect docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 1 | estado final igual ao inicial |

Uma execucao focada intermediaria encontrou `JSONDecodeError` cru ao mutar
`metadata.json`. O parser foi corrigido para produzir
`HISTORY_ASSET_BYTES_INVALID`; a repeticao focada executou 47 testes com exit
0 antes da matriz integral.

## 9. Estado protegido

Verificacoes finais prescritas:

- `git diff --check`: exit 0;
- `git diff --cached --name-only`: exit 0 e vazio;
- `git rev-parse --verify HEAD`: exit 128, `HEAD` inexistente;
- `git tag --list`: exit 0 e vazio;
- `git reflog`: exit 128, sem reflog porque nao existe commit;
- busca de `__pycache__`/`*.pyc`: exit 0 e zero resultados apos limpeza;
- workflows: exatamente `ci.yml`, `publish-candidate.yml` e
  `publish-release.yml`.

O indice real permaneceu vazio. Nao houve commit, tag, push ou mutacao remota.

## 10. Resultado

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao foi declarado `ACCEPTED` e a S15 nao foi criada.

## 11. Revisao do orquestrador — correção causal 01

**Veredito:** a S14 permanece `IN_PROGRESS`. A S15 continua bloqueada.

A revisão foi feita sobre contrato, relatório, workflow, helper, validador e
testes persistidos, sem reproduzir a suíte declarada pelo executor.

### 11.1 Divergências contra requisitos já expressos

1. `validate_identity` e `resolve_candidate` consultam
   `run.workflow.name`. A REST API de workflow runs fornece `name`,
   `workflow_id` e `path` no topo. As fixtures positivas reproduzem o shape
   inventado, portanto o caminho real falharia com `CURRENT_RUN_INVALID` ou
   `CANDIDATE_RUN_INVALID`.
2. IDs de artifacts e assets retornados remotamente entram em endpoints antes
   de validação de tipo, positividade, URL, repository/run binding, state,
   content type e size. Isso diverge da Seção 14, que proíbe dado remoto não
   validado controlando endpoint.
3. `candidate/metadata.json` é comparado somente depois de `json.loads`, e
   `outcome.json` também não tem seus bytes comparados ao JSON canônico. Isso
   aceita representações que não pertencem ao bundle canônico S12.
4. `download_assets` e `final_state` verificam nomes/bytes, mas não reaplicam
   integralmente IDs, URLs, state, content types e sizes antes do download e
   do outcome. Isso diverge das Seções 8, 12 e 14.

### 11.2 Omissão do orquestrador

O contrato definiu `notes.md` exato, porém a sequência de publicação não disse
explicitamente que ele seria o `body` da GitHub Release. A implementação
consequentemente cria a release sem `body`. Esse ponto não é classificado como
erro retroativo do executor; foi fechado prospectivamente na correção causal.

### 11.3 Contrato de correção

O contrato executável e sem escolhas abertas está em:

`S14-workflow-publicacao-release-global.correction-01.md`

Ele fixa:

- shapes REST reais de current/candidate run;
- validação integral antes de IDs remotos controlarem endpoints;
- canonicalidade de metadata/outcome;
- revalidação de assets em história, draft, final e outcome;
- `notes.md` como body exato;
- ordem mutável e compensação;
- 18 provas causais adicionais e matriz final.

Nenhuma alteração da S15 está autorizada antes da nova revisão.

## 12. Resposta à correção causal 01

> **Estado:** `IN_PROGRESS — aguardando nova revisão do orquestrador`  
> **Data:** `2026-07-29`  
> **CWD:** `/home/gregorio/git/baronesa/emporio`

### 12.1 Escopo e arquivos

Foram relidos integralmente, nesta ordem, a task S14, a correção causal 01 e
este relatório. Os shapes também foram conferidos na documentação oficial da
REST API de workflow runs e de criação de releases.

Alterados exclusivamente:

- `tools/releases/release_publication.py`;
- `tools/releases/tests/test_release_publication.py`;
- este relatório S14.

Não foi necessário alterar workflow ou validador estático: `notes.md` já é
transportado dentro do handoff sem interpolação em shell, e o helper passou a
consumi-lo diretamente. Task, tracker, schemas, exemplos, CI,
`publish-candidate.yml`, contratos comerciais e S15 permaneceram intactos.

### 12.2 Resposta aos achados

1. **Shape de workflow run:** removida toda leitura positiva de
   `run.workflow.name`. `validate_workflow_run` exige `name`, `workflow_id` e
   `path` no topo, além de estado, SHA, branch, repositories e atores.
2. **IDs antes de endpoints:** adicionados `_positive_int`,
   `validate_actions_artifact`, `validate_release_identity` e
   `validate_release_assets`. Cada conjunto é validado integralmente antes do
   primeiro download ou endpoint derivado.
3. **Canonicalidade do candidato:** `metadata.json` precisa ser byte a byte
   igual a `canonical(expected_metadata)`; `outcome.json` precisa ser igual a
   `canonical(parsed_outcome)`. JSON/UTF-8 inválido vira erro sanitizado.
4. **Assets em todas as fases:** história, draft pós-upload, reconciliação
   final e outcome reaplicam ID, URL, nome, state, content type, size e conjunto
   exato antes de baixar os três bytes.
5. **Body da release:** `notes.md` validado atravessa
   `create_draft(tag, sha, notes_bytes)`,
   `download_assets(..., notes_bytes, draft)` e
   `final_state(..., notes_bytes)`. O body é conferido na criação, compensação,
   publicação e outcome sem trim, normalização ou reconstrução.

### 12.3 Shapes REST positivos

O current run positivo usa os bindings:

```text
id=400
run_attempt=1
name=Publish Release
workflow_id=14
path=.github/workflows/publish-release.yml@main
event=workflow_dispatch
status=in_progress
conclusion=null
head_branch=main
head_sha=<40 hex>
repository.id=77
repository.full_name=greggorio/abaronesa-emporio
repository.owner.login=greggorio
head_repository.id=77
actor.login/actor.id=actor/1
triggering_actor.login/triggering_actor.id=actor/1
```

O candidate run positivo usa:

```text
id=200
run_attempt=1
name=Publish Candidate
workflow_id=12
path=.github/workflows/publish-candidate.yml@main
event=workflow_run
status=completed
conclusion=success
head_branch=main
head_sha=<candidate SHA>
repository.id=head_repository.id=77
actor e triggering_actor com login e ID positivos
```

Uma fixture contendo somente `workflow: {"name": ...}` é rejeitada.

### 12.4 Endpoints e gates prévios

| Dado remoto | Validação anterior | Endpoint liberado somente depois |
|---|---|---|
| candidate run | shape de topo, IDs, workflow/path, repo, SHA, attempt, atores | `/actions/runs/<id>/artifacts` |
| Actions artifact | dois artifacts completos: ID, nome, size, digest, URLs, run/SHA/repo | `/actions/artifacts/<id>/zip` |
| release listada | ID inteiro positivo e URL canônica | `/releases/<id>` ou uso no snapshot |
| conjunto de assets | os três IDs/URLs/names/state/types/sizes, sem duplicidade | `/releases/assets/<id>` |
| resposta do POST draft | ID inteiro positivo | primeiro `/releases/<id>` |
| draft reconsultado | URL/tag/name/target/body/draft/prerelease | primeiro upload |
| publication handoff | shape exato e ID decimal positivo | reconciliação do outcome |

Se o segundo artifact ou o segundo asset é inválido, a contagem de downloads
permanece zero. A validação do candidate run ocorre antes da listagem de
artifacts. A resposta de criação com ID inválido não forma endpoint.

### 12.5 Payload e sequência mutável

O payload do draft contém exatamente:

```json
{
  "tag_name": "<SemVer validado>",
  "target_commitish": "<sourceCommit validado>",
  "name": "<mesmo SemVer>",
  "body": "<notes.md integral redigido>",
  "draft": true,
  "prerelease": false
}
```

Na fixture sanitizada, `notes.md` possui 365 bytes e
`sha256:b1b12c82552baf4dabb81f5aeaaf386b3e8d5e795e2e4ff66e75b0e25366eda2`.
O teste comprova `payload["body"].encode("utf-8") == notes_bytes`. O body não
entra em argv, output ou interpolação do workflow.

Ordem comprovada:

1. handoff e notes;
2. identidade, candidato, história e snapshot;
3. ausência de target;
4. POST draft com body;
5. ID positivo e GET integral do draft;
6. três uploads;
7. GET e validação de todos os metadados de assets;
8. três downloads e comparação byte a byte;
9. snapshot, ignorando somente o draft revalidado;
10. tag lightweight;
11. publicação;
12. GET de release/assets/tag;
13. body, target, estado, metadados e bytes finais;
14. publication handoff e outcome.

Se a primeira reconsulta do draft diverge, uma segunda reconsulta independente
decide ownership. Apenas prova completa fornece `owned_id` à compensação. Sem
essa prova não há DELETE. Com ownership comprovado anteriormente, compensação
revalida ID/URL/tag/name/state antes de apagar o recurso próprio; isso permite
compensar justamente uma divergência posterior de body ou target sem atingir
recurso preexistente.

### 12.6 Dezoito provas adicionais

| # | Prova causal | Resultado |
|---:|---|---|
| 1 | current run no shape REST real | passou |
| 2 | candidate run no shape REST real | passou |
| 3 | shape antigo com somente `workflow.name` | rejeitado |
| 4 | mutantes de name/workflow/path/repos/actor/triggering actor atuais | rejeitados |
| 5 | candidate run divergente antes da listagem | `list_calls=0` |
| 6 | artifact ID/string/URL/size/workflow run divergente | rejeitado |
| 7 | segundo artifact inválido | `downloads=[]` |
| 8 | metadata semanticamente igual e não canônica | rejeitada |
| 9 | outcome semanticamente igual e não canônico | rejeitado |
| 10 | release/asset ID ou URL divergente | rejeitado antes do endpoint |
| 11 | segundo asset com content type divergente | `downloads=0` |
| 12 | draft recebe body byte a byte de notes | passou; seis campos exatos |
| 13 | notes vazio/UTF-8 inválido/body omitido ou alterado | zero uploads |
| 14 | ID/URL/tag/name/target/state de draft divergente | duas provas, sem delete não autorizado |
| 15 | metadado de asset divergente pós-upload | três uploads e compensação |
| 16 | body/target/asset final divergente | compensação e nenhum outcome |
| 17 | caminho positivo | três assets, provas finais e outcome posterior |
| 18 | 187 testes anteriores | verdes dentro dos 205 totais |

Foram adicionados 18 métodos independentes `test_c01_01` a
`test_c01_18`. A suíte S14 passou de 47 para 65 testes; a descoberta completa
passou de 187 para 205, preservando todos os anteriores.

### 12.7 Matriz executada

| Comando exato | Exit | Evidência |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | `Ran 205 tests in 3.486s — OK` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json` | 0 | `global-release:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `docker image inspect docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 1 | imagem ausente inicialmente |
| `docker run --rm -v /home/gregorio/git/baronesa/emporio:/repo:ro -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color=false .github/workflows/ci.yml .github/workflows/publish-candidate.yml .github/workflows/publish-release.yml` | 0 | três workflows sem achados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | somente a imagem efêmera removida |
| repetição do `docker image inspect` fixado | 1 | estado final igual ao inicial |

Antes da matriz foi executada a suíte S14 focada:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools/releases/tests/test_release_publication.py
```

Exit 0, `Ran 65 tests ... OK`.

### 12.8 Estado protegido

Não houve chamada `gh`, `curl`, GitHub API, GHCR, tag, release, artifact,
workflow remoto, commit, push, Maven, NPM, Docker build, Compose, VPS ou
produção. O pull externo limitado foi exclusivamente o actionlint autorizado,
e a imagem foi removida porque estava ausente no início.

O estado final permanece:

- `git diff --check`: exit 0;
- `git diff --cached --name-only`: exit 0 e vazio;
- `git rev-parse --verify HEAD`: exit 128, HEAD inexistente;
- `git tag --list`: exit 0 e vazio;
- `git reflog`: exit 128, reflog inexistente;
- busca de `__pycache__`/`*.pyc`: exit 0, zero resultados;
- workflows ativos: exatamente `ci.yml`, `publish-candidate.yml` e
  `publish-release.yml`;
- actionlint fixado: imagem ausente no estado final, exit 1 no inspect.

```text
IN_PROGRESS — aguardando nova revisão do orquestrador
```

S15 não foi criada e `ACCEPTED` não foi declarado.

## 13. Revisão terminal do orquestrador — correção causal 02

**Veredito:** a correção causal 01 foi cumprida, mas a S14 permanece
`IN_PROGRESS`. A S15 continua bloqueada.

A revisão foi feita sobre as 18 evidências persistidas, o helper, os testes, o
workflow e o validador, sem reproduzir a suíte do executor.

### 13.1 Correção 01

Os 18 comportamentos da correção 01 coincidem com código e testes:

- workflow runs usam o shape REST de topo;
- IDs/URLs/bindings precedem endpoints;
- metadata e outcome exigem bytes canônicos;
- assets são validados como conjunto antes de downloads;
- `notes.md` é o body exato;
- draft, reconciliação e compensação observam a ordem prescrita.

### 13.2 Bloqueios terminais encontrados

1. `lookup`, `release_lookup` e `tag_lookup` capturam qualquer
   `subprocess.CalledProcessError` e retornam `None`. Assim, 401, 403, 409, 429,
   5xx ou falha de transporte podem ser interpretados como ausência, permitindo
   mutação indevida ou uma falsa prova de cleanup.
2. respostas de Git refs são aceitas apenas pelo SHA. `ref`, URL, object type e
   object URL não são validados na história, criação, final ou compensação.
3. `CalledProcessError` de `gh`, upload ou Git pode escapar ao `main`, que não o
   captura, produzindo traceback em vez de código estável sanitizado.
4. o checkout do job `outcome` usa `fetch-depth: 1`; a task exige
   `fetch-depth: 0` em todo checkout, enquanto o validador incorretamente aceita
   os dois valores.

Esses pontos já pertenciam ao contrato-base. O orquestrador deveria tê-los
incluído na primeira correção antes de delegá-la.

### 13.3 Erro de path do orquestrador

A correção 01 autorizou um caminho documental inexistente. O caminho canônico
da task é `docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md`. O
executor respeitou corretamente a fronteira recebida; a correção do path é
responsabilidade do orquestrador.

### 13.4 Contrato terminal

O contrato fechado está em:

`S14-workflow-publicacao-release-global.correction-02.md`

Ele determina:

- transporte HTTP tipado, com 404 como única ausência;
- status por operação e erros sanitizados;
- validação integral de Git refs lightweight;
- encapsulamento das falhas de subprocesso;
- checkout depth 0 obrigatório;
- atualização da documentação canônica;
- 15 provas causais adicionais.

Nenhuma alteração da S15 está autorizada antes da revisão terminal.

## 14. Resposta à correção causal terminal 02

**CWD obrigatório:** `/home/gregorio/git/baronesa/emporio`

**Estado:** `IN_PROGRESS — aguardando revisão terminal do orquestrador`

Foi implementada somente a correção causal terminal 02. A correção 01 foi
preservada e a S15 não foi criada.

### 14.1 Arquivos alterados

- `.github/workflows/publish-release.yml`;
- `tools/releases/release_publication.py`;
- `tools/releases/validate_release_workflow.py`;
- `tools/releases/tests/test_release_publication.py`;
- `docs/infrastructure/deployment/ci/RELEASE_PUBLICATION.md`;
- este relatório.

Task, correção 01, tracker, schemas, exemplos, workflows anteriores, código
comercial, Docker, Compose e S15 não foram alterados.

### 14.2 Transporte HTTP tipado e status exatos

`GhTransport.api` agora usa `gh api --include`, array de argumentos,
`check=False`, bytes e timeout. O parser aceita `CRLFCRLF` e `LFLF`, limita
headers a 64 KiB e JSON a 4 MiB, exige uma única status line inicial, return
code compatível e JSON válido. Status 204 exige body vazio. Nenhum stdout,
stderr, body, token, descrição ou changelog é incluído no erro.

| Operação | Status exigido | Resultado divergente |
|---|---:|---|
| GET JSON | 200 | fail-closed |
| POST `/releases` | 201 | `REMOTE_RESPONSE_INVALID` |
| POST `/git/refs` | 201 | `REMOTE_RESPONSE_INVALID` |
| PATCH `/releases/<id>` | 200 | `REMOTE_RESPONSE_INVALID` |
| DELETE `/releases/<id>` | 204 | `REMOTE_RESPONSE_INVALID` |
| DELETE `/git/refs/tags/<tag>` | 204 | `REMOTE_RESPONSE_INVALID` |

HTTP não 2xx produz `RemoteHttpError` com status tipado. Processo sem status
parseável, timeout, sinal ou return code incompatível produz
`REMOTE_TRANSPORT_FAILED`; resposta 2xx estruturalmente inválida produz
`REMOTE_RESPONSE_INVALID`. `optional_get` converte exclusivamente 404 em
`None`; 401, 403, 409, 429, 5xx e falhas de transporte são propagados.

Upload e download mapeiam falhas respectivamente para
`REMOTE_UPLOAD_FAILED` e `REMOTE_DOWNLOAD_FAILED`, preservando o limite
durante streaming. Os subprocessos Git mapeiam falha para
`GIT_CONTEXT_INVALID`. Arquivos tar/zip inválidos recebem código contextual.
O CLI possui fronteira final sanitizada e emite somente
`release-publication:invalid:<CODIGO_ESTAVEL>`, exit 3, sem traceback ou dado
livre.

### 14.3 Git refs lightweight

O shape positivo exige conjuntamente:

```text
ref=refs/tags/<SemVer>
url=https://api.github.com/repos/greggorio/abaronesa-emporio/git/refs/tags/<SemVer>
object.type=commit
object.sha=<40 hex minúsculos>
object.url=https://api.github.com/repos/greggorio/abaronesa-emporio/git/commits/<sha>
```

Quando há SHA esperado, ele também deve coincidir byte a byte. A validação é
aplicada à história, resposta de criação, lookup, prova de ownership, estado
final e remoção compensatória. Mutantes de ref, URL, tipo anotado, SHA e URL do
commit são rejeitados. Uma resposta inválida do POST já tentado é reconciliada
por GET tipado; somente a ref canônica deste run, no SHA esperado, pode ser
apagada. Ref preexistente ou divergente nunca é removida.

### 14.4 Resposta individual às 15 provas

| # | Prova causal | Resultado |
|---:|---|---|
| 1 | parser `HTTP/2.0 200` com JSON | aceito |
| 2 | parser `HTTP/1.1 204` com body vazio | aceito |
| 3 | 404 tipado em `optional_get` | retorna `None` |
| 4 | 401, 403, 409, 429 e 500 | todos propagados; nenhum retorna `None` |
| 5 | ausência/status malformado/limites/body 204/JSON inválido | todos falham com código sanitizado |
| 6 | lookup 403 antes do draft | zero POST, upload, tag, PATCH ou DELETE |
| 7 | 500 na prova final de compensação | `PUBLICATION_COMPENSATION_FAILED` |
| 8 | ref lightweight canônica | aceita com SHA esperado |
| 9 | cinco mutantes do shape da ref | todos rejeitados |
| 10 | história com ref inválida | falha antes do snapshot |
| 11 | resposta inválida ao criar tag | ref própria reconciliada e compensada; divergente não apagada |
| 12 | upload/download/Git/CLI falhos | códigos estáveis, exit 3 e ausência de traceback/dados brutos |
| 13 | quatro checkouts depth 0 e mutante outcome depth 1 | estado real aceito; mutante rejeitado |
| 14 | seis garantias na documentação canônica | presentes |
| 15 | 205 testes anteriores | preservados e verdes dentro dos 220 totais |

Foram adicionados 15 métodos independentes `test_c02_01` a `test_c02_15`.
A suíte S14 passou de 65 para 80 testes; a descoberta completa passou de 205
para 220.

### 14.5 Falha intermediária e correção

A primeira execução focal após alterar as assinaturas de status executou 65
testes e terminou com exit 1: cinco mocks da correção 01 ainda não aceitavam o
argumento interno `expected_status`. Os mocks foram alinhados sem reduzir suas
asserções. A repetição focal executou 65 testes com exit 0; depois da inclusão
das novas provas, executou 80 testes com exit 0.

### 14.6 Matriz final executada

| Comando exato | Exit | Evidência |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | `Ran 220 tests in 3.507s — OK` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json` | 0 | `global-release:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_candidate_manifest.py` | 0 | `candidate:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/catalog.py validate --require-release-ready` | 0 | `catalog:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `docker image inspect docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 1 | imagem ausente inicialmente |
| `docker run --rm -v /home/gregorio/git/baronesa/emporio:/repo:ro -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color=false .github/workflows/ci.yml .github/workflows/publish-candidate.yml .github/workflows/publish-release.yml` | 0 | três workflows sem achados |
| `docker image rm docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9` | 0 | somente a imagem efêmera autorizada foi removida |
| repetição do `docker image inspect` fixado | 1 | estado final igual ao inicial |

### 14.7 Estado protegido final

- índice Git real vazio;
- HEAD inexistente;
- nenhuma tag ou reflog;
- exatamente `ci.yml`, `publish-candidate.yml` e `publish-release.yml` ativos;
- zero `__pycache__`, `*.pyc` ou `*.pyo`;
- imagem actionlint ausente ao final, como no estado inicial;
- nenhum `gh`, `curl`, acesso operacional à GitHub API, GHCR, VPS ou produção;
  nenhum workflow remoto, release, artifact, commit, tag ou push;
- nenhum Maven, NPM, Docker build, Compose ou instalação;
- o único acesso externo foi o pull efêmero do actionlint fixado, autorizado
  pelo contrato, seguido de sua remoção.

```text
IN_PROGRESS — aguardando revisão terminal do orquestrador
```

`ACCEPTED` não foi declarado e a S15 não foi criada.

## 15. Revisão terminal da correção 02 — ajuste 02-A

**Veredito:** a maior parte da correção 02 coincide com o contrato, mas a S14
permanece `IN_PROGRESS` por dois defeitos objetivos. A S15 continua bloqueada.

A revisão foi feita sobre código, testes e evidências persistidas, sem repetir
a matriz do executor.

### 15.1 Return code incompatível

`parse_http_response` produz `RemoteHttpError` para status não 2xx antes de
verificar se o return code é compatível. Assim:

```text
HTTP 404 + returncode 0
```

chega a `optional_get` como ausência. A correção 02 determinou que combinação
incompatível fosse `REMOTE_TRANSPORT_FAILED`.

### 15.2 Tag preexistente no mesmo SHA

Depois de qualquer falha de `create_tag`, `publish_transaction` usa
`tag_points_to` para transformar igualdade de SHA em ownership. Portanto um
POST recusado com 409/422, seguido de uma tag preexistente no mesmo SHA, pode
marcar `tag_created=true` e autorizar `delete_owned_tag`.

A prova 11 exigia que ref preexistente ou divergente nunca fosse apagada, mas o
teste entregue cobre somente a variante divergente. Igualdade de SHA prova
estado, não autoria da criação.

### 15.3 Contrato fechado

O ajuste estritamente limitado está em:

`S14-workflow-publicacao-release-global.correction-02A.md`

Ele fixa a matriz status/return code e separa `tag_attempted` de `tag_owned`,
com oito provas causais. Nenhum workflow, validador, documento operacional ou
artefato da S15 pode ser alterado.

## 16. Resposta ao ajuste corretivo terminal 02-A

**CWD:** `/home/gregorio/git/baronesa/emporio`

**Estado:** `IN_PROGRESS — aguardando aceite terminal do orquestrador`

Foi implementado somente o ajuste 02-A nos três arquivos autorizados:

- `tools/releases/release_publication.py`;
- `tools/releases/tests/test_release_publication.py`;
- este relatório.

Workflow, validador, documentação operacional, contratos anteriores, tracker e
S15 não foram alterados. O actionlint não foi repetido.

### 16.1 Compatibilidade entre status HTTP e return code

`parse_http_response` agora extrai o status e avalia a compatibilidade com o
return code antes de produzir qualquer `RemoteHttpError`.

| Status | Return code | Resultado implementado |
|---|---:|---|
| 2xx | 0 | continua a validação da resposta |
| 2xx | diferente de 0 | `REMOTE_TRANSPORT_FAILED` |
| não 2xx | positivo | `RemoteHttpError(status)` |
| não 2xx | 0 | `REMOTE_TRANSPORT_FAILED` |
| qualquer | negativo | `REMOTE_TRANSPORT_FAILED` |

Assim, `404 + returncode 1` pode chegar a `optional_get` e representar
ausência; `404 + returncode 0` e `500 + returncode 0` são incompatibilidades de
transporte e nunca retornam `None`.

Foi adicionado `RemoteResponseError(code="REMOTE_RESPONSE_INVALID",
status=<2xx>)`. Headers, body, JSON ou shape inválidos após status 2xx usam esse
tipo. `GhTransport.api` também o usa quando o status 2xx válido diverge do
status esperado. O CLI continua expondo somente o código estável.

### 16.2 Ownership da tag

`publish_transaction` mantém estados distintos:

```text
tag_attempted=true  imediatamente antes do POST
tag_owned=true      somente depois que create_tag retorna normalmente
```

A promoção por `tag_points_to` foi removida. Igualdade posterior de nome e SHA
não concede autoria e apenas `tag_owned=true` autoriza `delete_owned_tag`.

`GhTransport.create_tag` retorna normalmente somente nestes casos:

1. POST 201 com resposta canônica exata;
2. POST 201 com resposta 2xx inválida, seguido de GET tipado que comprova a ref
   canônica exata no SHA esperado.

Somente `RemoteResponseError(status=201)` entra nessa reconciliação. POST 409,
422, outro não 2xx, status 2xx diferente de 201 ou falha de transporte não
fazem GET para adquirir ownership. Se uma ref preexistente permanecer, a prova
final detecta sua presença e o resultado é
`PUBLICATION_COMPENSATION_FAILED`, sem DELETE da ref.

### 16.3 Resposta individual às oito provas

| # | Prova | Resultado |
|---:|---|---|
| 1 | matriz status/return code | cinco combinações prescritas passaram; somente 404 com return code positivo retornou `None` |
| 2 | POST 422 e ref preexistente no mesmo SHA | evento POST 422 registrado, zero DELETE de ref e `PUBLICATION_COMPENSATION_FAILED` |
| 3 | POST 409 e ref preexistente no mesmo SHA | evento POST 409 registrado, zero DELETE de ref e `PUBLICATION_COMPENSATION_FAILED` |
| 4 | transporte ambíguo e GET com mesmo nome/SHA | evento de transporte registrado, zero DELETE de ref e `PUBLICATION_COMPENSATION_FAILED` |
| 5 | POST 201 com resposta canônica | `create_tag` retornou, ownership foi concedido e a compensação posterior pôde remover a ref própria |
| 6 | POST 201 com body inválido e GET canônico | sequência `POST 201, GET 200` retornou normalmente e recuperou ownership |
| 7 | POST 201 inválido com GET ausente ou divergente | ambos falharam; nenhum evento DELETE foi produzido |
| 8 | preservação dos 220 testes | todos verdes dentro dos 228 testes do discovery |

Nos casos 422, 409 e transporte ambíguo, a lista de eventos contém POST e a
reconsulta final, mas não contém
`DELETE /repos/greggorio/abaronesa-emporio/git/refs/tags/...`.

Foram adicionados oito métodos independentes `test_c02a_01` a
`test_c02a_08`. A suíte focal passou de 80 para 88 testes e a descoberta
completa passou de 220 para 228.

### 16.4 Falha intermediária

A primeira execução focal após adicionar as provas executou 88 testes com exit
1 por uma expectativa antiga do próprio teste `test_c02_11`: após compensação
bem-sucedida, o fluxo preserva corretamente o erro original injetado
`RuntimeError`, enquanto a asserção esperava `PublicationError`. A asserção foi
alinhada sem alterar o comportamento produtivo. A repetição passou com 88
testes e exit 0.

### 16.5 Comandos e resultados

| Comando exato | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/releases/tests/test_release_publication.py` | 0 | `Ran 88 tests in 0.294s — OK` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | `Ran 228 tests in 3.494s — OK` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |

### 16.6 Estado protegido

- índice Git real vazio;
- HEAD inexistente;
- nenhuma tag ou reflog;
- exatamente `ci.yml`, `publish-candidate.yml` e `publish-release.yml` ativos;
- zero `__pycache__`, `*.pyc` ou `*.pyo`;
- nenhum actionlint, `gh`, `curl`, acesso operacional à GitHub API, GHCR, VPS
  ou produção;
- nenhum workflow remoto, release, artifact, commit, tag ou push;
- nenhuma alteração da S15.

```text
IN_PROGRESS — aguardando aceite terminal do orquestrador
```

`ACCEPTED` não foi declarado.

---

## 17. Revisão terminal do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-29`

O ajuste 02-A fecha os dois defeitos objetivos que ainda bloqueavam a S14:

- a combinação entre status HTTP e return code é validada antes de qualquer
  classificação semântica da resposta;
- `404` somente representa ausência quando existe resposta HTTP tipada, e
  combinações incompatíveis falham como transporte;
- `tag_attempted` e `tag_owned` são estados independentes;
- somente retorno normal de um POST `201` comprovado concede ownership;
- `409`, `422`, falha de transporte ou coincidência posterior de SHA nunca
  autorizam exclusão da ref;
- as oito provas causais cobrem as combinações corrigidas e preservam a suíte
  anterior;
- workflow, contratos, estado Git e fronteiras remotas permaneceram
  protegidos.

O orquestrador revisou o código e as evidências persistidas sem repetir a
suíte executada pelo executor. A ausência de nova execução do actionlint não
bloqueia o aceite: o ajuste 02-A não alterou workflow YAML, e o validador do
workflow permaneceu aprovado.

Decisão:

```text
S14 ACCEPTED — 29/07/2026
S15 autorizada
```
