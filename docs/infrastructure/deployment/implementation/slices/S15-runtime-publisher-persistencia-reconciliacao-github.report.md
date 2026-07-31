# S15 — Runtime publisher, persistência, reconciliação e GitHub

## 1. Estado

Execução integral no CWD obrigatório:

```text
/home/gregorio/git/baronesa/emporio
```

Foi implementado o runtime local exclusivamente publisher. Não houve
credencial real, acesso à API GitHub/GHCR/VPS/produção, workflow remoto,
release, artifact remoto, `git add`, commit, tag ou push.

## 2. Resultado entregue

- runtime Python `>=3.13,<3.14` com FastAPI, Uvicorn e lock `uv`;
- configuração fail-closed, sem defaults sensíveis;
- PostgreSQL 16 real nos testes, Alembic e persistência transacional;
- JWT RS256/JWKS, scopes separados e rate limit por ator;
- GitHub App como única credencial outbound;
- descoberta fail-closed de candidatos e releases;
- dispatch fixo e reconciliação restart-safe por `operationId`;
- API publisher exata, ProblemDetails e headers de segurança;
- validador estrutural com seis provas mutantes;
- correção coordenada do polling S06;
- documentação operacional e arquitetural.

O workflow recebeu exatamente:

```yaml
run-name: publish-release-${{ inputs.operation_id }}
```

O runtime correlaciona esse valor exclusivamente com `display_title` no
workflow run remoto.

## 3. Arquivos criados

```text
release_control/.env.example
release_control/README.md
release_control/alembic.ini
release_control/pyproject.toml
release_control/uv.lock
release_control/migrations/env.py
release_control/migrations/script.py.mako
release_control/migrations/versions/0001_publisher_runtime.py
release_control/src/emporio_release_control/__init__.py
release_control/src/emporio_release_control/api.py
release_control/src/emporio_release_control/artifacts.py
release_control/src/emporio_release_control/config.py
release_control/src/emporio_release_control/constants.py
release_control/src/emporio_release_control/errors.py
release_control/src/emporio_release_control/github.py
release_control/src/emporio_release_control/main.py
release_control/src/emporio_release_control/persistence.py
release_control/src/emporio_release_control/reconciliation.py
release_control/src/emporio_release_control/schemas.py
release_control/src/emporio_release_control/security.py
release_control/src/emporio_release_control/service.py
release_control/src/emporio_release_control/sync.py
release_control/tests/conftest.py
release_control/tests/test_api.py
release_control/tests/test_config_security.py
release_control/tests/test_persistence_service.py
release_control/tests/test_reconciliation.py
release_control/tests/test_remote_contract.py
tools/releases/validate_publisher_runtime.py
tools/releases/tests/test_publisher_runtime_contract.py
docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md
docs/infrastructure/deployment/implementation/slices/S15-runtime-publisher-persistencia-reconciliacao-github.report.md
```

## 4. Arquivos alterados

```text
.github/workflows/publish-release.yml
.gitignore
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/RELEASES.md
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/api/publisher.openapi.yml
docs/infrastructure/deployment/release-control/contracts/security-matrix.yml
tools/releases/release_control_contract.py
tools/releases/tests/test_release_control_contract.py
```

A task S15, o tracker e os workflows `ci.yml` e `publish-candidate.yml` não
foram alterados. Nenhum artefato fora da fronteira autorizada foi modificado.

## 5. Dependências resolvidas

`uv lock --check` resolveu 57 pacotes. Dependências diretas:

```text
alembic 1.18.5
fastapi 0.141.0
httpx 0.28.1
jsonschema 4.26.0
psycopg[binary] 3.3.4
pydantic-settings 2.14.2
pyjwt[crypto] 2.13.0
sqlalchemy 2.0.51
uvicorn 0.52.0
mypy 1.20.2
pytest 8.4.2
pytest-cov 6.3.0
ruff 0.16.0
testcontainers 4.15.0
types-jsonschema 4.26.0.20260518
```

Não houve instalação global. O ambiente `.venv` criado por `uv` foi removido
após a matriz.

## 6. Persistência e concorrência

Alembic cria:

```text
rc_publication_operation
rc_idempotency_key
rc_candidate_snapshot
rc_release_snapshot
rc_audit_event
rc_sync_state
```

Foram comprovados:

- upgrade idempotente na revisão `0001_publisher_runtime`;
- unicidade do escopo HMAC da idempotência;
- índice partial unique `active_slot=1`;
- optimistic locking da operação;
- advisory lock PostgreSQL entre conexões reais;
- audit append-only por trigger para UPDATE e DELETE;
- limpeza apenas de chave expirada associada a operação terminal;
- uma operação para duas requisições concorrentes com a mesma chave/request;
- conflito para payload divergente e para publicação ativa distinta.

A operação, a chave HMAC, o request canônico, a reserva e o primeiro audit são
gravados numa única transação. O dispatch ocorre somente após commit.

## 7. Rotas e segurança HTTP

Rotas implementadas, sem aliases ou rotas extras:

```text
GET  /health/live
GET  /health/ready
GET  /api/release-control/v1/capabilities
GET  /api/release-publisher/v1/candidates
GET  /api/release-publisher/v1/releases
POST /api/release-publisher/v1/releases
GET  /api/release-publisher/v1/operations/{operationId}
```

O polling S06 usa somente `OperationId`, response `PublicationOperation`,
role `release:read` e os status `200/400/401/403/404/429/500`.

JWT aceita somente RS256, issuer e audience fixos, `exp`, `sub` e scope.
CORS usa allowlist HTTPS. O POST autentica/autoriza/limita taxa antes de ler e
validar JSON, rejeita corpo acima de 16 KiB e campos extras. Respostas possuem
`nosniff`, `no-store` e `no-referrer`. Cursor usa JSON canônico, Base64URL sem
padding e HMAC-SHA-256.

## 8. GitHub App e endpoints exercitados

O transporte usa os headers fixos prescritos e apenas identidades imutáveis.
O fake registrou chamadas aos seguintes shapes:

```text
POST /app/installations/{installationId}/access_tokens
POST /repos/greggorio/abaronesa-emporio/actions/workflows/publish-release.yml/dispatches
GET  /repos/greggorio/abaronesa-emporio/actions/workflows/publish-candidate.yml/runs
GET  /repos/greggorio/abaronesa-emporio/actions/workflows/publish-release.yml/runs
GET  /repos/greggorio/abaronesa-emporio/actions/runs/{runId}
GET  /repos/greggorio/abaronesa-emporio/actions/runs/{runId}/jobs
GET  /repos/greggorio/abaronesa-emporio/actions/runs/{runId}/artifacts
GET  /repos/greggorio/abaronesa-emporio/actions/artifacts/{artifactId}
GET  /repos/greggorio/abaronesa-emporio/actions/artifacts/{artifactId}/zip
GET  /repos/greggorio/abaronesa-emporio/releases
GET  /repos/greggorio/abaronesa-emporio/releases/assets/{assetId}
GET  /repos/greggorio/abaronesa-emporio/git/matching-refs/tags/v
```

Um servidor HTTP real de teste escutou somente em `127.0.0.1`, registrou
método/path/Accept/User-Agent e confirmou dois POSTs: aquisição do token e uma
única tentativa de dispatch recusada. Um guard autouse bloqueou sockets TCP
não loopback em todos os testes; a prova causal rejeitou `192.0.2.1`.
MockTransport foi mantido para mutantes determinísticos. Nenhum host GitHub
real foi resolvido ou acessado.

GET `401` invalida token e tenta exatamente uma vez novamente, inclusive em
download binário. POST nunca é repetido. Transporte incerto marca
`UNCERTAIN`; resposta HTTP negativa marca `FAILED`.

## 9. Descoberta, artifacts e reconciliação

Candidato exige run verde, exatamente um outcome, manifesto próprio ou
herdado, seis componentes, `deployable=false`, digest REST, sidecar,
canonicalidade, metadata, schema e bindings run/attempt/SHA/artifact.

Release exige conjunto exato de três assets, size/digest, manifesto global,
metadata, tag lightweight completa e igualdade exata entre releases e refs.
Um ciclo inválido preserva snapshots anteriores, grava drift/audit e derruba
readiness até ciclo verde.

Reconciliação usa:

