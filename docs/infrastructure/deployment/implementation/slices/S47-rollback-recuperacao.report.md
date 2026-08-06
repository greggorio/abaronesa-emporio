# S47 — alvo de rollback, upgrade e recuperação exercitada

> Data: 06/08/2026
> Escopo: `/tmp/S47-escopo-delegacao.md` (`AUTHORIZED`)
> Resultado: `IN_PROGRESS — alvo de rollback estabelecido e recuperação exercitada; aguardando aceite e encerramento documental`

## 1. Resultado executivo

A S47 terminou com o ciclo comercial completo:

```text
candidato                 31074291015 / success
release                   v0.1.3 / publicada
deploy v0.1.3             SUCCEEDED / CONFIRMED
rollback para v0.1.2      SUCCEEDED / CONFIRMED
database restore          false / NOT_REQUIRED
current                   releases/v0.1.2
previous                  releases/v0.1.3
instalação corrente       v0.1.2 / reconciled=true
serviços                  sete healthy
HTTPS                     quatro respostas 200
operações não terminais   0
slot ativo                0
```

A reconstrução das seis imagens do candidato foi registrada como divergência
aceitável do primeiro texto do escopo. Como `7c710ac` só altera
`tools/deploy/`, as imagens são funcionalmente equivalentes às de v0.1.2, mas
possuem digests diferentes. O upgrade real substituiu os seis serviços de
aplicação; o rollback os substituiu novamente pelos digests de v0.1.2. O
container PostgreSQL comercial permaneceu com ID curto `847aedde12d2`.

## 2. Estado herdado e candidato

A passagem inicial confirmou v0.1.2 corrente, `previous` ausente, sete serviços
healthy, quatro endpoints 200, control plane live/ready e instalação
reconciliada. O candidato aceito foi:

```text
commit                     7c710ac6fb93df9a1e51af5be2445341245abc2e
CI                         31073604887 / success
Publish Candidate          31074291015 / success
candidateId                candidate-7c710ac6fb93df9a1e51af5be2445341245abc2e-31074291015-1
classificação de paths     unknown
componentes reconstruídos  backend, website_back, frontend, website_front,
                           whatsapp_service, gateway
integração                 passed
cleanup                    zero recurso residual
```

O diff continha apenas cinco arquivos em `tools/deploy/`. A reconstrução mudou
os seis digests, sem introduzir alteração funcional nas imagens.

## 3. Allowlist e release v0.1.3

A primeira janela ampliou a variável para `312233471,35626201`, disparou o run
`31077188417` e restaurou imediatamente `312233471`. Como o job leu a variável
após a restauração, terminou com o código exato
`release-publication:invalid:ACTOR_NOT_AUTHORIZED`; nenhum artifact ou release
foi produzido.

Na janela substituta, a allowlist permaneceu ampliada até o gate de confiança
materializar a autorização e foi restaurada no mesmo ciclo:

```text
valor inicial              312233471
valor ampliado             312233471,35626201
operationId                relctl-s47-42eb1fe429424639ab4f55d6ae31b239
run                        31077702266 / success
valor terminal             312233471
release ID                 366014447
tag / target               v0.1.3 / 7c710ac6fb93df9a1e51af5be2445341245abc2e
publicação                 2026-08-06T06:34:55Z
```

Não houve rerun nem alteração de permissão ou visibilidade no site do GitHub.

## 4. Deploy comercial de v0.1.3

O JWKS permanente respondeu com o `kid` real; não foi criado bootstrap
temporário nem houve alteração de Nginx. JWT e idempotency key foram mantidos
em arquivos `0600`, nunca em argv. A operação terminal foi:

```text
operationId                dep_db5422e27cf544ca96009e3050912f80
workflow run               31081020773 / attempt 1 / success
state                      SUCCEEDED
transportStatus            CONFIRMED
errorCode                  null
databaseRestoreRequired    false
current                    releases/v0.1.3
previous                   releases/v0.1.2
```

Journal completo:

| Passo | Estado |
|---|---|
| PULL | SUCCEEDED |
| BACKUP | SKIPPED |
| MIGRATE | SKIPPED |
| UPDATE | SUCCEEDED |
| VERIFY | SUCCEEDED |
| COMMIT_STATE | SUCCEEDED |
| ROLLBACK | PENDING |

Os seis serviços foram substituídos sob `docker compose ... --wait`; PostgreSQL
não foi recriado. Os artifacts preservados foram `deployment-trust`,
`deployment-handoff`, `deployment-result` e `deployment-workflow-outcome`.

### 4.1 Tentativas anteriores sem mutação

Cinco operações chegaram a runs que falharam antes de journal ou alteração
comercial. Em cada caso foram confrontados links, ausência de journal,
containers, sete healthchecks e quatro endpoints 200 antes da adjudicação
manual como `FAILED/CONFIRMED`, com `rc_audit_event`:

| Operação | Run | Causa observada/diagnosticada |
|---|---:|---|
| `dep_6d922412…` | 31078188550 | falha pré-journal |
| `dep_264861…` | 31078855756 | falha pré-journal |
| `dep_b472…` | 31079704660 | falha pré-journal |
| `dep_e9b…` | 31080126902 | falha pré-journal |
| `dep_8e59…` | 31080422632 | `JOURNAL_CORRUPT` preservado |

