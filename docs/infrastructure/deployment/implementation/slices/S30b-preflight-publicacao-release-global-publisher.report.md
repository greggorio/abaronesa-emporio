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

## Execução authorization-01

> **Data:** 02/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Estado terminal:** bloqueada fail-closed antes da primeira mutação

### Autoridade e snapshot reconfirmados

Os três documentos foram lidos integralmente, nesta ordem, e seus hashes foram
confirmados antes da execução:

```text
sha256sum docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.task.md
exit 0; 91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11

sha256sum docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.report.md
exit 0; 1fd095cb747a7457858f6c9b4a9ac67cd17487514315d260a05fb027c754ba31

sha256sum docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.authorization-01.md
exit 0; 33543ec2cc399d3a82c9d21cd4ba815465e0b40927d91043a6bc55cb7a897961
```

O preflight Git/GitHub obrigatório passou antes de qualquer ação:

```text
git status --short --branch
exit 0; ## main...origin/main [ahead 3]

git rev-parse HEAD origin/main
exit 0
7297501d76fac902ddcfc2ea4448133854b855d5
50f423a979d7723d0e15d56b1d72625ea2b8ebea

git log --oneline --decorate -5
exit 0
7297501 (HEAD -> main) docs: authorize S30b release publication
73108f2 docs: accept S30b read-only preflight
da0a63e docs: accept S36 and open S30b
50f423a (origin/main) fix: close candidate integration security gates

git merge-base --is-ancestor origin/main HEAD
exit 0

git diff --check origin/main..HEAD
exit 0

git ls-remote origin refs/heads/main
exit 0; 50f423a979d7723d0e15d56b1d72625ea2b8ebea	refs/heads/main

git ls-remote --tags origin
exit 0; vazio

gh run list --branch main --limit 30
exit 0; somente runs completed; nenhum queued ou in_progress

gh run list --workflow publish-release.yml --branch main --limit 20
exit 0; vazio

gh release list --limit 20
exit 0; vazio
```

`git diff --name-status origin/main..HEAD` mostrou somente o conjunto
documental herdado nos três commits autorizados; o stage permaneceu vazio e o
worktree permaneceu limpo nesta reconfirmação. Não houve pull, push, merge,
rebase, amend, commit ou alteração de qualquer documento de autoridade.

### Descoberta segura da GitHub App publisher

Antes de configurar `RELEASE_PUBLISHER_ACTOR_IDS` ou iniciar qualquer serviço,
foram consultados somente nomes de variáveis e paths, sem abrir conteúdo de
segredo:

```text
sed -n '1,220p' release_control/.env.example
exit 0; somente placeholders versionados; nenhum valor operacional

if test -e release_control/.env; then printf 'release_control/.env=PRESENT\n'; else printf 'release_control/.env=ABSENT\n'; fi
if test -e ops/env/release-control.env; then printf 'ops/env/release-control.env=PRESENT\n'; else printf 'ops/env/release-control.env=ABSENT\n'; fi
if test -e /run/secrets/github-app-private-key; then printf '/run/secrets/github-app-private-key=PRESENT\n'; else printf '/run/secrets/github-app-private-key=ABSENT\n'; fi
exit 0
release_control/.env=ABSENT
ops/env/release-control.env=ABSENT
/run/secrets/github-app-private-key=ABSENT

find /home/gregorio/.config /home/gregorio/.secrets /home/gregorio/secrets /run/secrets /etc/emporio /etc/release-control /opt/emporio-release-control -maxdepth 6 -type f \( -name '*github*app*' -o -name '*publisher*' -o -name '*release-control*' -o -name '*.pem' \) -print 2>/dev/null | sort
exit 0; saída vazia

names=$(env | cut -d= -f1 | rg -i 'RELEASE_CONTROL_GITHUB|RELEASE_PUBLISHER_ACTOR_IDS|GITHUB_APP|PUBLISHER')
if test -n "$names"; then printf '%s\n' "$names"; else printf 'CONFIG_ENV_NAMES=ABSENT\n'; fi
exit 0; CONFIG_ENV_NAMES=ABSENT

if gh api repos/greggorio/abaronesa-emporio/actions/variables --paginate --jq '.variables[]?.name' | rg -qx 'RELEASE_PUBLISHER_ACTOR_IDS'; then printf 'RELEASE_PUBLISHER_ACTOR_IDS=PRESENT\n'; else printf 'RELEASE_PUBLISHER_ACTOR_IDS=MISSING\n'; fi
exit 0; RELEASE_PUBLISHER_ACTOR_IDS=MISSING
```

O único arquivo versionado disponível é o exemplo com placeholders para
`RELEASE_CONTROL_GITHUB_APP_ID`, `RELEASE_CONTROL_GITHUB_INSTALLATION_ID` e
`RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH`; não existe configuração operacional
no checkout, no ambiente ou nos paths seguros consultados. A chave privada
publisher, o App ID, o installation ID, o slug, o ator bot e as permissões
efetivas não puderam ser descobertos sem inventar valores ou abrir um segredo
não localizado. Nenhum JWT, installation token, chave PEM, senha, pepper ou
token foi lido, criado ou impresso.

### Primeira causa e ponto de parada

Esta é a primeira causa técnica comprovada: a configuração real da GitHub App
publisher e sua chave privada, obrigatórias pela seção 7 da authorization-01,
estão ausentes dos paths seguros e do ambiente disponível. A autorização exige
validar App, instalação, repositório, `actions: write`, `contents: read`, ator
bot e chave antes de alterar `RELEASE_PUBLISHER_ACTOR_IDS`; portanto a variável
permaneceu `MISSING` e não foi criada nem alterada. Não houve chamada ao
endpoint `/app` sem JWT de App, não houve tentativa de criar App, rotacionar
chave, ampliar permissão ou usar o token pessoal do `gh` como substituto.

A execução parou imediatamente antes de configurar a variável, criar o
diretório de prova, gerar segredos/chave RSA, iniciar PostgreSQL/ERP/publisher/
frontend, aplicar migrations, abrir a UI, emitir os POSTs ou observar qualquer
dispatch. O candidato, artifacts e workflows não foram rebaixados nem
substituídos; a revalidação operacional da etapa B não prosseguiu porque o
primeiro gate específico da authorization-01 já era bloqueante.

### Ambiente, resíduos e negativos preservados

- Nenhum `mktemp -d` da authorization-01 foi criado; não houve processos, PIDs,
  browser, container, rede, volume, banco ou segredo temporário para limpar.
- O container preexistente `baronesa-postgres`, suas portas 5432/5434 e seus
  volumes não foram tocados; sua presença foi apenas observada em consulta
  read-only anterior.
- `RELEASE_PUBLISHER_ACTOR_IDS` remoto permaneceu `MISSING`; nenhuma variável,
  tag, release, run, artifact ou log remoto foi criado, excluído ou alterado.
- Não houve início de ERP, PostgreSQL, publisher ou frontend; migration,
  bootstrap SYSTEM, autenticação, UI, POST, dispatch, replay, restart,
  publicação, tag, GitHub Release, deploy, rollback, SSH, VPS, produção ou
  acesso GHCR.
- Não houve retry, rerun, cancel, dispatch manual, nova chave idempotente,
  segunda operação ou segunda release.
- Não houve stage, commit, push, pull, merge, rebase ou amend. Nenhum arquivo
  além deste relatório foi editado; task e authorization-01 permanecem
  imutáveis.
- A decisão de aceitar a S30b permanece com o orquestrador; não foi criada a
  próxima slice.

O relatório foi apenas acrescentado com esta seção e permanece local,
modificado, não staged e não commitado.

BLOCKED — authorization-01 interrompida fail-closed na primeira causa

## Revisão do orquestrador — parada da authorization-01 aceita

> **Data:** 02/08/2026
> **Resultado da execução:** `ACCEPTED — fail-closed conforme contrato`
> **Estado da S30b:** `IN_PROGRESS`

O orquestrador confirmou `HEAD=7297501d76fac902ddcfc2ea4448133854b855d5`,
`origin/main` e remoto em
`50f423a979d7723d0e15d56b1d72625ea2b8ebea`, stage vazio e somente este
relatório modificado. Task e authorization-01 conservaram os hashes esperados.

A inspeção independente confirmou que não há configuração publisher no
checkout, ambiente ou paths seguros nominais e que
`RELEASE_PUBLISHER_ACTOR_IDS` permanece ausente. A tentativa somente leitura de
listar instalações com a autenticação atual do `gh` recebeu HTTP 403 por falta
de autoridade do token; ela não fornece credencial de App e não autoriza usar o
token pessoal como substituto.

Não houve variável criada, serviço iniciado, migration, POST, dispatch, tag,
release, deploy ou efeito em produção. A primeira causa foi corretamente
isolada antes da primeira mutação. A S30b não é rejeitada nem aceita: sua
continuidade exige provisionar uma GitHub App publisher própria, instalá-la
somente no repositório canônico e armazenar sua chave privada fora do
repositório. Criar e instalar essa identidade administrativa não estava
autorizado pela authorization-01, que explicitamente proibia inventar ou criar
a App.

IN_PROGRESS — parada aceita; aguardando autoridade para provisionar a GitHub App publisher

## Retomada authorization-02

> **Data:** 02/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Resultado:** `BLOCKED — authorization-02 interrompida fail-closed na primeira causa`

Esta retomada foi executada sob o contrato cumulativo da task, da
authorization-01 e da authorization-02, sem editar esses documentos. As
integridades verificadas antes da primeira mutação foram:

| Documento | SHA-256 verificado |
|---|---|
| `S30b-preflight-publicacao-release-global-publisher.task.md` | `91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11` |
| `S30b-preflight-publicacao-release-global-publisher.authorization-01.md` | `33543ec2cc399d3a82c9d21cd4ba815465e0b40927d91043a6bc55cb7a897961` |
| `S30b-preflight-publicacao-release-global-publisher.authorization-02.md` | `96245fc58b5fdd32b7fac454614ca2d9caa694b4dad870c2c2625691cd598044` |
| relatório antes da retomada | `066f4180bd7470ff8cb73ca8174c44a28f82fbcf43f9c805cde8c2c8773a29cf` |

### Preflight obrigatório antes da mutação

Os comandos de reconciliação foram somente leitura e retornaram os seguintes
resultados:

| Comando | Exit | Resultado sanitizado |
|---|---:|---|
| `git status --short --branch` | 0 | `main...origin/main [ahead 5]`; sem alterações locais antes deste apêndice |
| `git rev-parse HEAD origin/main` | 0 | `HEAD=991bb35acc16d69cc2033f731ba3400fd96d266e`; `origin/main=50f423a979d7723d0e15d56b1d72625ea2b8ebea` |
| `git rev-list --count origin/main..HEAD` | 0 | `5` |
| `git rev-list --oneline origin/main..HEAD` | 0 | cinco commits documentais lineares, iniciando em `991bb35` e terminando em `da0a63e` |
| `git merge-base --is-ancestor origin/main HEAD` | 0 | base preservada e fast-forward |
| `git diff --check origin/main..HEAD` | 0 | sem erro de whitespace |
| `git ls-remote origin refs/heads/main` | 0 | remoto `main=50f423a979d7723d0e15d56b1d72625ea2b8ebea` |
| `git ls-remote --tags origin` | 0 | nenhuma tag remota |
| `gh auth status` | 0 | autenticação disponível; nenhum token impresso |
| `gh run list --branch main --limit 30` | 0 | nenhum run ativo |
| `gh run list --workflow publish-release.yml --branch main --limit 20` | 0 | nenhum run retornado |
| `gh run list --workflow deploy-production.yml --branch main --limit 20` | 0 | nenhum run retornado |
| `gh run list --workflow rollback-production.yml --branch main --limit 20` | 0 | nenhum run retornado |
| `gh release list --limit 20` | 0 | nenhuma release retornada |
| `gh api repos/greggorio/abaronesa-emporio/actions/variables --paginate --jq '.variables[]?.name'` filtrado para `RELEASE_PUBLISHER_ACTOR_IDS` | 0 | variável `MISSING` |

O stage estava vazio e a única alteração anterior ao trabalho desta retomada
era o estado documental já registrado no relatório. O remoto não apresentava
tag, release, run de publicação ou operação concorrente.

### Provisionamento oficial da App e primeira causa

Foi iniciado um único fluxo Manifest oficial, com o nome, proprietário,
repositório, URL, descrição, App privada, webhook inativo, eventos vazios e
somente `actions: write` e `contents: read` conforme a authorization-02. O
callback local usado foi `http://127.0.0.1:52359/`; o estado, o código de
conversão, a chave privada, o client secret, o webhook secret e qualquer token
foram mantidos fora do relatório e não foram impressos.

A página oficial foi aberta e a única instrução humana apresentada foi:

> `Confirme no GitHub a criação da App com o nome e as duas permissões exibidas. Não altere os campos. Conclua login/2FA se solicitado.`

O processo único do fluxo Manifest terminou com:

```text
AUTHORIZATION_02_BLOCKED:manifest_identity_mismatch
TEMP_DIR=/tmp/emporio-s30b-auth02-wua_h76n
exit=3
```

Esse é o primeiro gate de identidade da resposta oficial da conversão. O
marcador `CREATION_CONFIRMED` não foi emitido; portanto não houve avanço
autorizado para a URL de instalação. Não foi feita segunda conversão, segunda
App, nova chave, instalação manual, uso de PAT como identidade publisher,
retry, rerun ou dispatch.

As verificações posteriores foram somente de preservação e não constituem
tentativa de correção:

| Comando | Exit | Resultado sanitizado |
|---|---:|---|
| `test -e /home/gregorio/.config/emporio/release-control/publisher-github-app.pem` | 0 | ausente |
| `test -e /home/gregorio/.config/emporio/release-control/publisher-github-app.env` | 0 | ausente |
| `gh api repos/greggorio/abaronesa-emporio/actions/variables/RELEASE_PUBLISHER_ACTOR_IDS --jq '.name'` | 1 | variável ausente/não criada |
| `gh api /apps/emporio-publisher-1315264421 --jq '[.id,.slug,.name] | @tsv'` | 1 | lookup público não confirmou a App; nenhum dado sensível foi exibido |
| `find /tmp/emporio-s30b-auth02-wua_h76n -mindepth 1 -maxdepth 1 -print` | 0 | diretório temporário vazio |
| `rmdir /tmp/emporio-s30b-auth02-wua_h76n` | 0 | resíduo temporário vazio removido |

Como a identidade retornada não passou no gate declarado pelo contrato, não se
assume criação bem-sucedida nem se faz qualquer operação destrutiva remota. Se
o GitHub tiver preservado algum objeto intermediário da conversão, ele foi
deixado intacto para inspeção do orquestrador; não há instalação, chave local
ou credencial confirmada para remover ou substituir.

### Escopo não executado, arquivos e negativos preservados

- Não foram criados nem alterados `publisher-github-app.pem`,
  `publisher-github-app.env` ou `RELEASE_PUBLISHER_ACTOR_IDS`; não foram
  impressos PEM, JWT, installation token, client secret, webhook secret,
  state, code, header, senha ou valor de variável sensível.
- Não houve instalação, validação de escopo/permissões, derivação do ator,
  ambiente local, PostgreSQL, backend, migrations, bootstrap SYSTEM, publisher,
  frontend, browser de negócio, POST de publicação, replay, restart, dispatch,
  observação de run, tag, GitHub Release, artifact, BOM, deploy, rollback, SSH,
  VPS, GHCR ou produção.
- O container preexistente `baronesa-postgres`, seus volumes e portas não foram
  tocados. Nenhum recurso remoto foi excluído, cancelado, reexecutado ou
  substituído.
- O único arquivo editado nesta retomada é este relatório. Não houve stage,
  commit, push, pull, merge, rebase ou amend; task, authorizations, tracker e
  demais arquivos permaneceram inalterados. O relatório permanece local,
  modificado, não staged e não commitado.
- A S30b não foi aceita e nenhuma próxima slice foi criada. A primeira causa
  permanece `manifest_identity_mismatch`, aguardando decisão do orquestrador
  sobre a evidência remota sem nova tentativa automática.

BLOCKED — authorization-02 interrompida fail-closed na primeira causa

## Revisão do orquestrador — parada da authorization-02 aceita

> **Data:** 02/08/2026
> **Resultado da execução:** `ACCEPTED — parada fail-closed conforme contrato`
> **Estado da S30b:** `IN_PROGRESS`

O orquestrador confirmou `HEAD=991bb35acc16d69cc2033f731ba3400fd96d266e`,
`origin/main` e remoto em
`50f423a979d7723d0e15d56b1d72625ea2b8ebea`, stage vazio e somente este
relatório modificado. Os quatro documentos de entrada conservaram os hashes
esperados. A conta autenticada continua sendo `greggorio`, ID `35626201`; não
há instalação, variável publisher, tag, release ou run de publicação.

A parada antes de instalação e persistência foi segura. Entretanto,
`manifest_identity_mismatch` não é uma pendência humana nem um bloqueio
terminal da S30b. A authorization-02 fixou antecipadamente um slug que é saída
derivada do registro no GitHub e não exigiu que o executor preservasse no
relatório a tupla sanitizada recebida. Com a resposta descartada e o diretório
temporário removido, não é possível distinguir pelo relatório entre uma App
criada com slug derivado diferente e uma conversão sem objeto persistido.

A continuidade correta é reconciliar primeiro as GitHub Apps pertencentes à
conta pela interface administrativa, reutilizar uma única App produzida pela
tentativa se ela existir e recuperar uma chave privada operacional. Somente se
nenhuma App compatível existir será admissível repetir uma vez o Manifest flow.
O slug real confirmado pelo GitHub passa a ser o vínculo canônico; nome,
owner, repositório e permissões continuam fechados.

IN_PROGRESS — parada aceita; aguardando retomada de reconciliação da GitHub App

## Retomada authorization-03

> **Data:** 02/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Resultado:** `BLOCKED — authorization-03 interrompida fail-closed na primeira causa`

Esta retomada executou o contrato cumulativo da task e das três autorizações.
Task, authorizations e tracker não foram editados. As integridades verificadas
antes da primeira mutação foram:

| Documento | SHA-256 verificado |
|---|---|
| `S30b-preflight-publicacao-release-global-publisher.task.md` | `91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11` |
| `S30b-preflight-publicacao-release-global-publisher.authorization-01.md` | `33543ec2cc399d3a82c9d21cd4ba815465e0b40927d91043a6bc55cb7a897961` |
| `S30b-preflight-publicacao-release-global-publisher.authorization-02.md` | `96245fc58b5fdd32b7fac454614ca2d9caa694b4dad870c2c2625691cd598044` |
| `S30b-preflight-publicacao-release-global-publisher.authorization-03.md` | `2bc5f4c3bec0da5ed5798e5a626d64cbcb263712d1541456eb244ae223ddef86` |
| relatório antes desta retomada | `bb819f4ffdcc0ed36b33e85621c43d16c5514b3dbdfafc111dc5ee8af4b1b237` |

