# S19 — Núcleo transacional e journal de implantação

> **Estado:** `ACCEPTED` — 31/07/2026  
> **Tipo:** máquina transacional offline, journal durável e compensação  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01 a S18 `ACCEPTED`  
> **Relatório de saída:** `S19-nucleo-transacional-journal-implantacao.report.md`

## Instrução para delegação

Execute integralmente esta slice. Leia, nesta ordem:

1. esta task inteira;
2. a revisão terminal da S18;
3. `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`;
4. `docs/infrastructure/deployment/release-control/CONTRATO_API_ESTADOS_SEGURANCA.md`;
5. `docs/infrastructure/deployment/release-control/contracts/state-machines.yml`;
6. `docs/infrastructure/deployment/release-control/api/deployer.openapi.yml`;
7. `ops/deploy/schemas/installed-state.schema.json`;
8. `ops/deploy/schemas/deployment-plan.schema.json`;
9. `ops/deploy/examples/deployment-plan.example.json`;
10. `tools/deploy/deployment_plan.py`;
11. a seção “Execução do deploy na VPS” de
    `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`.

O executor implementa as decisões abaixo. Não escolha estados, ordem, política
de retomada, semântica de probe, formato do journal, lock, momento de
compensação, confirmação do estado ou códigos de erro.

Não altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

## 1. Resultado observável

Ao final existe um núcleo Python testável que:

- recebe um bundle S18 já validável;
- adquire um lock global exclusivo;
- cria ou retoma um journal durável por `operationId`;
- executa a ordem `PULL`, `BACKUP`, `MIGRATE`, `UPDATE`, `VERIFY`,
  `COMMIT_STATE`;
- nunca confia somente no retorno de uma ação externa;
- exige probe reconciliado antes/depois de cada ação;
- não repete ação já comprovada;
- compensa falhas ocorridas a partir da fase de migration;
- deriva o estado instalado confirmado somente após `VERIFY`;
- retorna operações terminais idempotentemente;
- bloqueia operação concorrente ou journal divergente.

Todas as ações operacionais são fornecidas por um adapter injetado nos testes.
A S19 não possui adapter real e não executa Docker, Compose, PostgreSQL,
backup, migration, HTTP, SSH, GitHub, GHCR ou produção.

## 2. Razão do recorte

A S18 decidiu **o que** implantar. A S19 decide **como governar a transação**,
mas ainda não implementa comandos de infraestrutura.

Separar o núcleo dos adapters reais permite provar agora:

- persistência antes de side effects;
- retomada após interrupção;
- idempotência por evidência;
- exclusão mútua;
- compensação;
- confirmação pós-verificação.

A S20 futura implementará os adapters reais e o entrypoint operacional,
consumindo este núcleo sem reescrever suas decisões.

## 3. Fronteira autorizada

### 3.1 Criar

```text
ops/deploy/schemas/deployment-journal.schema.json
ops/deploy/examples/deployment-journal.example.json
tools/deploy/deployment_executor.py
tools/deploy/validate_deployment_executor.py
tools/deploy/tests/test_deployment_executor.py
tools/deploy/tests/test_deployment_executor_contract.py
docs/infrastructure/deployment/release-control/TRANSACAO_IMPLANTACAO.md
docs/infrastructure/deployment/implementation/slices/S19-nucleo-transacional-journal-implantacao.report.md
```

### 3.2 Alterar somente

```text
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md
```

### 3.3 Ler/importar sem alterar

```text
tools/deploy/deployment_plan.py
ops/deploy/schemas/installed-state.schema.json
ops/deploy/schemas/deployment-plan.schema.json
docs/infrastructure/deployment/release-control/api/deployer.openapi.yml
docs/infrastructure/deployment/release-control/contracts/state-machines.yml
```

## 4. Fora de escopo

Não:

