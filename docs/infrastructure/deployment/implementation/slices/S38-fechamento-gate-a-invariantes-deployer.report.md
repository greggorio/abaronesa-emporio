# S38 — Fechamento do Gate A e invariantes do deployer

> **Data:** 03/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Executor:** CLI
> **Contrato:** `S38-fechamento-gate-a-invariantes-deployer.task.md`
> **Resultado:** `IN_PROGRESS — Gate A fechado; aguardando aceite e autorização da preparação da VPS`

## 1. Integridade da base

| Item | Exigido | Observado | Estado |
|---|---|---|---|
| `HEAD` inicial | `69d90ba8bc77f37d1b8bba60e920c8f95c31f22f` | idêntico | conforme |
| `origin/main` inicial | `67abde48fd4a74de5bcff22bf592bd9005094210` | idêntico | conforme |
| remoto `main` inicial | `67abde4...` | idêntico | conforme |
| divergência | ahead 8 / behind 0 | `ahead=8 behind=0` | conforme |
| worktree | limpo | `git status --porcelain` sem saída | conforme |
| SHA-256 do relatório S37 | `f9511f81...68c9b1a5` | idêntico | conforme |
| SHA-256 da task S38 | `ba52fcc5...b01d97cbb0` | idêntico | conforme |

Nenhuma edição foi feita antes desta validação.

## 2. Reprodução causal antes de corrigir (§4.1)

Ambiente de teste isolado provisionado fora do repositório
(`uv sync --locked` com `UV_PROJECT_ENVIRONMENT` apontando para o scratchpad);
`git status --porcelain release_control/` permaneceu vazio, comprovando que
lockfile e árvore não foram tocados.

```text
pytest -q tests/test_deployer_api.py::test_rollback_persists_dispatches_replays_and_supports_get \
          tests/test_deployer_api.py::test_rollback_state_machine_restore_recovery_and_terminal_replay
2 failed, 4 warnings in 3.01s
exit=1
```

Causas, distintas entre si:

| Teste | Sintoma | Causa |
|---|---|---|
| `..._persists_dispatches_replays_and_supports_get` | `assert 429 == 409` | `rollback_actor` aplica `rate_limiter.check` **na resolução da dependência** (`deployer_api.py:165`), antes do corpo do handler e portanto antes da detecção de conflito idempotente. O default é `rollback_rate_per_minute = 2` (`config.py:47`) e o teste emite três POSTs de rollback na mesma janela |
| `..._state_machine_restore_recovery_and_terminal_replay` | `CheckViolation` em `ck_rc_deployment_workflow_binding` | fixture criava a operação com `dispatch_state="CONFIRMED"` e as quatro colunas de vínculo nulas |

### 2.1 Call graph confrontado

Busca completa em código de produção:

```text
def apply_rollback_outcome   deployer_service.py:909
def apply_outcome            deployer_service.py:1045

callers em src/:
  deployer_reconciliation.py:248  ->  service.apply_outcome(
  deployer_reconciliation.py:339  ->  service.apply_rollback_outcome(
```

Ambos os callers ficam **depois** de `_bind_run` (linhas `206` e `303`).
Confirma-se a §2.3 da task: não existe caller de produção que aplique outcome
antes do bind, e a `CheckViolation` observada era de fixture.

## 3. Defeito de produto revelado pela correção da fixture

Corrigida a fixture, o teste avançou e falhou mais fundo, numa asserção que a
`CheckViolation` vinha mascarando:

```text
assert [] == ['PRECHECKING', 'RESTORING', 'SWITCHING', 'VERIFYING', 'SUCCEEDED']
```

Diagnóstico: `_append_journal` mutava o dict armazenado **no lugar** e
reatribuía **o mesmo objeto** a `operation.journal_json`. A coluna é `JSONB`
simples (`persistence.py:119`), sem `MutableDict`, de modo que a alteração não
era detectada e **não chegava ao banco**. Escalares aplicados na mesma
transação — `state` e `active_slot` — persistiam normalmente, o que explica a
asserção anterior passar e apenas o journal ficar vazio.

Impacto real: em produção, um rollback perderia **todas** as suas transições de
journal. A correção passa a atribuir um objeto novo, sem alterar schema,
constraint ou contrato.

