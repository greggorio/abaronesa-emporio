# S30b — Relatório do executor — etapa A

## Identidade, autoridade e CWD

- CWD: `/home/gregorio/git/baronesa/emporio`
- Data/hora registrada no fechamento do preflight: `2026-08-02T14:33:19-03:00`
- Contrato imutável: `docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.task.md`
- SHA-256 do contrato: `91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11`
- O contrato foi lido integralmente, 340 linhas e 13044 bytes, antes de
  qualquer prova técnica.
- Autoridade executada: somente a etapa A, seções 3 a 9. Nenhuma autorização
  complementar foi criada ou presumida.

## Snapshot Git/GitHub inicial

Os comandos obrigatórios da seção 3 foram executados antes dos validadores:

```text
git status --short --branch
exit 0
## main...origin/main [ahead 1]

git rev-parse HEAD origin/main
exit 0
da0a63edd48a76b1bac015ca558b057b49e4a2c4
50f423a979d7723d0e15d56b1d72625ea2b8ebea

git log --oneline --decorate -5
exit 0
da0a63e (HEAD -> main) docs: accept S36 and open S30b
50f423a (origin/main) fix: close candidate integration security gates
fcaf9d8 docs: hand off implementation closure
68a3528 fix: return 404 for unknown API routes
44758a2 fix: probe absent control routes with a valid token

git merge-base --is-ancestor origin/main HEAD
exit 0

git diff --check origin/main..HEAD
exit 0

git diff --name-status origin/main..HEAD
exit 0
M	docs/infrastructure/deployment/implementation/README.md
M	docs/infrastructure/deployment/implementation/slices/S30a-paridade-local-fechamento-ci-candidato.report.md
A	docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.task.md
A	docs/infrastructure/deployment/implementation/slices/S36-fechamento-terminal-s30a-seguranca-migracoes-candidato.report.md

git ls-remote origin refs/heads/main
exit 0
50f423a979d7723d0e15d56b1d72625ea2b8ebea	refs/heads/main

git ls-remote --tags origin
exit 0; saída vazia

gh auth status
exit 0; conta greggorio ativa via keyring; nenhum token, header ou credencial foi registrado

gh run list --branch main --limit 30
exit 0; somente runs completed; nenhum queued ou in_progress

gh run list --workflow publish-release.yml --branch main --limit 20
exit 0; saída vazia; zero runs de publish-release.yml

gh release list --limit 20
exit 0; saída vazia; zero releases

sha256sum docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.task.md
exit 0
91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11  docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.task.md
```

Interpretação: branch, HEAD, mensagem do checkpoint, remoto, ancestralidade,
um único commit documental à frente, stage vazio, worktree limpo, tags,
releases, hash e ausência de concorrência estavam conformes. Os quatro
caminhos mostrados por `git diff --name-status origin/main..HEAD` pertencem ao
checkpoint documental já recebido; não foram editados nesta execução.

## Revalidação local dos contratos publisher

Os comandos foram executados uma única vez, exatamente na ordem prescrita:

```text
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
exit 0; release-control-contract:valid

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py
exit 0; release-workflow:valid

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py
exit 0; publisher-runtime:valid

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_identity_bridge.py
exit 0; publisher-identity-bridge:valid

PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_ui.py
exit 0; publisher-ui:valid

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/releases/tests/test_release_control_contract.py tools/releases/tests/test_global_release.py tools/releases/tests/test_publisher_runtime_contract.py tools/releases/tests/test_publisher_identity_bridge_contract.py tools/releases/tests/test_publisher_ui_contract.py -v
exit 0; Ran 154 tests in 4.896s; OK

git diff --check
exit 0

git status --short
exit 0; saída vazia
```

Os gates confirmaram estaticamente o workflow fixo, `workflow_dispatch` com
cinco inputs fechados, permissões e concurrency, ponte de identidade, token e
scopes documentados, usuário root/`ROLE_SYSTEM` como contrato, POST sem retry
automático, idempotência, reconciliação, BOM global, seis componentes,
referências por digest e contratos de bundle/outcome. Isso é prova local; não
substitui os checks seguros de runtime.

## Candidato terminal autorizado

Foi consultado somente o candidato terminal informado:

```text
gh run view 30757174785 --json headSha,event,attempt,status,conclusion,jobs,url --jq '[.headSha,.event,(.attempt|tostring),.status,.conclusion,([.jobs[]|select(.conclusion=="success")]|length|tostring),(.jobs|length|tostring),.url]|@tsv'
exit 0
50f423a979d7723d0e15d56b1d72625ea2b8ebea	push	1	completed	success	13	13	https://github.com/greggorio/abaronesa-emporio/actions/runs/30757174785

gh run view 30757430990 --json headSha,event,attempt,status,conclusion,jobs,url --jq '[.headSha,.event,(.attempt|tostring),.status,.conclusion,([.jobs[]|select(.conclusion=="success")]|length|tostring),(.jobs|length|tostring),.url]|@tsv'
exit 0
50f423a979d7723d0e15d56b1d72625ea2b8ebea	workflow_run	1	completed	success	11	11	https://github.com/greggorio/abaronesa-emporio/actions/runs/30757430990

gh api repos/gregorio/abaronesa-emporio/actions/runs/30757430990/artifacts --paginate --jq '.artifacts[] | select(.name=="candidate-effective-plan" or .name=="candidate-manifest" or .name=="candidate-outcome") | [.name,(.id|tostring),(.expired|tostring),(.workflow_run.id|tostring),.workflow_run.head_sha,.digest]|@tsv'
exit 0; três linhas, todas expired=false, run 30757430990 e TARGET_SHA
```

| Artifact | ID | Digest API | Estado |
|---|---:|---|---|
| `candidate-effective-plan` | `8836368371` | `sha256:f409bdde9726cbabe48e57ba71726f05e8b5ba37ac28ab7419af24462b82dd84` | único, não expirado, vinculado ao TARGET_SHA |
| `candidate-manifest` | `8836442429` | `sha256:6b8e0bd3eafedefd8dfe55828c54c270b53f28cc995265345f5262b70e182bfd` | único, não expirado, vinculado ao TARGET_SHA |
| `candidate-outcome` | `8836442612` | `sha256:88864e8f49930d8fe68c267dd5ad41c1b94e0d0bc6c032c170fb65d522830d3d` | único, não expirado, vinculado ao TARGET_SHA |

O download foi limitado aos três nomes nominais, em diretório temporário
dedicado:

```text
mktemp -d
exit 0
/tmp/tmp.kUauSQbgd6

gh run download 30757430990 -n candidate-effective-plan -n candidate-manifest -n candidate-outcome -D /tmp/tmp.kUauSQbgd6
exit 0

find /tmp/tmp.kUauSQbgd6 -type f -printf '%P\n' | sort
exit 0
candidate-effective-plan/candidate-effective-plan.json
candidate-manifest/candidate.json
candidate-manifest/candidate.json.sha256
candidate-manifest/metadata.json
candidate-outcome/outcome.json
candidate-outcome/outcome.json.sha256
```

Validação sem imprimir JSON bruto:

```text
python3 tools/releases/candidate_manifest.py validate --manifest /tmp/tmp.kUauSQbgd6/candidate-manifest/candidate.json
exit 0; candidate:valid

PYTHONPATH=tools/candidates:tools/releases python3 - <<'PY'
<validação sanitizada de canonicalidade, sidecars, lineage, schema, metadata,
bindings, predecessor, componentes, checks, integração, outcome e ausência de
provenance/attestation; nenhum JSON bruto foi emitido>
PY
exit 0
candidate-artifacts:valid
candidateId=candidate-50f423a979d7723d0e15d56b1d72625ea2b8ebea-30757430990-1
commit=50f423a979d7723d0e15d56b1d72625ea2b8ebea; sourceCi=30757174785/1; publishCandidate=30757430990/1
predecessor=candidate-68a3528b563ad0c19819da34ab106396ae679596-30752210806-1; predecessorRun=30752210806; predecessorArtifact=8834868927
mode=continue; components=6; inherited=0; checks=passed; integration=passed; outcome=published
sidecars=valid; schemas=valid; provenance-attestation-fields=absent
```