- alterar planner, schemas ou exemplos da S18;
- alterar OpenAPI, máquina de estados ou matriz de segurança;
- alterar `release_control`;
- criar workflow, shell script, Dockerfile, Compose ou systemd;
- implementar adapter real ou comando externo;
- usar `subprocess`, `os.system`, `exec`, `spawn`, Docker SDK ou biblioteca de
  banco/rede;
- acessar GitHub, GHCR, VPS, DNS ou produção;
- criar diretórios sob `/opt`;
- criar ou alterar symlinks `current` e `previous`;
- implementar retenção/transferência de backup;
- afirmar que compensação de imagens restaura banco;
- instalar dependência Python;
- criar S20;
- executar `git add`, commit, tag ou push.

## 5. Superfície Python obrigatória

`tools/deploy/deployment_executor.py` deve exportar:

```python
class DeploymentExecutionError(Exception):
    code: str
    exit_code: int

@dataclass(frozen=True)
class ProbeResult:
    status: Literal["ABSENT", "SUCCEEDED", "FAILED", "UNKNOWN"]
    observed_at: str
    evidence_id: str | None

@dataclass(frozen=True)
class ActionContext:
    operation_id: str
    action: Literal["PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY", "ROLLBACK"]
    bundle: Path
    source_release: str | None
    target_release: str
    services: tuple[str, ...]
    databases: tuple[str, ...]
    database_restore_required: bool

class DeploymentAdapter(Protocol):
    def probe(self, context: ActionContext) -> ProbeResult: ...
    def execute(self, context: ActionContext) -> None: ...

class DeploymentClock(Protocol):
    def now(self) -> str: ...

def execute_deployment(
    *,
    bundle: Path,
    operation_id: str,
    journal_dir: Path,
    installed_state_path: Path,
    adapter: DeploymentAdapter,
    clock: DeploymentClock,
) -> dict[str, Any]: ...
```

Pode haver helpers privados e dataclasses adicionais, mas não outra API
operacional pública. Não crie CLI de deploy nesta slice.

O módulo pode importar e chamar:

```python
tools/deploy/deployment_plan.py::validate_bundle
```

por import seguro baseado no caminho do repositório. Não copie nem relaxe a
validação S18.

## 6. Entradas e paths

### 6.1 Bundle

Antes de lock ou journal, `bundle` deve passar integralmente por
`validate_bundle`. A identidade da operação é:

```text
sha256: + SHA-256 dos bytes exatos de bundle/bundle.sha256
```

O bundle nunca é modificado.

### 6.2 Operation ID

`operation_id` deve satisfazer:

```text
^[A-Za-z0-9_-]{20,128}$
```

Ele não é derivado de release, path ou chave idempotente e nunca aparece em
comando externo nesta slice.

### 6.3 Diretórios

`journal_dir` e o pai de `installed_state_path`:

- devem existir;
- devem ser diretórios reais, não symlinks;
- devem ter modo exatamente `0700`;
- devem estar abaixo do workspace resolvido ou abaixo de `/tmp`;
- não podem ser `/`, `/tmp`, o workspace ou um diretório home;
- não podem conter symlink em nenhum componente existente do path.

`installed_state_path`:

- deve ser filho direto do pai validado;
- deve terminar em `installed-state.json`;
- se existir, deve ser arquivo regular não symlink, modo `0600`, até `2 MiB`;
- se ausente, só é compatível com `firstInstallation=true`.

O journal da operação é sempre:

```text
<journal_dir>/<operationId>.json
```

e o lock:

```text
<journal_dir>/.production.lock
```

Nenhum nome vem do manifesto.

## 7. Lock global

Use exclusivamente biblioteca padrão:

```python
fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
```

Regras:

- criar/abrir `.production.lock` com modo `0600`, `O_NOFOLLOW` e sem
  truncamento;
- confirmar por `fstat` que o descriptor é arquivo regular e tem modo `0600`;
- manter o descriptor aberto durante toda a transação;
- lock indisponível retorna `PRODUCTION_OPERATION_ACTIVE`, exit `4`;
- liberar no `finally`;
- nunca apagar o arquivo de lock;
- lock de processo é complementado pela auditoria dos journals não terminais.