### Preflight antes da reconciliação

Os comandos obrigatórios foram executados antes da criação do diretório
protegido e de qualquer alteração externa:

| Comando | Exit | Resultado sanitizado |
|---|---:|---|
| `git status --short --branch` | 0 | `## main...origin/main [ahead 6]`; stage e worktree vazios |
| `git rev-parse HEAD origin/main` | 0 | `HEAD=6930cca0734696642773abeff73232b607e78426`; `origin/main=50f423a979d7723d0e15d56b1d72625ea2b8ebea` |
| `git rev-list --count origin/main..HEAD` | 0 | `6` |
| `git rev-list --oneline origin/main..HEAD` | 0 | seis commits documentais lineares, iniciando em `6930cca` e terminando em `da0a63e` |
| `git merge-base --is-ancestor origin/main HEAD` | 0 | ancestralidade preservada |
| `git diff --check origin/main..HEAD` | 0 | sem erro de whitespace |
| `git ls-remote origin refs/heads/main` | 0 | remoto `main=50f423a979d7723d0e15d56b1d72625ea2b8ebea` |
| `git ls-remote --tags origin` | 0 | nenhuma tag |
| `gh auth status` | 0 | conta `greggorio` ativa; nenhum segredo registrado |
| `gh run list --branch main --limit 30` | 0 | somente runs concluídos; nenhum concorrente |
| `gh run list --workflow publish-release.yml --branch main --limit 20` | 0 | nenhum run |
| `gh run list --workflow deploy-production.yml --branch main --limit 20` | 0 | nenhum run |
| `gh run list --workflow rollback-production.yml --branch main --limit 20` | 0 | nenhum run |
| `gh release list --limit 20` | 0 | nenhuma release |
| consulta de nomes em `actions/variables` filtrada para `RELEASE_PUBLISHER_ACTOR_IDS` | 0 | `MISSING` |
| `sha256sum` dos cinco documentos | 0 | todos os hashes acima conformes |

### Caso A — inventário e reconciliação

A página autenticada `https://github.com/settings/apps` foi aberta e a App
compatível foi identificada pela captura da interface administrativa e por
`GET /app` assinado com a chave recém-preservada. A classificação é exatamente
`Caso A`: uma única App compatível existe; nenhum Manifest flow novo foi
iniciado.

| Campo | Valor sanitizado/verificado |
|---|---|
| candidatas compatíveis | `1` |
| App ID | `4467123` |
| nome | `Emporio Publisher 1315264421` |
| slug canônico real | `emporio-publisher-1315264421` |
| owner | `greggorio`, ID `35626201`, tipo `User` |
| homepage / external URL | `https://github.com/greggorio/abaronesa-emporio` |
| descrição | `Publisher local do controle de releases do Emporio` |
| visibilidade | página privada; API devolveu `public: null`, interpretação privada |
| eventos | lista vazia |
| permissões | exatamente `actions: write`, `contents: read`, `metadata: read` |
| client secret | um existente e mascarado na UI; não lido, regenerado ou usado |
| IP allow list | vazia; não alterada |
| instalações antes da ação | `0` |

O campo que explica o `manifest_identity_mismatch` anterior foi identificado
na reconciliação: o endpoint da App devolve `public: null` para esta App
privada, enquanto o fluxo anterior comparava esse campo literalmente com
`false`. O payload original da conversão foi descartado pela execução
anterior, portanto a atribuição é baseada na resposta atual equivalente do
GitHub e não em uma transcrição retroativa do payload perdido. O slug real
confirmado pela UI e pelo endpoint é o mesmo valor canônico acima.

### Chave substituta e vínculo por fingerprint

Antes da ação havia `0` PEM operacional local e a UI mostrava `2` chaves
públicas. O PEM baixado pelo usuário foi validado sem imprimir conteúdo:

```text
source=/run/media/gregorio/dados/Downloads/emporio-publisher-1315264421.2026-08-02.private-key.pem
regular file; owner=1000:1000; mode=777; size=1675 bytes
target absent before move
mv -- <source> <target>
target=/home/gregorio/.config/emporio/release-control/publisher-github-app.pem
regular file; owner=1000:1000; mode=600; size=1675 bytes
source absent after move
```

O diretório protegido foi criado com modo `700`. A chave carregou como RSA de
2048 bits, formato emitido pelo GitHub, e o fingerprint oficial foi calculado
com:

```text
openssl rsa -in /home/gregorio/.config/emporio/release-control/publisher-github-app.pem -pubout -outform DER | openssl sha256 -binary | openssl base64
exit 0
HnQAy4kVpI2ZTZubSF3pyzf7FTTfH5hlF5JDr0UENb8=
```

A captura da seção `Private keys` confirmou o mesmo fingerprint na chave
recém-gerada. A primeira chave pública, sem PEM preservado, era
`SHA256:60HKUW9CTOxNy4Z34/fBlTkCbxM+9cbwKUeZXWcY8Q=` e foi removida pela UI
somente depois da prova da nova chave. A captura seguinte mostrou exatamente
`1` chave pública, `HnQ...`, e nenhuma IP allow list.

As primeiras validações efêmeras registraram dois problemas de formato do
cliente de prova, sem mutação remota: o primeiro encoder rejeitou `iss` inteiro
(exit 1, antes de request); o segundo parser tratou a lista de instalações como
objeto (exit 1, após GET 200). Ambos foram corrigidos somente no processo
efêmero. A reconciliação final passou:

```text
GET /app                         status=200
GET /app/installations           status=200
APP_JWT_VALIDATION=PASSED
APP_TUPLE id=4467123 name=Emporio Publisher 1315264421 slug=emporio-publisher-1315264421 owner=greggorio/35626201/User public=null events=[] permissions=actions:write,contents:read,metadata:read
INSTALLATION_COUNT=0
KEY_FINGERPRINT=HnQAy4kVpI2ZTZubSF3pyzf7FTTfH5hlF5JDr0UENb8=
```

Nenhum JWT ou installation token foi impresso. A variável do repositório não
foi configurada nesta fase porque a instalação exclusiva ainda não existia.

### Instalação exclusiva e primeira causa terminal

Foi aberta uma única URL baseada no slug real:

```text
https://github.com/apps/emporio-publisher-1315264421/installations/new/permissions?suggested_target_id=35626201&repository_ids[]=1315264421
```

`xdg-open` retornou exit `0`. A instrução apresentada foi exatamente:

> `Selecione Only select repositories, mantenha somente abaronesa-emporio, revise Actions write e Contents read e clique Install.`

Um único monitoramento read-only esperou no máximo cinco minutos pela
instalação compatível. O processo terminou com:

```text
INSTALLATION_MONITOR=started
WAITING_INSTALLATION
...
AUTHORIZATION_03_BLOCKED:installation_timeout
exit=3
```

Nenhum `INSTALLATION_OBSERVED` foi emitido; a consulta final continuou
retornando `GET /app/installations=200`, `COUNT=0`. Como a primeira causa foi a
ausência da instalação dentro da janela contratada, não houve POST para mintar
installation token, leitura de repositório por token, derivação de bot,
criação de `.env` ou configuração de `RELEASE_PUBLISHER_ACTOR_IDS`. Não foi
feito retry, rerun, segunda instalação, nova App ou nova chave.

### Preservação, limpeza e negativos

Após a parada, as verificações sanitizadas foram:

| Comando | Exit | Resultado |
|---|---:|---|
| `stat` do diretório protegido e PEM | 0 | diretório `700`, PEM regular `600`, owner `1000:1000`, 1675 bytes |
| presença de `publisher-github-app.env` | 0 | ausente; não havia installation ID para persistir |
| leitura nominal da variável remota | 1 | `RELEASE_PUBLISHER_ACTOR_IDS` permanece ausente |
| `GET /app/installations` com JWT | 0 | `COUNT=0` |
| `ss -ltnp` nas portas `8080`, `8084`, `8090` | 0 | nenhuma porta-alvo em uso |
| `docker ps --filter name=^baronesa-postgres$` | 0 | `baronesa-postgres` preservado, healthy, porta `5434` original |

O navegador isolado criado nominalmente para a reconciliação foi encerrado
com `Ctrl-C` (exit `0`). O perfil temporário
`/tmp/emporio-s30b-apps-VnBOck` foi removido somente com exclusões dirigidas de
arquivos/links e diretórios vazios (exits `0`); a captura temporária local
também foi removida (exit `0`). Nenhum processo ERP, PostgreSQL, publisher ou
frontend foi iniciado; não houve migration, bootstrap, UI de negócio, POST,
dispatch, replay, restart, tag, release, artifact novo, deploy, rollback, SSH,
VPS, GHCR ou produção.

O único recurso operacional preservado é a App reconciliada com sua única
chave pública correspondente e o PEM protegido. A instalação não existe; a
allowlist continua ausente. Não houve stage, commit, push, pull, merge, rebase
ou amend. O único arquivo do repositório editado nesta retomada é este
relatório, que permanece local, modificado, não staged e não commitado. A
S30b não foi aceita e nenhuma próxima slice foi criada.

BLOCKED — authorization-03 interrompida fail-closed na primeira causa

## Retomada authorization-03 — segunda execução

> **Data:** 02/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Resultado:** `BLOCKED — authorization-03 interrompida fail-closed na primeira causa`

Esta execução retomou o contrato cumulativo a partir do ponto exato em que a
execução anterior parou (`installation_timeout`), sem repetir a reconciliação
já fechada e sem iniciar novo Manifest flow.

### Divergência de checkpoint declarada antes da primeira mutação

