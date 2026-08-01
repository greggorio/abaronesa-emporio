# S30 — correction-01: fechamento dos gates do primeiro CI

> **Estado:** AUTHORIZED pelo orquestrador em 31/07/2026
> **Commit-base:** b71272f4b5c313aa70cb97c8948643eda73d7bec
> **Remote:** git@github.com:greggorio/abaronesa-emporio.git
> **Branch:** main

## 1. Motivo

S30 foi rejeitada: o commit chegou corretamente a origin/main, mas a CI
falhou em plan, contracts e backend. O publish-candidate.yml falhou fechado
no trust porque o artifact candidate-plan não existia. Não houve candidato,
manifesto, digest, provenance, attestation, imagem GHCR, release, tag, deploy,
rollback ou produção.

## 2. Fronteira autorizada

Alterar ou criar somente:

- .github/workflows/ci.yml;
- frontend/.env.example;
- tools/candidates/candidate_plan.py;
- tools/candidates/tests/test_causal_corrections.py;
- tools/ci/validate_ci.py;
- tools/ci/tests/test_ci.py;
- tools/releases/validate_publisher_ui.py;
- tools/releases/tests/test_publisher_ui_contract.py;
- tools/releases/validate_publisher_identity_bridge.py;
- tools/releases/tests/test_release_publication.py;
- o relatório S30.

Não alterar S17, S29, OpenAPI, schemas, runtime, backend Java, frontend além
do .env.example, release_control, outros workflows, .gitignore, frontend/.env
local, Dockerfiles, Compose, produção ou S31.

## 3. Correções fechadas

### A — plano do commit raiz

candidate_plan.validate() deve aceitar DIFF_BASE_UNAVAILABLE_FAIL_CLOSED
somente quando baseCommitSha for exatamente 40 zeros, mantendo os demais
campos e warnings iguais ao resultado canônico de
catalog.resolve(..., first_release=True). Warning arbitrário, warning removido,
campo divergente ou uso fora do commit raiz continuam inválidos. Adicionar
testes causais para cada mutação.

### B — UI publisher no checkout CI

Criar frontend/.env.example com:

~~~
VITE_RELEASE_CONTROL_MODE=publisher
VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:8090
~~~

O validador deve exigir .env.example versionável e, quando existir, ainda
validar o .env local. A ausência de frontend/.env não pode quebrar CI; a
ausência do .env.example deve quebrar. Não versionar .env nem relaxar o
contrato de modo, URL loopback ou produção desabilitada.

### C — contratos e mutantes

- Atualizar test_44_workflow_validator_valid para os cinco workflows,
  incluindo rollback-production.yml.
- Corrigir validate_publisher_identity_bridge.py para vincular SYSTEM ao
  matcher específico de POST /api/release-control/identity/token; o mutante
  .hasRole("SYSTEM") -> .authenticated() nesse matcher deve falhar, mesmo que
  outro matcher ainda contenha SYSTEM.

### D — PostgreSQL na CI

Adicionar ao job backend de .github/workflows/ci.yml o service:

- imagem postgres:16.6-alpine;
- POSTGRES_DB=testdb, POSTGRES_USER=test, POSTGRES_PASSWORD=test;
- porta 5432:5432;
- healthcheck pg_isready -U test -d testdb com intervalo, timeout e retries.

Estender validate_ci.py e test_ci.py para exigir cada elemento e matar
mutantes de remoção/alteração. Os valores são fixtures sintéticas, não
segredos.

## 4. Validação e novo push

Executar os validadores S30, scanner, suítes de candidates/releases/security/CI,
validate_publisher_ui.py, validate_publisher_identity_bridge.py,
validate_release_workflow.py e git diff --check. mvn -B verify pode ser
executado se houver PostgreSQL local; se não houver, registrar a limitação e
deixar a prova definitiva para o service da CI. Não instalar banco por rede.

Após os gates locais, revisar somente os arquivos desta fronteira e executar
um segundo commit/push normal:

~~~
git add .github/workflows/ci.yml frontend/.env.example \
  tools/candidates/candidate_plan.py tools/candidates/tests/test_causal_corrections.py \
  tools/ci/validate_ci.py tools/ci/tests/test_ci.py \
  tools/releases/validate_publisher_ui.py tools/releases/tests/test_publisher_ui_contract.py \
  tools/releases/validate_publisher_identity_bridge.py tools/releases/tests/test_release_publication.py \
  docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md
git diff --cached --check
git commit -m "fix: close first CI gates"
git push origin main
~~~

Não usar force, tags, outro remote, outra branch, --no-verify, git init ou
alteração de identidade. Após o push, observar a nova CI e o novo
publish-candidate.yml; exigir CI e candidato verdes, artifact, manifesto,
digests, provenance e attestations cruzados. Se faltar read:packages,
registrar a limitação sem criar credenciais.

Não publicar release, não executar publish-release.yml diretamente, deploy,
rollback, SSH, VPS, Docker de produção, cleanup destrutivo ou S31.

## 5. Relatório e prompt formal

Atualizar somente o relatório S30 com causas, arquivos, testes, matriz local,
SHA corretivo, runs remotos e divergências sanitizadas. Terminar com:

~~~
IN_PROGRESS — aguardando revisão do orquestrador
~~~

Prompt:

~~~
Execute exclusivamente a correction-01 da S30 em /home/gregorio/git/baronesa/emporio.
Leia a task S30, emenda-01, esta correction, HANDOFF, tracker e relatórios S11–S30.

Corrija somente A, B, C e D desta correction. Execute os validadores, testes,
scanner e diff check. Revise o staged diff e, se tudo passar, faça exatamente:

git add .github/workflows/ci.yml frontend/.env.example \
  tools/candidates/candidate_plan.py tools/candidates/tests/test_causal_corrections.py \
  tools/ci/validate_ci.py tools/ci/tests/test_ci.py \
  tools/releases/validate_publisher_ui.py tools/releases/tests/test_publisher_ui_contract.py \
  tools/releases/validate_publisher_identity_bridge.py tools/releases/tests/test_release_publication.py \
  docs/infrastructure/deployment/implementation/slices/S30-ensaio-remoto-candidato-publisher-release.report.md
git diff --cached --check
git commit -m "fix: close first CI gates"
git push origin main

Observe a nova CI e publish-candidate.yml. Não publique release, não use force,
tags, outro remote/branch ou no-verify, não crie credenciais, não faça
deploy/rollback/produção e não crie S31. Termine com:
IN_PROGRESS — aguardando revisão do orquestrador
~~~