Antes de iniciar ou retomar:

- carregar e validar todos os `*.json` em `journal_dir`;
- journal malformado, symlink ou com nome divergente falha
  `JOURNAL_CORRUPT`;
- outro journal não terminal falha `PRODUCTION_OPERATION_ACTIVE`;
- somente o mesmo `operationId` pode retomar sua operação não terminal.

## 8. Schema definitivo do journal

`deployment-journal.schema.json` usa Draft 2020-12, fecha propriedades
adicionais em todos os níveis e exige:

```json
{
  "schemaVersion": 1,
  "kind": "deployment-journal",
  "operationId": "deployment_0123456789abcdef",
  "operationType": "deployment",
  "bundleIdentity": "sha256:<64 hex>",
  "sourceRelease": null,
  "sourceStateSha256": null,
  "targetRelease": "v0.0.1",
  "state": "QUEUED",
  "createdAt": "2026-07-29T16:01:00Z",
  "updatedAt": "2026-07-29T16:01:00Z",
  "finishedAt": null,
  "sequence": 1,
  "steps": [
    {
      "name": "PULL",
      "status": "PENDING",
      "attempts": 0,
      "startedAt": null,
      "finishedAt": null,
      "evidence": null,
      "errorCode": null
    }
  ],
  "transitions": [
    {
      "sequence": 1,
      "from": null,
      "to": "QUEUED",
      "at": "2026-07-29T16:01:00Z"
    }
  ],
  "databaseRestoreRequired": false,
  "errorCode": null,
  "rollbackErrorCode": null,
  "confirmedStateSha256": null
}
```

O exemplo acima abrevia `steps`; schema e arquivos reais exigem exatamente,
nesta ordem:

```text
PULL
BACKUP
MIGRATE
UPDATE
VERIFY
COMMIT_STATE
ROLLBACK
```

### 8.1 Enums

Journal state:

```text
QUEUED
PULLING
BACKING_UP
MIGRATING
UPDATING
VERIFYING
SUCCEEDED
ROLLING_BACK
ROLLED_BACK
FAILED
```

Step status:

```text
PENDING
RUNNING
SKIPPED
SUCCEEDED
FAILED
```

### 8.2 Evidence

Evidence não aceita objeto livre. Quando presente:

```json
{
  "status": "SUCCEEDED",
  "evidenceId": "evidence_0123456789abcdef",
  "observedAt": "2026-07-29T16:02:00Z"
}
```

`evidenceId` satisfaz `^[A-Za-z0-9_.:-]{8,128}$`. Não contém URL, path,
mensagem, stdout, segredo ou payload.

### 8.3 Invariantes adicionais em código

O validador semântico exige:

- `sequence == len(transitions)`;
- sequências contíguas a partir de `1`;
- primeiro transition `null -> QUEUED`;
- demais transições pertencentes exatamente à máquina da seção 9;
- `updatedAt` igual ao `at` da última transição ou a um timestamp de mutação
  de step posterior, nunca anterior;
- timestamps não decrescentes;
- terminal exige `finishedAt`, não terminal exige `finishedAt=null`;
- `SUCCEEDED` exige `COMMIT_STATE=SUCCEEDED`, `errorCode=null`,
  `rollbackErrorCode=null`, `confirmedStateSha256` preenchido;
- `ROLLED_BACK` exige `ROLLBACK=SUCCEEDED` e `errorCode` preenchido;
- `FAILED` exige `errorCode` ou `rollbackErrorCode`;
- `databaseRestoreRequired=true` nunca volta a `false`;
- `sourceStateSha256=null` somente em primeira implantação; atualização exige
  o SHA-256 dos bytes exatos do estado confirmado de origem;
