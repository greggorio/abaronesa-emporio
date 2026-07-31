# S19 — Núcleo transacional e journal de implantação

> **Estado:** IN_PROGRESS — aguardando revisão do orquestrador  
> **Data da execução:** 29/07/2026  
> **CWD:** `/home/gregorio/git/baronesa/emporio`

## 1. Resultado

A S19 foi executada integralmente dentro da fronteira autorizada. Foi
implementado um núcleo transacional offline, dirigido exclusivamente por
adapter e clock injetados, sem CLI operacional e sem qualquer adapter real.

O resultado contém:

- schema fechado e exemplo do journal;
- máquina transacional vinculada aos estados da S06;
- lock global real com `fcntl.flock`, exclusivo e não bloqueante;
- journal durável, canônico e atômico;
- probes antes e depois de cada side effect abstrato;
- retomada de operações interrompidas;
- compensação a partir de `MIGRATING`;
- confirmação atômica do estado instalado somente após `VERIFY`;
- validador local fail-closed;
- 49 testes funcionais e 18 testes contratuais/mutantes da S19;
- documentação operacional da fronteira S19/S20.

Nenhum comando operacional externo foi implementado ou executado.

## 2. Arquivos criados

1. `ops/deploy/schemas/deployment-journal.schema.json`
2. `ops/deploy/examples/deployment-journal.example.json`
3. `tools/deploy/deployment_executor.py`
4. `tools/deploy/validate_deployment_executor.py`
5. `tools/deploy/tests/test_deployment_executor.py`
6. `tools/deploy/tests/test_deployment_executor_contract.py`
7. `docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md`
8. `docs/infrastructure/deployment/implementation/slices/S19-nucleo-transacional-journal-implantacao.report.md`

## 3. Arquivos alterados

1. `docs/infrastructure/deployment/release-control/README.md`
2. `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`

Nenhum arquivo fora da lista autorizada pela task foi criado ou alterado pela
S19.

## 4. Implementação entregue

### 4.1 Superfície do núcleo

`tools/deploy/deployment_executor.py` expõe a superfície determinada:

- `DeploymentExecutionError`;
- `ProbeResult`;
- `ActionContext`;
- `DeploymentAdapter`;
- `DeploymentClock`;
- `execute_deployment`.

Helpers adicionais são privados. O módulo reutiliza
`deployment_plan.validate_bundle` e não contém CLI, subprocesso, relógio do
sistema, biblioteca de rede, banco, Docker ou adapter operacional.

### 4.2 Validação, paths e lock

Antes de lock ou journal, o bundle S18 é integralmente revalidado. Operation
IDs, diretórios, modos, limites e symlinks são validados de forma fail-closed.
O lock `.production.lock` usa `O_NOFOLLOW`, modo `0600` e:

```python
fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
```

Todos os journals `*.json` são auditados sob o lock. Journal inválido bloqueia
com `JOURNAL_CORRUPT`; outro journal não terminal bloqueia com
`PRODUCTION_OPERATION_ACTIVE`.

### 4.3 Journal, estados e steps

O schema Draft 2020-12 fecha propriedades adicionais em todos os objetos.
Estados e transições coincidem com a máquina S06 a partir de `QUEUED`, com a
fronteira local determinada `null -> QUEUED`.

Ordem fixa dos steps:

```text
PULL
BACKUP
MIGRATE
UPDATE
VERIFY
COMMIT_STATE
ROLLBACK
```

O validador semântico verifica sequência, encadeamento, timestamps, estados
terminais, coerência entre estado e step, evidence fechada, hashes confirmados
e monotonicidade de `databaseRestoreRequired`.

### 4.4 Persistência e retomada

Journal e estado instalado usam JSON canônico com LF, temp irmão exclusivo em
`0600`, escrita, flush, `fsync` do arquivo, releitura, validação,
`os.replace` e `fsync` do diretório pai. Falhas anteriores ao replace removem
somente o temp. Artefato integral substituído antes de falha/crash do processo
é reconhecido na retomada.

Step `RUNNING` é retomado por probe antes de qualquer novo `execute`. Steps
concluídos não são repetidos. Steps `FAILED` persistidos antes de uma
interrupção continuam a decisão terminal/compensatória na invocação seguinte,
sem serem reinterpretados como corrupção.

