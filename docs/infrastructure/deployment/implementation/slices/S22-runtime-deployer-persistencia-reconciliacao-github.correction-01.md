# S22 — Correção causal consolidada 01

> **Estado:** `IN_PROGRESS — correção consolidada obrigatória`
> **Contrato-base:** `S22-runtime-deployer-persistencia-reconciliacao-github.task.md`
> **Relatório a atualizar:** `S22-runtime-deployer-persistencia-reconciliacao-github.report.md`
> **Próxima slice:** S23 continua bloqueada

## 1. Decisão do orquestrador

A S22 ainda não está aceita. Esta é a única emenda de correção autorizada para
o ciclo atual e substitui qualquer decisão implícita ou item `NAO DETERMINADO`
do relatório.

O orquestrador reconhece três defeitos no contrato original:

1. `uv sync --frozen --extra test` foi prescrito, embora o projeto congele as
   dependências de teste no grupo `dev`;
2. o helper `release_control_contract.py` foi prescrito sem o subcomando
   obrigatório `validate`;
3. as regressões integrais foram exigidas sem autorizar a adaptação de dois
   validadores legados incompatíveis com requisitos mandatórios da S22.

Esses defeitos não são responsabilidade do executor. As decisões abaixo estão
fechadas; o executor deve implementá-las sem escolher novos códigos, estados,
rotas, políticas ou arquivos.

## 2. Leitura obrigatória

Ler integralmente, nesta ordem:

1. task S22;
2. relatório S22, incluindo a revisão do orquestrador ao final;
3. esta correção;
4. task e relatório S19;
5. `state-machines.yml` e seu contrato humano;
6. contrato OpenAPI deployer S06/S22;
7. validadores e testes citados na fronteira abaixo.

## 3. Fronteira autorizada

Alterar somente os arquivos necessários desta lista:

```text
release_control/migrations/versions/0002_deployer_runtime.py
release_control/src/emporio_release_control/persistence.py
release_control/src/emporio_release_control/deployer_service.py
release_control/src/emporio_release_control/deployer_reconciliation.py
release_control/src/emporio_release_control/deployer_schemas.py
release_control/tests/test_deployer_api.py
release_control/tests/test_deployer_persistence.py
release_control/tests/test_deployer_reconciliation.py
release_control/tests/test_deployer_remote_contract.py
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

Não alterar:

- task S22, tracker ou S23;
- `pyproject.toml`, `uv.lock` ou dependências;
- OpenAPI, schema de outcome S21 ou máquina de estados v2;
- código publisher de runtime, workflows ou helpers S12–S21;
- código comercial, Dockerfiles, Compose ou produção;
- qualquer arquivo não enumerado na fronteira autorizada.

## 4. Correção A — comandos canônicos

Substituir, somente na execução e no relatório, os dois comandos incorretos da
task pelos comandos definitivos:

```bash
cd /home/gregorio/git/baronesa/emporio/release_control
uv sync --frozen --group dev

cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
```

Não criar um extra `test` e não alterar a interface do helper.

## 5. Correção B — compatibilidade dos validadores legados

### 5.1 Máquina de estados S19/S22

Em `validate_deployment_executor.py`, separar duas autoridades:

- o journal S19 continua aceitando exatamente as transições S19 originais;
- a máquina v2 completa deve conter exatamente essas transições mais:

```text
QUEUED -> SUCCEEDED
QUEUED -> ROLLED_BACK
```

As duas arestas adicionais devem ter, exatamente:

```yaml
actor: reconciler
requires_remote_evidence: true
```

Não permitir as arestas diretas no journal S19 e não aceitar nenhuma terceira
aresta adicional.

Adicionar mutantes independentes que falhem quando:

1. uma aresta S19 é removida;
2. `QUEUED -> SUCCEEDED` é removida;
3. `QUEUED -> ROLLED_BACK` é removida;
4. uma aresta arbitrária é adicionada;
5. ator ou `requires_remote_evidence` de uma aresta S22 é alterado;
6. o journal S19 passa a aceitar uma aresta direta S22.

### 5.2 Isolamento publisher/deployer

Em `validate_publisher_runtime.py`, validar a ausência de
`/api/deployment-control/` apenas no router publisher
`release_control/src/emporio_release_control/api.py`. A existência legítima do
router isolado `deployer_api.py` não pode invalidar o publisher.

Preservar as verificações transversais de segredos, capabilities, workflow,
repository, ref e dependências. A constante publisher deve ser verificada pelo
nome inequívoco `PUBLISHER_WORKFLOW`; não usar a substring genérica
`WORKFLOW =` como prova.

Adicionar mutantes independentes que comprovem:

1. o pacote com `deployer_api.py` legítimo passa;
2. uma rota deployer inserida em `api.py` falha;
3. workflow publisher divergente falha;
4. uma capability deployer anunciada no publisher falha.

## 6. Correção C — leitura da instalação e elegibilidade

Implementar uma avaliação única da instalação atual com três resultados
internos: ausente limpa, reconciliada consistente ou não reconciliada.

Uma linha marcada `reconciled=true` só é consistente quando:

- `release`, `source_commit`, `installed_at` e `last_operation_id` existem;
- existe `ReleaseSnapshot` para a release instalada;
- `ReleaseSnapshot.source_commit == CurrentInstallation.source_commit`;
- o domínio `releases` está verde.

Aplicar exatamente:

| Operação | ausente limpa | reconciliada consistente | não reconciliada |
|---|---:|---:|---:|
| `GET /current` | `404` | `200` | `409 CURRENT_INSTALLATION_UNRECONCILED` |
| `GET /releases` | `200` | `200` | `200`, todos `eligible=false` |
| `GET /releases/{release}/plan` | regra da primeira release | regra da próxima release | `409 CURRENT_INSTALLATION_UNRECONCILED` |
| `POST /deployments` | regra da primeira release | regra da próxima release | `409 CURRENT_INSTALLATION_UNRECONCILED` |
| readiness deployer | verde se demais gates verdes | verde se demais gates verdes | vermelho |

`GET /releases` nunca retorna `409`; seu OpenAPI não prevê essa resposta. Não
apagar nem sobrescrever a evidência da instalação somente por causa de uma
leitura.

Adicionar provas para linha explicitamente incerta, snapshot ausente e commit
divergente, cobrindo as cinco linhas da tabela.

## 7. Correção D — outcome confirmado incompatível com restore

A combinação abaixo é inválida:

```text
transportStatus = CONFIRMED
deploymentState = SUCCEEDED
databaseRestoreRequired = true
```

Antes de gravar hash, estado terminal, instalação ou liberar slot,
`apply_outcome` deve falhar com o código interno estável:

```text
DEPLOYMENT_OUTCOME_RESTORE_CONFLICT
```

O reconciliador deve então executar `mark_uncertain` para a operação. O estado
resultante é:

```text
operation.state              = QUEUED
operation.active_slot        = 1
operation.transport_status   = INDETERMINATE
current.reconciled           = false
current.uncertainty_code     = DEPLOYMENT_OUTCOME_RESTORE_CONFLICT
```

Não persistir o outcome conflitante como aplicado e não confirmar a nova
release. As combinações válidas já congeladas na task S22 permanecem
inalteradas, inclusive `SUCCEEDED/REMOTE_CLEANUP_FAILED` com restore `false`.

## 8. Correção E — incerteza em toda evidência remota inconsistente

Depois que um run potencialmente correlacionável foi encontrado, qualquer uma
das falhas abaixo deve manter a operação ativa e chamar `mark_uncertain`:

- shape, data, repository, workflow, branch, URL, SHA ou attempt inválido;
- run ID/control SHA divergente do binding existente;
- attempt regressivo ou igual com evidência divergente;
- artifact/outcome inválido, ausente, ambíguo ou divergente;
- falha ao aplicar outcome, inclusive snapshot alvo ausente;
- conflito `DEPLOYMENT_OUTCOME_RESTORE_CONFLICT`.

O código persistido deve ser o código estável da falha tipada. Para exceção não
tipada durante essa fase, usar exatamente `RECONCILE_FAILED`. Nunca
terminalizar como sucesso/falha e nunca liberar o slot nesses casos.

Uma falha em `list runs` antes de encontrar qualquer candidato marca o domínio
`deployments` em drift, mas não inventa que uma operação específica executou.

Adicionar provas de ciclo, não apenas testes diretos de helpers, para run
inválido, binding divergente, attempt regressivo e falha de `apply_outcome`.

## 9. Correção F — ciclo resiliente e estado do domínio

Depois de adquirir o advisory lock deployer:

- uma exceção em sync, cleanup, consulta de operações ou operação individual
  não pode escapar nem encerrar a thread;
- qualquer uma dessas exceções torna o domínio `deployments` vermelho;
- falha individual é auditada e as demais operações continuam quando a lista
  já foi obtida;
- `_set_domain(false)` deve ser tentado mesmo após falha de cleanup/consulta;
- o advisory lock deve ser liberado em `finally` em todos os caminhos;
- lock não adquirido continua retornando `false` sem alterar o domínio;
- ciclo adquirido retorna `true`, ainda que tenha registrado drift.

Se a própria persistência de `_set_domain` falhar, registrar apenas evento e
código sanitizados e permitir que o loop sobreviva; não logar exception text.

Adicionar provas causais para falha de sync, cleanup, consulta, operação,
`_set_domain` e liberação do lock.

## 10. Correção G — corrida do slot ativo

No `IntegrityError` após a tentativa de inserção:

1. consultar novamente o replay idempotente;
2. se não houver replay, consultar o `active_slot=1` em nova sessão;
3. se existir operação ativa, lançar `ActiveOperationFailure` com seu
   `activeOperationId`;
4. se não existir operação ativa, tratar como `500 INTERNAL_ERROR` sanitizado,
   sem classificar toda violação de integridade como conflito de produção.

Adicionar uma prova de concorrência/violação da constraint parcial que valide
status `409`, código `PRODUCTION_OPERATION_ACTIVE` e o ID real da operação
vencedora, além de uma prova de `IntegrityError` não relacionado resultando em
`500 INTERNAL_ERROR` sem texto SQL.

## 11. Correção H — binding persistente do workflow

Na migration `0002_deployer_runtime.py`, adicionar constraint com a regra
exata:

```text
dispatch_state = CONFIRMED
<=>
workflow_run_id, workflow_attempt, workflow_run_url e control_sha são todos não nulos
```

Nos estados `NOT_SENT`, `SENT` e `UNCERTAIN`, os quatro campos devem ser todos
nulos. Preservar os checks existentes de ID, attempt, URL e SHA.

Espelhar a constraint no model somente se necessário para manter metadata e
testes coerentes. Adicionar provas PostgreSQL reais para:

1. `CONFIRMED` com binding integral passa;
2. `CONFIRMED` sem binding falha;
3. binding integral com `SENT` falha;
4. binding parcial em qualquer estado falha.

## 12. Correção I — enum público deployer

Remover `VERSION_RESERVATION_CONFLICT` de `DeployerProblemCode`. Esse código é
exclusivo do publisher e não pertence ao enum do OpenAPI deployer.

Preservar exatamente os códigos atualmente declarados no OpenAPI deployer; não
adicionar nenhum código interno desta correção ao response público. Adicionar
prova de igualdade exata entre o `Literal` e o enum OpenAPI.

## 13. Validador S22

Fortalecer `validate_deployer_runtime.py` e sua suíte mutante para provar, por
estrutura/AST ou execução causal conforme adequado, pelo menos:

1. listagem incerta retorna `200` com todos inelegíveis;
2. current sem snapshot ou com commit divergente não é reconciliada;
3. success com restore `true` não confirma instalação;
4. falha de validação/binding/aplicação no ciclo chama `mark_uncertain`;
5. falha de cleanup/consulta derruba o domínio sem escapar do ciclo;
6. corrida do slot preserva `activeOperationId`;
7. constraint vincula `CONFIRMED` ao binding integral;
8. enum público deployer é exato;
9. os validadores legados corrigidos continuam detectando contaminação real.

Não aceitar verificações que passem apenas pela presença de substrings sem
provar a semântica causal correspondente.

## 14. Matriz terminal obrigatória

Executar e registrar comando, exit, contagem e duração:

```bash
cd /home/gregorio/git/baronesa/emporio/release_control
uv sync --frozen --group dev
uv run alembic upgrade head
uv run pytest --cov=emporio_release_control --cov-branch --cov-fail-under=90
uv run ruff check .
uv run mypy --strict src tests