- evidence existe somente em step `SUCCEEDED`;
- `SKIPPED` não tem evidence;
- step `SUCCEEDED`, `SKIPPED` ou `FAILED` tem `finishedAt`;
- step `RUNNING` tem `startedAt`, `finishedAt=null`;
- step `PENDING` tem timestamps nulos e `attempts=0`.

## 9. Máquina de estados

Use exatamente:

```text
null -> QUEUED
QUEUED -> PULLING
PULLING -> BACKING_UP
BACKING_UP -> MIGRATING
MIGRATING -> UPDATING
UPDATING -> VERIFYING
VERIFYING -> SUCCEEDED

QUEUED -> FAILED
PULLING -> FAILED
BACKING_UP -> FAILED
MIGRATING -> ROLLING_BACK
UPDATING -> ROLLING_BACK
VERIFYING -> ROLLING_BACK
ROLLING_BACK -> ROLLED_BACK
ROLLING_BACK -> FAILED
```

Não acrescente estado de conveniência. `COMMIT_STATE` ocorre dentro de
`VERIFYING`.

`AVAILABLE` pertence à elegibilidade de uma release antes da criação da
operação e não é estado de `DeploymentOperation`. A criação do journal
materializa essa fronteira como `null -> QUEUED`; depois disso, todas as
transições são exatamente as transições S06 a partir de `QUEUED`.

Estados terminais nunca transitam.

## 10. Clock determinístico

O núcleo não lê relógio do sistema. Todo timestamp vem de `clock.now()`.

Cada valor:

- UTC RFC 3339 estrito;
- precisão de segundos;
- sufixo `Z`;
- sem fração;
- maior ou igual ao timestamp anteriormente persistido;
- no estado confirmado, `installedAt >= bundle.plannedAt`.

Clock inválido ou regressivo falha `INVALID_CLOCK` antes de persistir o novo
evento.

## 11. Persistência atômica

Journal e estado confirmado usam o mesmo primitivo:

1. serializar JSON canônico com LF final;
2. criar temp irmão exclusivo em `0600`;
3. escrever, flush e `fsync` do arquivo;
4. reler e validar bytes/schema/invariantes;
5. `os.replace`;
6. `fsync` do diretório pai.

Regras:

- journal final sempre `0600`;
- nunca editar arquivo in-place;
- temp tem prefixo `.<nome>.tmp-`;
- falha anterior ao replace remove somente o temp;
- falha no fsync do pai após replace preserva o arquivo integral para
  reconciliação;
- nunca apagar journal;
- erro de persistência retorna `JOURNAL_IO_FAILED`, exit `5`;
- nenhum conteúdo de journal, bundle ou exception aparece no erro público.

## 12. Validação do estado de origem

Antes de criar journal:

### 12.1 Primeira implantação

Se `plan.firstInstallation=true`:

- `plan.sourceRelease=null`;
- `installed_state_path` deve estar ausente.

Arquivo existente falha `CURRENT_STATE_CONFLICT`.

### 12.2 Atualização

Se `plan.firstInstallation=false`, o estado instalado deve:

- existir e passar pelo schema S18;
- estar `reconciled=true`;
- ter `release == plan.sourceRelease`;
- ter seis componentes na ordem do plano;
- apresentar, para cada componente, digest igual a
  `plan.components[].currentDigest`;
- ter dois bancos na ordem do plano;
- apresentar `migrationSetSha256` igual a
  `plan.databases[].currentMigrationSetSha256`.

Divergência falha `CURRENT_STATE_CONFLICT` antes do primeiro side effect.

Calcule o SHA-256 dos bytes do estado de origem e persista no journal inicial:

```text
sourceStateSha256 = sha256:<64 hex>
```

Na primeira implantação, persista `sourceStateSha256=null`.

Antes de `COMMIT_STATE` e em toda retomada, releia o estado:

- hash igual a `sourceStateSha256`: origem ainda instalada;
- hash igual a `confirmedStateSha256`: alvo já confirmado;
- qualquer outro hash: `CURRENT_STATE_CONFLICT`.