### 4.5 Probes, compensação e confirmação

Para ação não no-op:

```text
persistir RUNNING -> probe -> [execute se ABSENT] -> probe -> persistir evidence
```

Falhas em `PULLING` ou `BACKING_UP` terminam sem rollback. Falhas a partir de
`MIGRATING` entram em `ROLLING_BACK`. Rollback comprovado termina
`ROLLED_BACK`; rollback falho ou incerto termina `FAILED`.

`databaseRestoreRequired` torna-se verdadeiro antes do primeiro probe de uma
migration real e nunca volta a falso. Rollback não declara que o banco foi
restaurado.

`installed-state.json` somente é materializado depois de
`VERIFY=SUCCEEDED`. O hash dos bytes canônicos confirmados é persistido no
journal e também forma a evidence sanitizada de `COMMIT_STATE`.

## 5. Comandos e resultados

Todos os comandos desta seção foram executados a partir do CWD registrado.

### 5.1 Pré-validação sintática

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m py_compile tools/deploy/deployment_executor.py
```

- Exit code: `0`.
- Resultado: módulo sintaticamente válido.
- Resíduo intermediário: um `__pycache__` foi detectado e removido antes da
  validação final.

### 5.2 Primeira execução da suíte de deploy

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
```

- Exit code: `1`.
- Resultado intermediário: `101` testes executados, `1` falha.
- Causa: o teste de conflito de identidade adulterava `bundle.sha256` e,
  portanto, produzia corretamente `INVALID_CONTRACT` antes de alcançar
  `OPERATION_CONFLICT`.
- Correção: o harness passou a gerar um segundo bundle S18 íntegro, com mesma
  release alvo e `plannedAt` distinto, produzindo identidade válida diferente.
  O contrato do executor não foi relaxado.

### 5.3 Auditoria causal adicional

A revisão do núcleo identificou uma fronteira de crash não suficientemente
tratada: um step já persistido como `FAILED`, antes da transição seguinte,
poderia ser rejeitado como `JOURNAL_CORRUPT` na retomada. A correção passou a
continuar a decisão terminal ou compensatória a partir desse step, inclusive
para `ROLLBACK=FAILED`.

Na mesma revisão foram tornadas explícitas as validações de coerência entre
estado e step, monotonicidade de `databaseRestoreRequired` e canonicalidade do
alvo reconhecido na janela de crash.

Sete provas funcionais adicionais foram incluídas para:

- outro journal não terminal;
- retomada não terminal;
- step `RUNNING`;
- crash antes do replace do estado;
- crash depois do replace do estado;
- falhas em write, fsync, verify e replace;
- interrupção depois do fsync do diretório pai.

### 5.4 Suíte completa de deploy final

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_*.py'
```

- Exit code: `0`.
- Resultado: `108` testes aprovados em `36.322s`.
- Separação: `41` regressões S18 e `67` testes S19.

### 5.5 Contagem S18 isolada

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_deployment_plan*.py'
```

- Exit code: `0`.
- Resultado: `41` testes aprovados em `5.778s`.

### 5.6 Contagem S19 isolada

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/deploy/tests -p 'test_deployment_executor*.py'
```

- Exit code: `0`.
- Resultado: `67` testes aprovados em `29.873s`.
- Composição: `49` funcionais e `18` contratuais/mutantes.

### 5.7 Validador S18

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py
```

- Exit code: `0`.
- Resultado: `deployment-plan-contract:valid`.
- Interpretação: o contrato offline S18 permaneceu íntegro.

### 5.8 Validador S19

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py
```

- Exit code: `0`.
- Resultado: `deployment-executor-contract:valid`.
- Interpretação: schema, exemplo, máquina, superfície Python, lock,
  atomicidade e documentação passaram pelo validador fail-closed.

### 5.9 Regressão de releases

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_*.py'
```

- Exit code: `0`.
- Resultado: `277` testes aprovados em `5.416s`.
- Saídas finais sanitizadas:
  `global-release:generated`, `global-release:valid` e
  `release-workflow:valid`.

### 5.10 Prova funcional sanitizada

Foi executado um harness Python local via stdin, com `FakeAdapter`,
`FakeClock` e diretório temporário, chamando `execute_deployment`. Nenhum
adapter operacional foi usado.

