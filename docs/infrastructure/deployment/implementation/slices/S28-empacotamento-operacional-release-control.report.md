# S28 — Relatório de empacotamento operacional do release-control

## 1. Escopo e CWD

- CWD obrigatório: `/home/gregorio/git/baronesa/emporio`.
- Task executada: `docs/infrastructure/deployment/implementation/slices/S28-empacotamento-operacional-release-control.task.md`.
- Tipo: empacotamento local, declarativo e offline.
- Nenhuma execução de rollback, deploy, build, pull, publicação ou instalação
  foi realizada.
- A task, o tracker, o handoff, S01–S27 e S29 não foram alterados/criados.

## 2. Arquivos da fronteira

### Criados

- `release_control/Dockerfile`
- `release_control/.dockerignore`
- `ops/compose/release-control.yml`
- `ops/env/release-control.env.example`
- `ops/systemd/emporio-release-control.service.example`
- `docs/infrastructure/deployment/release-control/OPERACAO_RELEASE_CONTROL.md`
- `tools/deploy/validate_release_control_package.py`
- `tools/deploy/tests/test_validate_release_control_package.py`
- este relatório.

### Alterado

- `release_control/README.md`, somente com a seção de empacotamento operacional
  S28.

### Não alterados

- `release_control/src/**`;
- `release_control/migrations/**`;
- `release_control/pyproject.toml` e `release_control/uv.lock`;
- backend, frontend, gateway, Nginx, publisher, deployer, UI, workflows,
  OpenAPI, schemas e contratos;
- tasks, correções, relatórios e slices S01–S27;
- tracker, `README.md` de implementation e `HANDOFF_ORQUESTRADOR.md`;
- nenhum arquivo fora da fronteira acima.

## 3. Implementação por requisito

### Imagem e contexto

`release_control/Dockerfile` usa estágios `python:3.13-slim`, recebe uma versão
explícita do instalador `uv`, copia `pyproject.toml` e `uv.lock` e executa
`uv sync --locked --no-dev`; o estágio final não contém o grupo dev. O usuário
final é `10001:10001`, com `groupadd`/`useradd` de UID e GID explícitos e
`WORKDIR /app`.

O contexto é fechado por `.dockerignore`: `.env`, variantes de ambiente,
PEM/chaves/certificados, caches, bytecode, `.venv`, `node_modules`, uploads e
testes ficam fora do contexto. A imagem copia somente `src`, `migrations`,
`alembic.ini`, metadados de projeto e o ambiente de produção resolvido pelo
lock.

O `CMD` executa `alembic upgrade head && exec uvicorn ...` na porta interna
8080; uma falha de migration impede o Uvicorn de iniciar. O healthcheck usa
somente `http://127.0.0.1:8080/health/live` e não imprime ambiente, segredo ou
configuração.

### Compose, persistência e segurança

`ops/compose/release-control.yml` contém exatamente os serviços
`release_control` e `release_control_postgresql`. O PostgreSQL usa imagem
parametrizada, healthcheck `pg_isready` e o volume nomeado
`emporio_release_control_postgresql_data`. A aplicação depende de
`service_healthy`.

A rede nomeada `emporio_release_control_internal` é exclusiva do pacote e não
é compartilhada com a stack comercial. Não há publicação de porta para
interfaces externas: o único binding é
`127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}:8080`. A rede permanece
privada quanto à publicação, mas não é marcada como `internal: true`, pois o
runtime existente precisa de egress HTTPS para JWKS e GitHub API.

O contêiner da aplicação usa `user: "10001:10001"`, `read_only: true`, `tmpfs`
restrito, `no-new-privileges:true` e `cap_drop: ALL`. Não há
`network_mode: host`, `privileged`, `cap_add`, Docker socket, `--reload`,
`build` ou import do Compose comercial. A chave privada do GitHub App é
referenciada como segredo externo e montada em
`/run/secrets/github-app-private-key` com modo `0440`; nenhum valor de chave é
criado.