O prompt exigiu `stage e worktree vazios` e o relatório em
`bb819f4ffdcc0ed36b33e85621c43d16c5514b3dbdfafc111dc5ee8af4b1b237`. O estado
real encontrado foi:

| Item | Exigido | Observado | Avaliação |
|---|---|---|---|
| `HEAD` | `6930cca0734696642773abeff73232b607e78426` | idêntico | conforme |
| `origin/main` e remoto | `50f423a979d7723d0e15d56b1d72625ea2b8ebea` | idêntico | conforme |
| `origin/main..HEAD` | 6 commits documentais lineares | `6` | conforme |
| `task` | `91c890d5…c492b55e11` | idêntico | conforme |
| `authorization-01` | `33543ec2…7a897961` | idêntico | conforme |
| `authorization-02` | `96245fc5…1cd598044` | idêntico | conforme |
| `authorization-03` | `2bc5f4c3…e223ddef86` | idêntico | conforme |
| relatório | `bb819f4f…5ea8f4b1b237` | `be640b47ce6240b672994a134842e8ee1ea548ba78a001dab53e10b57a4a920b` | **divergente** |
| stage e worktree | vazios | somente este relatório modificado | **divergente** |

As duas divergências têm causa única e idêntica: a execução anterior já havia
acrescentado a seção `Retomada authorization-03` com terminal `BLOCKED`. Não é
corrupção nem mutação fora de escopo — o relatório contínuo é o único arquivo
do repositório que as autorizações permitem modificar, e ele permaneceu não
staged e não commitado.

A execução não restaurou o relatório para `bb819f4f`. Restaurá-lo apagaria o
registro escrito de uma mutação externa **irreversível** já consumada pela
execução anterior: a geração da chave substituta e a exclusão da chave pública
antiga sem PEM. A authorization-03 §7 determina preservar App, instalação,
chave e configuração válidas já obtidas; descartar a evidência escrita dessa
rotação contrariaria essa preservação. Por isso a evidência foi **acrescentada**
e a divergência foi declarada explicitamente acima em vez de ser normalizada.

Nenhum outro item do checkpoint divergiu. `RELEASE_PUBLISHER_ACTOR_IDS` estava
ausente (`total_count=0`), sem tags, releases, runs de `publish-release.yml`,
`deploy-production.yml` ou `rollback-production.yml`.

### Estado externo revalidado antes de agir

Verificação somente leitura com o PEM já preservado, sem imprimir JWT:

```text
GET /app                    status=200
APP_TUPLE id=4467123 name=Emporio Publisher 1315264421
          slug=emporio-publisher-1315264421
          external_url=https://github.com/greggorio/abaronesa-emporio
          owner=greggorio/35626201/User public=null events=[]
          permissions=actions:write,contents:read,metadata:read
GET /app/installations      status=200 COUNT=0
KEY_FINGERPRINT            HnQAy4kVpI2ZTZubSF3pyzf7FTTfH5hlF5JDr0UENb8=
```

O `Caso A` permaneceu válido e fechado: uma única App compatível, uma única
chave pública com PEM correspondente. Nenhum Manifest flow foi iniciado.

### Instalação exclusiva concluída

Foi aberta uma única URL, derivada do slug real canônico:

```text
https://github.com/apps/emporio-publisher-1315264421/installations/new/permissions?suggested_target_id=35626201&repository_ids[]=1315264421
```

`xdg-open` retornou exit `0`. A instrução apresentada foi exatamente:

> `Selecione Only select repositories, mantenha somente abaronesa-emporio, revise Actions write e Contents read e clique Install.`

Diferentemente da execução anterior, o monitoramento read-only não expirou:

```text
INSTALLATION_MONITOR=started
INSTALLATION_OBSERVED COUNT=1
exit=0
```

Interação classificada como `INSTALLATION_CONFIRMED`.

### Prova operacional da identidade reconciliada

| Verificação | Exit/Status | Resultado sanitizado |
|---|---:|---|
| `GET /app/installations/{id}` | 200 | `id=150814210`, `app_id=4467123`, account `greggorio`/`35626201`/`User`, `repository_selection=selected`, `events=[]`, `single_file=null` |
| `POST /app/installations/{id}/access_tokens` | 201 | token mintado somente em memória; `permissions=actions:write, contents:read, metadata:read`; `repository_selection=selected`; `expires_at` presente |
| `GET /installation/repositories` | 200 | `total_count=1`; repo `1315264421 greggorio/abaronesa-emporio private=True`; `MATCH_CANONICAL=True` |
| `GET .github/workflows/publish-release.yml@main` (contents) | 200 | legível, `size=5699` |
| `GET actions/workflows/publish-release.yml` | 200 | `state=active` |
| `GET actions/workflows/publish-release.yml/runs` | 200 | `total_count=0` |
| `GET releases` | 200 | `0` |
| `GET git/refs/tags` | 404 | nenhuma tag |
| `GET actions/runs/30757430990` | 200 | `conclusion=success`, `head_sha=50f423a979d7723d0e15d56b1d72625ea2b8ebea` |
| `GET actions/runs/30757430990/artifacts` | 200 | `8836368371 candidate-effective-plan expired=False`; `8836442429 candidate-manifest expired=False`; `8836442612 candidate-outcome expired=False` |

Exatamente uma instalação, exatamente um repositório, permissões exatamente as
contratadas, nenhum escopo administrativo. Nenhum dispatch de ensaio foi feito;
`actions:write` foi comprovado apenas pela resposta de criação do token.

### Ator bot e allowlist

```text
GET /users/emporio-publisher-1315264421[bot]   status=200
BOT_TUPLE login=emporio-publisher-1315264421[bot] type=Bot site_admin=false
           id_positive_int=true id_digits=9
```

A variável foi configurada uma única vez com a autenticação administrativa
corrente do `gh`. O valor foi passado por `--input` a partir de arquivo
protegido no diretório temporário, nunca por `argv`:

```text
gh api -X POST repos/greggorio/abaronesa-emporio/actions/variables --input <FILE>
POST_variable_exit=0
```

Revalidação sem expor o valor:

```text
VARIABLE_NAME            RELEASE_PUBLISHER_ACTOR_IDS
VARIABLE_STATE           PRESENT
CSV_VALID                True
ENTRY_COUNT              1
MATCHES_CONFIRMED_BOT    True
VALUE_LOGGED             False
```

Estado: `CONFIGURED`.

### Configuração operacional protegida

O `.env` estava ausente e foi gravado atomicamente (`umask 077`, escrita em
`.tmp` seguida de `mv -f`), sem sobrescrever nada:

```text
/home/gregorio/.config/emporio/release-control/            directory   700 gregorio:gregorio
/home/gregorio/.config/emporio/release-control/publisher-github-app.pem  regular 600 gregorio:gregorio 1675 links=1 REGULAR
/home/gregorio/.config/emporio/release-control/publisher-github-app.env  regular 600 gregorio:gregorio  199 links=1 REGULAR
```

O `.env` contém exatamente três linhas com nomes operacionais não secretos
(`RELEASE_CONTROL_GITHUB_APP_ID`, `RELEASE_CONTROL_GITHUB_INSTALLATION_ID`,
`RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH`). Nenhum client secret, webhook
secret, JWT, installation token, code ou state foi persistido.

Com isto, as seções 4 e 5 da authorization-03 ficaram integralmente verdes.

### Retomada da authorization-01 §8 — ambiente local isolado

| Passo | Exit | Resultado |
|---|---:|---|
| `mktemp -d /tmp/emporio-s30b-XXXXXXXX` | 0 | diretório nominal modo `700` com `secrets/`, `keys/`, `logs/`, `frontend/`, `artifacts/` também `700` |
| geração de segredos fortes | 0 | 5 segredos aleatórios modo `600` (senha admin PG, senha ERP `SYSTEM`, JWT secret comercial, pepper do publisher, `kid`); nenhum impresso |
| `openssl genpkey` RSA 3072 | 0 | chave PKCS8 do emissor ERP, modo `600`, fora do repositório; `Private-Key: (3072 bit, 2 primes)` |
| porta dinâmica livre | 0 | `57341` |
| `docker run … postgres:16 --tmpfs /var/lib/postgresql/data` | 0 | bind somente `127.0.0.1:57341`; storage descartável em tmpfs; sem volume nomeado |
| `pg_isready` | 0 | pronto em 2 s; `PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)` |
| `CREATE DATABASE emporio_erp` / `emporio_release_control` | 0 | dois bancos distintos criados |

Divergência menor registrada: a authorization-01 §5 cita
`postgres@sha256:4e6e670bb069649261c9c18031f0aded7bb249a5b6664ddec29c013a89310d50`
entre as imagens locais utilizáveis, mas a `postgres:16` presente no host tem
`RepoDigest sha256:33f923b05f64ca54ac4401c01126a6b92afe839a0aa0a52bc5aeb5cc958e5f20`
e `Id sha256:88a36c64c1003dad93f56daa12d1f8916ec66d1fa3e5fb1fb0ae7cb77efd56d1`.
Aquela seção é permissiva (“é permitido usar”), enquanto a exigência dura da §8
é “PostgreSQL 16 efêmero”, satisfeita por `16.14`. Nenhuma imagem foi puxada
para o host. A imagem `node@sha256:f70403e87646dc51b45295f4b8b70cdad0b63d2297c4c9899119b03f7af7a6b3`
confere exatamente e não chegou a ser usada.

O container preexistente `baronesa-postgres` e as portas `5432`/`5434` não
foram tocados em nenhum momento.

### ERP local — migrations aplicadas e primeira causa terminal

