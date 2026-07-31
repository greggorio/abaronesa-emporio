# S28 — Empacotamento operacional isolado do release control

> **Estado:** `PLANNED`  
> **Tipo:** empacotamento local, runtime containerizado e operação declarativa  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01–S27 `ACCEPTED`  
> **Relatório de saída:** `S28-empacotamento-operacional-release-control.report.md`

## 0. Autoridade e limite

Leia antes de agir:

1. `docs/infrastructure/deployment/implementation/HANDOFF_ORQUESTRADOR.md`;
2. `docs/infrastructure/deployment/implementation/README.md`;
3. `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`;
4. `docs/infrastructure/deployment/release-control/README.md`;
5. `docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md`;
6. `release_control/README.md`, `release_control/pyproject.toml`, `release_control/uv.lock`;
7. as tasks e relatórios S19–S27.

Esta task é o contrato fechado. Não a altere, não aceite a slice e não crie
S29. O relatório registra evidências; somente o orquestrador decide o estado.

## 1. Objetivo

Criar o pacote operacional isolado do `release_control`, pronto para futura
publicação e instalação controlada, sem acoplar o serviço ao Compose comercial.
O pacote deve declarar:

- imagem Python 3.13 construída a partir de `pyproject.toml` e `uv.lock`, com
  usuário não-root, diretório de trabalho explícito, dependências de produção
  separadas das de desenvolvimento, nenhum segredo na imagem e healthcheck de
  `/health/live`;
- execução que aplica `alembic upgrade head` antes de iniciar o Uvicorn;
- Compose dedicado em `ops/compose/release-control.yml`, contendo somente os
  serviços `release_control` e `release_control_postgresql`, uma rede interna,
  um volume PostgreSQL próprio, healthcheck do banco, `no-new-privileges`,
  filesystem somente leitura quando compatível e publicação do HTTP apenas em
  `127.0.0.1`;
- perfil de ambiente sem segredos em
  `ops/env/release-control.env.example`, com placeholders obrigatórios,
  audiência/mode coerentes e caminho de chave montável em runtime;
- unidade systemd de exemplo em
  `ops/systemd/emporio-release-control.service.example`, com usuário/grupo,
  diretórios, `EnvironmentFile`, ordem de inicialização e parada segura;
- documentação operacional em
  `docs/infrastructure/deployment/release-control/OPERACAO_RELEASE_CONTROL.md`,
  cobrindo instalação futura, migration, live/ready, backup/restore do banco,
  atualização independente e limites desta slice.

O Compose não deve importar `ops/compose/compose.prod.yml`,
`docker-compose.emporio.yml` ou qualquer serviço comercial. O serviço não deve
publicar porta pública; o gateway futuro poderá encaminhá-lo pela topologia
declarada, sem expor sua porta privada.

## 2. Fronteira autorizada

Pode criar ou alterar somente:

- `release_control/Dockerfile`;
- `release_control/.dockerignore`;
- `release_control/README.md`;
- `ops/compose/release-control.yml`;
- `ops/env/release-control.env.example`;
- `ops/systemd/emporio-release-control.service.example`;
- `docs/infrastructure/deployment/release-control/OPERACAO_RELEASE_CONTROL.md`;
- `tools/deploy/validate_release_control_package.py`;
- `tools/deploy/tests/test_validate_release_control_package.py`;
- o relatório S28.

Não alterar `release_control/src`, migrations existentes, `pyproject.toml`,
`uv.lock`, backend comercial, frontend, gateway, Nginx, workflows, OpenAPI,
state machines, matriz de segurança, publisher S17, deployer S22/S26 ou UI S24/S27.

## 3. Requisitos fechados

### 3.1 Imagem

- O estágio final deve executar como usuário não-root com UID/GID explícitos.
- O contexto não pode incluir `.env`, chaves, certificados privados, caches,
  `node_modules`, `.venv`, `.pytest_cache`, `.coverage`, uploads ou testes.
- A imagem deve conter `src`, `migrations`, `alembic.ini`, metadados de projeto
  e somente dependências necessárias ao runtime.
- O entrypoint deve falhar se a migration não alcançar `head`; depois deve
  iniciar `uvicorn emporio_release_control.main:app` na porta interna 8080.
- O healthcheck deve consultar somente `http://127.0.0.1:8080/health/live` e
  não pode vazar configuração ou segredo.

### 3.2 Compose e persistência

- `release_control_postgresql` usa imagem parametrizada e volume nomeado
  exclusivamente para o banco do control plane.
- `release_control` depende da saúde do banco, recebe apenas variáveis do
  perfil `RELEASE_CONTROL_*` e monta a chave privada como segredo somente
  leitura; nenhum valor real deve ser criado.
- O binding host deve ser
  `127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}:8080`.
- Não usar `network_mode: host`, Docker socket, `privileged`, `cap_add`,
  `--reload` ou comandos de shell que imprimam variáveis secretas.
- O volume e a rede devem ter nomes próprios do release control e não podem
  reutilizar volumes ou redes do Compose comercial.

### 3.3 Operação

- O exemplo systemd não executa nada no host durante a slice; apenas declara a
  unidade, o usuário/grupo e paths esperados.