### Ambiente e operação

`ops/env/release-control.env.example` declara placeholders sem credenciais,
com `profile=runtime`, `mode=deployer`, audience deployer, TLS do PostgreSQL,
imagem por referência imutável e o caminho de segredo montável em runtime.

O unit file systemd declara usuário, grupo, diretório de trabalho,
`EnvironmentFile`, ordem após Docker/rede, `up --detach --no-build --wait` e
parada segura com `stop --timeout 30`.

`OPERACAO_RELEASE_CONTROL.md` documenta o limite declarativo, topologia,
migration antes de Uvicorn, distinção entre `/health/live` e `/health/ready`,
backup/restore, atualização independente, segredo externo, parada segura e o
fato de que a S28 não instala, publica ou opera o serviço em produção.

## 4. Validador, testes causais e mutantes

O validador `tools/deploy/validate_release_control_package.py` lê a raiz
recebida, verifica a presença da fronteira, a imagem não-root, lock e
dependências de produção, ordem migration/Uvicorn, contexto, healthcheck,
serviços e binding Compose, persistência/rede próprias, segredo montado,
environment scope, systemd e documentação sem valores secretos ou afirmação
de instalação.

Os 12 mutantes prescritos foram exercitados individualmente:

1. usuário final root no Dockerfile — rejeitado;
2. remoção da exclusão `.env.*` — rejeitado;
3. dependência dev no `uv sync` — rejeitado;
4. Uvicorn antes da migration — rejeitado;
5. healthcheck em endereço público — rejeitado;
6. import do Compose comercial — rejeitado;
7. binding host em `0.0.0.0` — rejeitado;
8. volume compartilhado — rejeitado;
9. serviço privilegiado — rejeitado;
10. senha literal no env example — rejeitado;
11. systemd sem `EnvironmentFile` — rejeitado;
12. documentação alegando instalação concluída — rejeitado.

Resultado literal:

```text
release-control-package:valid
.............                                                            [100%]
13 passed in 0.04s
```

Os 13 testes são o baseline mais os 12 mutantes. O lint adicional dos dois
arquivos Python também passou com `All checks passed!`.

## 5. Matriz terminal completa

Todos os comandos abaixo foram executados a partir de
`/home/gregorio/git/baronesa/emporio`. As durações são as observadas nesta
execução.

| Comando | Exit | Duração | Contagem/saída literal relevante | Interpretação |
|---|---:|---:|---|---|
| `python3 tools/deploy/validate_release_control_package.py` | 0 | 0,071 s | `release-control-package:valid` | fronteira e regras S28 válidas |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q tools/deploy/tests/test_validate_release_control_package.py` | 0 | 0,638 s | `13 passed in 0.04s` | baseline + 12 mutantes verdes |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m compileall -q release_control/src tools/deploy` | 0 | 0,172 s | saída vazia | sintaxe compilável; bytecode gerado apenas temporariamente e movido para `/tmp/emporio-s28-generated-bytecode-final/` após a verificação |
| `uv lock --check` | 2 | 0,054 s | `error: No \`pyproject.toml\` found in current directory or any parent directory` | o CWD prescrito é a raiz sem `pyproject.toml`; o lock está em `release_control/` |
| `cd release_control && uv lock --check` | 0 | 0,089 s | `Using CPython 3.13.9 interpreter at: /usr/bin/python3` / `Resolved 57 packages in 0.63ms` | verificação equivalente do lock correto, sem atualização |
| `python3 tools/releases/release_control_contract.py validate` | 0 | 0,155 s | `release-control-contract:valid` | contrato existente preservado |
| `python3 tools/deploy/validate_deployer_runtime.py` | 0 | 0,182 s | `deployer-runtime:valid` | runtime deployer preservado |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | 0,080 s | `rollback-runtime:valid` | runtime rollback preservado |
| `git diff --check` | 0 | 0,052 s | saída vazia | sem erro de whitespace |
| `git rev-parse --verify HEAD` | 128 | 0,061 s | `fatal: Needed a single revision` | workspace sem HEAD conforme estado pré-Git |
| `git tag --list` | 0 | 0,051 s | saída vazia | nenhuma tag |
| `git reflog show --all` | 0 | 0,051 s | saída vazia | nenhum reflog |
| matriz `find release_control ops -maxdepth 4 ...` após limpeza | 0 | 0,058 s | `release_control/.pytest_cache` | único cache preexistente; preservado por estar fora da fronteira |
| `docker compose -f ops/compose/release-control.yml config --quiet` | 0 | 0,101 s | saída vazia | Compose declarativamente válido; não iniciou daemon, rede, volume ou container |