Enquanto `confirmedStateSha256` ainda for nulo, um estado candidato ao alvo
somente pode ser aceito pela reconciliação integral da seção 15.1.

## 13. Adapter e probes

### 13.1 Contexto por ação

| Ação | `services` | `databases` |
|---|---|---|
| `PULL` | `servicesToPull` | vazio |
| `BACKUP` | vazio | bancos com `changed=true` |
| `MIGRATE` | vazio | bancos com `changed=true` |
| `UPDATE` | `servicesToUpdate` | vazio |
| `VERIFY` | seis serviços canônicos | `erp`, `website` |
| `ROLLBACK` | seis serviços canônicos | bancos com `changed=true` |

`database_restore_required` reflete o journal no instante da chamada.

### 13.2 Validação de ProbeResult

- `observed_at` segue o formato temporal da seção 10;
- `SUCCEEDED` exige `evidence_id`;
- `ABSENT`, `FAILED` e `UNKNOWN` exigem `evidence_id=None`;
- status desconhecido falha `INVALID_ADAPTER_RESULT`;
- timestamp do probe não pode ser futuro em relação ao próximo
  `clock.now()` usado para persistir o resultado.

O core não persiste mensagem ou exception do adapter.

Falha de ação que consegue ser persistida até estado terminal faz
`execute_deployment` retornar o journal terminal `FAILED` ou `ROLLED_BACK`;
não lança a exception original do adapter.

### 13.3 Algoritmo de ação

Para ação não no-op:

1. persistir state e step `RUNNING`;
2. chamar `probe(context)`;
3. se `SUCCEEDED`, persistir step `SUCCEEDED` e evidence sem chamar execute;
4. se `ABSENT`, chamar `execute(context)` uma vez;
5. exigir que o retorno de `execute` seja `None`;
6. chamar novo `probe(context)`;
7. aceitar somente `SUCCEEDED`;
8. `FAILED`, `UNKNOWN`, resultado inválido ou exception falham fechados.

Step já persistido como `SUCCEEDED` ou `SKIPPED` não chama adapter novamente.
Step `RUNNING` após restart retoma a partir do probe e nunca executa antes de
reconciliar.

### 13.4 No-op

- `PULL`: skip quando `servicesToPull=[]`;
- `BACKUP`: skip quando `backupRequired=false`;
- `MIGRATE`: skip quando `migrationRequired=false`;
- `UPDATE`: skip quando `servicesToUpdate=[]`;
- `VERIFY`: nunca skip;
- `COMMIT_STATE`: nunca skip;
- `ROLLBACK`: só existe em compensação.

No-op persiste `SKIPPED`, sem probe/execute/evidence.

## 14. Fluxo principal

Após validações e journal `QUEUED`:

1. `PULLING` executa `PULL`;
2. `BACKING_UP` executa ou pula `BACKUP`;
3. `MIGRATING` executa ou pula `MIGRATE`;
4. `UPDATING` executa ou pula `UPDATE`;
5. `VERIFYING` executa `VERIFY`;
6. ainda em `VERIFYING`, executa `COMMIT_STATE`;
7. transita para `SUCCEEDED`.

Ao iniciar um step `MIGRATE` não no-op, persista
`databaseRestoreRequired=true` **antes do primeiro probe**. Assim, exception,
`FAILED` ou `UNKNOWN` do probe também permanecem conservadores quanto ao
estado do banco.

`databaseRestoreRequired` significa que restauração do banco pode ser
necessária; não significa que rollback de imagem a realizou.

## 15. Confirmação do estado instalado

Depois de `VERIFY=SUCCEEDED`:

1. releia `installed-state.next.json` do bundle;
2. confirme que continua byte/coerentemente vinculado ao plano;
3. obtenha timestamp de `clock.now()`;
4. derive novo objeto sem modificar o bundle:
   - `reconciled=true`;
   - `installedAt=<timestamp real fornecido>`;
   - demais campos idênticos à intenção;