Resultado:

```text
terminal=SUCCEEDED
transitions=QUEUED>PULLING>BACKING_UP>MIGRATING>UPDATING>VERIFYING>SUCCEEDED
steps=PULL:SUCCEEDED,BACKUP:SUCCEEDED,MIGRATE:SUCCEEDED,UPDATE:SUCCEEDED,VERIFY:SUCCEEDED,COMMIT_STATE:SUCCEEDED,ROLLBACK:PENDING
adapter_events=probe:PULL,execute:PULL,probe:PULL,probe:BACKUP,execute:BACKUP,probe:BACKUP,probe:MIGRATE,execute:MIGRATE,probe:MIGRATE,probe:UPDATE,execute:UPDATE,probe:UPDATE,probe:VERIFY,execute:VERIFY,probe:VERIFY
confirmed_hash_matches=true
state_release=v0.0.1
state_reconciled=true
journal_mode=0o600
state_mode=0o600
```

- Exit code: `0`.
- Resíduo: o diretório temporário do harness foi removido pelo cleanup do
  teste.

## 6. Matriz causal da Seção 20

| # | Causa prescrita | Evidência |
|---:|---|---|
| 1 | fluxo completo e ordem | teste funcional 01 e prova sanitizada |
| 2 | primeira instalação confirma após verify | teste 02 |
| 3 | update exige origem coerente | teste 03 |
| 4 | divergência de digest atual | teste 04, seis componentes |
| 5 | divergência de migration set | teste 05, dois bancos |
| 6 | bundle inválido antes de lock/journal/adapter | teste 06 |
| 7 | operation ID inválido | teste 07 |
| 8 | path, modo e symlink inseguros | testes 08 e 09, mais mutantes contratuais |
| 9 | segundo lock simultâneo | teste 10 |
| 10 | outro journal não terminal | teste adicional 43 |
| 11 | journal corrompido | teste 11 |
| 12 | mesmo ID e bundle retoma | testes 12 e adicional 44 |
| 13 | mesmo ID e bundle distinto | teste 13 com segundo bundle válido |
| 14 | `SUCCEEDED` idempotente | teste 14 |
| 15 | `FAILED`/`ROLLED_BACK` não reiniciam | teste 15 |
| 16 | probe `SUCCEEDED` evita execute | teste 16 |
| 17 | probe `ABSENT` executa uma vez | teste 17 |
| 18 | probe `FAILED` falha fechado | teste 18 |
| 19 | probe `UNKNOWN` falha fechado | teste 19 |
| 20 | ProbeResult inválido e sanitizado | teste 20 |
| 21 | retorno de execute diferente de `None` | teste 21 |
| 22 | PULL/BACKUP falhos sem rollback | teste 22 |
| 23 | MIGRATE/UPDATE/VERIFY falhos com rollback | teste 23 |
| 24 | rollback comprovado | teste 24 |
| 25 | rollback falho/incerto | teste 25 |
| 26 | `databaseRestoreRequired` monotônico | teste 26 e validação semântica |
| 27 | no-ops exatos | teste 27 |
| 28 | VERIFY nunca skip | teste 28 |
| 29 | journal antes de cada execute | teste 29 |
| 30 | RUNNING começa por probe | teste adicional 45 |
| 31 | steps concluídos não repetem adapter | testes 14, 15 e 30 |
| 32 | clock inválido/regressivo | testes 31 e 32 |
| 33 | estado usa clock injetado | teste 33 |
| 34 | crash antes/depois do replace | testes adicionais 46 e 47 |
| 35 | mudança concorrente antes do commit | teste 35 |
| 36 | falhas write/fsync/verify/replace | teste adicional 48 |
| 37 | fsync do pai após replace reconciliável | teste adicional 49 |
| 38 | journal/schema/evidence fechados | teste 38 e mutantes contratuais |
| 39 | erro público sanitizado | testes 31 e 39 |
| 40 | sem comando, rede ou relógio do sistema | testes 40 e 41 |
| 41 | mutantes removendo gates | 18 testes contratuais/mutantes |
| 42 | zero caches e temporários | teste 42 e auditoria final |

## 7. Evidências de lock, retomada, compensação e atomicidade

### 7.1 Lock