Verificação adicional:

```text
$ python3 -m ruff check tools/deploy/validate_release_control_package.py tools/deploy/tests/test_validate_release_control_package.py
exit=0
All checks passed!
```

## 6. Estado Git, workflows e resíduos

O workspace não possui HEAD verificável, tags ou reflog. `git status --short`
mostrou todos os diretórios como não rastreados, incluindo `ops/`,
`release_control/`, `tools/` e o restante do workspace; portanto não há diff
Git histórico que permita separar mudanças por HEAD. A lista de workflows
permaneceu:

```text
.github/workflows/README.md
.github/workflows/ci.yml
.github/workflows/deploy-production.yml
.github/workflows/publish-candidate.yml
.github/workflows/publish-release.yml
.github/workflows/rollback-production.yml
```

Nenhum workflow foi alterado. Após a execução final, a busca prescrita não
encontrou `.env`, `.venv`, coverage, caches Ruff/mypy, `__pycache__` ou `.pyc`
no pacote/ops, exceto `release_control/.pytest_cache`, que já existia antes
da S28 e não foi removido por estar fora da fronteira autorizada. O bytecode
gerado pelo `compileall` obrigatório foi apenas movido para `/tmp`, fora do
workspace, sem alterar fonte ou runtime.

## 7. Acessos externos e segredos

- Rede não utilizada.
- Nenhum `docker build`, `pull`, `up`, `run`, container, PostgreSQL, volume ou
  rede foi iniciado.
- `docker compose ... config --quiet` foi somente análise declarativa.
- Nenhum `uv sync`, instalação, SSH, GitHub, GHCR, VPS, DNS, gateway, Nginx,
  produção ou workflow remoto foi acessado.
- Nenhuma chave privada, senha real, pepper, JWT, token ou segredo foi lido ou
  criado.
- O Compose referencia apenas um segredo externo por nome; o valor não está
  no workspace. O env example usa placeholders e a verificação rejeita
  literals de segredo.

## 8. Divergências restantes

1. O comando literal `uv lock --check` da matriz, executado no CWD raiz
   obrigatório, termina em exit 2 porque a raiz não possui
   `pyproject.toml`. A checagem correta e equivalente
   `cd release_control && uv lock --check` termina em exit 0; nenhum arquivo
   fora da fronteira foi criado para mascarar essa condição.
2. `release_control/.pytest_cache` permanece como resíduo preexistente fora da
   fronteira S28, conforme instrução da task; não é um artefato criado pela
   implementação.

Não há outra divergência funcional identificada na fronteira S28.

IN_PROGRESS — aguardando revisão do orquestrador

## 9. Revisão terminal do orquestrador

**Veredito: `ACCEPTED` — 31/07/2026.**

O validador S28, os 13 testes causais, o Compose declarativo e os contratos
de release, deployer e rollback passaram na revisão. A checagem do lock correta
em `release_control/` também foi reproduzida com cache temporário em `/tmp`,
sem rede.

Permanece registrada a divergência do comando literal `uv lock --check` na
raiz: o workspace não possui `pyproject.toml` nesse nível. O cache
`release_control/.pytest_cache` continua preexistente e fora da fronteira. Não
houve build, pull, container, rede, publicação, produção ou segredo real.

S28 está aceita. A próxima slice autorizada é a S29.
