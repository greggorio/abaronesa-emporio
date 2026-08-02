# S30a — authorization-03: relatório de execução da retomada terminal

> **Estado:** push executado; CI aprovada; `Publish Candidate` reprovado no job `integrated`
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Autoridade:** `S30a-paridade-local-fechamento-ci-candidato.authorization-03.md`
> **TARGET_SHA:** `cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af`
> **Arquivo local, não rastreado e não commitado.** Nenhum segundo push.

## 1. Preflight da §2

| Item | Exigido | Observado | OK |
|---|---|---|:--:|
| CWD | `/home/gregorio/git/baronesa/emporio` | idem | sim |
| branch | `main` | `main` | sim |
| mensagem de `HEAD` | `docs: accept S34 and authorize S30a terminal closure` | idem | sim |
| `HEAD` | — | `cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af` | — |
| `origin/main` | `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb` | idem | sim |
| commits em `origin/main..HEAD` | 4 | 4 | sim |
| stage | vazio | vazio | sim |
| worktree | limpo | limpo | sim |
| `git diff --check origin/main..HEAD` | exit 0 | exit 0 | sim |

Confirmações adicionais antes da mutação remota:

```text
git ls-remote origin refs/heads/main
0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb	refs/heads/main

git merge-base --is-ancestor origin/main HEAD   -> exit 0
```

Autenticação GitHub ativa para a conta `greggorio`; nenhum token, header ou
credencial registrado. Nenhum run `queued` ou `in_progress` de `CI` ou
`Publish Candidate` em `main` no momento da verificação.

Cinco validadores, executados uma vez, todos exit 0:

| Comando | Saída |
|---|---|
| `python3 tools/ci/invocability.py` | `invocability:valid:commands=26:parse_args=23:argument-free=3` |
| `python3 tools/candidates/validate_candidate_workflow.py` | `candidate-workflow:valid` |
| `python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json` | `candidate:valid` |
| `python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json` | `global-release:valid` |
| `python3 tools/ci/secret_scan.py --tracked` | `secret-scan:clean:scanned=2460:allowed=384:unsupported=0:history_scanned=56229` |

`unsupported=0`. Nenhum teste, Maven, npm, Docker ou Trivy local foi repetido.
Nenhum arquivo foi alterado antes do push.

## 2. Única mutação remota — push

`TARGET_SHA = cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af`

```text
git push origin main:main
To github.com:greggorio/abaronesa-emporio.git
   0d6f11f..cf02e2a  main -> main
```

Exit 0. Push único, não forçado, fast-forward. Sem rebase, merge, pull, amend,
tag, outro remote, outra branch ou segundo push.

Confirmação imediata:

```text
git ls-remote origin refs/heads/main
cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af	refs/heads/main
```

## 3. CI — `success`

| Campo | Valor |
|---|---|
| run id | `30746044072` |
| URL | `https://github.com/greggorio/abaronesa-emporio/actions/runs/30746044072` |
| evento | `push` |
| attempt | 1 |
| headSha | `cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af` |
| conclusão | **`success`** |
| janela | `2026-08-02T11:35:06Z` → `2026-08-02T11:40:16Z` |

Execução única elegível para o SHA. Treze jobs, todos `success`:

```text
plan, backend, website_back, frontend, website_front, whatsapp, contracts,
images (backend), images (website_back), images (frontend),
images (website_front), images (whatsapp_service), images (gateway)
```

Nenhum `rerun`, `dispatch`, cancelamento ou aprovação manual foi usado.

## 4. Publish Candidate — `failure`

| Campo | Valor |
|---|---|
| run id | `30746220083` |
| URL | `https://github.com/greggorio/abaronesa-emporio/actions/runs/30746220083` |
| evento | `workflow_run` |
| attempt | 1 |
| headSha | `cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af` |
| conclusão | **`failure`** |
| janela | `2026-08-02T11:40:18Z` → `2026-08-02T11:47:12Z` |

Execução única elegível, disparada por `workflow_run` dois segundos após o
término da CI. Conclusão por job:

| Job | Conclusão |
|---|---|
| `trust` | success |
| `predecessor` | success |
| `build (backend, …)` | success |
| `build (website_back, …)` | success |
| `build (frontend, …)` | success |
| `build (website_front, …)` | success |
| `build (whatsapp_service, …)` | success |
| `build (gateway, ops/gateway, …)` | success |
| `assemble` | success |
| `integrated` | **failure** |
| `publish` | **failure** |

### 4.1 O que a authorization-03 exigia e foi cumprido