- Um descriptor já bloqueado produz `PRODUCTION_OPERATION_ACTIVE`.
- Outro journal não terminal válido também produz
  `PRODUCTION_OPERATION_ACTIVE`.
- O adapter permanece sem eventos nas duas rejeições.

### 7.2 Retomada

- Journal não terminal compatível avança até `SUCCEEDED`.
- Step `PULL=RUNNING` consulta probe antes de qualquer decisão.
- Probe já `SUCCEEDED` não repete execute e preserva `attempts=1`.
- Journals terminais são retornados sem chamada ao adapter.

### 7.3 Compensação

- Falhas em PULL/BACKUP terminam `FAILED`, sem evento de rollback.
- Falhas em MIGRATE/UPDATE/VERIFY chamam rollback.
- Rollback comprovado produz `ROLLED_BACK`.
- Rollback `FAILED`/`UNKNOWN` produz `FAILED` e
  `rollbackErrorCode=ROLLBACK_FAILED`.
- `databaseRestoreRequired=true` permanece conservador e não afirma restore.

### 7.4 Atomicidade

- Falhas injetadas em `_write_bytes`, `_fsync_file`,
  `_verify_staged_json` e `_replace_atomic` preservam o journal anterior byte
  a byte e não deixam temp.
- Crash antes do replace do estado deixa o estado ausente/origem e permite
  retomada.
- Crash depois do replace mantém o alvo integral e a retomada conclui sem
  reescrever seus bytes.
- Interrupção após `fsync` do pai preserva o estado integral, reconciliável na
  próxima execução.

## 8. Integridade dos artefatos somente leitura

Hashes observados ao final:

```text
8dc6cbc2c0f435c13ca90683f527c2d82dbafae72c77cfb4a6f1fd4961d871f9  S19-nucleo-transacional-journal-implantacao.task.md
766745f519ba898c4b0e055c64aee26a75c28618760d693ed37f741fd2b9b2eb  implementation/README.md
d706865f29b03890c5d462c4559f2da8bcb8bc737827d2d648ec0a6fae31edd8  tools/deploy/deployment_plan.py
3ae6b1a63d3dfda408bcbbc7a8134bcd06164196843b6157ddcf28023b8975ac  installed-state.schema.json
0ad6249e4b29efd61d72d18eca82ea007e6fd78cfc008af66271215c12c24f20  deployment-plan.schema.json
5cd55981f3d4c6cadc695134ffe15d9bbda961a2630061e98c1ae55aead5248f  deployer.openapi.yml
a2969d65d94b4f5a9fd540e95f8020bc38acb3aac0563c1ad1f221223c479f0c  state-machines.yml
```

A task S19 e o tracker não foram alterados.

## 9. Estado Git e resíduos

Verificações finais executadas:

```bash
git diff --check
git status --short
git diff --cached --name-only
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find . -path '*/__pycache__' -o -name '*.pyc' -o -name '.pytest_cache' -o -name '.mypy_cache'
find .github/workflows -maxdepth 1 -type f -printf '%P\n' | sort
test ! -e docs/infrastructure/deployment/implementation/slices/S20*
```

Uma primeira tentativa de verificação usou `git reflog exists` sem informar
uma ref e retornou exit `129` com a ajuda do Git. Esse comando diagnóstico
incorreto não alterou estado. A verificação foi corrigida para
`git reflog show --all`, que retornou exit `0` e saída vazia.

Resultado confirmado após a persistência deste relatório:

- `git diff --check`: aprovado;
- `git status --short`: workspace pré-primeiro-commit permanece integralmente
  não rastreado, coerente com o estado recebido;
- índice real vazio;
- HEAD inexistente (`git rev-parse`: exit `128`);
- nenhuma tag;
- reflog inexistente;
- três workflows YAML ativos e já existentes:
  `ci.yml`, `publish-candidate.yml`, `publish-release.yml`;
- `README.md` transitório também permanece no diretório de workflows;
- nenhum cache Python, `.pytest_cache`, `.mypy_cache` ou temp S19;
- S20 ausente.

Nenhum `git add`, commit, tag ou push foi executado.

## 10. Ações não executadas

Não houve:

- Docker, Compose ou Podman;
- banco, migration real, backup real ou restore real;
- comando externo operacional;
- acesso de rede;
- GitHub, GHCR, DNS, VPS ou produção;
- Maven, npm, actionlint ou testes de `release_control`;
- instalação de dependências;
- criação de workflow, adapter real, CLI operacional ou S20.

## 11. Divergências e itens não determinados

Não há divergência conhecida entre a implementação final e o contrato S19.

Os comandos reais de Docker, PostgreSQL, backup, health checks e rollback
continuam deliberadamente não determinados nesta slice e pertencem à futura
fronteira S20. Nenhuma afirmação foi feita de que rollback de imagens restaura
banco.

## 12. Estado final

**IN_PROGRESS — aguardando revisão do orquestrador**

## 13. Revisão do orquestrador — ciclo 1

### 13.1 Veredito

`IN_PROGRESS — correção causal requerida`.

Os testes do executor não foram reexecutados. A revisão comparou o relatório,
o contrato S19 e as superfícies versionadas.

Foram confirmados o fluxo nominal, persistência antes dos adapters, lock,
probes, compensação, vínculo do estado de origem e reconciliação da janela de
replace. Permanecem dois bloqueios.

### 13.2 Bloqueio A — conflito de estado em retomada antes de step

Em `execute_deployment`, um journal não terminal é submetido a
`_state_classification` antes de `_run_transaction`. Se a classificação falha
com journal ainda em `QUEUED`, `_complete_failure` tenta persistir:

```text
QUEUED -> FAILED
errorCode=CURRENT_STATE_CONFLICT
```

Nenhum step começou nesse instante. Entretanto,
`_validate_journal_semantics` rejeita todo `FAILED` sem ao menos um step
`FAILED`. A própria transição terminal falha durante `_persist_journal` e é
convertida em `JOURNAL_IO_FAILED`. O journal anterior continua `QUEUED`, de
modo que a retomada repete o mesmo impasse.

Esse comportamento diverge das seções 16.1, 16.4 e 17 da task: conflito
posterior à criação do journal deve ser persistido como decisão terminal
quando ainda não há fase compensatória.

### 13.3 Bloqueio B — causalidade temporal incompleta no journal runtime

`_validate_journal_semantics` valida formato e limites globais, mas não rejeita
integralmente:

- `finishedAt < startedAt` dentro de um step;
- `evidence.observedAt < startedAt`;
- `evidence.observedAt > finishedAt`;
- timestamp de step fora da janela das transições do estado que o contém;
- `finishedAt` terminal diferente de `updatedAt`.

O validador versionado cobre algumas dessas relações, porém também não fecha
todas as janelas entre transitions e steps. Assim, um journal adulterado pode
passar na leitura runtime apesar de descrever causalidade impossível, em
desacordo com as seções 8.3, 9, 10 e 19 da task.

### 13.4 Correção causal fechada

Alterar somente:

```text
tools/deploy/deployment_executor.py
tools/deploy/validate_deployment_executor.py
tools/deploy/tests/test_deployment_executor.py
tools/deploy/tests/test_deployment_executor_contract.py
docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md
docs/infrastructure/deployment/implementation/slices/S19-nucleo-transacional-journal-implantacao.report.md
```

Não alterar schema, exemplo, task, tracker, S18 ou outras superfícies.

Implementar exatamente:

1. remover a exigência runtime adicional de que todo `FAILED` tenha algum
   step `FAILED`; o contrato canônico exige `errorCode` ou
   `rollbackErrorCode`, e a transição `QUEUED -> FAILED` pode ocorrer antes do
   primeiro step;
2. preservar as demais invariantes de `FAILED`, inclusive estado terminal,
   `finishedAt`, transition válida e erro sanitizado;
3. validar em runtime e no validador versionado, para todo step não pendente:
   `startedAt <= finishedAt` quando finalizado;
4. para step `SUCCEEDED`, exigir
   `startedAt <= evidence.observedAt <= finishedAt`;
