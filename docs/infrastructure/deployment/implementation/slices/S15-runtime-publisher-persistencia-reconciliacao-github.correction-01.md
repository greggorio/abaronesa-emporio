# S15 — Correção causal consolidada 01

> **Estado:** `IN_PROGRESS — seis divergências bloqueantes`  
> **Contrato-base:** `S15-runtime-publisher-persistencia-reconciliacao-github.task.md`  
> **Relatório a atualizar:** `S15-runtime-publisher-persistencia-reconciliacao-github.report.md`  
> **Próxima slice:** S16 continua bloqueada

## 1. Instrução

Corrija somente as seis divergências prescritas neste documento. Não escolha
novos status, códigos, rotas, limites, campos, tecnologias ou políticas.

Leia primeiro:

1. task S15 integral;
2. Seção 15 do relatório S15;
3. esta correção integral;
4. OpenAPI publisher e contrato humano S06;
5. `CANDIDATOS.md`, schemas de candidate/outcome/release;
6. validadores aceitos S12–S15.

## 2. Fronteira autorizada

Alterar somente se necessário:

```text
release_control/src/emporio_release_control/api.py
release_control/src/emporio_release_control/schemas.py
release_control/src/emporio_release_control/errors.py
release_control/src/emporio_release_control/github.py
release_control/src/emporio_release_control/service.py
release_control/src/emporio_release_control/artifacts.py
release_control/src/emporio_release_control/sync.py
release_control/src/emporio_release_control/reconciliation.py
release_control/tests/test_api.py
release_control/tests/test_persistence_service.py
release_control/tests/test_remote_contract.py
release_control/tests/test_reconciliation.py
tools/releases/validate_publisher_runtime.py
tools/releases/tests/test_publisher_runtime_contract.py
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md
docs/infrastructure/deployment/implementation/slices/S15-runtime-publisher-persistencia-reconciliacao-github.report.md
```

Não alterar:

- task S15, tracker ou S16;
- OpenAPI, máquina de estados ou matriz de segurança;
- migration, modelo/tabelas ou dependências;
- workflows;
- schemas/helpers S05–S14;
- código comercial, Docker, Compose ou produção.

## 3. Correção A — fronteira pública de erros

### 3.1 Enum pública

`ProblemDetails.code` deve aceitar somente:

```text
BAD_REQUEST
UNAUTHORIZED
FORBIDDEN
NOT_FOUND
IDEMPOTENCY_CONFLICT
VERSION_RESERVATION_CONFLICT
UNPROCESSABLE
RATE_LIMITED
INTERNAL_ERROR
SERVICE_UNAVAILABLE
```

Use `Literal` no model público. Código interno de GitHub, artifact, sync,
dispatch, banco ou estado nunca é devolvido ao cliente.

### 3.2 Mapeamento

O handler de `RuntimeFailure` preserva status/código somente para:

```text
400 BAD_REQUEST
401 UNAUTHORIZED
403 FORBIDDEN
404 NOT_FOUND
409 IDEMPOTENCY_CONFLICT
409 VERSION_RESERVATION_CONFLICT
422 UNPROCESSABLE
429 RATE_LIMITED
```

Qualquer outro `RuntimeFailure` vira:

```text
500 INTERNAL_ERROR
```

Não devolver `502`, `GITHUB_*`, `WORKFLOW_*`, `SCHEMA_*`,
`CANDIDATE_*`, `RELEASE_*`, SQL ou exception text.

Adicionar handler final para `Exception`, também como `500 INTERNAL_ERROR`.
Ele não inclui stack/body no response. Testes devem criar a app com
`raise_server_exceptions=False` e comprovar o JSON.

### 3.3 Erros de validação FastAPI

- request validation em `GET`: `400 BAD_REQUEST`;
- request validation em `POST`: `422 UNPROCESSABLE`;
- manter os comportamentos manuais já prescritos;
- nenhum endpoint deve emitir status não documentado por seu OpenAPI em
  condições cobertas pelo contrato.

### 3.4 Provas