```text
REQUESTED -> VALIDATING -> PUBLISHING -> PUBLISHED
     |             |              |
     +-------------+--------------+-> FAILED
```

No bootstrap e a cada intervalo, um advisory lock limita a uma instância.
Primeiro sincroniza releases e depois candidatos, classifica elegibilidade,
limpa idempotências expiradas elegíveis e reconcilia operações não terminais.
Restart reconsulta evidência e nunca redispatcha. Estados terminais não
regridem.

## 10. Provas causais

A suíte runtime possui 83 casos resolvidos a partir dos testes nominais:

- `test_settings_reject_each_invalid_value`;
- `test_runtime_profile_fixes_github_and_ssl`;
- `test_private_key_is_read_from_file_only`;
- `test_jwt_valid_and_scope_exclusively_from_scope_claim`;
- `test_jwt_rejects_identity_algorithm_and_claim_mutants`;
- `test_cursor_is_canonical_signed_and_tamper_evident`;
- `test_rate_limit_is_per_actor_and_window`;
- `test_migration_upgrade_is_idempotent_and_tables_indexes_exist`;
- `test_advisory_lock_is_exclusive_across_real_connections`;
- `test_create_dispatch_and_replay_never_persist_raw_key`;
- `test_idempotency_conflict_and_active_slot_conflict`;
- `test_concurrent_same_request_returns_one_operation`;
- `test_dispatch_failure_classification`;
- `test_transition_is_monotonic_and_terminal_releases_slot`;
- `test_audit_table_is_database_append_only`;
- `test_candidate_and_release_keyset_pagination`;
- `test_missing_operation_mutations_are_sanitized`;
- `test_exact_routes_and_public_health`;
- `test_authentication_authorization_and_capabilities`;
- `test_candidate_release_lists_and_tampered_cursor`;
- `test_post_validation_idempotency_and_polling_by_operation`;
- `test_post_rejects_content_type_missing_fields_and_key`;
- `test_extra_field_body_limit_and_cors`;
- `test_readiness_after_both_green_syncs`;
- `test_artifact_positive_bundles_and_low_level_identifiers`;
- `test_candidate_bundle_rejects_sidecar_metadata_and_extra`;
- `test_zip_rejects_path_traversal`;
- `test_zip_rejects_symlink_digest_size_and_bomb`;
- `test_github_app_token_cache_get_retry_pagination_and_dispatch`;
- `test_github_transport_rejects_endpoint_http_and_exhausted_pagination`;
- `test_github_response_mutants`;
- `test_github_app_token_fail_closed`;
- `test_github_binary_success_and_invalid_page_shape`;
- `test_loopback_fake_records_exact_transport_and_no_retry`;
- `test_candidate_and_release_sync_green_and_drift`;
- `test_inherited_candidate_artifact_is_bound_to_its_own_run`;
- `test_sync_scalar_validators_fail_closed`;
- `test_release_sync_rejects_ref_and_asset_binding`;
- `test_outcome_valid_and_canonical_binding_mutants`;
- `test_zero_run_before_and_after_timeout`;
- `test_multiple_run_and_red_run_fail`;
- `test_nonterminal_run_advances_and_restart_does_not_dispatch`;
- `test_success_requires_single_valid_outcome_and_release`;
- `test_missing_outcome_and_invalid_run_are_fail_closed`;
- `test_cycle_runs_both_syncs_and_converts_runtime_failure`;
- `test_reconcile_loop_bootstrap_periodic_failure_and_stop`.

Os parâmetros/mutantes expandem esses nomes para 83 casos. O validador
estrutural acrescenta seis casos próprios. A descoberta histórica completa
executou 236 testes `unittest`.

## 11. Falhas intermediárias e correções

1. Primeira suíte: `10 passed, 37 errors`, exit `1`. Causa: leitura
   case-sensitive não reconhecia os nomes de ambiente maiúsculos prescritos.
   Correção: env case-insensitive sem aceitar campos extras.
2. Segunda suíte: `40 passed, 7 failed`, exit `1`. Causa: flush ORM podia
   inserir a FK de idempotência antes da operação. Correção: flush explícito
   da operação.