O ERP foi iniciado a partir do artefato já construído
`backend/target/emporio-backend-0.0.1-SNAPSHOT.jar` (build de 02/08/2026
05:57; o código não mudou desde então — o último commit de código é
`50f423a…` e os seis commits locais são documentais). Todas as credenciais
ficaram apenas no ambiente do processo, lidas de arquivos `600`; nenhuma
apareceu em `argv`.

Primeira tentativa — `exit != 0`:

```text
Caused by: org.springframework.util.PlaceholderResolutionException:
  Could not resolve placeholder 'app.cors.allowed-origins' in value "${app.cors.allowed-origins}"
```

O perfil `dev` não define `app.cors.allowed-origins`; apenas `prod` e `test` o
fazem. Resolvido sem alterar código, por variável de ambiente
(`APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:8084`, binding relaxado do Spring).

Segunda tentativa — migrations verdes, boot reprovado:

```text
org.flywaydb.core.internal.command.DbValidate : Successfully validated 50 migrations
org.flywaydb.core.internal.command.DbMigrate  : Successfully applied 50 migrations
                                                to schema "public", now at version v20260331100000
```

```text
flyway_schema_history: count=50 max_version=6 success=all_true
```

Em seguida, o contexto Spring foi cancelado:

```text
UnsatisfiedDependencyException: Error creating bean with name
  'releaseControlIdentityController' … Unsatisfied dependency expressed through
  constructor parameter 0: Error creating bean with name
  'releaseControlIdentityService' … Failed to instantiate
  [com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityService]:
  No default constructor found
Caused by: java.lang.NoSuchMethodException:
  com.baronesa.emporio.releasecontrol.identity.ReleaseControlIdentityService.<init>()
```

Terceira execução, somente para caracterizar a causa (`-Ddebug=true`), com o
relatório de avaliação de condições do Spring:

```text
ReleaseControlIdentityConfiguration matched:
   - @ConditionalOnProperty (app.release-control.identity.enabled=true) matched
ReleaseControlIdentityController matched:
   - @ConditionalOnProperty (app.release-control.identity.enabled=true) matched
ReleaseControlIdentityService matched
```

Isso elimina configuração incorreta como causa. A ponte foi habilitada
corretamente, o `@ConditionalOnProperty` casou, e a fábrica do bean
`releaseControlIdentityKeyMaterial` — que valida issuer loopback, `kid` contra
`^[A-Za-z0-9][A-Za-z0-9._-]{15,63}$`, arquivo regular absoluto não symlink,
PEM PKCS8 não criptografado e RSA CRT ≥ 3072 bits — **não emitiu nenhuma
objeção**: o issuer `http://127.0.0.1:8080/api/release-control/identity`, o
`kid` de 32 caracteres e a chave PKCS8 3072 bits foram aceitos.

A primeira causa é um defeito de código no commit autorizado:

```text
componente  backend/src/main/java/com/baronesa/emporio/releasecontrol/identity/ReleaseControlIdentityService.java
sintoma     Spring 6.2.19 não consegue selecionar construtor
detalhe     a classe declara dois construtores — público (ReleaseControlIdentityKeyMaterial)
            e package-private (ReleaseControlIdentityKeyMaterial, Clock) — e nenhum
            está anotado com @Autowired
efeito      determineCandidateConstructors devolve nenhum candidato; o container recai
            em instanciação por construtor sem argumentos, que não existe
```

Confirmado por bytecode (`javap -p`): exatamente `2` construtores, sem
`@Autowired` em nenhum deles. O gêmeo do deployer
(`DeployerReleaseControlIdentityService`) tem a forma idêntica, portanto o
defeito é sistêmico da ponte de identidade, embora o modo deployer esteja fora
do escopo da S30b.

O artefato não está desatualizado: a classe dentro do jar e a de
`backend/target/classes` têm o mesmo `sha256`
(`ff4515d1fcdb5627f3b97c035c2d934b61125c0efbcbae601ae4b38c3c60a7b2`), e
`git log` mostra o arquivo inalterado desde o baseline. Recompilar não altera o
resultado.

A correção seria de uma linha (`@Autowired` no construtor público, ou remoção
de um dos construtores), mas alterar código é expressamente proibido pela
authorization-01 §6, pela authorization-02 §10 e pela authorization-03 §7.
Também não existe caminho de contorno por configuração: a resolução de
construtor é interna ao container e independe de propriedades; e desabilitar a
ponte não é opção, porque a authorization-01 §8 a exige e a UI não obteria o
token publisher sem ela.

Por isso a execução parou fail-closed nesta primeira causa, antes de qualquer
efeito remoto.

### O que não foi executado

Como a parada ocorreu antes do ERP ficar de pé, não houve: bootstrap do
usuário `SYSTEM` (a tabela `usuarios` do banco efêmero ficou com `0` linhas,
pois o `ApplicationRunner` nunca chegou a rodar), migration Alembic do
publisher (o schema `public` do banco `emporio_release_control` ficou com `0`
tabelas), inicialização do publisher, inicialização do frontend, navegação
autenticada, interceptação do primeiro `POST`, `POST` real, idempotency key,
`operationId`, dispatch, run, tag, GitHub Release, assets, replay, restart ou
reconciliação.

### Limpeza dirigida

| Comando | Exit | Resultado |
|---|---:|---|
| encerramento do ERP | — | `0` processos `java` do jar nominal remanescentes; o próprio Spring encerrou no fail-closed |
| `docker stop emporio-s30b-pg` | 0 | parado |
| `docker rm -v emporio-s30b-pg` | 0 | `CONTAINER_REMOVED` (volume anônimo removido junto) |
| `rm -rf <TMPROOT>` | 0 | `TMPROOT_REMOVED`, com os cinco segredos, a chave RSA efêmera e os logs |
| `ss -ltn` em `57341`, `8080`, `8084`, `8090` | 0 | todas `FREE` |
| `docker ps` | 0 | `baronesa-postgres Up 5 days (healthy) 0.0.0.0:5434->5432/tcp` preservado |

Incidente de ferramenta registrado por transparência: uma primeira tentativa de
limpeza usou `pgrep -f`/`pkill -f` com um padrão que casou com a própria linha
de comando do shell da execução, encerrando-o (exit `144`). Nenhum processo do
usuário, container ou dado foi afetado; a limpeza foi refeita com seleção
exata por `comm=java`, o que também revelou que o `ERP_ALIVE` observado antes
era falso positivo do mesmo auto-casamento.

### Preservados, negativos e estado final

Preservados como infraestrutura operacional válida, conforme authorization-03
§7: App `4467123`, sua única chave pública, o PEM protegido, o `.env`
protegido, a instalação `150814210` e a variável `RELEASE_PUBLISHER_ACTOR_IDS`.

Negativos revalidados após a parada:

```text
tags remotas                       0
GitHub Releases                    0
runs publish-release.yml           0
runs deploy-production.yml         0
runs rollback-production.yml       0
instalações da App                 1 (a contratada, inalterada)
```

Nenhum deploy, rollback, SSH, VPS, GHCR, Docker de produção ou efeito em
produção. `ops/env/.env.production` não foi aberto. Nenhum PEM, JWT, token,
code, state, senha, pepper ou idempotency key foi impresso. Código, workflows,
task, authorizations e tracker não foram alterados. Não houve stage, commit,
push, pull, merge, rebase ou amend.

Snapshot Git final:

```text
## main...origin/main [ahead 6]
 M docs/infrastructure/deployment/implementation/slices/S30b-preflight-publicacao-release-global-publisher.report.md
HEAD        6930cca0734696642773abeff73232b607e78426
origin/main 50f423a979d7723d0e15d56b1d72625ea2b8ebea
ahead       6
```

O único arquivo do repositório modificado é este relatório, local, não staged e
não commitado. A S30b não foi aceita e nenhuma próxima slice foi criada.

### Pendência única para o orquestrador

A authorization-03 está integralmente cumprida nas seções 4 e 5 — reconciliação,
chave, instalação, permissões, bot e allowlist. A retomada da publicação
(seção 6) está bloqueada por um defeito de código fora da fronteira do
executor. Destravar exige uma autorização que permita corrigir
`ReleaseControlIdentityService` (e, coerentemente, seu gêmeo do deployer), ou
uma decisão equivalente do orquestrador.

BLOCKED — authorization-03 interrompida fail-closed na primeira causa

## Execução ampliada — correção do caminho de publicação

> **Data:** 03/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Natureza:** execução sob autorização humana ampliada, além da fronteira das
> authorizations 01 a 03

A parada acima foi correta e permanece válida como registro. Em seguida o
usuário suspendeu expressamente proibições do contrato, em decisões sucessivas
e explícitas. Esta seção registra a execução resultante. Ela **não** é a
execução contratada pela authorization-03: é uma execução exploratória de
correção, cujo objetivo passou a ser destravar o caminho de publicação inédito.

### Extensão de autoridade concedida pelo usuário

| # | Pedido do executor | Decisão do usuário |
|---|---|---|
| 1 | correção do defeito que impedia o ERP de subir | *"ignore a proibição e aplique a correção necessária para que o ERP suba"* |
| 2 | segundo defeito, no runtime publisher sob certificação | *"Corrigir e seguir até a v0.1.0"* |
| 3 | push para `origin/main`, ação externa e compartilhada | *"Sim — push das quatro correções"* |
| 4 | continuar após a primeira tentativa consumida | *"Corrigir o trust e republicar"* |
| 5 | ciclo único de push e dispatch | *"aceito sua sugestão"* |
| 6 | duas correções pendentes antes de prosseguir | *"apenas efetue as duas correções pendentes"* |

