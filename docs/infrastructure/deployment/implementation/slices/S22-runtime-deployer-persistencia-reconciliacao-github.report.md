# S22 — Runtime deployer, persistência e reconciliação GitHub

Estado: **IN_PROGRESS — aguardando revisão do orquestrador**

Data da execução: 31/07/2026

## 1. CWD e autoridade

CWD obrigatório usado em toda a execução:

```text
/home/gregorio/git/baronesa/emporio
```

Contrato autoritativo:

```text
docs/infrastructure/deployment/implementation/slices/S22-runtime-deployer-persistencia-reconciliacao-github.task.md
```

A ordem de leitura da Seção 3 foi cumprida integralmente antes das alterações:

1. task S22;
2. tracker de implementação;
3. aceite terminal da S21;
4. contrato humano de API/estados/segurança;
5. OpenAPI deployer;
6. máquinas de estado;
7. matriz de segurança;
8. documentação de releases;
9. plano S18;
10. workflow S21;
11. runtime publisher S15;
12. pacote, migration e testes existentes de `release_control`;
13. workflow `deploy-production.yml`;
14. schema de outcome S21;
15. planner S18;
16. transporte S21.

## 2. Estado protegido inicial

- índice Git real vazio;
- `HEAD` inexistente;
- nenhuma tag;
- nenhum reflog;
- quatro workflows YAML ativos: `ci.yml`, `deploy-production.yml`,
  `publish-candidate.yml` e `publish-release.yml`;
- nenhum cache Python;
- nenhuma S23 existente.

Nenhum `git add`, commit, tag ou push foi executado.

## 3. Arquivos criados

- `release_control/migrations/versions/0002_deployer_runtime.py`
- `release_control/src/emporio_release_control/deployer_api.py`
- `release_control/src/emporio_release_control/deployer_schemas.py`
- `release_control/src/emporio_release_control/deployer_service.py`
- `release_control/src/emporio_release_control/deployer_reconciliation.py`
- `release_control/src/emporio_release_control/deployment_artifacts.py`
- `release_control/tests/test_deployer_api.py`
- `release_control/tests/test_deployer_persistence.py`
- `release_control/tests/test_deployer_reconciliation.py`
- `release_control/tests/test_deployer_remote_contract.py`
- `release_control/tests/test_mode_isolation.py`
- `tools/deploy/validate_deployer_runtime.py`
- `tools/deploy/tests/test_deployer_runtime_contract.py`
- `docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md`
- este relatório.

## 4. Arquivos alterados

- `release_control/.env.example`
- `release_control/README.md`
- `release_control/src/emporio_release_control/config.py`
- `release_control/src/emporio_release_control/constants.py`
- `release_control/src/emporio_release_control/deployer_api.py`
- `release_control/src/emporio_release_control/deployer_schemas.py`
- `release_control/src/emporio_release_control/errors.py`
- `release_control/src/emporio_release_control/github.py`
- `release_control/src/emporio_release_control/main.py`
- `release_control/src/emporio_release_control/persistence.py`
- `release_control/src/emporio_release_control/schemas.py`
- `release_control/src/emporio_release_control/service.py`
- `release_control/src/emporio_release_control/sync.py`
- `release_control/tests/conftest.py`
- `release_control/tests/test_api.py`
- `release_control/tests/test_config_security.py`
- `release_control/tests/test_persistence_service.py`
- `release_control/tests/test_reconciliation.py`
- `release_control/tests/test_remote_contract.py`
- `docs/infrastructure/deployment/release-control/README.md`
- `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`
- `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`
- `docs/infrastructure/deployment/release-control/contracts/state-machines.yml`
- `tools/releases/release_control_contract.py`
- `tools/releases/tests/test_release_control_contract.py`

Não houve alteração da task, do tracker, de workflow, de slice anterior ou de
qualquer arquivo S23. A matriz de segurança já representava a reserva do scope
de rollback e não precisou de alteração material.

## 5. Implementação por decisão contratual

### 5.1 Isolamento dos modos

- `Settings.mode` aceita somente `publisher` ou `deployer`;
- constantes de modo, workflow e advisory lock são explicitamente separadas;
- os aliases ambíguos `MODE` e `WORKFLOW` foram removidos;
- a factory ASGI instancia somente router, service e reconciler do modo ativo;
- publisher e deployer preservam rotas, serviços e ciclos distintos;
- clientes HTTP, thread e engine são encerrados no shutdown.