3. Suíte focal: `16 passed, 2 failed`, exit `1`. Causas: corrida da mesma
   chave e expectativa antiga de erro HTTP. Correções: advisory transaction
   lock por escopo idempotente e resposta local `202` também no FAILED.
4. Mutante ZIP: `9 passed, 1 failed`, exit `1`. O fixture não estava
   comprimindo a entry. O fixture foi corrigido; a proteção contra
   decompression bomb passou.
5. Reconciliação: `5 passed, 2 failed`, exit `1`. Um mutante alterava bytes
   comprimidos sem efeito e o restart tentava regredir PUBLISHING para
   VALIDATING. Correções: mutante causal do metadata e avanço monotônico.
6. Validador estrutural: `5 passed, 1 failed`, exit `1`. O mutante YAML havia
   inserido `/extra` fora de `paths`. O mutante passou a alterar o nó causal.
7. Mypy detectou tipo de `rowcount` no cleanup. A implementação passou a
   selecionar IDs elegíveis, apagar esse conjunto fechado e retornar seu
   tamanho.

Nenhuma correção ampliou a arquitetura ou alterou artefato proibido.

## 12. Matriz final

CWD `/home/gregorio/git/baronesa/emporio/release_control`:

| Comando exato | Exit | Resultado |
|---|---:|---|
| `uv lock --check` | 0 | 57 pacotes resolvidos; lock íntegro |
| `uv run ruff check .` | 0 | all checks passed |
| `uv run mypy --strict src` | 0 | 14 arquivos, zero issue |
| `uv run pytest -q` | 0 | 83 testes aprovados |
| `uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90` | 0 | 83 aprovados; cobertura branch 90,17% |

CWD `/home/gregorio/git/baronesa/emporio`:

| Comando exato | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py` | 0 | `publisher-runtime:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/releases/tests/test_publisher_runtime_contract.py -v` | 0 | 6/6 mutantes |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 236 testes |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate` | 0 | contrato válido |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py` | 0 | workflow válido |
| `docker run --rm -v "$PWD:/repo" -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color=false .github/workflows/ci.yml .github/workflows/publish-candidate.yml .github/workflows/publish-release.yml` | 0 | três workflows válidos |

O actionlint não existia localmente (`docker image inspect`, exit `1`), foi
baixado pelo comando prescrito e removido pelo digest exato (`docker image rm`,
exit `0`). A imagem PostgreSQL 16 já existia localmente. Testcontainers
removeu os containers/redes/volumes desta execução. Recursos Docker antigos
e alheios foram somente inventariados, sem alteração.

## 13. Estado protegido e limpeza

Após a validação:

- `.venv`, `.coverage`, `.pytest_cache`, `.mypy_cache`, `.ruff_cache`,
  `__pycache__`, `*.pyc` e `*.pyo` criados nesta execução foram removidos;
- nenhum container efêmero desta execução permaneceu;
- a imagem actionlint criada nesta execução foi removida;
- permanecem exatamente `ci.yml`, `publish-candidate.yml` e
  `publish-release.yml`;
- índice Git real vazio;
- HEAD inexistente;
- tags vazias e reflog inexistente;
- nenhum commit, tag, push, publicação ou execução remota;
- nenhuma S16 criada.

As referências oficiais usadas pelo contrato foram confirmadas: `run-name`
aceita inputs e o REST de workflow runs expõe `display_title`. Isso sustenta a
correlação por `operationId` sem depender da SemVer futura.

## 14. Divergências

Nenhuma divergência conhecida em relação ao contrato S15. Os cinco warnings
da suíte são deprecation/insecure-key-length deliberadamente causados por
fixtures e não representam falha funcional ou relaxamento de validação.

Verificações finais protegidas:

| Comando | Exit | Interpretação |
|---|---:|---|
| `git diff --check` | 0 | sem erro de whitespace |
| `git diff --cached --name-only` | 0 | saída vazia; índice real vazio |
| `git rev-parse --verify HEAD` | 128 | HEAD inexistente, como requerido |
| `git tag --list` | 0 | saída vazia |
| `git reflog` | 128 | reflog inexistente no repositório unborn |
| busca de `__pycache__`, `*.pyc`, `*.pyo` | 0 | saída vazia |
| inventário de workflows | 0 | exatamente os três arquivos esperados |
| `docker image inspect` do actionlint | 1 | imagem removida após uso |

IN_PROGRESS — aguardando revisão do orquestrador

---

## 15. Revisão do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS — correção causal consolidada requerida`  
> **Data:** `2026-07-29`