Foram confirmados o candidate ID exato, commit e runs, plano efetivo em modo
`continue`, predecessor selecionado, seis componentes canônicos sem herdados,
checks `build/test/scan` `passed`, referências imutáveis
`imageRepository@digest`, integração `passed`, outcome `published`,
canonicalidade, sidecars e ausência de campos de provenance/attestation.

O predecessor foi confrontado somente por metadata GET, sem download:

```text
gh api repos/greggorio/abaronesa-emporio/actions/artifacts/8834868927 --jq '[.id,.name,(.expired|tostring),(.workflow_run.id|tostring),.workflow_run.head_sha,.digest]|@tsv'
exit 0
8834868927	candidate-manifest	false	30752210806	68a3528b563ad0c19819da34ab106396ae679596	sha256:0bfbbd4bce1110cd8d75e01170a107e9db488dc64f0d333cb671fabd1575ef89

gh api repos/greggorio/abaronesa-emporio/actions/artifacts/8834869091 --jq '[.id,.name,(.expired|tostring),(.workflow_run.id|tostring),.workflow_run.head_sha,.digest]|@tsv'
exit 0
8834869091	candidate-outcome	false	30752210806	68a3528b563ad0c19819da34ab106396ae679596	sha256:89c3352be0d79b82861841bd17ae53905d59b9428612f3d06f91b60fd98b01b4
```

## Inventário remoto pré-release

As provas somente leitura exigidas foram:

```text
gh run list --workflow deploy-production.yml --branch main --limit 20
exit 0; saída vazia; zero runs

gh run list --workflow rollback-production.yml --branch main --limit 20
exit 0; saída vazia; zero runs

gh api repos/greggorio/abaronesa-emporio/actions/variables --paginate --slurp --jq '[.[].variables[]?.name] | if index("RELEASE_PUBLISHER_ACTOR_IDS") != null then "PRESENT" else "MISSING" end'
exit 1; combinação de flags não suportada pelo gh api; nenhuma operação remota foi iniciada

if gh api repos/greggorio/abaronesa-emporio/actions/variables --paginate --jq '.variables[]?.name' | rg -qx 'RELEASE_PUBLISHER_ACTOR_IDS'; then printf 'PRESENT\n'; else printf 'MISSING\n'; fi
exit 0; MISSING

gh api 'repos/greggorio/abaronesa-emporio/contents/.github/workflows/publish-release.yml?ref=main' --jq '[.path,.name,.sha]|@tsv'
exit 0
.github/workflows/publish-release.yml	publish-release.yml	2932f04e24bd3410f90aaf1402be081e880adec3

git rev-parse 50f423a979d7723d0e15d56b1d72625ea2b8ebea:.github/workflows/publish-release.yml
exit 0; 2932f04e24bd3410f90aaf1402be081e880adec3

git rev-parse origin/main:.github/workflows/publish-release.yml
exit 0; 2932f04e24bd3410f90aaf1402be081e880adec3

gh api repos/greggorio/abaronesa-emporio/actions/workflows/publish-release.yml --jq '[.id,.name,.path,.state]|@tsv'
exit 0
324791791	Publish Release	.github/workflows/publish-release.yml	active
```

O workflow remoto está no commit candidato por blob SHA idêntico, ativo e
validado localmente como somente `workflow_dispatch` com exatamente os cinco
inputs obrigatórios `operation_id`, `candidate_id`, `version_bump`,
`description` e `changelog`. Não houve POST, dispatch, replay, restart ou
qualquer mutação GitHub.

## Matriz de prontidão segura