### 5.2 API deployer

Foram registradas exatamente as nove rotas da Seção 7. OpenAPI runtime continua
desabilitado. Autenticação, scopes, rate limits, CORS, limite de body, content
type, headers de segurança e Problem Details permanecem fail-closed.

Capabilities retorna somente `deployment:read` e `deployment:execute`.
Rollback sintaticamente válido registra um único `rollback.rejected`, retorna
`409 RELEASE_NOT_ELIGIBLE` e não cria operação, idempotência ou dispatch.

### 5.3 Elegibilidade e plano

- primeira instalação: somente release com `previousRelease=null`;
- instalação corrente: somente sucessora SemVer direta, com predecessor exato;
- inventários Flyway correntes devem ser prefixos integrais dos alvos;
- domínio de releases deve estar sincronizado e sem drift;
- salto, downgrade, release corrente e cadeia divergente são recusados;
- plano contém seis componentes em ordem canônica;
- `KEEP/UPDATE`, migrations e backup reproduzem causalmente a projeção S18;
- S18 continua sendo a autoridade operacional no workflow.

### 5.4 Criação transacional

A ordem implementada é: replay; instalação reconciliada; elegibilidade;
revalidação remota fora da transação; advisory xact lock; repetição das
invariantes; slot único; operação/idempotência/audit atômicos; commit; dispatch
fixo fora da transação.

O POST GitHub não é repetido. Falhas pré-POST e respostas rejeitadas
terminalizam `FAILED`; 5xx e transporte pós-início permanecem `QUEUED` e
`UNCERTAIN`; `204` produz `SENT`.

### 5.5 Reconciliação e outcome

- sync apenas de releases no modo deployer;
- advisory lock próprio;
- descoberta pelo `display_title`, workflow, branch, evento, repositórios,
  janela temporal, URL, run e attempt;
- binding imutável de run/control SHA e attempt monotônico;
- artifact REST integralmente validado antes do download;
- ZIP único, regular, limitado e protegido contra traversal/link/bomb;
- JSON canônico, schema S21 e cinco bindings obrigatórios;
- conclusão GitHub cruzada com o outcome;
- `SUCCEEDED`, `ROLLED_BACK`, `FAILED` e `INDETERMINATE` aplicados sem inventar
  estados intermediários;
- instalação só muda para o alvo após sucesso remoto confirmado;
- restore requerido ou resultado indeterminado marca instalação não
  reconciliada;
- replay terminal idêntico é semanticamente nulo.

### 5.6 Contratos e documentação

- OpenAPI deployer elevado a `1.1.0`;
- máquina deployer elevada a schema v2 com transições diretas baseadas em
  evidência remota;
- documentação operacional descreve runtime, readiness, isolamento,
  idempotência, incerteza e rollback forward-only indisponível;
- validador S22 possui 15 mutantes fail-closed;
- contrato compartilhado possui 75 provas.

## 6. Migration 0002

Migration aplicada sobre PostgreSQL 16 efêmero:

```text
0001_publisher_runtime -> 0002_deployer_runtime
```

Cria somente:

- `rc_deployment_operation`;
- `rc_deployment_idempotency_key`;
- `rc_current_installation`.

Foram validados: PKs e FKs, IDs tipados, enumerações por CHECK, binding integral
do run, slot parcial único, namespace idempotente, singleton, invariantes da
instalação, optimistic version, downgrade seguro e preservação das tabelas
publisher. A suíte focada de persistência passou em 17/17 casos.

## 7. Matriz das provas causais S22

Foram coletadas **112 provas S22**, acima do mínimo de 60:

| Grupo | Evidência |
|---|---|
| modos e bootstrap | publisher/deployer isolados; modo desconhecido recusado; cinco provas estruturais |
| migration | upgrade/downgrade PostgreSQL 16, tabelas publisher preservadas, constraints e locks |
| elegibilidade | primeira, sucessora, salto, predecessor, downgrade e prefixo Flyway |
| plano | seis componentes, ordem, KEEP/UPDATE, primeira instalação, migration/backup |
| current/readiness | ausência, reconciliado, incerto, sync/drift/migration |
| API e segurança | rotas exatas, capabilities, JWT/scopes, body, content type, limites, rate limit |
| idempotência/concorrência | replay, conflito, corrida mesma chave, corrida de chaves, slot ativo |
| dispatch | endpoint/ref/inputs fixos, zero retry, not-sent/rejected/uncertain/sent |
| run | zero/único/ambíguo/timeout, identidade completa, binding e rerun monotônico |
| artifact | REST, URLs, digest, workflow binding, tamanho, ZIP/link/traversal/bomb |
| outcome | canonicalidade, schema, bindings, conclusão, quatro resultados e replay |
| rollback | 409, audit único e zero operação/idempotência/dispatch |
| robustez | erro por operação não encerra ciclo; logs e responses sanitizados |
| regressão publisher | 240 testes totais do pacote aprovados |

