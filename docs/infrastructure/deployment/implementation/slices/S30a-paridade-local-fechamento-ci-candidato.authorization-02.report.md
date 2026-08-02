# S30a — authorization-02: relatório de execução do fechamento remoto

> **Estado:** push executado; CI aprovada; `Publish Candidate` reprovado
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** `S30a-paridade-local-fechamento-ci-candidato.authorization-02.md`
> **TARGET_SHA:** `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb`
> **Arquivo local, não commitado.** Nenhum segundo push foi executado.

## 1. Preflight da §2

| Item | Exigido | Observado | OK |
|---|---|---|:--:|
| CWD | `/home/gregorio/git/baronesa/emporio` | idem | sim |
| branch | `main` | `main` | sim |
| mensagem de `HEAD` | `docs: accept S33 and authorize S30a remote closure` | idem | sim |
| `HEAD` | — | `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb` | — |
| `origin/main` | `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06` | idem | sim |
| commits em `origin/main..HEAD` | 15 | 15 | sim |
| stage | vazio | vazio | sim |
| worktree | limpo | limpo | sim |
| `git diff --check origin/main..HEAD` | exit 0 | exit 0 | sim |

Confirmações adicionais, antes de qualquer mutação remota:

```text
git ls-remote origin refs/heads/main
0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06	refs/heads/main

git merge-base --is-ancestor origin/main HEAD   -> exit 0
```

`origin/main` era ancestral de `HEAD`, portanto o push seria fast-forward.
Autenticação GitHub ativa para a conta `greggorio` (`gh auth status`, exit 0);
nenhum token, header ou credencial foi registrado. Remote `origin` =
`git@github.com:greggorio/abaronesa-emporio.git`.

Nenhuma execução `queued` ou `in_progress` de `CI` ou `Publish Candidate` para
`main` no momento da verificação. As execuções imediatamente anteriores, ambas
no SHA `0bd563b`, estavam `completed/failure` — estado esperado antes da cadeia
local aceita.

Gates rápidos prescritos, executados uma vez:

| Comando | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v` | 0 | `Ran 117 tests in 1.174s`; `OK` |
| `python3 tools/ci/secret_scan.py --tracked` | 0 | `secret-scan:clean:scanned=2455:allowed=320:unsupported=0:history_scanned=46395` |

`unsupported=0`. Maven, npm, builds Docker e Trivy locais **não** foram
reabertos, conforme a §2.

## 2. Única mutação remota — push

`TARGET_SHA = 0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb`

```text
git push origin main:main
To github.com:greggorio/abaronesa-emporio.git
   0bd563b..0d6f11f  main -> main
```

Exit 0. Push único, não forçado, fast-forward. Sem force, tag, outro remote,
outra branch, rebase, merge, pull, amend ou novo commit.

Confirmação imediata:

```text
git ls-remote origin refs/heads/main
0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb	refs/heads/main
```

O remoto passou a ser exatamente `TARGET_SHA`.

## 3. Observação causal dos workflows

Exatamente **duas** execuções com `headSha = TARGET_SHA`, ambas em `attempt=1`.
Nenhum `rerun`, `workflow dispatch`, cancelamento ou aprovação manual foi
usado em nenhum momento.

### 3.1 CI — `success`

| Campo | Valor |
|---|---|
| run id | `30742017194` |
| URL | `https://github.com/greggorio/abaronesa-emporio/actions/runs/30742017194` |
| evento | `push` |
| attempt | 1 |
| headSha | `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb` |
| conclusão | **`success`** |
| janela | `2026-08-02T09:33:55Z` → `2026-08-02T09:41:30Z` |

Conclusão de cada job:

| Job | Conclusão |
|---|---|
| `plan` | success |
| `backend` | success |
| `website_back` | success |
| `frontend` | success |
| `website_front` | success |
| `whatsapp` | success |
| `contracts` | success |
| `images (backend, backend, backend/Dockerfile)` | success |
| `images (website_back, website_back, website_back/Dockerfile)` | success |
| `images (frontend, frontend, frontend/Dockerfile)` | success |
| `images (website_front, website_front, website_front/Dockerfile)` | success |
| `images (whatsapp_service, whatsapp_service, whatsapp_service/Dockerfile)` | success |
| `images (gateway, ops/gateway, ops/gateway/Dockerfile)` | success |