5. valide schema e invariantes S18;
6. valide novamente o hash/ausência do estado de origem;
7. persista atomicamente em `installed_state_path`;
8. grave no journal:
   `confirmedStateSha256 = sha256:<hash dos bytes canônicos com LF>`;
9. marque `COMMIT_STATE=SUCCEEDED`;
10. somente então transite `VERIFYING -> SUCCEEDED`.

### 15.1 Janela de crash

Se o processo reiniciar com journal em `VERIFYING`:

- se `installed_state_path` corresponde exatamente ao estado confirmado alvo,
  reconcile `COMMIT_STATE=SUCCEEDED` sem reescrever;
- se ainda corresponde ao estado de origem, continue a confirmação;
- qualquer terceiro conteúdo é `CURRENT_STATE_CONFLICT`.

Se o fsync do pai falhar após o replace, releia:

- estado alvo integral: trate como confirmação reconciliável;
- estado origem integral: permita nova tentativa;
- outro estado: falhe fechado.

## 16. Falhas e compensação

Mapeamento primário:

```text
PULL_FAILED
BACKUP_FAILED
MIGRATION_FAILED
UPDATE_FAILED
VERIFY_FAILED
COMMIT_STATE_FAILED
INVALID_ADAPTER_RESULT
CURRENT_STATE_CONFLICT
```

### 16.1 Sem compensação

Falha em `QUEUED`, `PULLING` ou `BACKING_UP`:

- marcar step aplicável `FAILED`;
- transitar diretamente para `FAILED`;
- não chamar `ROLLBACK`.

### 16.2 Com compensação

Falha/resultado incerto em `MIGRATING`, `UPDATING` ou `VERIFYING`, salvo estado
alvo já confirmado conforme seção 15.1:

1. preservar `errorCode` primário;
2. transitar para `ROLLING_BACK`;
3. executar `ROLLBACK` com o mesmo algoritmo probe/execute/probe;
4. sucesso comprovado: `ROLLED_BACK`;
5. falha ou incerteza: `FAILED` com
   `rollbackErrorCode=ROLLBACK_FAILED`.

Em primeira implantação, `ROLLBACK` significa compensar/parar a stack parcial;
não inventa release anterior.

O adapter de rollback futuro não restaura banco automaticamente.
`databaseRestoreRequired=true` permanece verdadeiro em `ROLLED_BACK` e deve
ser exposto à operação futura.

### 16.3 Falha de persistência

Se não for possível persistir a decisão antes do side effect, não execute o
side effect.

Se persistência falhar depois de side effect possível, interrompa
imediatamente com `JOURNAL_IO_FAILED`. Não tente ação seguinte. Uma nova
invocação do mesmo `operationId` deve reconciliar pelo probe.

### 16.4 Exceptions públicas do núcleo

`DeploymentExecutionError` é reservada para falha que impede produzir/retomar
um resultado transacional confiável:

```text
exit 3: INVALID_CONTRACT, JOURNAL_CORRUPT, INVALID_CLOCK,
        CURRENT_STATE_CONFLICT
exit 4: PRODUCTION_OPERATION_ACTIVE, OPERATION_CONFLICT, UNSAFE_PATH
exit 5: JOURNAL_IO_FAILED
```

Quando `CURRENT_STATE_CONFLICT` ocorre depois da criação do journal e é
possível persistir compensação/terminal, ele fica em `errorCode` e a função
retorna o journal; antes da criação, é exception pública.

`str(error)` deve ser exatamente `error.code`, em uma linha. Causa interna,
path, payload, retorno do adapter e traceback não entram na mensagem.

## 17. Idempotência e retomada

### 17.1 Mesmo operationId