Os reparos causais foram publicados normalmente nos commits `01348bf`,
`d2b83e4`, `f45f0d6`, `f2bf8e1` e `f2c9e0f`. Nenhum run histórico foi repetido.

## 5. Ativação do rollback real

O workflow existente era apenas um envelope de protocolo e não realizava SSH
ou mutação comercial. O primeiro run, `31081941699`, terminou GitHub `success`
com apenas o job `protocol`, sem artifact, journal ou mudança de containers.
A operação `rbk_ca79…` foi adjudicada como `FAILED/CONFIRMED` com base factual
e audit `s47-rollback-stub-31081941699`.

O runtime real foi implementado nos commits:

```text
3a309b9  transporte, workflow, helper remoto e compensação transacional
8753773  validação correta de installed-state.next do predecessor
832e96b  binding REST de name/display_title ao operationId
bd7fca7  aplicação canônica do único outcome terminal no journal
```

O helper valida `current`, `previous`, os dois bundles, linkage de digests,
estado instalado e lock global antes da troca. Se houver falha depois que os
serviços estiverem no alvo, recompõe serviços, installed-state e links para a
origem antes de retornar falha.

Os gates locais relevantes fecharam verdes: 178 testes do deploy, 144 testes
do control plane no primeiro ciclo, 169 testes após a correção do predecessor,
80 testes do reconciliador e 145 testes do API/reconciliador no fechamento.
Os validadores de rollback, deployer e control-root package também fecharam
verdes.

O control root foi reconstruído duas vezes por SHA e rotacionado
transacionalmente. O root terminal está íntegro em
`bd7fca7b1cc3efabac5faf1180983fd96a1954ea`, com capabilities canônicas.

## 6. Execuções do rollback e divergências registradas

O primeiro run do workflow real, `31090218610`, falhou antes de mutação porque
`installed-state.next.json` de v0.1.2 (`reconciled=false`) era validado como
estado corrente confirmado. Produção permaneceu em v0.1.3; a operação
`rbk_efcf…` foi adjudicada como falha factual e auditada.

O run terminal `31090791927` realizou o rollback corretamente. O artifact foi
publicado antes de o reconciliador aceitar a transição direta de `QUEUED` para
o único outcome terminal. A mutação comercial já estava confirmada, mas o
control plane registrou `STATE_TRANSITION_INVALID`. O runtime foi corrigido
para materializar a sequência canônica do artifact terminal. A operação foi
reaberta apenas para reconciliação, sem novo POST, novo workflow ou nova
mutação comercial; o reconciliador aplicou o artifact preservado e terminou a
operação.

## 7. Rollback terminal e artifacts

```text
operationId                rbk_d0405d4bce0d47369d4d7032776e17cb
workflow run               31090791927 / attempt 1 / success
controlSha do run          832e96b7a683ecb713d447f03a8f501408cff027
state / remoteState        SUCCEEDED / SUCCEEDED
dispatchState              CONFIRMED
transportStatus            CONFIRMED
errorCode                  null
databaseRestoreRequired    false
databaseRestore evidence   NOT_REQUIRED
outcome SHA-256            sha256:d33fe7864958fe2b38f1a5e6e174552d29fa7d8763341b508810a61e2a9b3f05
targetState SHA-256        sha256:01b1de6dc9ae8db6abd0940edde89b3cab2f419aded6925051e4be775d271095
```

Journal do control plane:

```text
QUEUED -> PRECHECKING -> SWITCHING -> VERIFYING -> SUCCEEDED
```

O artifact `rollback-workflow-outcome.json` foi preservado. Ele vincula
operationId, v0.1.3 como origem, v0.1.2 como alvo, run/attempt, control SHA,
`SUCCEEDED/CONFIRMED` e ausência de restore.

Estado terminal observado:

```text
current link               releases/v0.1.2
previous link              releases/v0.1.3
instalação corrente        v0.1.2 / reconciled=true
lastOperationId            rbk_d0405d4bce0d47369d4d7032776e17cb
uncertaintyCode            null
operações não terminais    0
slots ativos               0
PostgreSQL comercial       847aedde12d2 / preservado / healthy
seis serviços de app       substituídos / healthy
ERP / e /healthz           200 / 200
site / e /healthz          200 / 200
control live / ready       200 / 200
```

O campo histórico `previousRelease` da instalação corrente voltou a `v0.1.1`
por vir do manifesto de v0.1.2; o link operacional `previous` registra a
verdade da troca e aponta v0.1.3. Essa divergência de representação foi
registrada e não impediu o objetivo: a instalação corrente está reconciliada
em v0.1.2.

## 8. Control plane terminal e preservação

A correção final do reconciliador foi publicada pelo run `31091039908`. A
imagem terminal é:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:85c31560b28d01f992b6ca8f85aa3c5bbd3eea13dec04d3d923ccddf9a96b1f7
```

Somente `RELEASE_CONTROL_IMAGE` mudou em
`/etc/emporio/release-control.env`; o hash das demais linhas permaneceu
`129c015874deebbed84f88b4e841478250292db15e61136fd0566f286fd4e55b`.
O PostgreSQL do control plane manteve o ID
`2f5a1b924d66d46739a293fd7ae8602156f09336a45811c950c7c4cdc1fea8ac`.

O relatório permanece não rastreado e fora do stage. O secret scan exclusivo
foi executado depois da redação terminal.

IN_PROGRESS — alvo de rollback estabelecido e recuperação exercitada; aguardando aceite e encerramento documental