| Item | Estado | Evidência | Próxima autoridade |
|---|---|---|---|
| 1. Git e checkpoint documental | `READY` | Snapshot, ancestralidade, diff check, remoto e hash da task conformes | Revisão do orquestrador |
| 2. Contratos locais publisher/UI/workflow | `READY` | Cinco validadores e 154 testes verdes | Nenhuma correção; somente eventual `authorization-01` |
| 3. Candidato terminal da S36 | `READY` | CI 13/13, Publish Candidate 11/11, três artifacts únicos e válidos | `authorization-01` deve conservar o candidate ID literal |
| 4. Ausência de release/tag/operação concorrente | `READY` | Zero tags, releases, publish-release, deploy e rollback; nenhum run ativo | Reconfirmar antes de qualquer etapa B |
| 5. GitHub App publisher separada da deployer | `PENDING_SECURE_RUNTIME_CHECK` | Contratos e docs estáticos presentes; identidade operacional da App não é comprovável por metadata público | Orquestrador com check seguro na `authorization-01` |
| 6. Permissões mínimas da identidade publisher | `PENDING_SECURE_RUNTIME_CHECK` | Workflow limita permissões públicas; `RELEASE_PUBLISHER_ACTOR_IDS` está `MISSING`; permissões efetivas da App não foram inferidas | Orquestrador com configuração segura, sem expor valor |
| 7. PostgreSQL local e migration atual | `PENDING_SECURE_RUNTIME_CHECK` | Nenhum processo foi iniciado, banco não foi aberto e migration não foi aplicada | `authorization-01`, somente migration autorizada e prova segura |
| 8. Backend ERP com ponte publisher habilitável | `PENDING_SECURE_RUNTIME_CHECK` | Validator da ponte verde; backend não foi iniciado nem autenticado | `authorization-01` e check de runtime isolado |
| 9. Usuário root com `ROLE_SYSTEM` | `PENDING_HUMAN_INPUT` | Identidade real não foi fornecida e banco/token não foram lidos | Usuário ERP literal fornecido pelo orquestrador/usuário |
| 10. Publisher development em loopback e readiness | `PENDING_SECURE_RUNTIME_CHECK` | Publisher não foi iniciado e readiness não foi consultado | `authorization-01` com processo local controlado |
| 11. Frontend development com modo publisher | `PENDING_SECURE_RUNTIME_CHECK` | Frontend não foi iniciado e UI não foi usada | `authorization-01` com modo development verificado |
| 12. Bump SemVer | `PENDING_HUMAN_INPUT` | Nenhuma escolha `PATCH`, `MINOR` ou `MAJOR` foi presumida | Metadado literal na `authorization-01` |
| 13. Descrição de 1 a 500 caracteres | `PENDING_HUMAN_INPUT` | Descrição humana não fornecida | Texto literal na `authorization-01` |
| 14. Changelog de 1 a 10000 caracteres | `PENDING_HUMAN_INPUT` | Changelog humano não fornecido | Texto literal na `authorization-01` |
| 15. Autorização de publicação/tag/release | `PENDING_HUMAN_INPUT` | Esta execução é somente leitura e não autoriza mutação externa | Autorização enumerada do orquestrador |
| 16. Autorização do replay idempotente | `PENDING_HUMAN_INPUT` | Nenhum replay foi executado ou presumido | Autorização enumerada do orquestrador |
| 17. Autorização do restart local controlado | `PENDING_HUMAN_INPUT` | Nenhum processo foi iniciado ou reiniciado | Autorização enumerada do orquestrador |
| 18. Isolamento de deploy, rollback, VPS e produção | `READY` | Workflows deploy/rollback sem runs; validadores e limites de workflow conformes; nenhum acesso externo realizado | Manter a exclusão na futura autorização |

### Pendências humanas e checks seguros

Ficam pendentes, sem decisão do executor: `versionBump`, descrição, changelog,
usuário ERP autorizado, janela da prova, autorização explícita de publicação/
tag/release, autorização do replay idempotente e autorização do restart local.

Ficam pendentes como checks seguros: separação e permissões efetivas da GitHub
App publisher, configuração `RELEASE_PUBLISHER_ACTOR_IDS` (presença observada
como `MISSING`, sem leitura de valor), PostgreSQL/migration, ponte runtime do
backend, usuário/role no ERP, readiness do publisher e modo publisher do
frontend. Nenhum deles foi convertido em `READY` por inferência.

## Resíduos e estado final

O diretório temporário foi removido somente depois da validação:

```text
find /tmp/tmp.kUauSQbgd6 -type f -delete
exit 0

find /tmp/tmp.kUauSQbgd6 -depth -type d -empty -delete
exit 0

if test ! -e /tmp/tmp.kUauSQbgd6; then printf 'absent:/tmp/tmp.kUauSQbgd6\n'; else printf 'present:/tmp/tmp.kUauSQbgd6\n'; fi
exit 0; absent:/tmp/tmp.kUauSQbgd6
```