- `trust` e `predecessor` em `success`: **sim**;
- seis jobs da matriz `build` em `success`: **sim**, um por componente
  (`backend`, `website_back`, `frontend`, `website_front`, `whatsapp_service`,
  `gateway`), o que confirma resolução com os seis componentes construídos;
- nenhuma permissão, action ou etapa de attestation: **sim**. Sequência literal
  do job `build (backend, …)`:

```text
4 | Build once and load exact local tag                     | success
5 | Scan exact local image                                  | success
6 | Authenticate only after scan                            | success
7 | Push once and inspect remote registry manifest          | success
8 | actions/upload-artifact                                 | success
9 | Logout remove exact tag and prove absence               | success
```

  Não existe mais step de `attest-build-provenance` nem `gh attestation verify`;
  o upload do resultado ocorre **imediatamente após** o push e a inspeção
  remota, exatamente como a S34 estabeleceu;
- attempt 1 e execução única: **sim**.

O bloqueio de atestação que causou a parada da authorization-02 está
**resolvido**: naquele run os seis `build` reprovavam no step 8; aqui os seis
concluíram e produziram `candidate-component-*`.

### 4.2 Primeira causa comprovada — `integrated`

Job `integrated`, id `91492588276`. Steps 1 a 8 em `success`; falha no step 9:

| # | Step | Conclusão |
|---:|---|---|
| 6 | `Validate complete pending artifact before environment or Docker` | success |
| 7 | `Emit LF environment` | success |
| 8 | `docker/login-action` | success |
| 9 | **`Pull test probe cleanup and receipt`** | **failure** |
| 10 | `actions/upload-artifact` | skipped |

Saída literal, na ordem observada:

```text
Container candidate-30746220083-1-postgresql-1      Healthy
Container candidate-30746220083-1-frontend-1        Healthy
Container candidate-30746220083-1-whatsapp_service-1 Healthy
Container candidate-30746220083-1-website_front-1   Healthy
Container candidate-30746220083-1-website_back-1    Healthy
Container candidate-30746220083-1-backend-1         Error
dependency failed to start: container candidate-30746220083-1-backend-1 is unhealthy
integration:invalid:Command '['docker', 'compose', '-p', 'candidate-30746220083-1', '-f', 'ops/compose/docker-compose.emporio.yml', '-f', 'ops/compose/testing/compose.candidate.yml', 'up', '-d', '--no-build', '--pull', 'never', '--wait', '--wait-timeout', '600']' returned non-zero exit status 1.
##[error]Process completed with exit code 3.
```

**Cinco dos seis serviços e o PostgreSQL alcançaram `Healthy`. Somente o
`backend` não alcançou.** A imagem exercida foi:

```text
ghcr.io/greggorio/abaronesa-emporio-backend@sha256:f72b3ae2c959ade92c64c251dc23d016d47ed2f5058ceb5418ba5131a66ee6bc
```

Cronologia do container `backend`: `Started` às `11:45:58.409Z`, `Error` às
`11:46:57.412Z` — aproximadamente 59 s depois. Para comparação, o
`website_back`, também Spring Boot 3.5.16 com actuator, alcançou `Healthy` às
`11:46:30.412Z`.

O motivo em nível de aplicação **não está no log do run**: o harness de
integração não despeja os logs dos serviços quando o `compose up --wait` falha.
A busca por `Caused by`, `Exception`, `APPLICATION FAILED` ou
`Started EmporioApplication` no log completo do job retorna zero ocorrências.
Obter esse detalhe exigiria rerun, dispatch ou execução manual, todos proibidos
por esta autorização; por isso a investigação parou aqui.

### 4.3 Encadeamento até `publish`

Job `publish`, id `91492751402`: steps 1 a 5 em `success`, falha no step 6, um
`actions/download-artifact` — o artefato `candidate-integration-result`, que o
`integrated` só envia no step 10, nunca foi produzido. Steps 7 a 16 `skipped`,
incluindo `Finalize exact candidate bundle`, `Upload final candidate`,
`Create published outcome`, `Upload canonical outcome` e
`Record candidate artifact identity`.

### 4.4 Observação de contexto, verificada

Esta foi a **primeira execução real do job `integrated`** na história do
repositório:

| Run | SHA | `integrated` |
|---|---|---|
| `30686325732` | `bf20c02` | skipped |
| `30687306886` | `0bd563b` | skipped |
| `30742264661` | `0d6f11f` | skipped |
| `30746220083` | `cf02e2a` | **failure** |