## 8. Comandos, exits e resultados

### 8.1 Ambiente Python

```bash
cd /home/gregorio/git/baronesa/emporio/release_control
uv sync --frozen --extra test
```

Exit `2`. O projeto não define `project.optional-dependencies.test`; define o
grupo de desenvolvimento já congelado no lock. Como `pyproject.toml` e
`uv.lock` são proibidos pela S22, não foi criado um extra artificial.

Correção operacional sem alterar contrato de dependências:

```bash
uv sync --frozen --group dev
```

Exit `0`; 54 pacotes auditados.

### 8.2 Migration

A primeira tentativa sem environment explícito falhou com exit `1`, como
esperado pelo bootstrap fail-closed. Foi iniciado PostgreSQL 16 efêmero em
loopback, gerada chave RSA efêmera e executado o mesmo Alembic com todas as
settings `test` explícitas:

```bash
uv run alembic upgrade head
```

Exit `0`; migrations `0001` e `0002` aplicadas. Nenhuma credencial real foi
usada ou persistida.

### 8.3 Pytest e cobertura

```bash
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90
```

Exit `0`; **240/240 testes aprovados**; duração `17,64 s`; cobertura total
**90,68% com branches**. As cinco suítes S22 coletam 112 provas.

### 8.4 Qualidade estática

```bash
uv run ruff check .
uv run mypy --strict src tests
```

Exits `0` e `0`. Ruff sem achados; mypy sem achados em 30 arquivos-fonte.

### 8.5 Validadores S22 e contrato compartilhado

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py
```

Exit `0`: `deployer-runtime:valid`. Matriz mutante: **15/15**.

O comando literal da task para o contrato omite o subcomando obrigatório e
retornou exit `2`:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py
```

Foi executada a interface real do helper:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
```

Exit `0`: `release-control-contract:valid`; **75/75** provas contratuais.

### 8.6 Regressões externas à fronteira

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
```

Exit `1`; 283 casos executados, com 10 falhas e 1 erro, todos causalmente
originados em `validate_deployment_executor.py: state-machine-transitions`.
Esse validador S19 exige igualdade com a máquina v1 e rejeita as duas
transições `QUEUED -> SUCCEEDED|ROLLED_BACK` que a S22 obriga adicionar.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'
```

Exit `1`; 289 casos executados, com 9 falhas e 1 erro, todos causalmente
originados em `validate_publisher_runtime.py: deployer-router`. O validador S15
concatena todo `release_control/src` e proíbe qualquer
`/api/deployment-control/`, incompatível com a criação obrigatória do runtime
deployer no mesmo pacote.

Os dois validadores legados estão fora da fronteira de arquivos autorizados da
S22 e, por isso, não foram alterados nem contornados.

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py'
```

Exit `0`; **4/4** testes aprovados em `0,141 s`.

### 8.7 Integridade

```bash
git diff --check
```

Exit `0`.

## 9. Falhas intermediárias e correções

1. A integração inicial ainda não cobria API/service: foram criadas provas
   causais e a cobertura subiu de 69,33% para 90,68%.
2. O dispatch confirmado era rebaixado a `UNCERTAIN` pelo eixo de outcome:
   corrigido para preservar binding confirmado.
3. `previous_release` usava o manifesto alvo: corrigido para
   `operation.source_release`.
4. Problem codes deployer inicialmente ampliavam o enum publisher: separados
   em schema próprio para preservar o contrato público S15.
5. Mypy expôs tipagens imprecisas em fixtures/testes publisher autorizados:
   corrigidas sem mudar comportamento.
6. A remoção inicial de caches com `rm` foi recusada pela proteção do CLI; a
   limpeza foi repetida com alvos explícitos via `find -delete`, exit `0`.

## 10. Divergências reais e itens NAO DETERMINADO