- `bundleIdentity` diferente: `OPERATION_CONFLICT`;
- source/target divergente: `OPERATION_CONFLICT`;
- journal não terminal compatível: retomar;
- `SUCCEEDED`: validar estado alvo e retornar journal sem adapter;
- `ROLLED_BACK` ou `FAILED`: retornar journal sem adapter;
- nunca reiniciar operação terminal.

### 17.2 Outro operationId

Outro journal não terminal impede início com
`PRODUCTION_OPERATION_ACTIVE`.

Journals terminais históricos não bloqueiam nova operação.

### 17.3 Não regressão

- step completo nunca volta a `RUNNING`;
- state terminal nunca muda;
- sequence nunca diminui;
- timestamps nunca diminuem;
- evidence nunca é substituída;
- erro primário nunca é apagado durante rollback.

## 18. Exemplo versionado

`deployment-journal.example.json` deve representar primeira implantação
concluída em `SUCCEEDED`, com:

- operation ID fictício válido;
- bundleIdentity fictícia;
- `sourceStateSha256=null`, por ser primeira implantação;
- sete steps na ordem fixa;
- PULL/BACKUP/MIGRATE/UPDATE/VERIFY/COMMIT_STATE em `SUCCEEDED`;
- ROLLBACK em `PENDING`;
- evidence fictícia para as cinco ações de adapter;
- evidence de `COMMIT_STATE` com ID derivado do hash confirmado;
- transitions completos do fluxo principal;
- `databaseRestoreRequired=true`;
- `confirmedStateSha256` preenchido;
- nenhum segredo, URL, path ou mensagem.

Para `COMMIT_STATE`, a evidence usa:

```text
evidenceId = state:<64 hex de confirmedStateSha256 sem prefixo sha256:>
```

## 19. Validador versionado

`validate_deployment_executor.py` deve validar fail-closed:

- arquivos obrigatórios;
- JSON Schema Draft 2020-12 e objetos fechados;
- exemplo pelo schema e invariantes semânticas;
- estados/transições operacionais idênticos a `state-machines.yml` a partir de
  `QUEUED`, com a única adaptação documentada `null -> QUEUED` no lugar da
  elegibilidade externa `AVAILABLE -> QUEUED`;
- ordem dos steps;
- imports e superfície Python;
- ausência de subprocess/comandos/adapters reais;
- uso obrigatório de `fcntl.flock`, `LOCK_EX`, `LOCK_NB` e `O_NOFOLLOW`;
- vínculo com `deployment_plan.validate_bundle`;
- atomicidade, modos e fsync presentes;
- documentação.

Não considere busca textual isolada prova suficiente: mutantes devem chamar o
validador e as funções reais quando aplicável.

## 20. Testes causais obrigatórios

Use adapter e clock fakes. Cubra no mínimo:

1. fluxo completo produz ordem exata e `SUCCEEDED`;
2. primeira implantação confirma estado somente após verify;
3. atualização exige estado origem coerente;
4. divergência de cada digest atual falha antes do adapter;
5. divergência de migration set falha antes do adapter;
6. bundle inválido falha antes de lock/journal/adapter;
7. operation ID inválido falha;
8. paths/modes/symlinks inseguros falham;
9. segundo lock simultâneo falha;
10. outro journal não terminal bloqueia;
11. journal corrompido bloqueia;
12. mesmo operation ID e bundle retoma;
13. mesmo operation ID e bundle diferente conflita;
14. terminal `SUCCEEDED` é idempotente e não chama adapter;
15. terminais `FAILED`/`ROLLED_BACK` não reiniciam;
16. probe `SUCCEEDED` evita execute;
17. probe `ABSENT` executa uma vez e exige segundo probe;
18. probe `FAILED` falha;
19. probe `UNKNOWN` falha;
20. ProbeResult inválido falha sanitizado;
21. retorno não `None` de execute falha;
22. PULL/BACKUP falhos não chamam rollback;
23. MIGRATE/UPDATE/VERIFY falhos chamam rollback;
24. rollback comprovado termina `ROLLED_BACK`;
25. rollback incerto/falho termina `FAILED`;
26. `databaseRestoreRequired` torna-se monotonicamente verdadeiro;
27. flags no-op pulam exatamente as ações aplicáveis;
28. VERIFY nunca é pulado;
29. journal é persistido antes de cada execute;
30. step RUNNING retomado chama probe antes de execute;
31. steps completos não chamam adapter novamente;
32. clock inválido/regressivo falha;
33. estado confirmado usa timestamp do clock, não do sistema;
34. crash simulado antes/depois do replace do estado é reconciliado;
35. estado concorrente alterado antes do commit falha;
36. falha em cada write/fsync/verify/replace não deixa temp ou arquivo parcial;
37. fsync do pai após replace deixa artefato integral reconciliável;
38. journal/schema/evidence não aceitam campos extras;
39. erro público é uma linha sem traceback/payload/segredo;
40. nenhum comando externo, rede ou relógio do sistema é usado;
41. mutantes removendo gates essenciais são detectados;
42. zero cache Python e resíduos temporários ao final.

