# S30a — autorização 02: push único e fechamento remoto

> **Estado:** `AUTHORIZED` pelo orquestrador em 02/08/2026
> **Base remota esperada:** `0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06`
> **Branch e remote:** `main` e `origin`
> **Relatório:** `S30a-paridade-local-fechamento-ci-candidato.authorization-02.report.md`

## 1. Resultado esperado

Publicar por fast-forward o checkpoint documental que contém a cadeia local
aceita, acompanhar a CI e o Publish Candidate disparados para o SHA publicado e
validar o candidato final. Esta é a retomada terminal da S30a; não é uma nova
slice e não reabre as verificações locais já aceitas de S31, S32 e S33.

O resultado positivo exige, para o mesmo SHA:

- `origin/main` atualizado por um único push não forçado;
- workflow `CI` concluído com `success`;
- workflow `Publish Candidate` concluído com `success`;
- artefatos `candidate-manifest` e `candidate-outcome` íntegros e vinculados ao
  SHA e ao run exatos;
- outcome com estado `published` e manifesto válido pelo contrato versionado.

## 2. Preflight fechado

Antes de qualquer mutação remota, executar e registrar CWD, branch, `HEAD`,
mensagem do `HEAD`, `origin/main`, contagem local, stage e worktree. Exigir:

```text
CWD = /home/gregorio/git/baronesa/emporio
branch = main
mensagem de HEAD = docs: accept S33 and authorize S30a remote closure
origin/main = 0bd563b7bb44ffcf2d2f1d705a5bbafe7a356f06
commits em origin/main..HEAD = 15
stage = vazio
worktree = limpo
git diff --check origin/main..HEAD = exit 0
```

Confirmar com `git ls-remote origin refs/heads/main` que o remoto ainda aponta
para a base esperada, que `origin/main` é ancestral de `HEAD`, que a autenticação
GitHub está ativa e que não há execução `queued` ou `in_progress` de `CI` ou
`Publish Candidate` para `main`. Não registrar token, header ou credencial.

Executar uma vez, ainda antes do push:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -v
python3 tools/ci/secret_scan.py --tracked
```

Ambos devem retornar exit 0 e o secret scan deve registrar `unsupported=0`.
Não repetir Maven, npm, builds Docker ou Trivy locais: essas provas pertencem
aos commits já aceitos.

Qualquer divergência no preflight exige parada sem push e sem tentativa de
reparo.

## 3. Única mutação remota autorizada

Registrar o SHA completo de `HEAD` como `TARGET_SHA` e executar exatamente um
push não forçado de `main` para `origin/main`:

```text
git push origin main:main
```

Não usar force, tag, outro remote, outra branch, segundo push, rebase, merge,
pull, amend ou novo commit. Se o push for rejeitado ou `origin/main` tiver
mudado, parar; não reconciliar por iniciativa própria.

Após exit 0, exigir que `git ls-remote origin refs/heads/main` seja exatamente
`TARGET_SHA`. A partir desse ponto, nenhuma falha autoriza rollback do branch,
reexecução de workflow ou correção direta.

## 4. Observação causal dos workflows

Localizar pelo `headSha = TARGET_SHA` a única execução de `CI` disparada pelo
push. Aguardar sua conclusão sem usar `rerun`, `workflow dispatch`, cancelamento
ou aprovação manual. Registrar run id, URL, attempt, conclusão e a conclusão de
cada job.

Se a CI não concluir em `success`, capturar o primeiro job/step causal e parar.
Não aguardar um candidato como se pudesse compensar uma CI reprovada.

Com a CI aceita, localizar a única execução `Publish Candidate` cujo
`headSha = TARGET_SHA` e cujo evento é `workflow_run`. Exigir vínculo ao run da
CI e aguardar a conclusão, novamente sem retry ou intervenção. Registrar os
jobs `trust`, `predecessor`, matriz `build`, `assemble`, `integrated` e
`publish`, distinguindo `success` de `skipped` conforme o plano efetivo.

Se um workflow não aparecer em até cinco minutos, se houver mais de uma
execução elegível ou se qualquer job obrigatório falhar, parar com a evidência
causal. Não alterar código, workflow, pacote, imagem ou configuração durante a
observação.

## 5. Validação do candidato publicado

Somente após `Publish Candidate = success`, criar diretório temporário com
`mktemp -d`, baixar daquele run exato os artefatos `candidate-manifest` e
`candidate-outcome` e validar:

- sidecars SHA-256 dos dois bundles;
- `python3 tools/releases/candidate_manifest.py validate --manifest
  <candidate-manifest>/candidate.json` com exit 0 e `candidate:valid`;
- `tools/candidates/outcome.py::validate` sem erros;
- `commitSha = TARGET_SHA` no manifesto e no outcome;
- `workflowRunId` e `workflowAttempt` iguais ao run observado;
- `status = published` no outcome;
- `candidateId`, id e digest do artefato vinculados ao `candidate-manifest`
  retornado pela API do mesmo run;
- seis componentes canônicos no manifesto, com partição built/inherited
  coerente com `resolution`, checks `passed`, referências imutáveis e
  integração `passed`.

Remover apenas o diretório temporário nominal ao final. Não fazer login ou pull
do GHCR: o workflow já prova push, digest, attestation, pull integrado e
cleanup.

## 6. Paradas e negativos

Esta autorização não permite:

- qualquer edição técnica ou documental antes do push;
- novo commit ou segundo push depois dele;
- reexecução, dispatch, cancelamento ou alteração de workflow;
- push manual de imagem, tag, release, deploy, rollback, SSH, VPS ou produção;
- aceitar a S30a ou abrir a S30b por iniciativa do executor;
- ocultar falha, substituir run ou misturar evidência de SHAs distintos.

Falha antes do push preserva integralmente o remoto. Falha depois do push
preserva o SHA publicado e deve ser reportada sem tentativa corretiva.

## 7. Relatório de execução

Depois da parada, criar somente
`docs/infrastructure/deployment/implementation/slices/S30a-paridade-local-fechamento-ci-candidato.authorization-02.report.md`.
O arquivo fica local e não commitado para revisão do orquestrador; não executar
segundo push para publicá-lo.

Registrar de forma compacta:

- preflight, `TARGET_SHA` e saída do push;
- ids, URLs, attempts, conclusões e jobs dos dois workflows;
- identidade, digest, validações e campos vinculantes dos dois artefatos;
- primeira causa de falha, se houver;
- proibições preservadas e estado final de Git.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — aguardando aceite remoto da S30a pelo orquestrador
```

Em falha, terminar com `IN_PROGRESS —` seguido da causa objetiva e do ponto de
parada. O relatório não aceita a S30a.