Os runs anteriores morreram antes, em `trust` ou na matriz `build`. Portanto a
stack efêmera nunca havia exercido as imagens produzidas depois de S31–S34, e
este é o primeiro sinal remoto sobre o comportamento de runtime do `backend`
com o baseline Spring Boot 3.5.16 e sem a cadeia JasperReports. A CI aprova
build, testes e scan, mas não sobe a stack integrada — por isso o defeito só
aparece agora.

## 5. Validação de artefatos — não aplicável

A §5 condiciona a validação a `Publish Candidate = success`. Como o run
concluiu em `failure`, **nenhum artefato foi baixado ou validado**: não foi
criado diretório com `mktemp -d`, não houve download de
`candidate-effective-plan`, `candidate-manifest` ou `candidate-outcome`, nem
execução de `candidate_manifest.py validate` ou `outcome.py::validate`.

Artefatos efetivamente presentes no run `30746220083`:

```text
candidate-component-backend            | id=8833003669 |  536B
candidate-component-frontend           | id=8832980169 |  537B
candidate-component-gateway            | id=8832971512 |  537B
candidate-component-website_back       | id=8832988128 |  545B
candidate-component-website_front      | id=8832987931 |  547B
candidate-component-whatsapp_service   | id=8832991628 |  549B
candidate-effective-plan               | id=8832961873 |  725B
candidate-pending                      | id=8833007999 | 1877B
candidate-predecessor-context          | id=8832961988 |  225B
+ seis artefatos .dockerbuild da action de build
```

Os seis `candidate-component-*` existem, o que confirma que os seis builds
produziram resultado; `candidate-pending` existe, produzido por `assemble`.
**`candidate-integration-result`, `candidate-manifest` e `candidate-outcome`
não existem** neste run. Não há outcome `published` a validar, e nenhuma
evidência de outro SHA foi usada em substituição.

A resolução `first` com os seis componentes em `buildComponents` e nenhum
herdado é observável indiretamente pelos seis jobs da matriz `build` e pelos
seis `candidate-component-*`; a confirmação formal pelo `candidate-effective-plan`
depende do gate da §5 e não foi feita.

## 6. Negativos preservados

- exatamente **um** push, não forçado, fast-forward;
- nenhum arquivo alterado antes do push;
- nenhum segundo push, rerun, workflow dispatch, cancelamento, aprovação manual
  ou commit corretivo depois dele;
- nenhuma alteração de workflow, código ou configuração durante a observação;
- nenhuma release global, deploy, rollback, SSH, VPS ou produção;
- nenhum `docker login`, `pull` ou `push` manual no GHCR; as imagens deixadas
  por este e pelos runs anteriores permanecem intactas;
- a S30a **não** foi aceita e a S30b **não** foi aberta;
- este relatório permanece local, não rastreado e não commitado.

## 7. Estado final do Git

```text
HEAD                                  = cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af
origin/main (local ref)               = cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af
git ls-remote origin refs/heads/main  = cf02e2aa7010f16d0b02da8e9ecd54cbc273b6af
commits em origin/main..HEAD          = 0
branch                                = main
stage                                 = vazio
worktree                              = limpo, exceto este relatório não rastreado
```

O `TARGET_SHA` publicado está preservado; nenhum rollback foi tentado.

IN_PROGRESS — falha do `Publish Candidate` no run 30746220083: o job `integrated` reprovou no step `Pull test probe cleanup and receipt` porque o container `backend` da stack efêmera não alcançou `Healthy` — `dependency failed to start: container candidate-30746220083-1-backend-1 is unhealthy`, com PostgreSQL e os outros cinco serviços saudáveis —, o que deixou `candidate-integration-result` ausente e fez o job `publish` falhar no `download-artifact`; parada na primeira causa comprovada, sem retry, rerun, dispatch ou correção, e sem validação de artefatos, que exige `Publish Candidate = success`

## 8. Revisão do orquestrador

> **Data:** 02/08/2026
> **Estado:** `ACCEPTED — parada causal; S35 aberta`

A execução da authorization-03 está aceita. O push foi único e fast-forward; a
CI concluiu os 13 jobs em `success`; a S34 foi comprovada remotamente pelos seis
builds, scans, pushes e resultados de componente sem etapa de atestação. A
parada no primeiro erro do job `integrated`, sem retry ou correção remota, foi
correta.

A configuração versionada explica uma causa necessária: o banco efêmero nasce
vazio, o `backend` usa `ddl-auto=validate`, o runtime mantém Flyway desligado e
o harness não executava o passo de migração que antecede o update em produção.
O run não forneceu log da aplicação, portanto a S35 não afirma que essa seja a
única causa possível; ela fecha a precondição ausente antes de novo push.

Evidência remota aceita. A S30a permanece em progresso.