Os treze jobs concluíram em `success`. Em particular, a matriz `images` — que
antes da cadeia local reprovava em `backend` e `website_back` por achados
Trivy — passou nos seis componentes. A CI confirma remotamente o fechamento dos
grupos A, B e C provado localmente por S31, S32 e S33.

### 3.2 Publish Candidate — `failure`

| Campo | Valor |
|---|---|
| run id | `30742264661` |
| URL | `https://github.com/greggorio/abaronesa-emporio/actions/runs/30742264661` |
| evento | `workflow_run` |
| workflow | `.github/workflows/publish-candidate.yml` |
| attempt | 1 |
| headSha | `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb` |
| head_branch | `main` |
| conclusão | **`failure`** |
| janela | `2026-08-02T09:41:32Z` → `2026-08-02T09:45:32Z` |

Execução única e elegível: a consulta por `headSha = TARGET_SHA` e
`name = "Publish Candidate"` retornou exatamente 1. Foi disparada por
`workflow_run` imediatamente após a conclusão da CI, às `09:41:32Z`, um segundo
após o término da CI às `09:41:30Z`.

Conclusão de cada job:

| Job | Conclusão |
|---|---|
| `trust` | success |
| `predecessor` | success |
| `build (backend, …, ghcr.io/greggorio/abaronesa-emporio-backend)` | **failure** |
| `build (website_back, …)` | **failure** |
| `build (frontend, …)` | **failure** |
| `build (website_front, …)` | **failure** |
| `build (whatsapp_service, …)` | **failure** |
| `build (gateway, ops/gateway, …)` | **failure** |
| `assemble` | skipped |
| `integrated` | skipped |
| `publish` | **failure** |

## 4. Primeira causa de falha

O step causal é idêntico nos **seis** jobs `build`: o passo 8,
`Attest exact immutable subject`.

Sequência literal de steps no job `build (backend, …)`, id `91481710713`:

| # | Step | Conclusão |
|---:|---|---|
| 4 | `Build once and load exact local tag` | success |
| 5 | `Scan exact local image` | success |
| 6 | `Authenticate only after scan` | success |
| 7 | `Push once and inspect remote registry manifest` | success |
| 8 | `Attest exact immutable subject` | **failure** |
| 9 | `Verify attestation then finalize result` | skipped |
| 10 | `actions/upload-artifact` | skipped |
| 11 | `Logout remove exact tag and prove absence` | success |

Erro literal:

```text
##[error]Error: Failed to persist attestation: Feature not available for user-owned private repositories. To enable this feature, please make this repository public. - https://docs.github.com/rest/repos/attestations#create-an-attestation
```

A mesma mensagem foi confirmada nos seis jobs `build`.

Corroboração pela API do próprio repositório:

```text
private=true | owner_type=User | visibility=private
```

A causa é uma **limitação de plataforma do GitHub**, não do código, do
Dockerfile, do POM, do scanner ou de qualquer alteração das slices S31, S32 ou
S33. A API de attestations não está disponível para repositórios privados de
propriedade de usuário. Antes do passo 8, o build, o scan Trivy, a autenticação
no GHCR e o push da imagem concluíram com sucesso em todos os componentes.

Encadeamento até o `publish`:

1. o passo 8 falha nos seis `build`;
2. os passos 9 e 10 são `skipped`, de modo que os artefatos de resultado por
   componente nunca são produzidos;
3. `assemble` e `integrated` ficam `skipped`;
4. `publish` falha no step 5, um `actions/download-artifact`, por ausência do
   artefato que os `build` deveriam ter enviado.

Steps do job `publish`, id `91481994791`: steps 1–4 `success`, step 5
`download-artifact` **failure**, steps 6 a 16 `skipped` — incluindo
`Finalize exact candidate bundle`, `Upload final candidate`,
`Create published outcome`, `Upload canonical outcome` e
`Record candidate artifact identity`.

## 5. Validação do candidato — não aplicável

A §5 condiciona a validação a `Publish Candidate = success`. Como o workflow
concluiu em `failure`, **nenhuma validação de candidato foi executada**. Não foi
criado diretório temporário, não houve download de artefatos, nem execução de
`candidate_manifest.py validate` ou de `outcome.py::validate`.