## 4. Alterações

Fronteira da §5 respeitada: somente dois arquivos, ambos na lista permitida.
Nenhuma migration, workflow, Compose, arquivo de ambiente, contrato de release,
transporte SSH ou código comercial foi tocado.

### `release_control/src/emporio_release_control/deployer_service.py` (+51 −8)

| Linha | Alteração |
|---|---|
| `8` | `import re` |
| `17-23` | `REPOSITORY` acrescentado ao import de `.constants` |
| `58-61` | `RUN_URL_RE` e `CONTROL_SHA_RE`, derivados de `REPOSITORY` |
| `904` | `_require_workflow_binding`, validação comum e estática |
| `935` | `_append_journal` passa a atribuir objeto novo |
| `966` | chamada em `apply_rollback_outcome`, antes de qualquer mutação |
| `1103` | chamada em `apply_outcome`, antes de qualquer mutação |

`_require_workflow_binding` retorna imediatamente quando `transport` não é
`CONFIRMED`, preservando integralmente o caminho `INDETERMINATE`/`UNCERTAIN`.
Para `CONFIRMED`, exige `workflow_run_id` e `workflow_attempt` inteiros
positivos (rejeitando `bool`), `workflow_run_url` casando exatamente o padrão
do repositório canônico e `control_sha` com 40 hexadecimais minúsculos. Falha
com o código de domínio **já existente** `WORKFLOW_RUN_BINDING_INVALID`, sem
ampliar a API pública.

### `release_control/tests/test_deployer_api.py` (+224 −6)

| Linha | Alteração |
|---|---|
| `14-19` | `RuntimeFailure` acrescentado ao import de `.errors` |
| `130` | `build_deployer`, fábrica que aceita overrides de settings por cenário |
| `155` | fixture `deployer` passa a delegar à fábrica, sem mudança de comportamento |
| `164` | fixture `deployer_unthrottled_rollback`, com `rollback_rate_per_minute=10` |
| `533` | o teste de idempotência passa a usar essa fixture |
| `612` | **novo** `test_rollback_third_mutation_is_rate_limited` |
| `847-856` | fixture `CONFIRMED` recebe os quatro campos reais de vínculo |
| `926` | **novo** `test_confirmed_outcome_before_run_binding_is_refused_without_mutation` |
| `1003` | **novo** `test_each_binding_field_alone_invalidates_a_confirmed_outcome` |

## 5. Cobertura das exigências da task

### §4.2 — idempotência isolada do rate limiter

O default de produção **permanece `2/min`**; nenhum arquivo de configuração foi
alterado. O override existe apenas na fixture de um cenário, e a docstring
declara por que existe.

| Exigência | Onde é provada |
|---|---|
| primeira mutação aceita | `..._persists_dispatches_replays...` (`202`, `Idempotency-Replayed: false`) |
| replay idempotente aceito | mesmo teste (`202`, `Idempotency-Replayed: true`) |
| payload divergente com mesma chave devolve `409` | mesmo teste (`IDEMPOTENCY_CONFLICT`) |
| terceira mutação no default devolve `429` | `test_rollback_third_mutation_is_rate_limited` |
| `429` não cria operação, journal ou dispatch | mesmo teste, comparando contagem de `DeploymentOperation` e de `rollback_dispatches` antes e depois |

### §4.3 — fixture vinculada

```text
workflow_run_id   100
workflow_attempt  1
workflow_run_url  https://github.com/greggorio/abaronesa-emporio/actions/runs/100
control_sha       "c" * 40
```

Valores idênticos aos que `_bind_run` escreve e coerentes com
`ck_rc_deployment_workflow_run_url`. A constraint **não** foi relaxada e o
estado `CONFIRMED` **não** foi trocado por outro para fazer o teste passar.

### §4.4 — defesa em profundidade

