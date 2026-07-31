# S29 — Gate do primeiro commit e ativação remota

> **Estado:** `PLANNED`  
> **Tipo:** auditoria final local, saneamento mínimo de workflows e checklist de ativação  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01–S28 `ACCEPTED`  
> **Relatório de saída:** `S29-gate-primeiro-commit-ativacao-remota.report.md`

## 0. Autoridade e entrada conhecida

Leia antes de agir:

1. `docs/infrastructure/deployment/implementation/HANDOFF_ORQUESTRADOR.md`;
2. `docs/infrastructure/deployment/implementation/README.md`;
3. `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`;
4. os relatórios finais S01–S28;
5. `.gitignore`, `.github/workflows/README.md` e todos os workflows YAML da raiz;
6. os validadores de CI, candidate, release, deployer e rollback.

Esta task é o contrato fechado. Não a altere, não aceite a slice e não crie
S30. O relatório registra evidências; somente o orquestrador decide o estado.

Na entrada desta slice existem exatamente cinco workflows YAML em
`.github/workflows/`: `ci.yml`, `publish-candidate.yml`, `publish-release.yml`,
`deploy-production.yml` e `rollback-production.yml`. O README ainda declara
quatro e `rollback-production.yml` usa `actions/checkout@v4.2.2`; corrigir
somente essas divergências é parte explícita desta task.

## 1. Objetivo

Fechar localmente o gate de segurança para o primeiro commit e preparar o
checklist do primeiro push, sem executar Git ou qualquer mutação remota.

O resultado deve provar, com evidência sanitizada:

- conjunto candidato ao primeiro commit, exclusões do `.gitignore` e resíduos
  que não podem entrar no índice;
- scanner offline de segredos sem imprimir valores;
- inventário dos cinco workflows, gatilho, permissões, efeitos e actions
  fixadas por SHA de 40 hexadecimais;
- validação dos contratos existentes de CI, candidato, release, deployer e
  rollback;
- efeito esperado do primeiro push: CI em `main`, possível publicação de
  candidato após CI verde, e workflows de release/deploy/rollback somente por
  dispatch manual;
- pré-condições remotas que ainda exigirão autorização humana, sem consultar
  GitHub, GHCR, secrets, environments, branch protection ou workflows reais.

O primeiro push continua sendo uma ação manual do usuário. Esta slice produz
o gate e o checklist; não publica, não configura remoto e não acompanha run.

## 2. Fronteira autorizada

Pode criar ou alterar somente:

- `.github/workflows/README.md`, para corrigir o inventário de quatro para
  cinco workflows e documentar o rollback manual;
- `.github/workflows/rollback-production.yml`, somente para substituir
  `actions/checkout@v4.2.2` pelo SHA exato
  `actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd`;
- `tools/releases/validate_release_workflow.py`, somente para incluir
  `rollback-production.yml` no conjunto esperado de workflows ativos;
- `tools/ci/validate_workflow_inventory.py`;
- `tools/ci/tests/test_validate_workflow_inventory.py`;
- o relatório S29.

Não alterar `ci.yml`, `publish-candidate.yml`, `publish-release.yml`,
`deploy-production.yml`, contratos, schemas, runtime, backend, frontend,
Docker, Compose, Nginx, env, secrets, `.gitignore`, tasks, tracker ou S01–S28.
Não criar workflow novo. Não atualizar versões de actions além do checkout
indicado acima.

## 3. Requisitos fechados

### 3.1 Inventário e pinagem

- O conjunto de workflows YAML ativos deve ser exatamente os cinco nomes
  listados na Seção 0. Markdown não é workflow.
- Todo `uses:` nos cinco YAML deve usar `owner/repository@` seguido de SHA
  hexadecimal de 40 caracteres. Tags como `@v4.2.2`, `@main` ou `@latest` são
  inválidas.
- `rollback-production.yml` permanece somente `workflow_dispatch`, com inputs
  obrigatórios `operation_id` e `release`, permissão `contents: read`,
  concorrência `emporio-production` sem cancelamento e sem SSH, Docker,
  publicação ou acesso de segredo.
- O README deve descrever os cinco workflows sem afirmar que a CI ou a
  publicação remota já foi executada.

### 3.2 Scanner e candidato

- Escanear somente arquivos candidatos versionáveis e históricos disponíveis;
  valores encontrados nunca podem aparecer no relatório, apenas regra, path e
  fingerprint sanitizado.
- `.env`, `.env.production`, chaves, certificados, uploads, caches,
  `node_modules`, `.venv`, coverage, bytecode e demais resíduos ignorados não
  podem entrar no candidato. Não apagá-los nesta slice.
- Registrar contagem e hash do inventário candidato em forma reproduzível,
  usando apenas `/tmp` para arquivos auxiliares.
- O índice Git real deve continuar vazio e o workspace deve continuar sem
  HEAD, commit, tag ou reflog.

### 3.3 Efeito remoto previsto

Documentar no relatório, sem acessar rede:

- primeiro push em `main` pode disparar `ci.yml`;
- CI verde em `main` pode liberar `publish-candidate.yml` por `workflow_run`;
- `publish-release.yml`, `deploy-production.yml` e
  `rollback-production.yml` exigem dispatch manual;
- permissões, variáveis, secrets e environments ausentes ou não verificados
  são pré-condições remotas pendentes, não devem ser inventados.

## 4. Validação causal obrigatória

O validador e os testes devem rejeitar, no mínimo:

1. workflow YAML extra ou workflow canônico ausente;
2. action com tag, branch ou SHA diferente de 40 hexadecimais;
3. rollback acionado por `push` ou `pull_request`;
4. rollback com permissão de escrita;
5. README declarando quatro workflows;
6. conjunto esperado do validador de release divergente do inventário real;
7. Docker, SSH, publicação ou segredo introduzido no rollback;
8. relatório/documentação afirmando que o primeiro push ou workflow remoto já
   foi executado.

## 5. Matriz terminal obrigatória

Execute a partir do CWD indicado e registre comando, exit, duração/contagem,
saída literal relevante e interpretação:

```bash
cd /home/gregorio/git/baronesa/emporio
python3 tools/ci/validate_workflow_inventory.py
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q tools/ci/tests/test_validate_workflow_inventory.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/releases/validate_release_workflow.py
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_rollback_contract.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/releases/release_control_contract.py validate
git ls-files --cached --others --exclude-standard -z | xargs -0 python3 tools/ci/secret_scan.py
git ls-files --stage
git status --short
git rev-parse --show-toplevel
git rev-parse --verify HEAD
git tag --list
git reflog show --all
git diff --check
find .github/workflows -maxdepth 1 -type f -printf '%f\n' | sort
find . -path './.git' -prune -o \
  \( -name '.env' -o -name '.env.production' -o -name '*.pem' -o -name '*.key' \
     -o -name '.venv' -o -name '.coverage' -o -name '.pytest_cache' \
     -o -name '__pycache__' -o -name '*.pyc' -o -name '*.hprof' \) -print
```

`git rev-parse --verify HEAD` deve retornar 128; índice, tags e reflog devem
permanecer vazios. O scanner deve retornar `secret-scan:clean` ou findings
sanitizados exclusivamente allowlisted; nunca transcrever segredo.

Se `actionlint` estiver instalado, executá-lo nos cinco workflows. Não instalar
a ferramenta nem usar rede; registrar indisponibilidade como limitação
ambiental e manter o validador offline como gate principal.

## 6. Proibições

Não executar `git add` no índice real, `git commit`, `git tag`, `git push`,
`git init`, alteração de remote, `git ls-remote`, `gh`, `curl`, workflow remoto,
GitHub, GHCR, SSH, VPS, DNS, Docker, containers, Postgres, produção,
`npm`, instalação de dependências ou leitura/criação de segredos reais.
Não criar S30. Não remover resíduos. Não alterar arquivos fora da fronteira.

## 7. Relatório obrigatório

Crie somente:

`docs/infrastructure/deployment/implementation/slices/S29-gate-primeiro-commit-ativacao-remota.report.md`

O relatório deve conter arquivos criados/alterados/não alterados, inventário
do candidato, scanner sanitizado, matriz dos workflows, validações causais,
efeito do primeiro push, pré-condições remotas, Git, resíduos, acessos e
divergências. Termine exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED`, não alterar esta task, o tracker ou S01–S28, e não
criar S30.

## 8. Prompt formal para delegação

```text
Execute exclusivamente a S29 em /home/gregorio/git/baronesa/emporio.
Leia o handoff, tracker, arquitetura, relatórios S01–S28, .gitignore,
.github/workflows/README.md e todos os workflows YAML antes de alterar arquivos.

Feche o gate do primeiro commit e prepare o checklist do primeiro push.
Corrija somente o inventário do README de workflows, fixe o checkout do
rollback-production.yml no SHA
de0fac2e4500dabe0009e67214ff5f5447ce83dd e alinhe o EXPECTED do validador de
release para os cinco workflows atuais. Crie o validador offline de inventário
e seus testes causais.

Valide que todas as actions usam SHA de 40 hexadecimais, que o rollback é
manual/read-only, que o candidato não contém segredos ou resíduos ignorados e
que os contratos CI, candidate, release, deployer e rollback passam. Registre
o efeito previsto do primeiro push e as pré-condições remotas sem acessá-las.

Altere somente a fronteira listada na task S29. Não altere os outros workflows,
runtime, contratos, aplicação, Docker, Compose, .gitignore ou S01–S28.

Não use git add real, commit, tag, push, git init, remote write, git ls-remote,
gh, curl, rede, GitHub, GHCR, SSH, VPS, Docker, containers, Postgres,
produção, npm, instalação ou secrets reais. Não crie S30.

Execute a matriz terminal, registre saídas literais sanitizadas no relatório
S29 e termine com: IN_PROGRESS — aguardando revisão do orquestrador
```