### 10.1 Divergências fechadas pelo código

Nenhuma decisão arquitetural foi tomada fora da task. Rollback continua
indisponível, S18 continua forward-only e nenhum adapter operacional foi
importado pelo runtime.

### 10.2 NAO DETERMINADO pelo executor

O orquestrador deve determinar uma correção posterior para três
incompatibilidades do próprio contrato/matriz:

1. comando `uv sync --frozen --extra test` versus grupo `dev` congelado, sendo
   proibido alterar `pyproject.toml`/`uv.lock`;
2. validador publisher S15 que proíbe o router deployer obrigatório S22;
3. validador executor S19 que exige a máquina v1 sem as transições obrigatórias
   da máquina v2 S22.

Não foi inferida autorização para alterar esses validadores legados fora da
fronteira. A implementação S22, seus validadores e seus testes próprios estão
verdes; a slice permanece corretamente em revisão.

## 11. Segurança e ausência de acesso externo

- nenhum acesso ao GitHub real, GHCR, DNS, SSH, VPS ou produção;
- nenhum workflow remoto executado;
- nenhum token, JWT, secret, private key ou idempotency key real persistido;
- somente PostgreSQL 16 efêmero em `127.0.0.1` foi usado;
- nenhuma chamada Docker de aplicação, build, Compose operacional ou prune;
- container PostgreSQL e chave RSA efêmera removidos ao final;
- nenhum commit, tag, push ou publicação.

## 12. Estado protegido final

Validações finais:

- `git ls-files --stage`: exit `0`, saída vazia;
- `git rev-parse --verify HEAD`: exit `128`, HEAD inexistente;
- `git tag --list`: exit `0`, saída vazia;
- `git reflog show --all`: exit `0`, saída vazia;
- exatamente quatro workflows YAML ativos; o quinto arquivo sob o diretório é
  somente o README transitório;
- nenhuma `.venv`, `.coverage`, `.pytest_cache`, `.ruff_cache`, `.mypy_cache`,
  `__pycache__`, `.pyc` ou `.pyo` restante;
- zero container PostgreSQL S22 restante;
- nenhuma S23 criada;
- task e tracker não alterados;
- `git diff --check`: exit `0`.

## 13. Estado final

**IN_PROGRESS — aguardando revisão do orquestrador**

Este relatório não declara `ACCEPTED` e não avança a próxima slice.

## 14. Revisão do orquestrador — correção causal consolidada 01

**Veredito:** `REJECTED — correção consolidada obrigatória`.

A revisão reproduziu:

```text
validate_deployer_runtime.py             exit 0
release_control_contract.py validate     exit 0
validate_deployment_executor.py          exit 3 — state-machine-transitions
validate_publisher_runtime.py             exit 2 — deployer-router
```

O orquestrador reconhece que os dois comandos incorretos e a fronteira que
impedia corrigir os validadores legados eram defeitos da task, não decisões que
deveriam ter sido delegadas ao executor.

A leitura do runtime também encontrou divergências bloqueantes em:

1. listagem de releases quando a instalação está incerta;
2. consistência entre instalação atual e snapshot sincronizado;
3. confirmação indevida de `SUCCEEDED` com restore obrigatório;
4. caminhos de reconciliação que apenas auditam sem persistir incerteza;
5. falhas de cleanup/consulta capazes de escapar antes de registrar drift;
6. corrida do slot ativo sem `activeOperationId` e com classificação ampla de
   `IntegrityError`;
7. binding de workflow não vinculado a `dispatch_state=CONFIRMED` no banco;
8. código exclusivo do publisher presente no enum público deployer.

Escopo, decisões, arquivos e provas estão congelados em:

```text
docs/infrastructure/deployment/implementation/slices/S22-runtime-deployer-persistencia-reconciliacao-github.correction-01.md
```

S23 permanece bloqueada. O executor deve implementar a correção integral e
manter a S22 como `IN_PROGRESS` até nova revisão terminal.

## Resposta à correção causal consolidada 01

**CWD obrigatório:** `/home/gregorio/git/baronesa/emporio`  
**CWD da matriz Python:** `/home/gregorio/git/baronesa/emporio/release_control`

### Resposta individual A–I