O orquestrador, ao revisar, manteve a S30b `IN_PROGRESS`, dispensou nova
autorização humana e fixou a ordem de execução reproduzida na seção
"Sequência exigida pelo orquestrador" adiante.

Nenhuma autorização foi concedida — nem exercida — para deploy, rollback, SSH,
VPS, GHCR ou produção. Os workflows `deploy-production.yml` e
`rollback-production.yml` permanecem com zero execuções.

### Por que o caminho estava inteiro quebrado

O `publish-release.yml` **nunca havia sido executado**. As contagens remotas
comprovam a assimetria entre o que foi exercitado e o que só existia em
contrato:

```text
ci.yml                    16 execuções
publish-candidate.yml     16 execuções
publish-release.yml        0 execuções antes desta sessão
deploy-production.yml      0 execuções
rollback-production.yml    0 execuções
```

As slices S14, S21, S26 e S28 estão `ACCEPTED`, mas validaram contrato, plano
offline e empacotamento. O próprio `release_control/README.md` declara que os
testes usam *"um transporte fake em loopback; nunca usam a API GitHub real"*, e
a S28 declara que *"não executa build, pull, up, publicação, instalação ou
operação em produção"*. Todos os defeitos abaixo vivem exatamente na fronteira
onde o transporte falso termina e a API real começa — com uma exceção, o #7,
que é mudança do GitHub em código correto quando escrito.

### Inventário dos oito defeitos

| # | Componente | Defeito | Evidência |
|---|---|---|---|
| 1 | `ReleaseControlIdentityService.java` | dois construtores, nenhum com `@Autowired`; Spring 6.2.19 não seleciona candidato e recai em construtor sem argumentos | `NoSuchMethodException: <init>()`; `javap -p` confirma 2 construtores; relatório de condições mostra `ReleaseControlIdentityConfiguration matched` |
| 2 | `GitHubClient.get_bytes` | `Accept: application/octet-stream` no download de artifact devolve `415`; e o redirect assinado não era seguido | sondas: octet→415 em três versões de API, `vnd.github+json`→302 |
| 3 | `Synchronizer.sync_candidates` | consultava `status=completed`, que inclui runs falhos, enquanto `_run` exige `conclusion=success` | 12 runs falhos no histórico mantinham `drift=true` permanente |
| 4 | `validate_workflow_run` (`current`) | esperava `name="Publish Release"` e `path` com sufixo `@main` | GitHub devolve o run-name como `name` e `path` sem `@ref` |
| 5 | `userStore.isRootUser` | liberava o painel por `email === "root@localhost"`, que `RootUserInitializer` não pode criar (exige `^[^\s@]+@[^\s@]+\.[^\s@]+$`) | autoridade real é `hasRole("SYSTEM")` em `SecurityConfig` |
| 6 | `_cli_trust` | `git fetch --no-tags origin main` sem credencial, em repositório privado com `persist-credentials: false` | `GIT_CONTEXT_INVALID`; `persist-credentials: false` é exigido por contrato (`release-workflow:invalid:checkout-credentials`) |
| 7 | `dispatch_publication` e gêmeos | exigia `204`; a API `2026-03-10` responde `200` com `workflow_run_id` | sonda local capturou `status=200`, corpo com `workflow_run_id`, header `x-github-api-version-selected: 2026-03-10` |
| 8 | `validate_workflow_run` (`candidate`) | mesmo `@main` do #4, no ramo do candidato | `CANDIDATE_RUN_INVALID` no job `prepare` |

O #7 merece registro em favor do trabalho anterior: **não é desleixo**. O
GitHub alterou a resposta do `workflow_dispatch` na versão de API que o cliente
fixa. O #6 revelou uma tentativa de correção **errada** deste executor: a
primeira proposta foi `persist-credentials: true`, rejeitada pelo validador de
contrato do próprio repositório; a correção aceita remove o fetch redundante e
preserva a propriedade de segurança.

### Commits publicados em origin/main

```text
50f423a  base anterior
821658f  fix: repair the publisher publication path against real GitHub   (defeitos 1 a 5)
8f91577  fix: drop the unauthenticated fetch from the publication trust gate (defeito 6)
78b3559  fix: accept both workflow-dispatch success shapes and the real candidate path (defeitos 7 e 8)
```

Os seis commits documentais locais **não** foram publicados: cada push foi
montado em worktree destacada a partir de `origin/main`, contendo somente o
patch de código. O relatório contínuo nunca foi commitado.

### Reconciliação Git

Após o primeiro push, o `main` local ficou `ahead 7 / behind 2`, com `df3ab21`
duplicando o patch de `821658f`. A reconciliação foi feita sem desabilitar
guard e sem perder os documentais:

```text
git rebase --autostash origin/main
warning: skipped previously applied commit df3ab21
Rebasing (1/6) … (6/6)   Applied autostash.
```

Estado final: `ahead 6, behind 0`; os seis commits documentais reaplicados;
código local idêntico a `origin/main`; somente o relatório modificado. Os
hashes de autoridade permaneceram intactos durante toda a execução:

```text
task             91c890d52154dc983edfdf1b62a0e18d556234c03679fbee97bf32c492b55e11
authorization-01 33543ec2cc399d3a82c9d21cd4ba815465e0b40927d91043a6bc55cb7a897961
authorization-02 96245fc58b5fdd32b7fac454614ca2d9caa694b4dad870c2c2625691cd598044
authorization-03 2bc5f4c3bec0da5ed5798e5a626d64cbcb263712d1541456eb244ae223ddef86
```

### Identidade publisher — concluída

A authorization-03 §4 e §5 foram cumpridas integralmente antes de qualquer
correção de código.

| Verificação | Resultado |
|---|---|
| instalação | `150814210`, conta `greggorio`/`35626201`/`User`, `repository_selection=selected`, `events=[]` |
| token de instalação | `201`; `actions:write, contents:read, metadata:read`; formato `ghs_`, 383 caracteres |
| repositórios | `total_count=1`, exatamente `1315264421 greggorio/abaronesa-emporio` |
| ator bot | `emporio-publisher-1315264421[bot]`, `type=Bot`, id decimal positivo de 9 dígitos |
| allowlist | `RELEASE_PUBLISHER_ACTOR_IDS` `PRESENT`, CSV válido, exatamente um id, conferindo com o bot; valor nunca registrado |
| `.env` protegido | escrito atomicamente, `600`, arquivo regular, três nomes não secretos |

A prova mais forte da cadeia não é local: o job `trust` do run `30775358812`
publicou no próprio log `TRUSTED_ACTOR: emporio-publisher-1315264421[bot]`,
`TRUSTED_ACTOR_ID: 312233471` e `RELEASE_PUBLISHER_ACTOR_IDS: 312233471`. O
GitHub Actions validou a identidade construída aqui.

### Ambiente local isolado

| Recurso | Estado |
|---|---|
| diretório nominal | `mktemp -d`, modo `700`, subdiretórios `700` |
| segredos | cinco valores aleatórios fortes, modo `600`, nenhum impresso |
| chave do emissor ERP | RSA 3072 PKCS8, `600`, fora do repositório |
| PostgreSQL | `16.14` efêmero, tmpfs, bind `127.0.0.1` em porta dinâmica, dois bancos |
| ERP | `127.0.0.1:8080`, 51 migrations Flyway, `Bootstrap root criou usuario SYSTEM`, JWKS RS256 servindo |
| publisher | `127.0.0.1:8090`, Alembic em `0003_commercial_rollback (head)`, 11 tabelas, `/health/live` e `/health/ready` **200** |
| frontend | `127.0.0.1:8084`, loopback exclusivo após `--hostname` |
| preexistentes | `baronesa-postgres` e as portas `5432`/`5434` intocados |

Divergência registrada: a authorization-01 §5 cita
`postgres@sha256:4e6e670b…` entre as imagens locais utilizáveis, mas a
`postgres:16` do host tem `RepoDigest sha256:33f923b0…`. Aquela seção é
permissiva; a exigência dura da §8 é "PostgreSQL 16 efêmero", satisfeita. A
imagem `node@sha256:f70403e8…` confere exatamente e não foi usada. Nenhuma
imagem foi puxada para o host. O `frontend/.env` foi preservado e as três
variáveis `VITE_*` vieram do ambiente do processo.

### Prova de UI e sequência de POSTs

Em cada tentativa a mecânica contratada pela authorization-01 §9 foi observada
integralmente:

```text
ESTIMATED_AFTER_MINOR      v0.1.0
DESCRIPTION_LEN            102
CHANGELOG_LEN              375
CONFIRM_DIALOG_VISIBLE     True
POST_ATTEMPT #1            → FIRST_RESPONSE_DROPPED (runtime respondeu 202)
NO_AUTO_RETRY_CHECK        attempted=1  (a UI não repetiu sozinha)
RESUMABLE_PRESENT          True
POST_ATTEMPT #2            → SAME_KEY_REPLAY via "Retomar envio"
```

O replay idempotente ficou provado em todas as tentativas: duas chamadas, uma
única `publication.requested` no audit e nenhuma segunda operação. Uma trava
dura foi adicionada ao roteiro — se a estimativa não for exatamente `v0.1.0`,
ou se descrição e changelog não baterem byte a byte com os textos aprovados, o
roteiro aborta antes do POST.

### Execuções de publish-release

| Run | headSha | trust | prepare | publish | outcome | primeira causa |
|---|---|---|---|---|---|---|
| `30775358812` | `50f423a` | falha | — | — | — | `CURRENT_RUN_INVALID` (defeito 4) |
| `30791694997` | `821658f` | falha | — | — | — | `GIT_CONTEXT_INVALID` (defeito 6) |
| `30793918240` | `8f91577` | **sucesso** | falha | — | — | `CANDIDATE_RUN_INVALID` (defeito 8) |