Artefatos efetivamente presentes no run `30742264661`:

```text
greggorio~abaronesa-emporio~69YX8E.dockerbuild | id=8831714385 | 192668 bytes
greggorio~abaronesa-emporio~GSEJ9J.dockerbuild | id=8831712663 | 194182 bytes
greggorio~abaronesa-emporio~HFUOG6.dockerbuild | id=8831711718 |  44587 bytes
greggorio~abaronesa-emporio~2M5DQ6.dockerbuild | id=8831706757 |  55887 bytes
greggorio~abaronesa-emporio~W86SVN.dockerbuild | id=8831697965 |  55412 bytes
greggorio~abaronesa-emporio~O31ZL5.dockerbuild | id=8831687115 |  39167 bytes
candidate-predecessor-context                  | id=8831678829 |    225 bytes
candidate-effective-plan                       | id=8831678719 |    728 bytes
```

**`candidate-manifest` e `candidate-outcome` não existem** neste run. Não há
outcome `published`, manifesto, sidecar SHA-256, `candidateId` ou digest a
validar. Nenhuma evidência de outro SHA foi usada em substituição.

## 6. Observação factual sobre o GHCR

O passo 7 de cada `build`, `Push once and inspect remote registry manifest`,
concluiu em `success` antes da falha do passo 8. Portanto o próprio workflow
publicou imagens no GHCR para este SHA, como parte do seu contrato. O passo 11,
`Logout remove exact tag and prove absence`, também concluiu em `success` e
remove a tag **local** do runner; as referências remotas permanecem conforme o
workflow as deixou.

O executor não fez `docker login`, `docker pull`, `docker push` manual, nem
qualquer interação direta com o GHCR. Este parágrafo é registro de estado, não
ação.

## 7. Proibições preservadas

- exatamente **um** push, não forçado, fast-forward;
- **nenhum** retry, segundo push, rerun, workflow dispatch, cancelamento,
  aprovação manual ou intervenção após o push;
- nenhuma edição técnica ou documental antes do push;
- nenhum novo commit depois dele;
- nenhuma alteração de código, workflow, pacote, imagem ou configuração durante
  a observação;
- nenhum push manual de imagem, tag, release, deploy, rollback, SSH, VPS ou
  produção;
- Maven, npm, builds Docker e Trivy locais não foram reabertos;
- a S30a **não** foi aceita e a S30b **não** foi aberta;
- nenhuma falha foi ocultada, nenhum run substituído e nenhuma evidência de
  SHAs distintos foi misturada;
- este relatório permanece local e não commitado.

## 8. Estado final do Git

```text
HEAD                                     = 0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb
origin/main (local ref)                  = 0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb
git ls-remote origin refs/heads/main     = 0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb
commits em origin/main..HEAD             = 0
branch                                   = main
mensagem de HEAD                         = docs: accept S33 and authorize S30a remote closure
stage                                    = vazio
worktree                                 = limpo, exceto este relatório não rastreado
```

O SHA publicado está preservado. Nenhum rollback de branch foi tentado.

IN_PROGRESS — falha do `Publish Candidate` no run 30742264661: os seis jobs `build` reprovaram no step `Attest exact immutable subject` com `Failed to persist attestation: Feature not available for user-owned private repositories`, o que deixou `assemble` e `integrated` em `skipped` e fez o job `publish` falhar no `download-artifact`; parada após a observação causal, sem retry, sem correção e sem validação de candidato, que exige `Publish Candidate = success`

## 9. Revisão do orquestrador

> **Data:** 02/08/2026
> **Evidência da execução:** `ACCEPTED`
> **Resultado terminal da S30a:** não atingido

Os estados dos runs, jobs, steps e artefatos foram reconciliados pela API. A CI
está aceita; a falha do candidato é causal e reproduzida nos seis builds. A
parada respeitou a authorization-02 e não houve tentativa corretiva.

O usuário rejeitou tornar público o repositório do produto e escolheu remover
a exigência de atestação. A S34 implementará essa decisão de ponta a ponta,
preservando a pinagem imutável por digest. A S30a permanece `IN_PROGRESS`.