| # | Exigência | Prova |
|---|---|---|
| 1 | vínculo completo permite o comportamento vigente | suíte inteira verde, incluindo os testes de sucesso e de `current` |
| 2 | cada campo ausente, isoladamente, falha | `test_each_binding_field_alone...`, 8 casos: cada um dos quatro campos como `None` e como valor inválido |
| 3 | vínculo parcial falha com o mesmo código estável | mesmo teste, sempre `WORKFLOW_RUN_BINDING_INVALID` |
| 4 | operação permanece semanticamente inalterada | `test_confirmed_outcome_before_run_binding...` compara `state`, `transport_status`, `outcome_sha256`, `active_slot` e `journal_json` antes e depois |
| 5 | sem evidência falsa | mesmo teste: `CurrentInstallation` continua `None` e `rc_audit_event` continua vazio |
| 6 | replay terminal idêntico continua idempotente | `test_success_outcome_updates_current_and_replay_is_noop` e o replay terminal do teste de rollback |
| 7 | `INDETERMINATE` sem vínculo segue fail-closed | `test_uncertain_outcome_does_not_regress_confirmed_dispatch` e assert explícito em `test_each_binding_field_alone...` |

Nota sobre o item 2: vínculos parciais **não podem ser persistidos**, porque a
constraint os proíbe. A prova por campo é feita diretamente contra o
invariante; a prova de não-mutação usa o cenário realista e persistível de uma
operação em `SENT` sem vínculo recebendo outcome `CONFIRMED` — exatamente a
ordenação que a S37 apontou como risco.

### §4.5 — raiz canônica

```text
tools/deploy/deployment_cli.py:27              DEFAULT_DEPLOY_ROOT = /opt/sistemas/emporio
tools/deploy/deployment_transport.py:36        DEPLOY_ROOT         = /opt/sistemas/emporio
tools/deploy/validate_production_adapter.py:223,334   literal exigido
tools/deploy/tests/test_deployment_transport.py:841   deployRoot esperado
ops/deploy/deployment-remote.py:34,285         DEPLOY_ROOT / deployRoot
```

Busca por `/opt/sistemas/baronesa` fora de `docs/`: **nenhuma ocorrência**. As
menções históricas ao path alternativo permanecem apenas no relatório da S37,
como evidência, e não foram reescritas. A VPS não foi tocada.

## 6. Validação local (§6)

### 6.1 Suíte do release control

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest -q release_control/tests
331 passed, 7 warnings in 25.09s
exit=0
```

Antes: `318 passed, 2 failed`. Depois: `331 passed`, zero falhas e **zero
skips**. O acréscimo líquido de 11 casos corresponde a 1 do rate limit, 2 da
recusa sem mutação e 8 por campo de vínculo.

### 6.2 Os 17 validadores

| Validador | Exit | Saída |
|---|---:|---|
| `tools/docker/validate_node_images.py validate` | 0 | `node-images-contract:valid` |
| `tools/docker/java_images_contract.py validate` | 0 | `java-images-contract:valid` |
| `tools/ci/validate_ci.py` | 0 | `ci:valid` |
| `tools/ci/invocability.py` | 0 | `invocability:valid:commands=26:parse_args=23:argument-free=3` |
| `tools/ci/migrations_contract.py` | 0 | `migrations:valid` |
| `tools/candidates/validate_candidate_workflow.py` | 0 | `candidate-workflow:valid` |
| `tools/releases/validate_release_workflow.py` | 0 | `release-workflow:valid` |
| `tools/releases/validate_publisher_ui.py` | 0 | `publisher-ui:valid` |
| `tools/releases/validate_publisher_identity_bridge.py` | 0 | `publisher-identity-bridge:valid` |
| `tools/ci/validate_workflow_inventory.py` | 0 | `workflow-inventory:valid` |
| `tools/deploy/validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `tools/deploy/validate_rollback_contract.py` | 0 | `rollback-contract:valid` |
| `tools/deploy/validate_rollback_runtime.py` | 0 | `rollback-runtime:valid` |
| `tools/releases/release_control_contract.py validate` | 0 | `release-control-contract:valid` |
| `tools/security/bootstrap_contract.py validate` | 0 | `bootstrap-contract:valid` |
| `tools/compose/validate_compose.py` | 0 | `Compose contract valid` |
| `tools/gateway/validate_gateway.py` | 0 | `Gateway contract valid` |

### 6.3 As oito suítes canônicas

