# S30 — correction-02: contracts e trust do candidato

> **Estado:** AUTHORIZED pelo orquestrador em 01/08/2026
> **Commit-base:** 41ab410d757154131ce6a2344fd8e561152d2acd
> **Remote:** git@github.com:greggorio/abaronesa-emporio.git
> **Branch:** main

## 1. Motivo

A correction-01 fechou A, B, C e D localmente. Remotamente, plan e backend
passaram, mas contracts manteve um erro e Publish Candidate falhou no trust.

E é o teste test_07_finalizer_metadata_sidecar_plan_predecessor: o fixture
não isola GITHUB_RUN_ID/GITHUB_RUN_ATTEMPT do ambiente Actions, fazendo
finalize_candidate validar contra o run real em vez do run da fixture.

F é a ordem do job trust: o workflow grava workflow-run.json, depois
actions/checkout com clean=true apaga o arquivo, e o passo trust.py falha com
No such file or directory.

Nenhum candidato final, imagem GHCR, release, tag, provenance ou attestation
foi criado.

## 2. Fronteira autorizada

Alterar ou criar somente:

- .github/workflows/publish-candidate.yml;
- tools/candidates/validate_candidate_workflow.py;
- tools/candidates/tests/test_definitive_contract.py;
- tools/releases/tests/test_candidate_manifest_v2.py;
- o relatório S30.

Não alterar candidate_manifest.py, finalize_candidate.py,
validate_pending.py, backend, frontend, release_control, ci.yml, outros
workflows, OpenAPI, schemas, .gitignore, correction-01, HANDOFF, tracker,
produção ou S31.

## 3. Correções fechadas

### E — isolar ambiente da fixture

Atualizar somente test_candidate_manifest_v2.py para que
test_07_finalizer_metadata_sidecar_plan_predecessor configure, dentro do teste,
GITHUB_RUN_ID e GITHUB_RUN_ATTEMPT exatamente com os valores da fixture antes
de chamar finalize_candidate.finalize().

A produção continua usando os IDs reais do workflow. O teste deve passar mesmo
quando o processo é executado dentro de Actions com IDs diferentes. Deve haver
prova causal de que a fixture não depende do ambiente externo.

### F — persistência segura do evento

Reordenar somente o job trust de publish-candidate.yml para:

1. checkout do SHA recebido;
2. persistência de workflow-run.json via RUN_JSON, sem interpolação insegura;
3. download do artifact candidate-plan;
4. execução de trust.py usando o arquivo persistido.

Não usar clean=false como atalho. Não remover persistência, validação de
evento, checkout, download ou binding de SHA.

Atualizar validate_candidate_workflow.py para exigir essa ordem no job trust e
adicionar teste causal em test_definitive_contract.py que rejeite a ordem
antiga ou a remoção de qualquer etapa.

## 4. Validação

Executar:

~~~
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/ci/validate_ci.py
python3 tools/releases/validate_release_workflow.py
python3 tools/releases/validate_publisher_ui.py
python3 tools/releases/validate_publisher_identity_bridge.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/candidates/tests -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/ci/tests -v
python3 tools/ci/secret_scan.py --tracked
git diff --check
~~~

A suíte release deve passar sem erro E; a suíte candidate e o validador devem
provar F. O scanner deve permanecer clean/unsupported=0.

## 5. Commit, push e nova observação

Após os gates locais, revisar somente os cinco caminhos autorizados e executar:

~~~
git add .github/workflows/publish-candidate.yml \
  tools/candidates/validate_candidate_workflow.py \
  tools/candidates/tests/test_definitive_contract.py \
  tools/releases/tests/test_candidate_manifest_v2.py \
  docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md
git diff --cached --check
git commit -m "fix: close candidate trust gates"
git push origin main
~~~

Não usar force, tags, outra branch/remote, no-verify, git init ou alteração de
identidade.

Observar a nova CI e o novo Publish Candidate. Exigir CI verde e todos os jobs
trust, predecessor, build, assemble, integrated e publish verdes, com
candidate-manifest e candidate-outcome, manifestos, digests, provenance e
attestations coerentes. Se read:packages continuar ausente, registrar a
limitação e não criar credenciais.

Não executar publish-release.yml, não publicar release, não fazer deploy,
rollback, SSH, VPS, Docker de produção, cleanup destrutivo ou S31.

## 6. Relatório

Atualizar somente o relatório S30 com a execução, testes, SHA corretivo, runs,
artefatos e divergências sanitizadas. Terminar com:

~~~
IN_PROGRESS — aguardando revisão do orquestrador
~~~

## 7. Prompt formal

~~~
Execute exclusivamente a correction-02 da S30 em
/home/gregorio/git/baronesa/emporio.

Leia a task S30, emenda-01, correction-01, esta correction, HANDOFF, tracker e
relatórios S11–S30.

Corrija somente E e F. Isole GITHUB_RUN_ID/GITHUB_RUN_ATTEMPT no teste
test_07_finalizer_metadata_sidecar_plan_predecessor sem alterar a produção.
Reordene o trust de publish-candidate para checkout, persistência do
workflow-run.json, download do candidate-plan e trust.py. Faça o validador e o
teste causal rejeitarem a ordem antiga.

Execute validadores, suítes, scanner e diff check. Se tudo passar, faça:

git add .github/workflows/publish-candidate.yml \
  tools/candidates/validate_candidate_workflow.py \
  tools/candidates/tests/test_definitive_contract.py \
  tools/releases/tests/test_candidate_manifest_v2.py \
  docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md
git diff --cached --check
git commit -m "fix: close candidate trust gates"
git push origin main

Observe CI e Publish Candidate. Não publique release, não use force/tags/outro
remote/branch/no-verify, não crie credenciais, não faça deploy/rollback/
produção e não crie S31.

Termine com:
IN_PROGRESS — aguardando revisão do orquestrador
~~~