- O `.env.example` deve conter nomes e placeholders, nunca credenciais reais.
- A documentação deve distinguir `live` de `ready`, declarar o backup do
  PostgreSQL antes de update, explicar `alembic upgrade head`, e deixar claro
  que S28 não instala na VPS nem publica imagem.

## 4. Validação causal obrigatória

Crie o validador e testes para matar, no mínimo, estes mutantes:

1. imagem final rodando como root;
2. contexto incluindo `.env`, chave, cache ou teste;
3. dependência de desenvolvimento instalada no runtime;
4. migration omitida ou executada depois do Uvicorn;
5. healthcheck apontando para endereço público ou endpoint inexistente;
6. Compose importando o stack comercial;
7. porta publicada em `0.0.0.0` ou diferente de loopback;
8. volume/rede compartilhado com o stack comercial;
9. serviço privilegiado, Docker socket ou `network_mode: host`;
10. segredo literal no exemplo de ambiente, Compose, systemd ou documentação;
11. systemd sem `EnvironmentFile`, usuário/grupo ou parada segura;
12. documentação alegando instalação, publicação ou produção concluída.

## 5. Matriz terminal obrigatória

Execute a partir do CWD indicado e registre comando, exit, duração/contagem,
saída literal relevante e interpretação:

```bash
cd /home/gregorio/git/baronesa/emporio
python3 tools/deploy/validate_release_control_package.py
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q tools/deploy/tests/test_validate_release_control_package.py
PYTHONDONTWRITEBYTECODE=1 python3 -m compileall -q release_control/src tools/deploy
uv lock --check
python3 tools/releases/release_control_contract.py validate
python3 tools/deploy/validate_deployer_runtime.py
python3 tools/deploy/validate_rollback_runtime.py
git diff --check
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find release_control ops -maxdepth 4 \
  \( -name '.env' -o -name '.venv' -o -name '.coverage' -o -name '.pytest_cache' \
     -o -name '.ruff_cache' -o -name '.mypy_cache' -o -name '__pycache__' \
     -o -name '*.pyc' \) -print
```

`git rev-parse --verify HEAD` deve retornar 128 no workspace pré-Git. Tags e
reflog devem permanecer vazios. O cache preexistente fora da fronteira deve
ser reportado, não apagado.

Se Docker estiver disponível, execute apenas validação declarativa, sem
download, build, pull, run, volume, rede ou container:

```bash
docker compose -f ops/compose/release-control.yml config --quiet
```

Se a validação exigir placeholders, use somente valores sintéticos no ambiente
do comando e registre-os como sintéticos. Não use rede para instalar qualquer
dependência e não substitua `uv lock --check` por atualização do lock.

## 6. Proibições

Não executar `docker build`, `docker pull`, `docker compose up`, `docker run`,
`uv sync`, `npm`, Postgres, SSH, GitHub, GHCR, VPS, DNS, gateway, Nginx,
produção, workflow remoto, commit, tag, push ou `git init`. Não ler nem criar
segredos, `.env` real, chaves privadas ou tokens. Não remover caches fora da
fronteira S28. Não criar S29.

## 7. Relatório obrigatório

Crie somente:

`docs/infrastructure/deployment/implementation/slices/S28-empacotamento-operacional-release-control.report.md`

O relatório deve conter CWD, arquivos criados/alterados/não alterados,
comandos completos, exits, saídas literais, testes causais e mutantes,
interpretação, resíduos, Git/workflows, acessos externos e divergências.
Termine exatamente com:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED`, não alterar esta task, o tracker ou S01–S27, e não
criar S29.

## 8. Prompt formal para delegação

```text
Execute exclusivamente a S28 no diretório /home/gregorio/git/baronesa/emporio.
Leia o handoff, README, arquitetura, release-control README/RUNTIME_DEPLOYER,
pyproject/uv.lock e tasks/relatórios S19–S27 antes de alterar arquivos.

Crie o pacote operacional isolado do release_control: Dockerfile não-root,
contexto sem segredos/caches/testes, migration antes do Uvicorn, healthcheck
de /health/live, Compose dedicado com somente release_control e
release_control_postgresql, volume/rede próprios, porta publicada apenas em
127.0.0.1, perfil env sem segredos, unidade systemd de exemplo e documentação
de live/ready, migration, backup/restore e atualização independente.

Altere somente a fronteira listada na task S28. Não altere src, migrations,
pyproject, uv.lock, backend, frontend, gateway, Nginx, workflows, contratos,
publisher, deployer ou UI. Não importe o Compose comercial.

Crie o validador e testes causais contra os mutantes da Seção 4. Execute a
matriz terminal e registre CWD, comando, exit, saída literal, interpretação,
arquivos e divergências no relatório S28. Use somente valores sintéticos.

Não use rede, Docker build/pull/up/run, containers, Postgres, uv sync, SSH,
GitHub, GHCR, VPS, DNS, produção, secrets, commit, tag, push ou git init.
Não altere a task/tracker, não aceite a slice e não crie S29.
Termine com: IN_PROGRESS — aguardando revisão do orquestrador
```