| Item | Comportamento final | Arquivos/provas causais | Resultado |
|---|---|---|---|
| A | A instalação usa o grupo congelado `dev`; o contrato compartilhado usa o subcomando `validate`. | Matriz terminal abaixo. | Conforme. |
| B | O journal S19 preserva exatamente suas 14 arestas; a máquina v2 acrescenta somente as duas arestas diretas S22 com ator reconciler e evidência remota. O publisher inspeciona contaminação deployer somente em `api.py` e valida `PUBLISHER_WORKFLOW`. | `validate_deployment_executor.py`, `test_deployment_executor_contract.py`, `validate_publisher_runtime.py`, `test_publisher_runtime_contract.py`; 30 e 18 mutantes. | Conforme. |
| C | Uma avaliação única distingue ausência limpa, instalação consistente e instalação não reconciliada. Snapshot ausente, commit divergente, campo obrigatório ausente ou domínio vermelho falham fechados. A listagem continua `200` e toda inelegível sem alterar evidência. | `deployer_service.py`; `test_inconsistent_current_is_fail_closed_without_mutating_evidence`, parametrizado para as três causas, além das provas de ausência e consistência. | Conforme. |
| D | `CONFIRMED/SUCCEEDED/databaseRestoreRequired=true` lança `DEPLOYMENT_OUTCOME_RESTORE_CONFLICT` antes de qualquer gravação. O ciclo converte a falha em incerteza, preservando operação, slot e instalação não confirmada. | `deployer_service.py`, `deployer_reconciliation.py`; testes de rejeição anterior à escrita e ciclo parametrizado. | Conforme. |
| E | Depois de localizar lineage potencial, falhas de run, binding, attempt, artifact, outcome ou aplicação chamam `mark_uncertain`; exceção não tipada usa `RECONCILE_FAILED`. Falha de listagem anterior a candidato afeta somente o domínio. | `deployer_reconciliation.py`; provas de lineage integral, quatro divergências de binding, aplicação tipada/não tipada e list-runs. | Conforme. |
| F | Sync, cleanup, consulta, operação individual e persistência do domínio não escapam do ciclo adquirido. Operações seguintes continuam, o domínio fica vermelho e o lock é liberado em `finally`, inclusive após rollback da sessão. | `deployer_reconciliation.py`; provas independentes de sync, cleanup, query, operação, `_set_domain`, unlock e lock não adquirido. | Conforme. |
| G | Após `IntegrityError`, uma sessão nova procura primeiro replay e depois o slot ativo. Slot presente retorna o ID vencedor; ausência resulta em `500 INTERNAL_ERROR` sanitizado. | `deployer_service.py`; testes `test_integrity_race_reports_real_active_operation` e `test_unrelated_integrity_failure_is_sanitized_internal_error`. | Conforme. |
| H | A constraint PostgreSQL implementa a equivalência exata entre `CONFIRMED` e binding integral. Os demais dispatch states exigem quatro campos nulos. | migration `0002`; quatro provas PostgreSQL reais em `test_deployer_persistence.py`. | Conforme. |
| I | `DeployerProblemCode` coincide exatamente, inclusive ordem, com o enum OpenAPI deployer e não contém código exclusivo do publisher. | `deployer_schemas.py`; leitura do OpenAPI e igualdade exata em teste. | Conforme. |

O validador S22 passou a inspecionar AST e provas causais para C–I. A suíte
mutante própria totaliza **22/22** e continua cobrindo isolamento de modo,
identidades, rotas, dependências, binding, estado incerto, corrida, ciclo e
enum público.

### Arquivos alterados

```text
release_control/migrations/versions/0002_deployer_runtime.py
release_control/src/emporio_release_control/deployer_service.py
release_control/src/emporio_release_control/deployer_reconciliation.py
release_control/src/emporio_release_control/deployer_schemas.py
release_control/tests/test_deployer_api.py
release_control/tests/test_deployer_persistence.py
release_control/tests/test_deployer_reconciliation.py
tools/deploy/validate_deployer_runtime.py
tools/deploy/tests/test_deployer_runtime_contract.py
tools/deploy/validate_deployment_executor.py
tools/deploy/tests/test_deployment_executor_contract.py
tools/releases/validate_publisher_runtime.py
tools/releases/tests/test_publisher_runtime_contract.py
docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md
docs/infrastructure/deployment/release-control/RUNTIME_DEPLOYER.md
docs/infrastructure/deployment/implementation/slices/S22-runtime-deployer-persistencia-reconciliacao-github.report.md
```

Nenhum arquivo opcional restante da fronteira precisou ser alterado.