Os três runs falhos foram preservados. Nenhum rerun, retry ou cancelamento.

### Consulta sanitizada do runtime

```text
select state, dispatch_state, error_code, count(*) from rc_publication_operation group by 1,2,3;
 FAILED | NOT_SENT | WORKFLOW_DISPATCH_REJECTED | 3

select eligibility, ci_status, manifest_status, count(*) from rc_candidate_snapshot group by 1,2,3;
 READY | PASSED | VALID | 4

select count(*) from rc_release_snapshot;
 0

select domain, (last_success_at is not null), drift, error_code from rc_sync_state;
 candidates | t | f | -
 releases   | t | f | -
```

As três operações `FAILED` com `WORKFLOW_DISPATCH_REJECTED` são consequência
direta do defeito #7: o dispatch chegou ao GitHub nas três vezes e criou o run,
mas o runtime leu `200` como recusa. Ambos os domínios de sincronização estão
verdes e sem drift após os defeitos #2 e #3.

### Suítes e validadores

| Suíte | Resultado |
|---|---|
| `tools/releases/tests/test_release_publication.py` | 89 testes, OK |
| conjunto contratual da task §4 (5 módulos) | 154 testes, OK |
| `release_control/tests/` completa | **318 passed, 2 failed** |
| `release_control_contract.py validate` | `release-control-contract:valid` |
| `validate_release_workflow.py` | `release-workflow:valid` |
| `validate_publisher_runtime.py` | `publisher-runtime:valid` |
| `validate_publisher_identity_bridge.py` | `publisher-identity-bridge:valid` |
| `validate_publisher_ui.py` | `publisher-ui:valid` |

As duas falhas são de `tests/test_deployer_api.py`, no domínio deployer, e têm
**causas distintas**:

```text
test_rollback_persists_dispatches_replays_and_supports_get
  assert 429 == 409   (rate limiter em memória, rollback_rate_per_minute=2)

test_rollback_state_machine_restore_recovery_and_terminal_replay
  psycopg.errors.CheckViolation: viola ck_rc_deployment_workflow_binding
  (linha rollback com dispatch_state='CONFIRMED' e workflow_run_id NULL)
```

Ambas são **preexistentes**: foram reproduzidas idênticas substituindo o
`github.py` local pelo de `origin/main`. Não decorrem destes patches e estão
fora do escopo da S30b.

Cobertura acrescentada para o defeito #7: 42 casos novos em
`test_remote_contract.py`, cobrindo `200` com `workflow_run_id` válido, `204`,
corpos `200` inválidos (ausente, string, zero, negativo, booleano, lista, JSON
malformado) e status inesperados (`201`, `202`, `403`, `422`), replicados nos
três dispatchers, e afirmando que um dispatch nunca é repetido. Verificados
como regressivos: falham 6 vezes contra a implementação anterior.

### Desvios do executor, registrados

1. **Candidato errado na terceira tentativa.** A UI pré-seleciona o candidato
   mais recente e o roteiro não forçava a escolha, de modo que foi usado
   `candidate-821658f…-30791301991-1` em lugar do contratado. Corrigido: o
   roteiro passou a selecionar o candidato explicitamente e aborta antes do
   POST se a seleção não conferir.
2. **Correção inicial errada do defeito #6**, proposta como
   `persist-credentials: true` e barrada pelo validador de contrato.
3. **Duas quedas do próprio shell** por `pgrep -f`/`pkill -f` casando com a
   linha de comando da execução (exit `144`). Nenhum processo do usuário,
   container ou dado foi afetado; a limpeza foi refeita com seleção exata por
   `comm=java`.
4. **Sonda de diagnóstico local** (`S30B_DISPATCH_PROBE`) inserida no
   `github.py` para capturar o status real do dispatch, e removida antes de
   qualquer commit. Nunca foi publicada.

### Critérios da S30b que se tornaram impossíveis

A S30b não pode ser aceita nos termos escritos, e este relatório não pretende
o contrário:

- a authorization-01 §9 exige **uma** intenção, **uma** chave e **um**
  dispatch; houve três intenções e três dispatches exploratórios;
- a authorization-01 §11 exige `headSha=50f423a…`; o `main` avançou para
  `78b3559` e os runs seguintes carregam o SHA vigente;
- as authorizations proíbem alterar código e executar push; ambos ocorreram sob
  autorização humana expressa e estão registrados acima;
- a terceira tentativa usou candidato diverso do fixado na authorization-01 §2.

Cabe ao orquestrador emitir o fechamento/auditoria que registre os runs
exploratórios e substitua os critérios que se tornaram impossíveis.

### Sequência exigida pelo orquestrador antes do próximo dispatch

| Passo | Estado |
|---|---|
| adicionar os testes do retorno 200/204 | concluído — 42 casos, regressivos |
| colocar os dois patches no `main` | concluído — `78b3559` |
| reconciliar Git sem desabilitar o guard nem perder os documentais | concluído — `ahead 6, behind 0` |
| aguardar CI e novo candidato verdes | em andamento |
| selecionar explicitamente o candidato, sem aceitar o default da UI | roteiro ajustado |
| reiniciar o publisher | pendente |
| registrar toda a execução ampliada no relatório | esta seção |

### Negativos preservados

```text
tags remotas                       0
GitHub Releases                    0
runs deploy-production.yml         0
runs rollback-production.yml       0
instalações da App                 1
operações PUBLISHED                0
```

Nenhum PEM, JWT, token, code, state, senha, pepper ou idempotency key foi
impresso. `ops/env/.env.production` não foi aberto. Nenhum acesso a GHCR,
Docker de produção, SSH, VPS, deploy ou rollback. Os documentos de autoridade e
o tracker não foram alterados por este executor.

## Publicação da v0.1.0 e encerramento operacional

> **Data:** 03/08/2026
> **Decisão do orquestrador:** caminho 1 — release aceita; nenhuma nova
> operação, dispatch, retry, replay com nova chave ou tentativa
> `already_published`

### Defeitos 9 e 10, encontrados ao exercitar `publish` e a reconciliação

| # | Componente | Defeito | Evidência |
|---|---|---|---|
| 9 | `GhTransport.upload` | `gh api --hostname uploads.github.com` nomeia instância Enterprise e o `gh` prefixa `api.`, endereçando `api.uploads.github.com`, host inexistente | sonda: com `--hostname`, `error connecting to api.uploads.github.com`; com URL absoluta, o GitHub responde `Bad Content-Length` HTTP 400, provando host e credencial válidos |
| 10 | `_bind_run` e os dois gêmeos do deployer | exigiam `name` igual ao nome do workflow e `path` com `@main` | o run real devolve `name` = `display_title` e `path` sem `@ref` |

O #9 ficou invisível no log porque o `stderr` do upload vai para `DEVNULL` por
design de sanitização. A cobertura existente afirmava apenas que a falha
permanece sanitizada, **nunca para onde o upload era endereçado**; foi assim
que o host quebrado sobreviveu. Um caso novo fixa o endpoint e rejeita
`--hostname`, e o guard de inventário `test_c02a_08` subiu de 81 para 82.

O #10 é a terceira instância do mesmo defeito de forma do run, e a correção
respeita a diferença entre os workflows: `publish-release.yml` e
`deploy-production.yml` declaram `run-name`, logo seu `name` é o título de
exibição; `rollback-production.yml` não declara, logo mantém o nome do
workflow. Não resta nenhum `@main` no código.

Commits publicados nesta fase, todos fast-forward:

```text
38385c1  fix: upload release assets to the real uploads host                    (defeito 9)
67abde4  fix: bind workflow runs by their real REST shape in both reconcilers    (defeito 10)
```

### Compensação transacional verificada sob falha real

Quando o `publish` falhou com `REMOTE_UPLOAD_FAILED` no run `30802942617`, a
`publish_transaction` compensou corretamente. Verificação incluindo drafts:
zero releases, zero drafts, zero tags. O desenho transacional segurou na sua
primeira falha real, sem deixar resíduo remoto.

### Rodada bem-sucedida — intenção única e replay

Run `30804834574`, `headSha=38385c1`, os quatro jobs verdes:

```text
trust ✅   prepare ✅   publish ✅   outcome ✅
```

Prova de UI capturada integralmente nesta rodada:

```text
CANDIDATE_OPTIONS_MATCHING 1
CANDIDATE_SELECTED         candidate-38385c100ab8b0ae07099b6a5a7b016b7c2b7322-30803878927-1
ESTIMATED_BEFORE_CONFIRM   v0.1.0
DESCRIPTION_LEN            102
CHANGELOG_LEN              375
POST_ATTEMPT #1            → FIRST_RESPONSE_DROPPED (runtime respondeu 202)
NO_AUTO_RETRY_CHECK        attempted=1 reached=1
POST_ATTEMPT #2            → SAME_KEY_REPLAY via "Retomar envio"
```

O candidato foi selecionado explicitamente, sem aceitar o default da UI, com
trava que aborta antes do POST se a seleção, a estimativa, a descrição ou o
changelog não conferirem byte a byte.

### Validação da release publicada