Os testes devem provar causas e ordem de chamadas, não somente procurar
strings.

## 21. Documentação

`TRANSACAO_IMPLANTACAO.md` deve explicar:

- relação entre bundle, journal, adapter e estado instalado;
- por que S19 ainda não implanta;
- lock global e conflito de operação;
- probes antes/depois e retomada;
- estados e steps;
- no-ops;
- confirmação após verify;
- diferença entre rollback de imagem e restore de banco;
- significado de `databaseRestoreRequired`;
- falhas terminais e retomáveis;
- fronteira da S20 futura.

Atualize os dois documentos autorizados apenas para apontar a transação S19 e
posicioná-la depois do plano S18.

## 22. Validação mínima e relatório

Execute:

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

Não execute Maven, npm, Docker, banco, actionlint ou testes de
`release_control`.

O relatório deve conter:

- `IN_PROGRESS — aguardando revisão do orquestrador`;
- CWD;
- arquivos criados/alterados;
- cada comando exato, exit code, resultado, interpretação e resíduos;
- contagem separada dos testes S18/S19 e regressão release;
- matriz causal da seção 20;
- ordem observada de actions/probes;
- evidência sanitizada do journal e estado confirmado;
- provas de lock, retomada, compensação e atomicidade;
- falhas intermediárias e correções;
- confirmação de zero comando externo/rede/produção;
- `git diff --check`, status, índice, HEAD, tags, reflog e workflows;
- confirmação de task/tracker preservados e S20 ausente;
- divergências e itens não determinados.

## 23. Critérios de aceite

A S19 só pode ser aceita se:

1. journal fechado e semanticamente validado;
2. máquina idêntica ao contrato S06;
3. lock global real e fail-closed;
4. side effect sempre precedido por persistência;
5. probe governa execução e retomada;
6. idempotência por operationId e bundle;
7. compensação segue exatamente a fase da falha;
8. banco nunca é declarado restaurado pelo rollback;
9. estado confirmado só existe depois de verify;
10. janela de crash é reconciliável;
11. nenhum adapter/comando operacional real existe;
12. implementação, testes, schemas e documentação coincidem;
13. regressões S18 e releases permanecem verdes;
14. estado Git protegido e nenhum resíduo.

## 24. Resposta final esperada do executor

Informe:

- caminho absoluto do relatório;
- arquivos alterados;
- resultados e contagens;
- estados/ordem comprovados;
- comportamento de probe, retomada e idempotência;
- comportamento de compensação e `databaseRestoreRequired`;
- prova de confirmação somente pós-verify;
- prova de lock e atomicidade;
- divergências e itens não determinados;
- confirmação de zero Docker/banco/rede/GitHub/GHCR/VPS/produção;
- confirmação de que não criou S20;
- estado `IN_PROGRESS — aguardando revisão do orquestrador`.