cd /home/gregorio/git/baronesa/emporio
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployer_runtime.py
PYTHONDONTWRITEBYTECODE=1 python3 tools/releases/release_control_contract.py validate
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py'
git diff --check
```

Todos os comandos devem terminar com exit `0`. Usar apenas PostgreSQL 16
efêmero local. Remover `.venv`, coverage, caches, chaves e recursos efêmeros ao
final.

## 15. Critérios de aceite

A correção só volta para revisão quando:

- todos os comportamentos das Seções 4–13 estiverem implementados;
- a matriz da Seção 14 estiver integralmente verde;
- cobertura branch permanecer em pelo menos `90%`;
- índice real continuar vazio e HEAD, tags e reflog inexistentes;
- houver exatamente quatro workflows ativos;
- não houver caches, ambiente virtual, containers ou arquivos temporários;
- não houver GitHub real, GHCR, DNS externo, SSH, VPS, deploy, commit ou push;
- o relatório contiver uma seção intitulada
  `Resposta à correção causal consolidada 01`, com tabela requisito, arquivo,
  teste e resultado;
- o relatório não declarar `ACCEPTED` e não criar S23.

## 16. Formato da resposta do executor

Responder somente com:

1. caminho absoluto do relatório atualizado;
2. arquivos alterados;
3. resumo das correções A–I;
4. matriz de validação com contagens e exits;
5. estado Git e resíduos;
6. divergências restantes, que devem ser `nenhuma` ou acompanhadas de prova;
7. estado literal:

```text
IN_PROGRESS — aguardando revisão terminal do orquestrador
```