5. exigir que timestamps de cada step fiquem dentro da janela causal:
   - `PULL`: entrada em `PULLING` até saída para `BACKING_UP`/`FAILED`;
   - `BACKUP`: entrada em `BACKING_UP` até saída para `MIGRATING`/`FAILED`;
   - `MIGRATE`: entrada em `MIGRATING` até saída para
     `UPDATING`/`ROLLING_BACK`;
   - `UPDATE`: entrada em `UPDATING` até saída para
     `VERIFYING`/`ROLLING_BACK`;
   - `VERIFY` e `COMMIT_STATE`: entrada em `VERIFYING` até saída para
     `SUCCEEDED`/`ROLLING_BACK`;
   - `ROLLBACK`: entrada em `ROLLING_BACK` até saída para
     `ROLLED_BACK`/`FAILED`;
6. dentro de `VERIFYING`, exigir que `COMMIT_STATE.startedAt` não anteceda
   `VERIFY.finishedAt`;
7. para qualquer terminal, exigir `finishedAt == updatedAt == timestamp da
   última transition`;
8. manter igualdade de segundos permitida;
9. journals ativos sem transition de saída usam `updatedAt` como limite
   superior da janela;
10. adicionar provas runtime causais para:
    - crash imediatamente após persistir o journal `QUEUED`, seguido de estado
      conflitante e retomada terminal `FAILED`, sem adapter e sem
      `JOURNAL_IO_FAILED`;
    - journal `PULLING` ainda sem step iniciado com estado conflitante;
    - cada uma das cinco adulterações temporais da seção 13.3;
11. adicionar mutantes equivalentes no validador versionado;
12. documentar que `FAILED` pode não possuir step falho quando a falha é
    detectada entre a criação/transição do journal e o início do próximo step.

### 13.5 Validação exigida

Executar e registrar novamente:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py

PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py

PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'
```

Persistir comandos, exit codes, contagens e matriz causal nesta seção do
relatório. Manter:

```text
IN_PROGRESS — aguardando nova revisão do orquestrador
```

Não criar S20, não executar adapters reais e não acessar Docker, banco, rede
ou produção.

## 14. Resposta às correções do ciclo 1

> **Data da correção:** 30/07/2026  
> **CWD:** `/home/gregorio/git/baronesa/emporio`  
> **Estado:** IN_PROGRESS — aguardando nova revisão do orquestrador

### 14.1 Fronteira executada

Foram implementadas exclusivamente as correções fechadas nas Seções 13.4 e
13.5. Arquivos alterados neste ciclo:

1. `tools/deploy/deployment_executor.py`;
2. `tools/deploy/validate_deployment_executor.py`;
3. `tools/deploy/tests/test_deployment_executor.py`;
4. `tools/deploy/tests/test_deployment_executor_contract.py`;
5. `docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md`;
6. `docs/infrastructure/deployment/implementation/slices/S19-nucleo-transacional-journal-implantacao.report.md`.

Schema, exemplo, task, tracker, artefatos S18, workflows e demais arquivos não
foram alterados.

### 14.2 Resposta ao bloqueio A — `FAILED` entre fases

A exigência runtime adicional de que todo journal `FAILED` possuísse algum
step `FAILED` foi removida. Permanecem obrigatórios:

- transition terminal permitida pela máquina;
- `finishedAt` terminal;
- erro sanitizado em `errorCode` ou `rollbackErrorCode`;
- coerência das demais estruturas e timestamps.

Isso permite representar uma falha detectada entre a criação/entrada no estado
e o início do próximo step, sem inventar um step executado.

Provas causais adicionadas:

- crash imediatamente depois da persistência de `QUEUED`;
- criação posterior de estado instalado conflitante;
- retomada terminal em `FAILED` com
  `errorCode=CURRENT_STATE_CONFLICT`;
- zero evento no adapter;
- ausência de `JOURNAL_IO_FAILED`;
- journal em `PULLING` com `PULL=PENDING`, seguido pelo mesmo conflito e
  término fail-closed.

As duas provas passaram. O conflito posterior à criação do journal agora
produz uma decisão terminal persistente e idempotente, em vez de repetir
indefinidamente uma falha de persistência.

### 14.3 Resposta ao bloqueio B — causalidade temporal

Runtime e validador versionado agora exigem:

1. `startedAt <= finishedAt` em todo step finalizado;
2. `startedAt <= evidence.observedAt <= finishedAt` em todo step
   `SUCCEEDED`;
3. timestamps de cada step dentro da janela causal do estado correspondente:
   - `PULL`: `PULLING` até `BACKING_UP` ou `FAILED`;
   - `BACKUP`: `BACKING_UP` até `MIGRATING` ou `FAILED`;
   - `MIGRATE`: `MIGRATING` até `UPDATING` ou `ROLLING_BACK`;
   - `UPDATE`: `UPDATING` até `VERIFYING` ou `ROLLING_BACK`;
   - `VERIFY` e `COMMIT_STATE`: `VERIFYING` até `SUCCEEDED` ou
     `ROLLING_BACK`;
   - `ROLLBACK`: `ROLLING_BACK` até `ROLLED_BACK` ou `FAILED`;
4. `COMMIT_STATE.startedAt >= VERIFY.finishedAt`;
5. em journal ativo sem transition de saída, `updatedAt` como limite superior
   da janela;
6. em qualquer terminal,
   `finishedAt == updatedAt == timestamp da última transition`;
7. igualdade no mesmo segundo permitida.

O runtime rejeita essas divergências como `JOURNAL_CORRUPT` antes do adapter.
O validador versionado emite códigos causais específicos para ordem do step,
evidence, janela, ordem verify/commit e término.

### 14.4 Provas runtime

| Prova | Mutação/causa | Resultado |
|---|---|---|
| retomada `QUEUED` | estado conflitante antes do primeiro step | `FAILED/CURRENT_STATE_CONFLICT`, zero adapter |
| retomada `PULLING` | `PULL=PENDING` e estado conflitante | `FAILED/CURRENT_STATE_CONFLICT`, zero adapter |
| ordem do step | `finishedAt < startedAt` | `JOURNAL_CORRUPT` |
| evidence anterior | `observedAt < startedAt` | `JOURNAL_CORRUPT` |
| evidence posterior | `observedAt > finishedAt` | `JOURNAL_CORRUPT` |
| janela do estado | timestamp de PULL anterior à entrada em PULLING | `JOURNAL_CORRUPT` |
| término | `finishedAt` terminal diferente de `updatedAt` | `JOURNAL_CORRUPT` |

Todos os casos temporais foram construídos a partir de um journal válido com
timestamps separados, alterando uma causa por vez. O adapter permaneceu sem
eventos em todas as rejeições.

### 14.5 Matriz de mutantes do validador versionado

| Teste causal | Gate comprovado |
|---|---|
| `test_step_finished_before_started_mutant_fails` | ordem interna do step |
| `test_evidence_before_started_mutant_fails` | limite inferior da evidence |
| `test_evidence_after_finished_mutant_fails` | limite superior da evidence |
| `test_step_timestamps_outside_state_window_mutants_fail` | entrada e saída da janela |
| `test_commit_state_cannot_start_before_verify_finishes` | ordem VERIFY/COMMIT_STATE |
| `test_terminal_timestamp_must_equal_updated_and_last_transition` | igualdade tripla terminal |
| `test_equal_second_boundaries_remain_valid` | igualdade de segundos preservada |

O arquivo de testes contratuais passou a conter `25` testes. Junto aos `52`
testes funcionais, a S19 possui `77` provas. As `41` provas S18 completam os
`118` testes da suíte de deploy.

### 14.6 Falha intermediária e ajuste

O fechamento temporal tornou causalmente inválido um fixture preexistente do
teste do clock confirmado: o clock do teste avançava para `17:10`, mas o
adapter fake continuava emitindo evidence observada às `17:00`, antes da
entrada do respectivo step. O fixture foi alinhado ao mesmo clock injetado.
Nenhuma regra do runtime foi relaxada.

Não houve falha nos quatro comandos finais prescritos.

### 14.7 Validação obrigatória

Todos os comandos foram executados no CWD registrado.

#### 14.7.1 Suíte de deploy

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/deploy/tests \
  -p 'test_*.py'
```

- Exit code: `0`.
- Resultado: `118` testes aprovados em `39.010s`.
- Contagem: `41` S18 e `77` S19.
- Composição S19: `52` funcionais e `25` contratuais/mutantes.

#### 14.7.2 Validador S18

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_plan.py
```

- Exit code: `0`.
- Resultado: `deployment-plan-contract:valid`.
- Interpretação: nenhum contrato S18 foi alterado ou relaxado.

#### 14.7.3 Validador S19

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py
```

