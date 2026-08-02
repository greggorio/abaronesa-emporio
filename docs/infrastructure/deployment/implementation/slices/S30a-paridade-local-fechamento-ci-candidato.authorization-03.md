# S30a — autorização 03: retomada causal terminal sem atestação

> **Estado:** `AUTHORIZED` pelo orquestrador em 02/08/2026
> **Base remota esperada:** `0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb`
> **Branch e remote:** `main` e `origin`
> **Relatório:** `S30a-paridade-local-fechamento-ci-candidato.authorization-03.report.md`

## 1. Resultado esperado

Publicar por fast-forward a S34 aceita e observar, sem intervenção, a CI e o
Publish Candidate associados ao novo SHA. Esta autorização substitui somente a
retomada remota que falhou por atestação indisponível; não repete a investigação
de S31–S34 e não altera código.

O resultado positivo exige, para o mesmo `TARGET_SHA`:

- `CI = success`, com os 13 jobs verdes;
- `Publish Candidate = success`;
- resolução `first`, com os seis componentes em `buildComponents` e nenhum em
  `inheritedComponents`;
- `candidate-manifest` e `candidate-outcome` válidos, íntegros e vinculados ao
  SHA, run e attempt observados;
- seis imagens identificadas por `imageRepository@sha256:digest`, sem campo de
  provenance ou attestation.

## 2. Preflight fechado

Antes de qualquer mutação remota, registrar CWD, branch, `HEAD`, mensagem do
`HEAD`, `origin/main`, contagem local, stage e worktree. Exigir:

```text
CWD = /home/gregorio/git/baronesa/emporio
branch = main
mensagem de HEAD = docs: accept S34 and authorize S30a terminal closure
origin/main = 0d6f11f826f5a538f9a008bc4a3326c1d4fd09fb
commits em origin/main..HEAD = 4
stage = vazio
worktree = limpo
git diff --check origin/main..HEAD = exit 0
```

Confirmar que:

- `git ls-remote origin refs/heads/main` ainda é a base remota esperada;
- `origin/main` é ancestral de `HEAD`;
- a autenticação GitHub está ativa;
- não há run `queued` ou `in_progress` de `CI` ou `Publish Candidate` em
  `main`.

Executar uma vez:

```text
python3 tools/ci/invocability.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/releases/candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json
python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json
python3 tools/ci/secret_scan.py --tracked
```

Todos devem retornar exit 0; o secret scan deve registrar `unsupported=0`.
Não repetir testes, Maven, npm, Docker ou Trivy locais. Qualquer divergência
exige parada sem push e sem correção por iniciativa própria.

## 3. Única mutação remota

Registrar o SHA completo de `HEAD` como `TARGET_SHA` e executar exatamente:

```text
git push origin main:main
```

O push deve ser único, não forçado e fast-forward. Não usar rebase, merge,
pull, amend, tag, outro remote, outra branch ou segundo push. Depois do exit 0,
`git ls-remote origin refs/heads/main` deve ser exatamente `TARGET_SHA`.

Uma falha posterior preserva o SHA publicado: não fazer rollback, retry,
rerun, dispatch ou commit corretivo nesta autorização.

## 4. CI e Publish Candidate

Localizar pela API o único run `CI`, evento `push`, com
`headSha = TARGET_SHA`; aguardar a conclusão sem intervenção e registrar id,
URL, attempt e todos os jobs. Exigir `success` e 13 jobs verdes.

Depois localizar o único `Publish Candidate`, evento `workflow_run`, para o
mesmo SHA e vinculado à CI observada. Aguardar sem intervenção e exigir:

- jobs `trust` e `predecessor` em `success`;
- seis jobs da matriz `build` em `success`;
- jobs `assemble`, `integrated` e `publish` em `success`;
- nenhuma permissão, action ou etapa de attestation;
- upload dos seis resultados depois de push e inspeção remota;
- attempt 1 e uma única execução elegível.

Se o run não aparecer em até cinco minutos, houver duplicidade ou qualquer job
obrigatório falhar, registrar a primeira causa e parar. Não substituir o run.

## 5. Validação dos artefatos

Somente após `Publish Candidate = success`, criar um diretório com `mktemp -d`
e baixar do run exato:

```text
candidate-effective-plan
candidate-manifest
candidate-outcome
```

Validar os sidecars e exigir:

- `candidate_manifest.py validate` com exit 0 e `candidate:valid`;
- `tools/candidates/outcome.py::validate` sem erros;
- `commitSha = TARGET_SHA` no plano, manifesto e outcome;
- `sourceCi.runId` e `sourceCi.attempt` iguais à CI que disparou o candidato;
- `manifesto.workflow.runId/attempt` e `outcome.workflowRunId/workflowAttempt`
  iguais ao run `Publish Candidate` observado;
- `predecessor.status = first`;
- resolução com `backend`, `website_back`, `frontend`, `website_front`,
  `whatsapp_service` e `gateway` em `buildComponents` e
  `inheritedComponents = []`;
- manifesto com os mesmos seis componentes, todos `state = built`, checks
  `build`, `test` e `scan` em `passed`, integração `passed` e referências
  imutáveis exatamente iguais a `imageRepository + "@" + digest`;
- ausência de `provenance`, `attestationId`, `attestationUrl`,
  `verifiedSubject` e `verifiedAt` em todos os três artefatos;
- outcome `published`, com `candidateId`, id e digest do artefato iguais aos do
  `candidate-manifest` retornado pela API do mesmo run.

Remover somente o diretório temporário nominal. Não fazer login, pull ou push
manual no GHCR e não apagar as imagens deixadas pelos runs anteriores.

## 6. Limites e parada

Esta autorização não permite edição antes do push, alteração de workflow,
novo commit, segundo push, publicação de release global, deploy, rollback, SSH,
VPS ou produção. O executor não aceita a S30a e não abre a S30b.

Falha antes do push preserva o remoto. Falha depois do push preserva o
`TARGET_SHA` publicado. Em ambos os casos, parar na primeira causa comprovada.

## 7. Relatório

Depois da parada, criar somente
`docs/infrastructure/deployment/implementation/slices/S30a-paridade-local-fechamento-ci-candidato.authorization-03.report.md`.
O arquivo fica local, não rastreado e não commitado; não há segundo push para
publicá-lo.

Registrar de forma compacta:

- preflight, `TARGET_SHA` e saída do push;
- ids, URLs, attempts, conclusões e jobs dos dois runs;
- plano efetivo, identidade dos artefatos e validações vinculantes;
- primeira causa e ponto de parada, se houver;
- negativos preservados e estado Git final.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — aguardando aceite remoto da S30a pelo orquestrador
```

Em falha, terminar com `IN_PROGRESS —` seguido da causa objetiva e do ponto de
parada. O relatório não aceita a S30a.