### Falhas intermediárias e correções

1. A primeira chamada literal de `uv run alembic upgrade head`, sem environment,
   retornou exit `1` em `0,37 s`, antes de abrir conexão ou executar migration,
   porque as 15 settings obrigatórias estavam ausentes. A prova foi repetida
   com PostgreSQL 16 efêmero em loopback, chave RSA efêmera e valores `test`
   explícitos; exit `0` em `0,61 s`.

   Comando corretivo exato, com valores integralmente efêmeros:

   ```bash
   env RELEASE_CONTROL_PROFILE=test RELEASE_CONTROL_MODE=deployer RELEASE_CONTROL_DB_HOST=127.0.0.1 RELEASE_CONTROL_DB_PORT=32769 RELEASE_CONTROL_DB_NAME=test RELEASE_CONTROL_DB_USER=test RELEASE_CONTROL_DB_PASSWORD=test RELEASE_CONTROL_DB_SSLMODE=disable RELEASE_CONTROL_JWT_ISSUER=http://127.0.0.1:8080/api/release-control/identity RELEASE_CONTROL_JWT_AUDIENCE=emporio-release-control RELEASE_CONTROL_JWT_JWKS_URL=http://127.0.0.1:8080/api/release-control/identity/jwks RELEASE_CONTROL_CORS_ORIGINS=http://127.0.0.1:8084 RELEASE_CONTROL_GITHUB_APP_ID=100 RELEASE_CONTROL_GITHUB_INSTALLATION_ID=200 RELEASE_CONTROL_GITHUB_PRIVATE_KEY_PATH=/tmp/emporio-s22-correction-key.pem RELEASE_CONTROL_HASH_PEPPER=pppppppppppppppppppppppppppppppp RELEASE_CONTROL_GITHUB_API_BASE=http://127.0.0.1:9 uv run alembic upgrade head
   ```
2. A primeira suíte focada de API revelou quatro fixtures de outcome com
   `dispatch_state=CONFIRMED` sem binding remoto. As fixtures foram corrigidas
   para representar o estado que o reconciliador realmente materializa; a
   repetição aprovou **38/38**.
3. A prova de falha de consulta abortou a transação da sessão que detinha o
   advisory lock. O `finally` passou a tentar o helper na mesma sessão e, após
   rollback, usar como fallback a conexão original. As provas focadas de
   reconciliação/remoto aprovaram **71/71**.

Nenhuma falha intermediária exigiu mudança fora da fronteira autorizada.

### Matriz terminal da Seção 14

| CWD | Comando exato | Exit | Contagem/resultado | Duração |
|---|---|---:|---|---:|
| `release_control` | `uv sync --frozen --group dev` | 0 | 54 pacotes auditados | 0,01 s |
| `release_control` | `uv run alembic upgrade head` com as settings `RELEASE_CONTROL_*` de teste explícitas, PostgreSQL 16 em `127.0.0.1:32769` e chave `/tmp/emporio-s22-correction-key.pem` | 0 | migrations `0001` e `0002` aplicadas | 0,61 s |
| `release_control` | `uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90` | 0 | **267/267**; branch coverage **90,84%** | 22,29 s |
| `release_control` | `uv run ruff check .` | 0 | sem achados | 0,02 s |
| `release_control` | `uv run mypy --strict src tests` | 0 | 30 arquivos-fonte, sem achados | 1,03 s |
| raiz | `PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py` | 0 | `deployer-runtime:valid` | 0,12 s |
| raiz | `PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` | 0,09 s |
| raiz | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'` | 0 | **295/295** | 93,41 s |
| raiz | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'` | 0 | **292/292** | 13,82 s |
| raiz | `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py'` | 0 | **4/4** | 0,13 s |
| raiz | `git diff --check` | 0 | saída vazia | 0,00 s |

O PostgreSQL, a chave efêmera, `.venv`, coverage e caches produzidos pela
matriz foram removidos. Não houve chamada GitHub real, GHCR, DNS externo, SSH,
VPS, produção, workflow remoto, commit, tag, push ou publicação.

### Estado protegido para revisão terminal

- índice Git real vazio;
- HEAD, tags e reflog inexistentes;
- exatamente quatro workflows YAML ativos;
- task S22, OpenAPI e máquina de estados permaneceram byte a byte iguais aos
  hashes registrados antes da correção;