Adicionar provas independentes para:

1. `RuntimeFailure("GITHUB_RESPONSE_INVALID")` no POST;
2. `RemoteHttpFailure(503)` na revalidação;
3. `RuntimeError` não tipado no serviço;
4. query inválida nos dois GETs paginados;
5. enum e shape exatos de todos os `ProblemDetails`;
6. ausência dos textos internos no body.

## 4. Correção B — fronteira da tentativa de dispatch

Separar explicitamente:

```text
PRE_DISPATCH_FAILED
DISPATCH_UNCERTAIN
DISPATCH_REJECTED
DISPATCH_SENT
```

Não é necessário persistir esses quatro nomes; eles definem a classificação.

### 4.1 Regras fechadas

- falha ao gerar App JWT, ler/parsear chave, obter token, validar token ou
  preparar headers ocorre antes do POST de dispatch;
- falha pré-dispatch termina a operação como
  `FAILED/WORKFLOW_DISPATCH_NOT_SENT`;
- resposta HTTP do dispatch diferente de `204` termina como
  `FAILED/WORKFLOW_DISPATCH_REJECTED`;
- somente erro de transporte lançado pela chamada HTTP do dispatch, depois
  de iniciada a tentativa, marca `dispatch_state=UNCERTAIN` e mantém
  `REQUESTED`;
- `204` marca `SENT`;
- nenhuma dessas falhas propaga para transformar o POST público em outra
  resposta: depois que a operação foi persistida, o endpoint retorna `202`
  com o estado local atual;
- nunca repetir o dispatch.

Uma exceção tipada pode carregar `attempted`/`uncertain`, ou o cliente pode
separar preparação e envio. A semântica acima é obrigatória.

### 4.2 Provas

Cobrir separadamente:

1. private key inválida;
2. token endpoint com transporte falho;
3. token `201` com shape inválido;
4. token endpoint HTTP negativo;
5. transporte falho no POST real de dispatch;
6. dispatch HTTP `403`;
7. dispatch `204`;
8. contagem de POSTs igual a zero ou um conforme a fase;
9. slot liberado somente nos casos terminais;
10. replay posterior não redispatcha.

## 5. Correção C — candidato próprio e herdado

### 5.1 Manifesto próprio

No outcome `published`, a lista do run deve conter exatamente um artifact
com nome `candidate-manifest`. Esse único artifact precisa ter o mesmo ID e
digest do outcome.

Segundo artifact com o mesmo nome, ainda que com outro ID, falha
`CANDIDATE_ARTIFACT_INVALID`.

### 5.2 Run herdado

No outcome `already_published`, buscar o run dono do artifact herdado e
validar integralmente com o mesmo contrato de um run `Publish Candidate`:

```text
id positivo
run_attempt positivo
name = Publish Candidate
event = workflow_run
status = completed
conclusion = success
head_branch = main
head_sha canônico
repository.full_name = greggorio/abaronesa-emporio
head_repository.full_name = greggorio/abaronesa-emporio
created_at UTC válido
```

Exigir também:

- artifact `workflow_run.id/head_sha` igual ao run validado;
- candidate manifest metadata/run/attempt/SHA igual ao run validado;
- artifact ID/digest igual ao outcome;
- candidate ID do manifesto igual ao outcome.

Não confiar em `run_attempt` e SHA isolados.

### 5.3 Predecessor

Para `published`:

```text
outcome.predecessorCandidateId
==
candidate.manifest.predecessor.candidateId
```

Isso inclui `null` no primeiro candidato.

Para `already_published`, preservar:

```text
outcome.predecessorCandidateId == outcome.candidateId
```

Não aplicar a regra de `published` ao caminho `already_published`.

### 5.4 Provas

Adicionar mutantes para:

- segundo `candidate-manifest`;
- run herdado vermelho;
- run herdado não concluído;
- workflow/event/branch/repository/head repository divergentes;
- attempt/SHA/artifact divergentes;
- predecessor `published` nulo/incorreto;
- caminho positivo `published`;
- caminho positivo `already_published`.

