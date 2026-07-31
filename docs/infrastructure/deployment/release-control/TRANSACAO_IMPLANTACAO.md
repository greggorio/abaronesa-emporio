# Transação local de implantação

> **Estado:** núcleo transacional offline e sem adapter operacional. A S19 não
> implanta, não executa Docker, banco, rede ou produção.

## Responsabilidades

O bundle imutável da S18 contém o manifesto, o Compose, o ambiente de imagens,
o plano e a intenção do próximo estado. O núcleo S19 primeiro revalida esse
bundle e não reinterpreta o BOM.

O journal é o registro durável de uma operação. Ele vincula `operationId`,
identidade exata do bundle, release de origem e alvo, steps, transições,
evidências sanitizadas e eventual hash do estado confirmado. Ele nunca é
apagado nem editado in-place.

O adapter é uma fronteira injetada. O core somente fornece `ActionContext` e
chama `probe`/`execute`; nenhum adapter real existe na S19. A implementação
operacional, reservada à S20, deverá traduzir essas chamadas para mecanismos
de infraestrutura sem alterar a máquina transacional.

O estado instalado confirmado descreve o resultado reconciliado. Ele só é
derivado de `installed-state.next.json` depois que `VERIFY` possui evidência
de sucesso. O bundle permanece inalterado.

## Lock global e identidade

Toda transação mantém um lock global de produção por `flock`, exclusivo e
não bloqueante, durante a operação inteira. Um segundo lock ou outro journal
não terminal produz `PRODUCTION_OPERATION_ACTIVE`. O mesmo `operationId`
somente pode retomar quando bundle, origem e alvo são idênticos; divergência
produz `OPERATION_CONFLICT`.

O arquivo `.production.lock` permanece no diretório com modo `0600`. O lock
do processo é complementado pela auditoria fail-closed de todos os journals.

## Estados e steps

A fronteira local começa em `null -> QUEUED`. O fluxo nominal é:

```text
QUEUED -> PULLING -> BACKING_UP -> MIGRATING
       -> UPDATING -> VERIFYING -> SUCCEEDED
```

Falhas anteriores à migration podem terminar em `FAILED`. A partir de
`MIGRATING`, falhas seguem por `ROLLING_BACK` e terminam em `ROLLED_BACK` ou
`FAILED`.

Um journal `FAILED` pode não possuir step `FAILED` quando o erro é detectado
depois da criação do journal ou de uma transição de estado, mas antes do início
do próximo step. Nessa janela, a transição terminal e o `errorCode` sanitizado
registram integralmente a decisão; não se cria um step fictício apenas para
representar a falha entre fases.

Os steps possuem ordem fixa:

```text
PULL
BACKUP
MIGRATE
UPDATE
VERIFY
COMMIT_STATE
ROLLBACK
```

`COMMIT_STATE` acontece dentro de `VERIFYING`; não cria estado intermediário.
Estados terminais nunca transitam nem reiniciam adapters.

Os timestamps também fecham a causalidade da transação: cada step permanece
dentro da janela entre a entrada e a saída do estado que o governa; em journal
ativo, `updatedAt` é o limite superior. Steps finalizados respeitam
`startedAt <= finishedAt`, evidências respeitam
`startedAt <= observedAt <= finishedAt`, e `COMMIT_STATE` só começa depois do
término de `VERIFY`. Em estado terminal, `finishedAt`, `updatedAt` e o
timestamp da última transição são idênticos. Igualdade no mesmo segundo é
válida.

## Probes, side effects e retomada

Antes de uma ação, o core persiste o step `RUNNING`. Em seguida:

1. executa `probe`;
2. evidência `SUCCEEDED` conclui o step sem novo side effect;
3. `ABSENT` permite uma única chamada `execute`;
4. um segundo probe deve comprovar `SUCCEEDED`.

`FAILED`, `UNKNOWN`, resultado inválido ou exception falham fechados. Um step
`RUNNING` encontrado após reinício sempre começa pelo probe. Steps
`SUCCEEDED` ou `SKIPPED` não voltam a executar.

Os no-ops são determinados pelo plano: `PULL`, `BACKUP`, `MIGRATE` e `UPDATE`
podem ficar `SKIPPED`. `VERIFY` e `COMMIT_STATE` nunca são pulados.

## Confirmação e janela de crash

Depois de `VERIFY`, o core deriva um estado com `reconciled=true` e
`installedAt` fornecido pelo clock injetado. Antes de gravá-lo, relê e confirma
o estado de origem. Journal e estado usam escrita canônica, arquivo temporário
irmão, `fsync`, releitura, validação, `os.replace` e `fsync` do pai.

Se houver reinício após o replace, o hash do estado permite reconhecer o alvo
integral e concluir `COMMIT_STATE` sem reescrever. Origem integral permite
continuar; terceiro conteúdo produz `CURRENT_STATE_CONFLICT`.

## Rollback e banco

Rollback compensa a stack, mas não afirma restaurar banco. Antes do primeiro
probe de uma migration real, `databaseRestoreRequired` torna-se verdadeiro e
nunca volta a falso. Esse indicador significa que uma restauração pode ser
necessária mesmo se imagens forem revertidas com sucesso.

O erro primário é preservado durante rollback. Evidência de rollback produz
`ROLLED_BACK`; falha ou incerteza produz `FAILED` com
`rollbackErrorCode=ROLLBACK_FAILED`.

## Falhas terminais e retomáveis

Journals `SUCCEEDED`, `ROLLED_BACK` e `FAILED` são retornados
idempotentemente e não reiniciam ações. Operações não terminais compatíveis
podem ser retomadas por probe.

Falhas de contrato, path, clock, origem ou journal são códigos públicos
sanitizados. Falha de persistência usa `JOURNAL_IO_FAILED` e interrompe antes
do próximo side effect. Mensagens públicas não incluem path, payload, segredo,
retorno de adapter ou traceback.

## Adapter operacional S20

A S19 comprova governança, persistência, exclusão mútua, reconciliação e
compensação com adapters injetados, mas isoladamente não implanta. A
[operação local S20](./OPERACAO_LOCAL_IMPLANTACAO.md) fornece o primeiro
adapter e CLI reais consumindo este núcleo sem copiar ou reescrever sua
máquina. Ela permanece local: workflow, transporte do bundle, bootstrap da
VPS, runtime deployer e produção continuam passos futuros.

Validação local do contrato:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 tools/deploy/validate_deployment_executor.py
```