O orquestrador revisou o relatório, o runtime, a migration, os contratos
alterados e as provas persistidas sem repetir as suítes do executor. A
fundação, a fronteira e a maior parte do comportamento coincidem com a task,
mas seis divergências objetivas ainda impedem o aceite:

1. `api.py` serializa qualquer `RuntimeFailure` diretamente. Falhas internas
   da revalidação podem expor códigos `GITHUB_*` e status `502`, embora o
   OpenAPI publique somente a enum pública e não declare `502`. Exceções não
   tipadas ainda caem no handler padrão, fora de `ProblemDetails`.
2. `service.py` trata qualquer `RemoteTransportFailure` como `UNCERTAIN`.
   Falha ao obter/validar o installation token ocorre antes da tentativa de
   dispatch e não pode ser classificada como envio ambíguo. Outras falhas
   pré-dispatch podem deixar a operação ativa sem resposta local `202`.
3. O caminho `already_published` valida apenas `id`, `attempt` e SHA do run
   herdado; não comprova workflow, evento, branch, conclusão e repositórios.
   O caminho `published` também aceita um segundo artifact
   `candidate-manifest` e não liga `predecessorCandidateId` ao predecessor do
   manifesto.
4. O outcome de publicação não cruza `workflow.url` com o run nem
   `githubRelease.tagName/url` com `release`.
5. Releases aceitam `content_type` divergente e não aplicam os limites
   específicos de 2 MiB/128 B/16 KiB já definidos pela S14.
6. `CONTRATO_API_ESTADOS_SEGURANCA.md` ainda afirma que framework,
   persistência, JWT, GitHub App, retenção e limites permanecem pendentes,
   contradizendo o runtime S15 e o próprio cabeçalho do documento.

A correção fechada está em:

```text
S15-runtime-publisher-persistencia-reconciliacao-github.correction-01.md
```

Esses pontos derivam de requisitos já escritos nas Seções 6, 7, 9, 10, 11 e
13 da task S15. Não há rejeição por escolha arquitetural omitida.

Decisão:

```text
S15 IN_PROGRESS
S16 bloqueada
```

## 16. Resposta à correção causal consolidada 01

> **Data da execução:** `2026-07-29`  
> **CWD obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Resultado:** correções A–F implementadas e matriz integral verde.

### 16.1 Resposta individual A — fronteira pública de erros

- `schemas.py:67-85` fecha `ProblemDetails.code` nos dez códigos públicos.
- `errors.py:38-59` preserva somente os oito pares públicos definidos e converte
  qualquer outra falha em `500 INTERNAL_ERROR`, com título fixo.
- `api.py:52-64,95-119` normaliza todas as `RuntimeFailure`, captura
  `Exception`, separa GET inválido em 400 e POST inválido em 422 e não devolve
  status Starlette não documentado.
- `test_api.py` comprova independentemente `GITHUB_RESPONSE_INVALID`,
  `RemoteHttpFailure(503)`, exceção não tipada, os dois GETs, enum/shape
  públicos e ausência de texto interno.

Comportamento final: nenhum código GitHub, workflow, schema, candidato,
release, SQL, status 502 ou texto de exceção alcança o ProblemDetails público.

### 16.2 Resposta individual B — tentativa de dispatch

- `errors.py:32-34` introduz a falha interna pré-dispatch
  `WORKFLOW_DISPATCH_NOT_SENT`.
- `github.py:184-208` prepara JWT/token/headers antes da tentativa e encapsula
  toda falha dessa fase; somente transporte durante o POST real produz
  `uncertain=True`.
- `service.py:214-225` classifica pré-dispatch e transporte não incerto como
  terminal `NOT_SENT`, transporte real como `UNCERTAIN`, HTTP negativo como
  rejeitado e 204 como `SENT`.