| Suíte | Exit | Testes | Resultado |
|---|---:|---:|---|
| `tools/docker/tests` | 0 | 117 | OK |
| `tools/ci/tests` | 0 | 30 | OK |
| `tools/candidates/tests` | 0 | 75 | OK |
| `tools/releases/tests` | 0 | 300 | OK |
| `tools/deploy/tests` | 0 | 353 | OK |
| `tools/security/tests` | 0 | 26 | OK |
| `tools/compose/tests` | 0 | 4 | OK |
| `tools/gateway/tests` | 0 | 4 | OK |
| **total** | | **909** | OK |

### 6.4 Gates finais

| Comando | Exit | Saída |
|---|---:|---|
| `candidate_manifest.py validate --manifest ops/releases/examples/candidate-manifest.example.json` | 0 | `candidate:valid` |
| `global_release.py validate --manifest ops/releases/examples/global-release.example.json` | 0 | `global-release:valid` |
| `tools/ci/secret_scan.py --tracked` | 0 | `secret-scan:clean:scanned=2475:allowed=752:unsupported=0:history_scanned=112958` |
| `git diff --check` | 0 | sem saída |

Todos os exits foram capturados explicitamente; nenhum gate foi considerado
verde por silêncio.

## 7. Commit e push

Remoto reconfirmado imediatamente antes do push:

```text
git ls-remote origin refs/heads/main  ->  67abde48fd4a74de5bcff22bf592bd9005094210
REMOTO_INALTERADO
```

Stage contendo apenas implementação e teste; o relatório S38 **não** foi
staged:

```text
release_control/src/emporio_release_control/deployer_service.py
release_control/tests/test_deployer_api.py
```

Commit único:

```text
bd1f51f  fix: hold the deployer workflow binding invariant in the service
         2 files changed, 275 insertions(+), 14 deletions(-)
```

Push único, verificado fast-forward antes (`git merge-base --is-ancestor
origin/main HEAD` exit `0`):

```text
git push origin main
   67abde4..bd1f51f  main -> main
```

O push levou, além do commit de código, os oito commits documentais já aceitos
que estavam locais — coerente com a instrução de "push normal e estritamente
fast-forward de `main`" e com a regra do tracker de que código e documentação
compartilham o critério de aceite. Nenhum rebase, cherry-pick, amend, force
push ou reescrita de histórico foi usado.

## 8. Validação remota

### 8.1 CI

```text
run        30812658858
url        https://github.com/greggorio/abaronesa-emporio/actions/runs/30812658858
event      push        attempt 1
headSha    bd1f51f96866665a3d5f0e43e15d27dab4e94e74
conclusão  success     jobs 13/13 success
```

### 8.2 Publish Candidate

```text
run        30813218997
url        https://github.com/greggorio/abaronesa-emporio/actions/runs/30813218997
event      workflow_run   attempt 1
headSha    bd1f51f96866665a3d5f0e43e15d27dab4e94e74
conclusão  success        jobs 11/11 success
```

Jobs, todos `success`:

```text
trust
predecessor
build (backend | website_back | frontend | website_front | whatsapp_service | gateway)
assemble
integrated
publish
```

Disparado pela CI do mesmo SHA, como exigido, e vinculado a ele.

### 8.3 Artifacts do candidato

Os três artifacts nominais do contrato, todos não expirados:

```text
candidate-manifest             id 8855849013  digest sha256:f1dd231e360fa9ec87807d4ca118a37300a19d5181b9a669dac564c0e315b3e5
candidate-outcome              id 8855849455  digest sha256:6faaec5b3a27eb0d433b2d82ef4b089b4ac6f011168ea8e990781570079338d6
candidate-effective-plan       id 8855629496  digest sha256:c138dc4b64cf4265bba4c9eb161fa5a715cea76b335594203bdb547e16344147
```

Acompanhados dos seis `candidate-component-*`, do
`candidate-integration-result`, do `candidate-pending` e do
`candidate-predecessor-context`, todos `expired=false`. Nenhuma release foi
publicada a partir deste candidato.

## 9. Negativos preservados

