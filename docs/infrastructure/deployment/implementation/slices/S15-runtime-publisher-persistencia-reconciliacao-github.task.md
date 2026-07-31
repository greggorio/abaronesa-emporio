# S15 — Runtime publisher, persistência e reconciliação GitHub

> **Estado:** `ACCEPTED` — 29/07/2026  
> **Tipo:** implementação backend operacional, persistência e integração  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01 a S14 `ACCEPTED`  
> **Relatório de saída:** `S15-runtime-publisher-persistencia-reconciliacao-github.report.md`

## Instrução para delegação

Execute integralmente esta slice. Antes de alterar arquivos, leia, nesta
ordem:

1. esta task inteira;
2. a revisão terminal da S14;
3. `release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
4. `release-control/api/publisher.openapi.yml`;
5. `release-control/contracts/state-machines.yml`;
6. `release-control/contracts/security-matrix.yml`;
7. `release-control/CANDIDATOS.md`;
8. `release-control/RELEASES.md`;
9. `ci/RELEASE_PUBLICATION.md`;
10. schemas de candidato e release em `ops/releases/`;
11. `publish-candidate.yml` e `publish-release.yml`.

O executor implementa as decisões abaixo; não escolhe framework, banco,
credencial, algoritmo de idempotência, mecanismo de lock, formato de cursor,
política de retry, rotas adicionais ou fronteiras futuras.

Não altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

## 1. Resultado observável

Ao final existe um serviço independente `release_control`, executável em modo
`publisher`, que:

- expõe exatamente a API publisher S06;
- autentica JWT e aplica `release:read`/`release:publish`;
- persiste operações, idempotência, snapshots e auditoria em PostgreSQL;
- descobre candidatos e releases do repositório canônico;
- dispara somente `publish-release.yml` em `main`;
- correlaciona o run pelo `operationId`;
- reconcilia reinícios e estados não terminais;
- só grava `PUBLISHED` após run remoto verde e outcome íntegro;
- nunca usa Git local, `gh`, Docker socket, SSH, shell ou URL arbitrária;
- possui testes sem qualquer acesso ao GitHub real.

## 2. Decisões arquiteturais fechadas

### 2.1 Tecnologia

Use:

- Python `>=3.13,<3.14`;
- FastAPI;
- Uvicorn;
- Pydantic Settings v2;
- SQLAlchemy 2 síncrono;
- Psycopg 3;
- Alembic;
- HTTPX síncrono;
- PyJWT com suporte criptográfico;
- `jsonschema`;
- PostgreSQL 16;
- `uv` com `pyproject.toml` e `uv.lock`.

Testes e qualidade:

- pytest;
- pytest-cov;
- Testcontainers PostgreSQL;
- Ruff;
- mypy estrito.

Não use Django, Flask, Spring, H2, SQLite, Redis, Celery, fila externa,
ORM assíncrono ou armazenamento em memória para estado autoritativo.

### 2.2 Localização e pacote

Criar:

```text
release_control/
  pyproject.toml
  uv.lock
  .env.example
  README.md
  alembic.ini
  migrations/
  src/emporio_release_control/
  tests/