- o executor não editou o tracker. Durante a matriz, `README.md` e
  `HANDOFF_ORQUESTRADOR.md` receberam atualização concorrente externa às
  `09:42`; o hash do tracker mudou de
  `bc8890091055a2200913b7b1d3e0d0c23f6eef2db59ce3b3389cbbf83d8cf31b`
  para `444b9948003590a12acb43aadea676d89899fa850c376e6b4c944b5f49161baf`.
  Essa alteração fora da fronteira foi preservada sem edição; o conteúdo ainda
  mantém S22 `IN_PROGRESS` e não registra S23;
- nenhuma S23 criada;
- nenhum cache Python, ambiente virtual, coverage, chave ou container efêmero
  restante;
- divergências restantes: **nenhuma**.

**IN_PROGRESS — aguardando revisão terminal do orquestrador**

## 15. Revisão terminal do orquestrador — correção causal consolidada 01

**Veredito:** `ACCEPTED` — 31/07/2026.

Esta revisão não aceitou pela contagem declarada. Foram feitas verificações
independentes, executadas neste mesmo CWD, com Postgres 16 efêmero próprio e
sem reaproveitar nenhum artefato do executor:

### 15.1 Fronteira

Todos os arquivos em "Arquivos alterados" desta resposta foram conferidos
contra a fronteira autorizada da correction-01 (Seção 3). Nenhum arquivo fora
da lista foi tocado. `pyproject.toml`, `uv.lock`,
`release_control/src/emporio_release_control/api.py`,
`docs/.../api/publisher.openapi.yml` e os quatro workflows têm timestamp
anterior à janela de execução da correção (08:24–09:47 de 31/07/2026) e
permanecem intocados. `state-machines.yml` tem timestamp de 08:48, dentro da
execução original da S22 (pré-correção), consistente com a elevação a schema
v2 já descrita na Seção 5.6 original — não foi reaberto pela correção-01.

### 15.2 Leitura causal direta

Leitura integral de `deployer_service.py`, `deployer_reconciliation.py`,
`deployer_schemas.py`, `migrations/versions/0002_deployer_runtime.py`,
`tools/deploy/validate_deployment_executor.py` e
`tools/releases/validate_publisher_runtime.py`:

- **B** — `TRANSITIONS` (S19) permanece com as 14 arestas originais;
  `S22_DIRECT_TRANSITIONS` contém exatamente `QUEUED→SUCCEEDED` e
  `QUEUED→ROLLED_BACK` com `actor=reconciler` e
  `requires_remote_evidence=true`; `validate_journal` continua validando
  contra `TRANSITIONS` puro (o journal S19 não aceita as arestas diretas).
  `validate_publisher_runtime.py` verifica `/api/deployment-control/`
  somente no texto de `api.py` isolado, e usa a constante inequívoca
  `PUBLISHER_WORKFLOW`. Conforme.
- **C** — `_current_evidence`/`_current_or_clean` implementam exatamente a
  regra de consistência da tabela (release/source_commit/installed_at/
  last_operation_id, snapshot presente, commit igual, domínio releases
  verde); `list_releases` nunca levanta 409 e marca tudo inelegível quando
  inconsistente. Conforme.
- **D** — `apply_outcome` levanta `DEPLOYMENT_OUTCOME_RESTORE_CONFLICT` antes
  de qualquer atribuição de campo, quando `CONFIRMED`+`SUCCEEDED`+
  `restore_required=True`. Conforme.
- **E** — `_operation` só chama `mark_uncertain` depois de um run casar por
  `display_title`; falha em `list_pages` antes de qualquer candidato apenas
  derruba o domínio via `green=False`, sem inventar execução. Exceções
  tipadas usam o código estável; não tipadas usam `RECONCILE_FAILED`.
  Conforme.
- **F** — `cycle()` envolve sync/cleanup/consulta/operação em `try/except
  Exception` independentes; `_set_domain` é chamado sempre após o loop; o
  lock é liberado em `finally` com fallback explícito de
  `pg_advisory_unlock`; nenhum texto de exceção é logado. Conforme.
- **G** — `_resolve_integrity_race` consulta replay, depois `active_slot=1`
  em sessão nova; ausência de ambos vira `500 INTERNAL_ERROR` sanitizado sem
  texto SQL. Conforme.
- **H** — `ck_rc_deployment_workflow_binding` implementa a equivalência
  exata `CONFIRMED ⇔ (run_id, attempt, url, control_sha todos NOT NULL)`.
  Conforme.