```text
tags remotas                 1   apenas v0.1.0
GitHub Releases              1   v0.1.0, draft=false, prerelease=false,
                                 createdAt 2026-08-03T09:53:45Z — inalterada
runs publish-release.yml     5   nenhum novo
runs deploy-production.yml   0
runs rollback-production.yml 0
```

Nesta slice não houve: acesso ou mutação da VPS; criação de usuário, diretório,
swap, serviço, container, volume, Nginx, TLS, firewall, backup ou reinício
agendado; abertura de `ops/env/.env.production` ou de qualquer segredo; criação
ou alteração de GitHub App, environment, variable ou secret; `gh workflow run`;
criação de tag ou release; deploy ou rollback; remoção de run, log, artifact ou
evidência; alteração da política real de rate limit; relaxamento de constraint,
schema ou estado fail-closed; force push ou reescrita de histórico.

## 10. Estado final e resíduos

```text
HEAD         bd1f51f96866665a3d5f0e43e15d27dab4e94e74
origin/main  bd1f51f96866665a3d5f0e43e15d27dab4e94e74
ahead 0 / behind 0
worktree     somente o relatório desta slice, não rastreado
```

Arquivo criado exclusivamente:

```text
docs/infrastructure/deployment/implementation/slices/S38-fechamento-gate-a-invariantes-deployer.report.md
```

O ambiente virtual de teste foi provisionado fora do repositório, no scratchpad
da sessão, e não deixou resíduo na árvore: `uv.lock`, `pyproject.toml` e
`release_control/` permaneceram sem modificação em todo o processo. Nenhum
container, banco ou processo persistente foi deixado — os testes usam
`testcontainers`, que remove o PostgreSQL efêmero ao final da sessão.

O relatório permanece local, não staged e não commitado. O executor não aceita
S38 e não cria a próxima slice.

IN_PROGRESS — Gate A fechado; aguardando aceite e autorização da preparação da VPS

---

## 11. Revisão e aceite do orquestrador — 03/08/2026

### 11.1 Evidência confirmada

O orquestrador revalidou independentemente:

- `HEAD`, `origin/main` e remoto no SHA
  `bd1f51f96866665a3d5f0e43e15d27dab4e94e74`, sem divergência;
- diff restrito a `deployer_service.py` e `test_deployer_api.py` no commit de
  implementação;
- default `rollback_rate_per_minute=2` e constraint/migrations inalterados;
- call graph com `_bind_run` anterior aos dois aplicadores de outcome;
- 13 casos causais direcionados verdes;
- suíte integral independente com `331 passed`, zero falhas;
- CI `30812658858`, SHA correto, tentativa 1 e 13/13 jobs verdes;
- Publish Candidate `30813218997`, mesmo SHA, tentativa 1 e 11/11 jobs verdes;
- três artifacts terminais e seis artifacts de componente presentes, não
  expirados e vinculados ao SHA;
- `v0.1.0` inalterada, nenhum novo publish-release e zero runs de deploy ou
  rollback.

O reparo adicional de `_append_journal` é aceito: a atribuição de um novo objeto
é necessária para que SQLAlchemy detecte a alteração da coluna JSONB. O defeito
estava mascarado pela fixture inválida e teria removido do banco as transições
de rollback sem impedir a persistência dos escalares.

### 11.2 Estado do roadmap

O Gate A está fechado. A inspeção posterior também corrigiu uma imprecisão da
S37: o Compose comercial já define `mem_limit`, `cpus` e `pids_limit` nos sete
serviços. Os defaults de memória somam 4.224 MiB; a decisão de capacidade segue
necessária antes do primeiro deploy porque a VPS tinha apenas 3,7 GiB
disponíveis, mas não exige nova implementação local do Compose por ausência de
limites.

Antes do Gate B, permanece uma lacuna objetiva: o `release_control` está
empacotado, porém seu Compose ainda referencia
`emporio-release-control:package-placeholder`, e não existe workflow de build,
scan e publicação da imagem operacional por digest. A arquitetura exige ciclo
independente do BOM comercial. S39 fecha esse mecanismo sem tocar VPS e sem
publicar ainda a imagem.

### 11.3 Decisão terminal

ACCEPTED — Gate A fechado; S38 encerrada e S39 aberta
