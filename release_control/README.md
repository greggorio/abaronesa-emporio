# Emporio release-control

Um mesmo pacote suporta processos mutuamente exclusivos `publisher` e
`deployer`. Cada bootstrap registra apenas seu router, serviço, reconciler e
credencial GitHub App. O publisher despacha somente `publish-release.yml`; o
deployer despacha somente `deploy-production.yml`. Nenhum modo acessa Git
local, Docker, SSH, GHCR ou produção diretamente.

## Ambiente local

Requer Python `>=3.13,<3.14`, `uv` e PostgreSQL 16. Instale exatamente o lock:

```bash
uv sync --locked
```

Copie apenas os nomes de `release_control/.env.example` para um gerenciador de
segredos. Não grave valores reais em `.env`.
`RELEASE_CONTROL_MODE=publisher|deployer` é obrigatório e imutável. O profile
`runtime` exige TLS no PostgreSQL, API
`https://api.github.com`, issuer/JWKS HTTPS, allowlist CORS explícita, pepper
aleatório com 32 bytes ou mais e chave PEM da GitHub App em arquivo.

Em desenvolvimento, um comando aplica as migrations pendentes e sobe o runtime
em loopback, dentro do ambiente travado:

```bash
uv run publisher     # 127.0.0.1:8090
uv run deployer      # 127.0.0.1:8091
```

Cada script fixa o próprio `RELEASE_CONTROL_MODE`, aceita `--host`, `--port`,
`--reload`, `--skip-migrations` e `--env-file`, e recusa qualquer perfil que não
seja `development`.

As variáveis vêm de `~/.config/emporio/release-control/<modo>-runtime.env`, ou
do caminho em `--env-file`/`RELEASE_CONTROL_ENV_FILE`. O que já estiver
exportado no shell sempre vence, e a origem efetiva é impressa a cada início —
carregar arquivo automaticamente é cômodo e o preço é rodar com configuração
velha sem perceber. Faltando variáveis, o launcher lista os nomes ausentes em
vez de deixar um traceback no lugar do diagnóstico.

A imagem não usa esses scripts: ela executa o próprio bootstrap com
`python -m alembic` e `python -m uvicorn`, e o perfil `runtime` continua sem ler
arquivo algum, mantendo a migration como ato explícito e separado:

```bash
uv run alembic upgrade head
uv run uvicorn emporio_release_control.main:app --host 127.0.0.1 --port 8080
```

O processo executa somente a sincronização/reconciliação do modo ativo no
bootstrap e periodicamente.
`/health/live` indica apenas processo HTTP. `/health/ready` só fica verde após
migration atual, PostgreSQL acessível, chave estruturalmente válida, ciclos
verdes de candidatos e releases e ausência de drift.

## Validação

```bash
uv lock --check
uv run ruff check .
uv run mypy --strict src
uv run pytest -q
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90
```

Os testes transacionais usam PostgreSQL 16 real e um transporte fake em
loopback; nunca usam a API GitHub real. Logs, respostas e auditoria usam códigos
estáveis e não incluem JWT, chave idempotente, chave privada, token de
instalação, bodies GitHub ou credenciais do banco.

## Diagnóstico sanitizado

- `503` em readiness: verifique migration, conectividade e o estado dos dois
  ciclos de sync; a resposta pública deliberadamente não revela a causa.
- `401/403`: confira issuer, audience, RS256, `sub` e scopes no provedor JWT.
- `409`: reutilize a mesma chave somente com o mesmo request. No deployer,
  instalação incerta e rollback forward-only indisponível também falham
  fechados com códigos públicos específicos.
- falha de sync/reconcile: use apenas o código estável e o trace ID no audit.

Idempotência é retida por 365 dias por padrão. Rate limit é em memória e esta
fase opera com uma única réplica.

## Empacotamento operacional S28

O pacote isolado da S28 fica em `ops/compose/release-control.yml`, com o
`release_control` e seu PostgreSQL em rede e volume próprios. A publicação do
HTTP é somente `127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180}` no host; a
imagem atende em `8080` apenas na rede própria do Compose, sem publicação
externa.

O contexto de `release_control/Dockerfile` usa `pyproject.toml` e `uv.lock`,
instala somente dependências de produção e executa `alembic upgrade head`
antes do Uvicorn. A imagem usa o usuário não-root `10001:10001` e o
healthcheck consulta somente `/health/live` no loopback do contêiner.

Os valores de operação devem ser fornecidos por um arquivo protegido baseado
em `ops/env/release-control.env.example`. O exemplo contém placeholders, não
é um arquivo de segredo e não deve ser copiado para dentro da imagem. A chave
privada do GitHub App é um segredo externo do Compose, montado somente para o
serviço e somente leitura.

Este diretório descreve um pacote declarativo local. A S28 não executa build,
pull, up, publicação, instalação ou operação em produção.