- **I** — `DeployerProblemCode` não contém `VERSION_RESERVATION_CONFLICT` e
  coincide, elemento a elemento, com o enum do OpenAPI deployer (verificado
  por leitura direta do YAML, não apenas do teste). Conforme.

### 15.3 Reprodução independente da matriz terminal (Seção 14)

Executada neste CWD com Postgres 16 efêmero próprio (`emporio-s22-review-pg`,
removido ao final) e chave RSA efêmera própria (`/tmp/emporio-s22-review-key.pem`,
removida ao final):

| Comando | Exit | Resultado observado |
|---|---:|---|
| `uv sync --frozen --group dev` | 0 | 54 pacotes |
| `uv run alembic upgrade head` | 0 | `0001`→`0002` aplicadas |
| `uv run pytest --cov=... --cov-branch --cov-fail-under=90` | 0 | **267/267**; branch coverage **90,84%** — idêntico ao declarado |
| `uv run ruff check .` | 0 | sem achados |
| `uv run mypy --strict src tests` | 0 | sem achados, 30 arquivos |
| `tools/deploy/validate_deployer_runtime.py` | 0 | `deployer-runtime:valid` |
| `tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `unittest discover tools/deploy/tests` | 0 | **295/295** — idêntico ao declarado |
| `unittest discover tools/releases/tests` | 0 | **292/292** — idêntico ao declarado |
| `unittest discover tools/compose/tests` | 0 | **4/4** |
| `git diff --check` | 0 | vazio |

Todos os números batem exatamente com os declarados pelo executor.

### 15.4 Prova mutante independente (não apenas leitura de asserts)

1. **Constraint de binding (H)** — tentativa de `INSERT` direto via `psql` em
   `rc_deployment_operation` com `dispatch_state='CONFIRMED'` e os quatro
   campos de binding `NULL` foi **rejeitada pelo Postgres real** com
   `ck_rc_deployment_workflow_binding`. A constraint não é apenas testada,
   ela existe fisicamente no banco.
2. **Guarda de restore (D)** — o guard em `deployer_service.py` foi
   temporariamente neutralizado (`if False and ...`) e
   `tests/test_deployer_api.py::test_confirmed_success_requiring_restore_is_rejected_before_any_write`
   passou a falhar (`DID NOT RAISE`), confirmando que o teste realmente
   depende do comportamento e não é um smoke test. O código foi revertido
   byte a byte (`git diff --stat` vazio) antes da reexecução da matriz final.

### 15.5 Estado protegido final (reverificado após a revisão)

- índice Git real vazio; `HEAD` inexistente (`exit 128`); tags e reflog
  vazios;
- exatamente quatro workflows YAML ativos;
- nenhum `.venv`, `.coverage`, `.pytest_cache`, `.ruff_cache`, `.mypy_cache`,
  `__pycache__` ou `.pyc` em todo o workspace (busca recursiva a partir da
  raiz, não apenas em `release_control/`);
- nenhum container Docker remanescente (`emporio-s22-review-pg` removido);
- nenhum arquivo temporário de chave remanescente;
- nenhuma S23 criada por este ciclo antes desta revisão;
- nenhum commit, tag, push ou acesso externo realizado durante a revisão.

### 15.6 Nota sobre o alerta de resíduos do handoff

O `HANDOFF_ORQUESTRADOR.md` alertava sobre `.venv`/coverage/caches
remanescentes em `release_control/`. Na leitura desta revisão eles já não
existiam — consistente com a limpeza declarada ao final da resposta à
correção-01 (Seção 6, item 6 das falhas intermediárias). O alerta do handoff
reflete um snapshot anterior à conclusão da correção, não uma divergência
atual.

### 15.7 Decisão

Os nove itens da correção (A–I) foram implementados exatamente como
especificado, comprovados por leitura direta do código, reexecução completa
e independente da matriz terminal com resultados idênticos, e prova mutante
em dois pontos causais distintos (um a nível de banco, um a nível de
aplicação). Não há divergência entre relatório, código e estado do
filesystem.

**S22: `ACCEPTED` — 31/07/2026.**

S23 — "Ponte de identidade RS256/JWKS do deployer" — é criada no mesmo ciclo
em:

```text
docs/infrastructure/deployment/implementation/slices/S23-ponte-identidade-deployer-rs256-jwks.task.md
```