## 6. Correção D — bindings do outcome de publicação

Além do schema e bindings existentes, exigir:

```text
outcome.workflow.url
==
https://github.com/greggorio/abaronesa-emporio/actions/runs/{runId}

outcome.githubRelease.tagName == outcome.release

outcome.githubRelease.url
==
https://github.com/greggorio/abaronesa-emporio/releases/tag/{outcome.release}
```

Depois do sync de releases, preservar as provas já existentes:

```text
snapshot.release == outcome.release
snapshot.candidateId == operation.candidateId
snapshot.sourceCommit == outcome.sourceCommit
digest(snapshot.manifest) == outcome.manifestSha256
```

Adicionar um mutante independente para cada igualdade nova. Nenhum mutante
pode falhar apenas pelo schema se a intenção é provar o binding cruzado.

## 7. Correção E — assets da release

Usar os limites e MIME types já aceitos na S14:

| Asset | Limite inclusivo | `content_type` |
|---|---:|---|
| `release.json` | 2 MiB | `application/json` |
| `release.json.sha256` | 128 bytes | `text/plain` |
| `metadata.json` | 16 KiB | `application/json` |

Antes de usar qualquer asset ID:

- validar os três registros integralmente;
- `size` deve ser inteiro, não boolean, e `1 <= size <= limite`;
- `content_type` deve ser exato;
- nomes/IDs continuam únicos;
- URL, estado e digest continuam obrigatórios.

Somente depois baixar os três assets e conferir tamanho real/digest.

Adicionar mutantes para MIME divergente, limite excedido e `size=true`.

## 8. Correção F — documentação canônica

Atualizar `CONTRATO_API_ESTADOS_SEGURANCA.md` sem reescrever o histórico:

- publisher usa Python 3.13/FastAPI;
- publisher persiste em PostgreSQL 16 via SQLAlchemy/Alembic;
- publisher valida JWT RS256 por issuer/audience/JWKS;
- publisher usa exclusivamente GitHub App;
- idempotência publisher retém 365 dias por padrão;
- limites/rates/CORS publisher estão implementados;
- proibições de Git/Docker/SSH são comportamento atual do publisher;
- UI, deployer, credenciais/implantação reais e operação remota continuam
  pendentes.

Remover da seção `Decisões pendentes` somente aquilo que a S15 decidiu.
Não alterar os placeholders machine-readable do deployer nem alegar produção
ativa.

Atualizar `RUNTIME_PUBLISHER.md` apenas para refletir as correções A–E.

## 9. Validador

Fortalecer o validador/mutantes S15 para detectar, no mínimo:

- ausência do normalizador público `INTERNAL_ERROR`;
- retorno público de `GITHUB_*`/`502`;
- remoção da validação integral do run herdado;
- remoção do binding predecessor;
- remoção dos limites/MIME dos assets;
- retorno da documentação às decisões publisher “pendentes”.

O validador não deve depender apenas de busca por um comentário ou nome de
teste; cada mutante deve alterar a superfície causal correspondente.

## 10. Matriz obrigatória

Executar e registrar:

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

Actionlint não precisa ser repetido: nenhum workflow está autorizado nesta
correção.

## 11. Relatório

Acrescentar ao relatório S15 uma resposta individual às correções A–F:

- arquivo/linhas;
- comportamento anterior e final;
- testes causais nominais;
- comandos/exits/resultados;
- falhas intermediárias;
- estado Git, caches, recursos efêmeros e rede.

Estado final:

```text
IN_PROGRESS — aguardando nova revisão do orquestrador
```

Não declarar `ACCEPTED` e não criar S16.

## 12. Bloqueios

Pare sem improvisar se:

- o OpenAPI precisar receber novo status/código;
- o candidate outcome aceito não permitir o binding de predecessor;
- o REST não fornecer os campos do run herdado ou dos assets já usados em
  S12/S14;
- a correção exigir migration, dependência, workflow ou schema;
- algum teste tentar usar rede não loopback;
- o índice Git deixar de estar vazio.
