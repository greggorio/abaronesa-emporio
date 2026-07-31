# S29 — correction-01 do scanner do gate de primeiro commit

> **Estado:** `IN_PROGRESS` — aguardando correção e nova revisão  
> **Slice:** S29 — gate do primeiro commit e ativação remota  
> **Data:** 31/07/2026  
> **Dependência:** rejeição terminal registrada no relatório S29

## 1. Achados objetivos

A revisão confirmou que os gates de workflow passaram, mas o scanner canônico
falhou com exit 123 por oito fingerprints não allowlisted:

- seis marcadores PEM sintéticos em código, teste, documentação e validador;
- um placeholder `replace-with-secret` em `release_control/.env.example`;
- um falso positivo causado pela expressão do scanner atravessar a linha
  `secrets:` do YAML até o nome da chave externa.

Nenhum valor de chave privada ou segredo real foi identificado. Ainda assim,
S29 não pode ser aceita enquanto o comando canônico não retornar clean.

## 2. Correção autorizada

Alterar somente:

```text
tools/ci/secret_scan.py
tools/ci/secret-allowlist.json
tools/ci/tests/test_ci.py
docs/infrastructure/deployment/implementation/slices/S29-gate-primeiro-commit-ativacao-remota.report.md
```

Não alterar S16, S23, S28, os arquivos de aplicação, workflows, contratos,
`.gitignore`, tracker, task S29 ou criar S30.

## 3. Regras da correção

1. Corrigir a expressão `SENSITIVE_ASSIGNMENT` para que espaços permitidos
   entre chave e separador não atravessem newline; o scanner não pode tratar o
   mapa YAML `secrets:` como atribuição de segredo.
2. Reconhecer como placeholders não secretos somente marcadores explícitos já
   usados no workspace: `replace-with`, `__SET_` e `from-secret-manager`.
3. Adicionar à allowlist somente os seis fingerprints `PRIVATE_KEY` já
   registrados no relatório S29, cada um com justificativa individual de que é
   marcador sintético/contratual sem material de chave:

```text
645a3fd1fbded7d1  backend/.../ReleaseControlIdentityConfiguration.java
45b857fbbf8f5cff  backend/.../DeployerReleaseControlIdentityConfiguration.java
adf9d660c6ecea59  backend/.../ReleaseControlIdentityContractTest.java
ece92808270e9fba  backend/.../ReleaseControlIdentityContractTest.java
697fe882c1911841  docs/.../S16-ponte-identidade-rs256-jwks-perfil-local-publisher.task.md
d5c0e5f19884c2e6  tools/releases/validate_publisher_identity_bridge.py
```

Não allowlistar regra, path ou fingerprint genérico. Não remover a regra
`PRIVATE_KEY` nem relaxar detecção de tokens, API keys ou assignments reais.

## 4. Testes causais obrigatórios

Atualizar `tools/ci/tests/test_ci.py` para provar, além da suíte existente:

- `secrets:\n  external_key:` não gera falso positivo de assignment;
- `PASSWORD=replace-with-secret` e
  `HASH_PEPPER=__SET_IN_PROTECTED_ENV_FILE__` são placeholders aceitos;
- assignment com valor sintético que não é placeholder continua sendo
  detectado;
- os seis fingerprints allowlisted são reconhecidos somente por igualdade
  exata e continuam sem imprimir valores.

Os testes devem matar mutantes que reintroduzam `\s*` atravessando newline,
removam cada marcador de placeholder, removam uma entrada da allowlist ou
aceitem uma alteração em path/fingerprint.

## 5. Matriz obrigatória

```bash
cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/ci/tests/test_ci.py -v
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q tools/ci/tests/test_ci.py
git ls-files --cached --others --exclude-standard -z | xargs -0 python3 tools/ci/secret_scan.py
python3 tools/ci/validate_workflow_inventory.py
python3 tools/releases/validate_release_workflow.py
python3 tools/candidates/validate_candidate_workflow.py
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_rollback_contract.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/releases/release_control_contract.py validate
git diff --check
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
```

O scanner canônico deve terminar com exit 0 e
`secret-scan:clean`, com `unsupported=0`. `git rev-parse --verify HEAD` deve
continuar retornando 128; índice, tags e reflog devem permanecer vazios.

Não usar rede, actionlint, GitHub, GHCR, SSH, Docker, containers, Postgres,
segredos reais, commit, tag, push ou `git init`. Remover somente bytecode/cache
gerado pela própria execução, se houver.

## 6. Relatório da correção

Atualize o relatório S29 com arquivos, mutantes, exits, fingerprints
sanitizados, interpretação e nova matriz. Se todos os gates passarem, termine
com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED`, não alterar esta correction, a task, o tracker ou
S01–S28, e não criar S30.

## 7. Prompt formal

```text
Execute exclusivamente a correction-01 da S29 em
/home/gregorio/git/baronesa/emporio.

Corrija apenas tools/ci/secret_scan.py, tools/ci/secret-allowlist.json,
tools/ci/tests/test_ci.py e o relatório S29.

Faça o regex SENSITIVE_ASSIGNMENT não atravessar newlines, reconheça somente
os placeholders replace-with, __SET_ e from-secret-manager, e allowliste
apenas os seis fingerprints PRIVATE_KEY já registrados, com justificativas
individuais de marcadores sintéticos sem material de chave.

Adicione testes causais para o falso positivo YAML, placeholders, assignments
reais, igualdade exata de allowlist e ausência de vazamento de valores.

Execute a matriz da correction. O scanner canônico precisa terminar com exit 0,
secret-scan:clean e unsupported=0. Não altere S16, S23, S28, workflows,
contratos, .gitignore ou a task/tracker. Não use rede, GitHub, GHCR, SSH,
Docker, containers, Postgres, secrets reais, commit, tag, push ou git init.
Não crie S30.

Termine com: IN_PROGRESS — aguardando revisão do orquestrador
```