```

O módulo importável é `emporio_release_control`. A aplicação ASGI pública é:

```text
emporio_release_control.main:app
```

### 2.3 Identidades imutáveis

Constantes de código, não variáveis recebidas do cliente:

```text
mode       = publisher
repository = greggorio/abaronesa-emporio
owner      = greggorio
repo       = abaronesa-emporio
ref        = main
workflow   = publish-release.yml
api        = https://api.github.com
```

Nenhum header, claim, query, body ou configuração de runtime pode substituir
repository, owner, ref ou workflow. Somente a base URL da API GitHub pode ser
sobrescrita no profile `test`, para servidor HTTP local.

### 2.4 Correção contratual pré-runtime

O OpenAPI S06 contém uma incompatibilidade descoberta nesta auditoria: o POST
retorna `operationId` e pode manter `release=null`, porém a rota de polling
exige `{releaseId}`. A release SemVer ainda não existe enquanto a operação
está em `REQUESTED`, `VALIDATING` ou `PUBLISHING`.

Substituir, de forma coordenada:

```text
GET /api/release-publisher/v1/releases/{releaseId}/status
```

por:

```text
GET /api/release-publisher/v1/operations/{operationId}
```

Prescrições:

- `operationId` usa o schema já existente `OperationId`;
- `operationId` é o único path parameter;
- `operationId` do response deve ser igual ao path;
- resposta `200` continua `PublicationOperation`;
- respostas continuam `400`, `401`, `403`, `404`, `429`, `500`;
- role continua `release:read`;
- não manter alias legado, pois não existe consumidor runtime;
- atualizar OpenAPI, matriz de segurança, documento humano, validador S06 e
  testes S06 juntos;
- não alterar nenhuma rota deployer.

Essa correção é decisão do orquestrador e parte obrigatória da S15.

## 3. Fronteira de arquivos

### 3.1 Criar

```text
release_control/**
tools/releases/validate_publisher_runtime.py
tools/releases/tests/test_publisher_runtime_contract.py
docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md
docs/infrastructure/deployment/implementation/slices/S15-runtime-publisher-persistencia-reconciliacao-github.report.md
```

### 3.2 Alterar somente

```text
.github/workflows/publish-release.yml
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RELEASES.md
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/api/publisher.openapi.yml
docs/infrastructure/deployment/release-control/contracts/security-matrix.yml
tools/releases/release_control_contract.py
tools/releases/tests/test_release_control_contract.py
.gitignore
```

No workflow, a única mudança funcional autorizada é adicionar, imediatamente
após `name`, o título correlacionável:

```yaml
run-name: publish-release-${{ inputs.operation_id }}
```

`.gitignore` só pode receber caches/builds realmente produzidos pelo novo
módulo. Não ignore migrations, lockfile, exemplos ou testes.

### 3.3 Não alterar

- código comercial dos cinco serviços;
- Dockerfiles, Compose, gateway e configuração de produção;
- workflows `ci.yml` e `publish-candidate.yml`;
- helpers e schemas aceitos S05–S14;
- OpenAPI deployer e máquina de estados S06;
- qualquer mudança S06 diferente da substituição exata da Seção 2.4;
- migrations dos backends comerciais;
- tasks/reports anteriores;
- tracker;
- qualquer S16.

## 4. Configuração obrigatória

`Settings` deve falhar no bootstrap se faltar ou divergir:

```text
RELEASE_CONTROL_MODE=publisher
RELEASE_CONTROL_DB_HOST=...
RELEASE_CONTROL_DB_PORT=5432
RELEASE_CONTROL_DB_NAME=emporio_release_control
RELEASE_CONTROL_DB_USER=...
RELEASE_CONTROL_DB_PASSWORD=...
RELEASE_CONTROL_DB_SSLMODE=require
RELEASE_CONTROL_JWT_ISSUER=https://...
RELEASE_CONTROL_JWT_AUDIENCE=emporio-release-control
RELEASE_CONTROL_JWT_JWKS_URL=https://...
RELEASE_CONTROL_CORS_ORIGINS=https://...
RELEASE_CONTROL_GITHUB_APP_ID=...
RELEASE_CONTROL_GITHUB_INSTALLATION_ID=...
RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH=/run/secrets/...
RELEASE_CONTROL_HASH_PEPPER=...
```

Regras:

- `MODE` aceita somente `publisher`;
- DB host, port, name, user, password e sslmode formam a URL internamente;
- `DB_SSLMODE` aceita somente `require`, exceto profile `test`, que usa
  explicitamente `disable` contra PostgreSQL efêmero em loopback;
- origins são lista explícita HTTPS, sem `*`, credenciais ou path;
- private key é lida de arquivo, nunca de variável multiline;
- pepper possui ao menos 32 bytes e nunca é logado;
- `.env.example` contém apenas valores fictícios `.invalid`;
- nenhum segredo possui default;
- logs imprimem nomes de propriedades, nunca valores sensíveis.

Parâmetros com defaults fixos:

```text
RECONCILE_INTERVAL_SECONDS=15
DISPATCH_DISCOVERY_TIMEOUT_SECONDS=600
HTTP_CONNECT_TIMEOUT_SECONDS=3
HTTP_READ_TIMEOUT_SECONDS=10
GITHUB_MAX_PAGES=10
IDEMPOTENCY_RETENTION_DAYS=365
READ_RATE_PER_MINUTE=120
PUBLISH_RATE_PER_MINUTE=5
MAX_JSON_BODY_BYTES=16384
```

Validar intervalos conservadores. Valores zero, negativos ou acima de:
`60`, `3600`, `30`, `60`, `20`, `3650`, `600`, `60`, `65536`,
respectivamente, são rejeitados.

## 5. Persistência PostgreSQL

Alembic cria, no mínimo:

```text
rc_publication_operation
rc_idempotency_key
rc_candidate_snapshot
rc_release_snapshot
rc_audit_event
```

### 5.1 Operação

`rc_publication_operation` registra:

- `operation_id`: `pub_` + UUID v4 sem hífens, unique;
- tipo `PUBLICATION`, modo `publisher`;
- estado S06;
- ator (`sub`) e scopes usados;
- candidate ID e request completo;
- hashes de request/idempotência;
- target release/source commit, quando conhecidos;
- run ID/attempt/URL e estado remoto;
- timestamps UTC;
- erro com código estável e mensagem sanitizada;
- `dispatch_state`: `NOT_SENT`, `SENT`, `UNCERTAIN`, `CONFIRMED`;
- versão para optimistic locking;
- `active_slot`, valor `1` somente enquanto não terminal.

Índice parcial unique em `active_slot=1` limita a uma publicação não terminal.
Conflito retorna `409 VERSION_RESERVATION_CONFLICT`. Isso é deliberadamente
mais conservador que apenas reservar SemVer e complementa `concurrency` do
workflow.

Estados terminais liberam `active_slot` na mesma transação. Estado terminal
nunca regride.

### 5.2 Idempotência

Escopo unique:

```text
publisher | POST:/api/release-publisher/v1/releases | actor sub | HMAC da chave
```

- HMAC-SHA-256 usa `HASH_PEPPER`;
- request hash é SHA-256 do JSON canônico UTF-8 dos quatro campos;
- chave bruta nunca é persistida/logada;
- mesma chave + mesmo request retorna a mesma operação e header
  `Idempotency-Replayed: true`;
- mesma chave + request diferente retorna `409 IDEMPOTENCY_CONFLICT`;
- primeira criação retorna `false`;
- inserção de operação, chave, audit e reserva ocorre em uma transação;
- cleanup só remove chave expirada vinculada a operação terminal;
- operação e auditoria não são apagadas nesta slice.

### 5.3 Snapshots e auditoria

Snapshots armazenam somente objetos remotos integralmente validados. Um ciclo
inválido não substitui snapshot anteriormente válido; registra auditoria e
torna readiness indisponível até novo ciclo verde.

Auditoria é append-only e contém trace ID, ator, ação, resultado, IDs opacos e
metadata allowlisted. Não contém token, chave, JWT, private key, body remoto,
changelog integral ou stack trace.

## 6. Autenticação e transporte

### 6.1 JWT de entrada

- bearer stateless;
- assinatura aceita somente `RS256`;
- validar `iss`, `aud`, `exp`, `nbf` quando presente e `sub`;
- JWKS vem apenas da URL configurada;
- scopes vêm exclusivamente do claim string `scope`, separados por espaço;
- `release:read` autoriza GETs;
- `release:publish` autoriza POST e também implica leitura apenas nessa
  requisição;
- token ausente/inválido: `401`;
- scope insuficiente: `403`;
- health é público;
- CSRF desabilitado somente por ser bearer stateless.

Testes usam chave/JWKS efêmeros e servidor local; nenhum segredo de fixture é
reutilizável.

### 6.2 GitHub App de saída

Único mecanismo permitido: GitHub App installation token.

- gerar App JWT RS256 com `iat=now-60s`, `exp=now+540s`, `iss=appId`;
- trocar em
  `POST /app/installations/{installationId}/access_tokens`;
- aceitar somente `201` com token e `expires_at` válidos;
- cache somente em memória até 60 segundos antes da expiração;
- nunca persistir token;
- em `401` remoto, invalidar uma vez, obter novo token e repetir somente GET;
- POST de dispatch nunca é repetido automaticamente;
- headers fixos `Accept: application/vnd.github+json`,
  `X-GitHub-Api-Version: 2026-03-10` e User-Agent próprio;
- redirects são desabilitados;
- respostas têm limite de bytes;
- erros expostos são sanitizados.

O App necessita somente leitura de Actions/Contents e escrita de Actions para
workflow dispatch. O workflow usa seu próprio `GITHUB_TOKEN` e continua sendo
o único escritor de tag/Release.

## 7. Descoberta autoritativa

### 7.1 Candidatos

O sincronizador percorre, no máximo, dez páginas de runs concluídos e verdes
do workflow `Publish Candidate` em `main`. Para cada candidato:

- exige run, attempt, repository, event, branch, SHA e conclusão coerentes;
- exige exatamente um artifact `candidate-outcome`;
- aceita outcome `published` ou `already_published`;
- resolve exatamente um `candidate-manifest`, inclusive quando herdado;
- limita ZIP e entries;
- bloqueia path traversal, symlink, duplicidade e decompression bomb;
- valida digest REST, sidecar, JSON canônico, metadata e schema v2;
- valida todos os bindings run/attempt/SHA/artifact/candidate;
- exige os seis componentes e `deployable=false`;
- persiste `ciStatus=PASSED`, `manifestStatus=VALID`;
- classifica `READY` se o candidate não aparece em release válida;
- classifica `NOT_ELIGIBLE` se já foi publicado.

Run/artifact inválido é auditado e não vira snapshot. Paginação esgotada no
limite sem conclusão inequívoca falha fechada.

### 7.2 Releases

Sincronizar Releases GitHub e lightweight tags usando o contrato S14:

- rejeitar draft/prerelease inesperado;
- exigir exatamente `release.json`, `release.json.sha256`, `metadata.json`;
- validar tamanho, digest, canonicalidade, schema global, metadata, tag, SHA,
  candidate e cadeia `previousRelease`;
- exigir igualdade exata entre releases válidas e refs `v*`;
- gravar somente snapshots `PUBLISHED`;
- histórico inconsistente falha o ciclo inteiro.

## 8. API HTTP exata

Implementar somente:

```text
GET  /health/live
GET  /health/ready
GET  /api/release-control/v1/capabilities
GET  /api/release-publisher/v1/candidates
GET  /api/release-publisher/v1/releases
POST /api/release-publisher/v1/releases
GET  /api/release-publisher/v1/operations/{operationId}
```

Shapes, status e limites devem coincidir byte semanticamente com o OpenAPI.
Campos extras de request retornam `422 UNPROCESSABLE`.

`live` responde `200 {"status":"ok"}` se o processo atende HTTP.
`ready` responde `200` somente com:

- migrations na revisão esperada;
- query PostgreSQL verde;
- configuração GitHub App estruturalmente válida;
- ao menos um ciclo de sincronização remota verde desde o bootstrap;
- nenhum drift remoto não resolvido.

Caso contrário: `503 {"status":"unavailable"}`, sem motivo externo.

### 8.1 Paginação

Ordenação:

- candidatos: `createdAt DESC, candidateId ASC`;
- releases: SemVer numérico descendente.

Cursor é Base64URL sem padding de JSON canônico contendo chave da última linha
e HMAC-SHA-256 com o pepper. Cursor alterado, expirado estruturalmente ou de
tipo incorreto retorna `400 BAD_REQUEST`. Não use offset.

## 9. Publicação e reconciliação

### 9.1 POST

Ordem obrigatória:

1. autenticar/autorizar/rate-limit;
2. limitar content type e bytes;
3. validar request e idempotency key;
4. em transação curta, resolver replay/conflito já persistido;
5. se for replay, retornar sem rede;
6. exigir candidate snapshot `READY`;
7. revalidar o candidato remoto fora de transação;
8. abrir nova transação e repetir atomicamente idempotência, elegibilidade e
   active slot para eliminar corrida;
9. persistir `REQUESTED`, idempotência, reserva e audit;
10. commit;
11. despachar workflow.

Dispatch:

```text
POST /repos/greggorio/abaronesa-emporio/actions/workflows/publish-release.yml/dispatches
```

Body exato:

```json
{
  "ref": "main",
  "inputs": {
    "operation_id": "<operationId>",
    "candidate_id": "<candidateId>",
    "version_bump": "MAJOR|MINOR|PATCH",
    "description": "<description>",
    "changelog": "<changelog>"
  }
}
```

Somente HTTP `204` marca `SENT`. Timeout/erro de transporte após envio marca
`UNCERTAIN`; nunca redispatcha. Erro HTTP tipado marca `FAILED`.

Resposta do POST é sempre a operação local atual, `202`, inclusive replay.

### 9.2 Correlação

O workflow recebe:

```text
run-name: publish-release-${{ inputs.operation_id }}
```

O reconciliador procura somente runs `workflow_dispatch` do workflow fixo,
branch `main`, criados após `createdAt - 5s`, cujo `display_title` seja
exatamente esse valor.

- zero runs antes de 600s: manter `REQUESTED`;
- zero após 600s: `FAILED/WORKFLOW_DISPATCH_UNCONFIRMED`;
- mais de um: `FAILED/WORKFLOW_RUN_AMBIGUOUS`;
- um: validar identidade integral, persistir run e `CONFIRMED`.

### 9.3 Estados

- run confirmado não terminal: `REQUESTED -> VALIDATING`;
- job `publish` iniciado: `VALIDATING -> PUBLISHING`;
- caminho `already_published` pode registrar
  `VALIDATING -> PUBLISHING -> PUBLISHED` na mesma transação, com dois eventos;
- run concluído não verde: estado não terminal -> `FAILED`;
- run verde exige exatamente um artifact `release-publication-outcome`;
- outcome, sidecar, metadata, artifact e run devem vincular operation,
  candidate, run/attempt, release e source commit;
- somente então `PUBLISHING -> PUBLISHED`;
- snapshot da release e liberação do active slot ocorrem na mesma transação;
- outcome ausente/ambíguo/inválido termina `FAILED` com código estável;
- nenhuma evidência permite regressão de terminal.

Reconciliar no bootstrap e a cada 15 segundos. Use advisory lock PostgreSQL
fixo para que somente uma instância execute cada ciclo. Reinício com operações
não terminais deve convergir sem novo dispatch.

## 10. Segurança HTTP

- aceitar JSON somente no POST;
- rejeitar body acima de 16 KiB antes de parsear;
- CORS somente para origins configuradas e métodos/headers necessários;
- rate por ator: 120 GET/min e 5 POST/min;
- `ProblemDetails` com `application/problem+json`, código estável e trace ID;
- nunca retornar stack trace, exception, SQL, endpoint com token ou body GitHub;
- adicionar `X-Content-Type-Options: nosniff`, `Cache-Control: no-store` e
  `Referrer-Policy: no-referrer`;
- logging estruturado sem dados sensíveis.

Rate limit pode ser memória local porque o serviço terá uma única réplica nesta
fase; documentar essa restrição. Persistência distribuída fica fora do escopo.

## 11. Testes causais obrigatórios

Criar testes independentes, nomeados por comportamento, cobrindo ao menos:

1. bootstrap/configuração válida e cada variável inválida;
2. migrations em PostgreSQL real e idempotência do upgrade;
3. índices uniques/parciais e advisory lock;
4. JWT válido, issuer/audience/algoritmo/sub/scope inválidos;
5. 401, 403, CORS e headers;
6. payload/content type/extra fields/limites;
7. HMAC sem chave bruta e request canônico;
8. replay, conflito e corrida concorrente real;
9. candidate/release sync positivos;
10. paginação, ZIP malicioso, digest/sidecar/metadata/schema/binding inválidos;
11. GitHub App JWT/token/cache/expiração;
12. retry único de GET 401 e ausência de retry do POST;
13. dispatch 204, HTTP negativo e transporte ambíguo;
14. zero/um/múltiplos runs correlacionados;
15. todos os estados e transições proibidas;
16. run vermelho, outcome ausente, duplicado e adulterado;
17. sucesso `published` e `already_published`;
18. restart/reconcile sem redispatch;
19. terminal não regride;
20. readiness antes/depois de sync e após drift;
21. sanitização de logs/erros/auditoria;
22. inexistência das rotas deployer e de rotas extras.

Servidor fake deve escutar somente loopback e registrar requests para provar
endpoint, método, headers e ausência de retry. Bloquear qualquer socket não
loopback durante os testes.

## 12. Validador estrutural

`validate_publisher_runtime.py` deve falhar fechado se:

- faltar arquivo/rota/migration/lockfile;
- houver router deployer;
- constantes canônicas divergirem;
- GitHub App for substituído por token estático;
- workflow não tiver `run-name` exato;
- aparecer `subprocess`, `os.system`, Git local, `gh`, SSH ou Docker no runtime;
- segredo/default sensível aparecer;
- OpenAPI e rotas divergem;
- testes causais mínimos desaparecerem.

O validador possui mutantes próprios no arquivo de teste autorizado.

## 13. Documentação

`release_control/README.md` deve explicar:

- propósito e fronteira publisher;
- instalação com `uv sync --locked`;
- variáveis sem segredo real;
- migration;
- execução local;
- testes;
- segurança e troubleshooting sanitizado.

`RUNTIME_PUBLISHER.md` deve documentar:

- componentes internos;
- tabelas e transações;
- fluxo request -> dispatch -> reconcile;
- estados e códigos de falha;
- configuração GitHub App e permissões mínimas;
- JWT esperado;
- operação após restart;
- política de retenção;
- limites desta slice.

Atualizar os dois documentos canônicos permitidos para distinguir contrato
implementado de execução remota ainda não realizada.

## 14. Matriz final obrigatória

Executar e registrar comando, exit, resultado e interpretação:

```bash
cd /home/gregorio/git/baronesa/emporio/release_control
uv lock --check
uv run ruff check .
uv run mypy --strict src
uv run pytest -q
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90

cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools/releases/tests/test_publisher_runtime_contract.py -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py
docker run --rm -v "$PWD:/repo" -w /repo \
  docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 \
  -color=false .github/workflows/ci.yml \
  .github/workflows/publish-candidate.yml \
  .github/workflows/publish-release.yml
git diff --check
git diff --cached --name-only
git rev-parse --verify HEAD
git tag --list
git reflog
find . -type d -name __pycache__ -o -type f \
  \( -name '*.pyc' -o -name '*.pyo' \)
find .github/workflows -maxdepth 1 -type f \
  \( -name '*.yml' -o -name '*.yaml' \) -print | sort
```

Não ajuste o percentual para obter verde. Remova containers, redes, volumes,
caches e imagem actionlint criados nesta execução, listando alvos exatos.

## 15. Proibições

- nenhum `git add`, commit, tag ou push;
- nenhum acesso ao GitHub/GHCR/VPS/produção;
- nenhum token real;
- nenhum workflow remoto;
- nenhum release/artifact remoto;
- nenhum Dockerfile/Compose/UI/deployer;
- nenhum mock que substitua PostgreSQL nos testes transacionais;
- nenhum teste dependente de ordem;
- nenhuma alteração fora da fronteira.

Downloads de dependências PyPI e da imagem PostgreSQL de teste são permitidos.
Eles não autorizam chamadas à API GitHub real.

## 16. Relatório obrigatório

Criar o relatório previsto com:

- resumo e estado;
- arquivos alterados;
- versões resolvidas no lockfile;
- decisões executadas sem reinterpretá-las;
- schema/tabelas/índices;
- rotas e segurança;
- endpoints GitHub exercitados pelo fake;
- matriz de estados e falhas;
- lista nominal dos testes causais;
- comandos exatos, exits e resultados;
- falhas intermediárias e correções;
- evidência de zero rede GitHub real;
- estado Git/workflows/caches/resíduos;
- divergências e itens não determinados.

Estado final obrigatório:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED` e não criar S16.

## 17. Critérios de aceite do orquestrador

A S15 só será aceita se:

- runtime e documentação coincidirem com esta task e S06;
- Postgres sustentar idempotência/concorrência/restart;
- GitHub App for a única credencial outbound;
- nenhuma autoridade técnica vier do cliente;
- candidato/release/outcome forem validados antes de persistir sucesso;
- dispatch ambíguo nunca for repetido;
- transições forem monotônicas e auditadas;
- API e ProblemDetails coincidirem com OpenAPI;
- testes causais, cobertura, lint, typing e validadores passarem;
- zero estado remoto real for criado;
- fronteira e estado Git forem preservados.

## 18. Condições de bloqueio

Pare e documente, sem improvisar, se:

- o OpenAPI impedir uma implementação exata;
- a API GitHub não expuser `display_title` para `run-name`;
- artifact REST não fornecer digest/bindings exigidos;
- Postgres 16 não suportar o índice/lock especificado;
- alguma dependência exigir Python fora de `>=3.13,<3.14`;
- um teste tentar acessar rede não loopback;
- for necessário alterar contrato S06, helper S05–S14 ou produção.