- Exit code: `0`.
- Resultado: `deployment-executor-contract:valid`.
- Interpretação: exemplo original, schema original, runtime, máquina,
  causalidade temporal e documentação permanecem coerentes.

#### 14.7.4 Regressão de releases

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'
```

- Exit code: `0`.
- Resultado: `277` testes aprovados em `5.833s`.
- Saídas sanitizadas:
  `global-release:generated`, `global-release:valid` e
  `release-workflow:valid`.

### 14.8 Integridade da fronteira proibida

Os hashes congelados antes da correção foram repetidos depois dela e
permaneceram idênticos:

```text
de8bb20bcadf762de6f0d492d13668c0048ccb34915bd45a93480f9463a2ceeb  deployment-journal.schema.json
f3d93267bc252ff08f36a8d71cb27d7dca03c30bf24b8a934edc67d15b36a373  deployment-journal.example.json
8dc6cbc2c0f435c13ca90683f527c2d82dbafae72c77cfb4a6f1fd4961d871f9  S19-nucleo-transacional-journal-implantacao.task.md
766745f519ba898c4b0e055c64aee26a75c28618760d693ed37f741fd2b9b2eb  implementation/README.md
d706865f29b03890c5d462c4559f2da8bcb8bc737827d2d648ec0a6fae31edd8  tools/deploy/deployment_plan.py
3ae6b1a63d3dfda408bcbbc7a8134bcd06164196843b6157ddcf28023b8975ac  installed-state.schema.json
0ad6249e4b29efd61d72d18eca82ea007e6fd78cfc008af66271215c12c24f20  deployment-plan.schema.json
47bd58f7f7b34f3ae28b2115b85d401379f7cd32a9f2a8669f05e70a77d4aeff  ci.yml
9f7d14dab0edde36c4ca2393b68b6c133dd95042463b5ce3d2b0b52bca4a1c3d  publish-candidate.yml
74e37580a317ce8d40c1f1794f4d6eef289c8b96bc6efcbc2e154907cdad6a07  publish-release.yml
```

### 14.9 Estado protegido final

Verificações:

```bash
git diff --check
git status --short
git diff --cached --name-only
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f -printf '%P\n' | sort
find docs/infrastructure/deployment/implementation/slices -maxdepth 1 -name 'S20*' -print
find . -type d \( -name __pycache__ -o -name .pytest_cache -o -name .mypy_cache \) -print -o -type f -name '*.pyc' -print
```

Resultados:

- `git diff --check`: exit `0`;
- workspace continua no estado pré-primeiro-commit recebido;
- índice real vazio;
- HEAD inexistente: exit `128`;
- tags e reflog vazios;
- workflows preservados: `ci.yml`, `publish-candidate.yml` e
  `publish-release.yml`, além do `README.md` transitório;
- nenhum cache Python, cache de teste ou resíduo temporário;
- S20 ausente;
- nenhum `git add`, commit, tag ou push;
- nenhum adapter real, Docker, banco, rede, GitHub, GHCR, VPS ou produção.

### 14.10 Estado da slice

**IN_PROGRESS — aguardando nova revisão do orquestrador**

## 15. Aceite terminal do orquestrador

> **Data:** 31/07/2026  
> **Estado:** `ACCEPTED`

Os dois bloqueios do ciclo anterior foram fechados:

1. retomadas em `QUEUED` ou `PULLING`, antes do início do step, persistem
   `FAILED/CURRENT_STATE_CONFLICT` sem adapter, step artificial ou ciclo de
   `JOURNAL_IO_FAILED`;
2. runtime e validador versionado fecham ordem interna, evidence, janelas
   causais, ordem `VERIFY`/`COMMIT_STATE` e igualdade temporal terminal.

Validação independente do orquestrador:

```text
118 testes de deploy aprovados
  41 testes S18
  77 testes S19
deployment-plan-contract:valid
deployment-executor-contract:valid
277 testes de releases aprovados
git diff --check aprovado
```

Os hashes da fronteira proibida coincidiram com os valores congelados pelo
executor. O índice permaneceu vazio, sem `HEAD`, tags, reflog ou caches. Não
foi encontrado adapter operacional dentro do núcleo S19.

A S19 satisfaz os 14 critérios da Seção 23 de sua task. A próxima fronteira é
a S20, criada pelo orquestrador neste mesmo ciclo.