```text
release id            364130074
tag_name / name       v0.1.0 / v0.1.0
draft / prerelease    false / false
target_commitish      38385c100ab8b0ae07099b6a5a7b016b7c2b7322
tag object            type=commit sha=38385c100ab8b0ae07099b6a5a7b016b7c2b7322
assets                metadata.json (343) release.json (21279) release.json.sha256 (65)
                      todos state=uploaded, tamanho baixado confere
sidecar               sha256(release.json) == release.json.sha256  →  True
manifest.release      v0.1.0
manifest.sourceCommit 38385c100ab8b0ae07099b6a5a7b016b7c2b7322
manifest.candidateId  candidate-38385c100ab8b0ae07099b6a5a7b016b7c2b7322-30803878927-1
deployable            True
```

BOM confrontado com o artifact `candidate-manifest` do run `30803878927`:

```text
componentes na release    6
componentes no candidato  6
mesmos ids                True
backend           identical=True  digest=sha256:032c5499fbae07de8…
frontend          identical=True  digest=sha256:e34c138c13d275054…
gateway           identical=True  digest=sha256:dbba76ec16731581f…
website_back      identical=True  digest=sha256:5fb8acf3618b5ee13…
website_front     identical=True  digest=sha256:a2abe0297a9ad1ac7…
whatsapp_service  identical=True  digest=sha256:7d8452b37f6aaf39f…
BOM idêntico ao candidato  True
```

### Critério superseded e dívida de auditoria

A operação `pub_ee1405cf87bc4e6b912eb13a4fb72a2d` permanece `FAILED`, com
`dispatch_state=SENT` e `error_code=WORKFLOW_RUN_INVALID`, porque o defeito #10
ainda estava presente no runtime durante aquele run. A release foi criada com
sucesso enquanto isso acontecia.

Por decisão do orquestrador, a exigência da authorization-01 §11 de que **essa
operação específica** termine em `PUBLISHED` fica **superseded** pelo resultado
real. A operação **não** foi alterada manualmente no banco e nenhuma evidência
retroativa foi fabricada. A discrepância entre operação `FAILED` e release
`PUBLISHED` é registrada como dívida de auditoria conhecida, não como bloqueio
do MVP.

A duplicação permanece protegida por duas camadas independentes: o candidato
`38385c1` passou a `NOT_ELIGIBLE` pelo snapshot de releases, e o `prepare`
calcularia modo `already_published` para ele.

### Suítes

| Suíte | Resultado |
|---|---|
| `tests/test_reconciliation.py` + `tests/test_deployer_reconciliation.py` | 57 passed |
| `release_control/tests/` completa | 318 passed, 2 failed |
| `tools/releases/tests/test_release_publication.py` | 90 passed |

As duas falhas são preexistentes, do domínio deployer, com **causas distintas**,
reproduzidas idênticas com o `github.py` de `origin/main`:

```text
test_rollback_persists_dispatches_replays_and_supports_get
  assert 429 == 409                       rate limiter (rollback_rate_per_minute=2)

test_rollback_state_machine_restore_recovery_and_terminal_replay
  psycopg.errors.CheckViolation           ck_rc_deployment_workflow_binding
```

### Restart controlado do publisher, sem redispatch

O publisher foi parado sozinho e reiniciado uma única vez, com o mesmo banco e
a mesma configuração. ERP, PostgreSQL e frontend seguiram no ar durante a
parada, comprovando que o alvo foi apenas o publisher:

```text
publisher durante a parada   DOWN
ERP                          200
frontend                     200
PostgreSQL efêmero           no ar
READY_AFTER_RESTART          10 s
```

Invariantes exigidos, todos verificados após o reinício:

| Exigência | Resultado |
|---|---|
| `/health/live` e `/health/ready` | `200` e `200` |
| snapshots sincronizados sem drift | `candidates drift=false`, `releases drift=false`, ambos com sucesso |
| `v0.1.0` continua `PUBLISHED` | `v0.1.0 state=PUBLISHED candidate=candidate-38385c1…-30803878927-1` |
| candidato `38385c1` continua `NOT_ELIGIBLE` | confirmado |
| nenhum novo dispatch | runs de `publish-release` = `5`, igual ao baseline |
| nenhuma segunda tag ou release | tags = `1`, releases = `1`, iguais ao baseline |
| operação histórica sem mutação retroativa | `pub_ee1405cf… state=FAILED dispatch=SENT err=WORKFLOW_RUN_INVALID version=3`, inalterada |
| nenhuma operação nova | total = `5`, `PUBLISHED` = `0` |

O log do reinício não registrou nenhuma linha de dispatch, erro ou drift.

### Limpeza dirigida

| Alvo | Resultado |
|---|---|
| frontend, publisher e ERP | encerrados pelos PIDs nominais; nenhum processo `java`, `uvicorn` ou `quasar` da prova remanescente |
| portas `8080`, `8084`, `8090` | todas `FREE` |
| PostgreSQL efêmero | `docker stop` e `docker rm -v` exit `0`; container removido com seu volume anônimo; porta dinâmica `FREE` |
| diretório temporário nominal | removido com os 5 072 arquivos, incluindo os cinco segredos, a chave RSA efêmera, os logs e o venv |
| temporários do scratchpad | payloads reais, `bot_id` e cópias de trabalho removidos |
| worktrees temporárias | podadas; resta apenas a árvore principal |

Preexistentes confirmados intactos: `baronesa-postgres` no ar há 6 dias,
`healthy`, na porta `5434` original; portas `5432` e `5434` não tocadas; volume
`baronesa_baronesa-pg-data` preservado.

Infraestrutura operacional preservada, conforme determinado:

```text
/home/gregorio/.config/emporio/release-control/           directory 700
/home/gregorio/.config/emporio/release-control/publisher-github-app.pem   600  1675 bytes
/home/gregorio/.config/emporio/release-control/publisher-github-app.env   600   199 bytes
App 4467123 emporio-publisher-1315264421   GET /app = 200   instalações = 1
RELEASE_PUBLISHER_ACTOR_IDS                 PRESENT
```

### Estado final

```text
HEAD         8fd722b1fdd0fddb355c55564387230f2170a422
origin/main  67abde48fd4a74de5bcff22bf592bd9005094210
remoto       67abde48fd4a74de5bcff22bf592bd9005094210
ahead 6 / behind 0            seis commits documentais reconciliados
worktree                      somente este relatório modificado
```

Negativos finais:

```text
tags remotas                 1   (v0.1.0, a release contratada)
GitHub Releases              1   (v0.1.0)
runs publish-release.yml     5   (4 exploratórios falhos + 1 bem-sucedido)
runs deploy-production.yml   0
runs rollback-production.yml 0
```

Nenhum deploy, rollback, SSH, VPS, GHCR manual ou produção. Nenhum PEM, JWT,
token, code, state, senha, pepper ou idempotency key foi impresso.
`ops/env/.env.production` não foi aberto. O tracker e os documentos de
autoridade não foram alterados por este executor. A S30b não foi aceita e
nenhuma próxima slice foi criada. Este relatório permanece local, modificado,
não staged e não commitado.

IN_PROGRESS — v0.1.0 publicada e S30b encerrada operacionalmente; aguardando aceite e fechamento documental pelo orquestrador

## Revisão terminal do orquestrador — S30b aceita

> **Data:** 03/08/2026
> **Resultado:** `ACCEPTED`
> **Próxima slice:** `S37 — inventário read-only da VPS e plano de preparação de produção`

O orquestrador revalidou o estado local, o remoto e as evidências materiais. O
Git remoto está em `67abde48fd4a74de5bcff22bf592bd9005094210`; os seis commits
documentais foram preservados linearmente no checkout, o stage está vazio e
somente este relatório foi modificado. A limpeza deixou livres as portas
`8080`, `8084` e `8090`; apenas o PostgreSQL preexistente `baronesa-postgres`
permanece ativo e saudável na porta `5434`.

O run `30804834574` concluiu `trust`, `prepare`, `publish` e `outcome` com
sucesso. A GitHub Release `364130074` é `v0.1.0`, não draft, não prerelease,
com tag imutável apontando para
`38385c100ab8b0ae07099b6a5a7b016b7c2b7322`. Os três assets estão
`uploaded`; os sidecars, o vínculo com o candidate run `30803878927` e o BOM
dos seis componentes conferem byte a byte. O Publish Candidate
`30806848165`, produzido depois do reparo #10 em `67abde4`, também terminou
verde sem alterar ou substituir `v0.1.0`.

O caminho real ultrapassou a cardinalidade e o SHA fixados na
authorization-01 porque dez defeitos causais só apareceram diante das APIs
reais. As cinco tentativas, os commits corretivos, a autorização humana
ampliada e os desvios foram preservados no relatório. Não se declara que o
contrato original foi cumprido literalmente.

A exigência de a operação `pub_ee1405cf87bc4e6b912eb13a4fb72a2d` terminar em
`PUBLISHED` fica `SUPERSEDED`: ela permanece `FAILED/SENT/WORKFLOW_RUN_INVALID`
sem edição retroativa, enquanto o snapshot canônico da release está
`PUBLISHED`. O reparo #10 foi publicado e coberto; o restart controlado provou
readiness, sync sem drift, nenhuma operação nova e nenhum redispatch. Essa
discrepância permanece dívida histórica de auditoria, não bloqueio do MVP.

As duas falhas preexistentes do domínio deployer — rate limiter de rollback e
`ck_rc_deployment_workflow_binding` — não invalidam a publicação, mas devem ser
classificadas antes do primeiro deploy. Nenhum workflow de deploy ou rollback,
SSH, VPS ou mutação de produção ocorreu.

S30b está aceita pelo resultado operacional real, com evidência remota,
imutabilidade da release, idempotência observada, restart e limpeza. A
continuidade passa para S37 exclusivamente em modo read-only, antes de qualquer
preparação material da VPS.

ACCEPTED — v0.1.0 publicada; S30b encerrada e S37 aberta