- As provas contam separadamente POST de token e POST de dispatch para chave
  inválida, transporte/HTTP/shape do token, transporte/403/204 do dispatch.
  As provas de serviço verificam slot, término e replay sem redispatch; as
  provas HTTP confirmam resposta 202 após persistência.

### 16.3 Resposta individual C — candidato próprio e herdado

- `sync.py:149-202` exige exatamente um `candidate-manifest` próprio, valida o
  run herdado integralmente por `_run`, cruza artifact/run/attempt/SHA e aplica
  ao caminho `published` o binding do predecessor, preservando a regra própria
  de `already_published`.
- Os testes cobrem duplicidade, caminho positivo próprio e herdado, predecessor
  incorreto, run vermelho/não concluído e mutações de workflow, evento, branch,
  repositórios, attempt e SHA.

### 16.4 Resposta individual D — outcome de publicação

- `reconciliation.py:76-94` cruza `workflow.url`, `githubRelease.tagName` e
  `githubRelease.url` com run e release.
- As provas usam três outcomes canônicos e schema-valid com identidades
  divergentes, isolando cada causa. Permanecem os bindings posteriores de
  release, candidato, source commit e digest do manifesto sincronizado.

### 16.5 Resposta individual E — assets da release

- `sync.py:32-36,288-328` define MIME e limites inclusivos exatos:
  `release.json` 2 MiB/JSON, sidecar 128 B/texto e metadata 16 KiB/JSON.
- A primeira passagem valida os três registros, IDs e nomes únicos, `size`
  inteiro e não booleano, estado, URL, MIME e digest. Somente depois a segunda
  passagem usa endpoints e confere tamanho/digest dos bytes.
- Mutantes de MIME, excesso de limite, `size=true` e ID duplicado comprovam
  zero download antes da rejeição.

### 16.6 Resposta individual F — documentação e validador

- `CONTRATO_API_ESTADOS_SEGURANCA.md` registra como implementados Python 3.13,
  FastAPI, PostgreSQL 16, SQLAlchemy 2, Alembic, Psycopg 3, JWT RS256/JWKS,
  GitHub App exclusiva, retenção de 365 dias, limites/rates e CORS HTTPS.
- Permanecem pendentes somente UI, deployer, credenciais/implantação reais e
  execução remota/produção.
- `RUNTIME_PUBLISHER.md` documenta explicitamente A–E sem alegar execução
  remota.
- `validate_publisher_runtime.py` valida causalmente enum/normalização,
  dispatch, candidato herdado/predecessor, outcome, assets e decisões
  documentais. Seus 15 testes incluem mutantes reais dessas expressões,
  branches, constantes e seção documental.

### 16.7 Arquivos alterados

```text
release_control/src/emporio_release_control/api.py
release_control/src/emporio_release_control/errors.py
release_control/src/emporio_release_control/github.py
release_control/src/emporio_release_control/reconciliation.py
release_control/src/emporio_release_control/schemas.py
release_control/src/emporio_release_control/service.py
release_control/src/emporio_release_control/sync.py
release_control/tests/test_api.py
release_control/tests/test_persistence_service.py
release_control/tests/test_reconciliation.py
release_control/tests/test_remote_contract.py
tools/releases/validate_publisher_runtime.py
tools/releases/tests/test_publisher_runtime_contract.py
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md
docs/infrastructure/deployment/implementation/slices/S15-runtime-publisher-persistencia-reconciliacao-github.report.md
```

Nenhum arquivo fora da fronteira autorizada foi alterado.

### 16.8 Falha intermediária e correção

O primeiro `uv run ruff check .` retornou exit `1` exclusivamente por ordem de
imports em `api.py`. A ordem foi corrigida manualmente; a repetição retornou
exit `0`. O teste causal focado anterior à matriz retornou `91 passed`. Na
limpeza, o primeiro `find ... -delete` retornou exit `1` porque os diretórios
de cache ainda continham arquivos de metadata não abrangidos pelo predicado;
uma segunda remoção, limitada aos três diretórios exatos já inventariados,
retornou exit `0`.

### 16.9 Matriz obrigatória

Todos os comandos foram executados no CWD indicado:

| Comando | Exit | Resultado |
|---|---:|---|
| `cd release_control && uv lock --check` | 0 | 57 pacotes resolvidos; lock íntegro |
| `cd release_control && uv run ruff check .` | 0 | all checks passed |
| `cd release_control && uv run mypy --strict src` | 0 | 14 arquivos sem issues |
| `cd release_control && uv run pytest -q` | 0 | 120 passed |
| `cd release_control && uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90` | 0 | 120 passed; 90,98% |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_publisher_runtime.py` | 0 | `publisher-runtime:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools/releases/tests/test_publisher_runtime_contract.py -v` | 0 | 15 passed |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | 245 passed |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `docker run --rm -v "$PWD:/repo" -w /repo docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 -color=false .github/workflows/ci.yml .github/workflows/publish-candidate.yml .github/workflows/publish-release.yml` | 0 | sem diagnósticos |

O actionlint não existia localmente antes da matriz. A imagem exata
`docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9`
foi obtida pelo próprio `docker run --rm` autorizado e removida individualmente
ao final com exit `0`.

### 16.10 Estado protegido

- task S15, tracker, migrations, dependências, workflows e schemas S05–S14:
  não alterados;
- nenhum GitHub, GHCR, VPS ou produção acessado com credenciais;
- nenhum `git add`, commit, tag ou push executado;
- S16 não criada;
- o índice Git real permanece vazio;
- HEAD, tags e reflog permanecem inexistentes/vazios;
- permanecem exatamente `ci.yml`, `publish-candidate.yml` e
  `publish-release.yml`;
- caches e resíduos gerados pela validação foram removidos;
- nenhum container, rede ou volume efêmero permaneceu.

Verificações finais:

| Comando exato | Exit | Interpretação |
|---|---:|---|
| `git diff --check` | 0 | sem erro de whitespace |
| `git diff --cached --name-only` | 0 | índice real vazio |
| `git rev-parse --verify HEAD` | 128 | HEAD esperado inexistente |
| `git tag --list` | 0 | saída vazia |
| `git reflog` | 128 | reflog esperado inexistente |
| `find . -type d -name __pycache__ -o -type f \( -name '*.pyc' -o -name '*.pyo' \)` | 0 | saída vazia |
| `find release_control tools -type d \( -name .venv -o -name .pytest_cache -o -name .mypy_cache -o -name .ruff_cache \) -print` | 0 | saída vazia |
| `find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -print \| sort` | 0 | exatamente os três workflows autorizados |
| `docker image inspect docker.io/rhysd/actionlint@sha256:887a259a5a534f3c4f36cb02dca341673c6089431057242cdc931e9f133147e9 --format '{{.Id}}'` | 1 | imagem ausente novamente, como no estado inicial |

IN_PROGRESS — aguardando nova revisão do orquestrador

---

## 17. Revisão terminal do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-29`

O orquestrador revisou as seis superfícies causais da emenda, suas provas
persistidas e os documentos vivos, sem repetir as suítes do executor.

As correções A–F fecham integralmente os bloqueios do ciclo anterior:

- a API expõe somente a enum pública de `ProblemDetails`, normaliza falhas
  internas e diferencia validação inválida de GET e POST;
- preparação de credencial/token ocorre antes da fronteira do dispatch, e
  somente transporte durante o POST real produz estado incerto;
- candidato próprio exige unicidade do artifact, enquanto candidato herdado
  revalida integralmente seu run e os respectivos bindings;
- predecessor, workflow e GitHub Release estão cruzados com o manifesto,
  outcome e run corretos;
- os três assets da release são validados integralmente, inclusive MIME,
  tamanho, identidade e digest, antes do primeiro download;
- a documentação distingue o runtime publisher já entregue dos itens que
  permanecem futuros.

A matriz persistida registra 120 testes do runtime, cobertura branch de
90,98%, 245 testes dos contratos, 15 provas do validador e aprovação de Ruff,
mypy, validadores e actionlint. Índice, HEAD, tags, reflog, workflows e
recursos efêmeros permaneceram dentro do estado protegido.

Não foi encontrada nova escolha arquitetural implícita nem divergência
residual dentro da fronteira A–F.

Decisão:

```text
S15 ACCEPTED — 29/07/2026
S16 autorizada
```