Foram observados diretórios Python ignorados `tools/candidates/__pycache__`,
`tools/candidates/tests/__pycache__`, `tools/releases/__pycache__` e
`tools/releases/tests/__pycache__`. Não foram apagados: não houve snapshot
causal anterior e a etapa não autoriza limpeza ampla de resíduos fora do
diretório temporário criado pelo executor.

Snapshot local antes da criação deste relatório:

```text
git status --short --branch
exit 0
## main...origin/main [ahead 1]

git diff --cached --name-status
exit 0; vazio

git rev-parse HEAD origin/main
exit 0
da0a63edd48a76b1bac015ca558b057b49e4a2c4
50f423a979d7723d0e15d56b1d72625ea2b8ebea

sha256sum docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.task.md
exit 0; 91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11
```

Este relatório é o único arquivo criado nesta execução; permanece local,
não rastreado, não staged e não commitado. A task, tracker, handoff,
relatórios de S30a/S35/S36 e implementação não foram editados.

## Negativos preservados

Não foram iniciados ERP, PostgreSQL, publisher ou frontend; não houve migration,
leitura de banco, abertura de arquivo de segredo, leitura de `.env` real,
troca de token, autenticação de usuário, POST, dispatch, retry, rerun, replay,
restart, tag, GitHub Release, asset, artifact ou run criado; não houve stage,
commit, push, pull, merge, rebase ou amend; não houve GHCR, Docker, SSH, VPS,
deploy, rollback ou produção.

Não foram alterados tracker, handoff, task, relatório de outra slice ou código;
nenhum `authorization-01` foi criado. Nenhuma evidência remota foi excluída e
nenhuma decisão de aceite da S30b foi tomada.

IN_PROGRESS — preflight verde; aguardando metadados humanos, checks seguros e autorização explícita da publicação

## Revisão do orquestrador — etapa A aceita

> **Data:** 02/08/2026
> **Resultado da etapa A:** `ACCEPTED`
> **Estado da S30b:** `IN_PROGRESS`

O orquestrador revalidou diretamente o checkpoint local e o remoto. `HEAD`
permaneceu em `da0a63edd48a76b1bac015ca558b057b49e4a2c4`, exatamente um commit
documental à frente de `origin/main` e do remoto
`50f423a979d7723d0e15d56b1d72625ea2b8ebea`, com stage vazio e somente este
relatório não rastreado. O hash da task permaneceu
`91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11`.

Os cinco validadores publisher foram reproduzidos em exit 0, e a suíte focal
encerrou novamente com 154 testes em `OK`. A API GitHub confirmou a CI
`30757174785` com 13/13 jobs e o Publish Candidate `30757430990` com 11/11
jobs, ambos attempt 1, `success` e vinculados ao SHA terminal. Os três
artifacts permaneceram únicos, não expirados, vinculados ao mesmo run/SHA e
com os IDs e digests registrados neste relatório.

As consultas independentes retornaram zero tags, zero GitHub Releases e zero
runs de `publish-release.yml`, `deploy-production.yml` ou
`rollback-production.yml`. A variável `RELEASE_PUBLISHER_ACTOR_IDS` foi
confirmada como `MISSING`, sem leitura ou exposição de valor.

A primeira consulta da variável usou uma combinação de flags não suportada e
retornou exit 1. Isso não representou falha do gate remoto nem produziu
mutação; a consulta somente leitura compatível comprovou o estado `MISSING`.
A ocorrência está registrada e não foi ocultada.

A etapa A está aceita como preflight somente leitura. A S30b não está aceita e
nenhuma `authorization-01` é emitida enquanto permanecerem pendentes:

- identidade e permissões efetivas da GitHub App publisher;
- valor seguro de `RELEASE_PUBLISHER_ACTOR_IDS` correspondente ao ator real do
  dispatch;
- PostgreSQL e migration local do publisher;
- ponte ERP, usuário root com `ROLE_SYSTEM`, publisher e frontend em loopback;
- `versionBump`, descrição, changelog e janela da prova;
- autorização explícita para publicação/tag/release, replay idempotente e
  restart local controlado.

Nenhuma ação externa foi autorizada por esta revisão.

IN_PROGRESS — etapa A aceita; aguardando inputs humanos e checks seguros para a authorization-01
