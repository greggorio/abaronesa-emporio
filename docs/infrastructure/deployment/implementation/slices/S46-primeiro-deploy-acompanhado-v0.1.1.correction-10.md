# S46 — correction-10: init de banco no rehearsal e no caminho comercial

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED` — a entrega deste documento ao executor é a janela; iniciar de imediato pela §6
> **Natureza:** reparo causal em `BACKUP`; sem reinício da S46
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Checkpoint técnico:** `c9bf081` (correction-09, CI 31031954991 13/13)
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`

---

## 1. Checkpoint aceito

A correction-09 fechou D1-D4 e C0. O run `31033385683` provou:

```text
PULL                     SUCCEEDED     D1 confirmado; manifest do PostgreSQL resolvido
BACKUP                   FAILED        causeCode=BACKUP_FAILED, cliExit=21
demais passos            PENDING
cleanupStatus            SUCCESS       zero resíduos
evidência estruturada    funcionou     causa localizada por passo, sem novo run
```

Não repetir: reparo de D1-D4, evidência estruturada, tri-status, cleanup por baseline, workflow,
validador, CI, testes. Control root permanece em `7128293…` — a rotação continua pendente e
carregará, num único ato, as mudanças da correction-09 e desta.

**S46 continua não aceita.** Deploys 3/3 FAILED, rollback 0.

---

## 2. Causa fechada — E1 (determinística)

`deployment_engine_rehearsal.py:793-794` materializa o script de init do banco e força modo `0700`:

```python
shutil.copyfile(ROOT / "ops/db/init-databases.sh", support / "init-databases.sh")
(support / "init-databases.sh").chmod(0o700)
```

`ops/compose/compose.prod.yml:28` monta esse arquivo dentro do container:

```yaml
- ../db/init-databases.sh:/docker-entrypoint-initdb.d/10-init-databases.sh:ro
```

Aritmética de permissão, sem ambiguidade:

```text
arquivo no host        uid 1001 (runner), modo 0700
processo no container  uid 70   (postgres, alpine) — o entrypoint faz
                       exec gosu postgres antes de processar initdb.d
resultado              sem read e sem execute para uid 70
```

O entrypoint oficial trata `/docker-entrypoint-initdb.d/*.sh` assim: executa se o bit `x` estiver
disponível, senão faz `source`. Ambos exigem no mínimo leitura. Sob `set -Eeo pipefail`, a falha
aborta o entrypoint, o container nunca fica healthy, `compose up --wait postgresql` retorna
diferente de zero e `_execute_backup` levanta `BACKUP_POSTGRES_FAILED`, que
`_run_adapter_action` colapsa em `BACKUP_FAILED`.

Isso reproduz exatamente o receipt: `PULL=SUCCEEDED`, `BACKUP=FAILED`, demais `PENDING`, cleanup
limpo — um container que nunca subiu não deixa resíduo.

O modo no repositório é `100755` (`git ls-files -s`). O `chmod(0o700)` do rehearsal é o desvio.

**Ressalva declarada:** a aritmética de permissão é prova; o comportamento exato do entrypoint é
inferido da implementação conhecida de `docker_process_init_files`, não da imagem em execução. A
conclusão não depende dessa inferência — nenhum caminho lê um arquivo sem permissão de leitura.

---

## 3. Segundo defeito — E2 (causaria a falha #9)

Mesmo com E1 corrigido, o healthcheck do PostgreSQL passa **durante** a inicialização.

`compose.prod.yml:31`:

```yaml
test: ["CMD-SHELL", "pg_isready -U \"$${POSTGRES_USER}\" -d postgres"]
```

Na primeira subida de um volume novo, o entrypoint oficial inicia um servidor temporário com
`listen_addresses=''` — **somente socket unix** — roda os scripts de `initdb.d`, para esse servidor
e só então executa o servidor real. `pg_isready` sem `-h` usa o socket unix, logo **retorna 0
durante a fase de init**.

Consequências, todas na janela entre `start_period: 10s` e o fim do init:

1. o container é marcado healthy antes de `rehearsal_erp` e `rehearsal_website` existirem;
2. `compose up --wait` retorna e `_execute_backup` dispara `pg_dump` imediatamente;
3. `pg_dump` falha com banco inexistente, ou a conexão cai quando o servidor temporário é
   derrubado para o restart.

Aumentar `start_period` **não** resolve: um check bem-sucedido dentro do start period já marca
healthy. A correção correta é forçar o check pelo TCP, que o servidor temporário não expõe:

```yaml
test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -U \"$${POSTGRES_USER}\" -d postgres"]
```

Durante o init, o TCP recusa e `pg_isready` retorna 2 → não healthy. Depois do restart real,
`listen_addresses` padrão aceita `127.0.0.1` → healthy. Vale igualmente para o rehearsal e para a
primeira instalação comercial.

`tools/compose/validate_compose.py:16` pina apenas a substring `pg_isready` para `postgresql`,
então não há quebra de lockstep. `ops/compose/compose.prod.yml` pertence ao control root
(`control_root_package.py:61`), então a mudança entra na rotação já pendente — sem custo extra.

---

## 4. Bloqueador de produção — E3 (verificação read-only decide o ramo)

`control_root_package.SOURCE_FILES` (`control_root_package.py:58-72`) é uma allowlist fechada —
"anything else is refused" — e **não inclui `ops/db/init-databases.sh`**. Nenhum ponto de
`control_root_package.py`, `deployment_transport.py` ou `deployment_plan.py` materializa
`<deploy_root>/releases/db/init-databases.sh`. O rehearsal é o **único** lugar do repositório que
cria esse arquivo.

Se ele não existir na VPS, o bind mount de `compose.prod.yml:28` não encontra a origem: o Docker
cria um diretório vazio no lugar, o entrypoint tenta tratar um diretório como script e aborta —
ou o Compose recusa a subida. Em qualquer caso, **o primeiro deploy comercial falha em BACKUP
exatamente como o rehearsal falhou**.

### 4.1 Verificação obrigatória, somente leitura, antes de qualquer reparo

```text
ls -ln <deploy_root>/releases/db/init-databases.sh
```

Registrar existência, tipo, owner e modo. Não criar, não alterar, não remover nada nesta etapa.

### 4.2 Ramo A — o arquivo existe, é regular, legível pelo container

Produção está íntegra; E1 é defeito exclusivo do rehearsal. Seguir §5 sem alteração adicional.
Registrar owner e modo observados no relatório.

### 4.3 Ramo B — ausente, é diretório, ou sem leitura para outros

Produção está quebrada e o reparo é pré-requisito do deploy comercial. Autorizado, nesta mesma
janela:

1. acrescentar `ops/db/init-databases.sh` a `SOURCE_FILES` e a `EXECUTABLE_FILES` em
   `control_root_package.py`, com o validador do pacote em lockstep;
2. materializar `<deploy_root>/releases/db/init-databases.sh` a partir da cópia do control root,
   com diretório `0700` e arquivo `0755`, owner `deploy-emporio`, no mesmo ponto do caminho de
   deploy que já prepara o root — sem tocar em bundle, planner ou contrato do bundle;
3. provar por teste causal que o pacote reconstruído contém o script com bit de execução e que a
   materialização é idempotente.

**Proibido** carregar o script dentro do bundle: `deployment_plan` valida o conjunto exato de
arquivos do bundle com modo `0600`, que seria ilegível pelo uid do container — trocaria um defeito
por outro.

---

## 5. Reparo

```text
tools/deploy/deployment_engine_rehearsal.py:794    chmod(0o700) -> chmod(0o755)   [E1]
ops/compose/compose.prod.yml:31                    pg_isready -h 127.0.0.1        [E2]
tools/deploy/control_root_package.py               somente no Ramo B              [E3]
testes e validadores diretamente correspondentes
espelhos documentais estritamente necessários
```

Não alterar planner, executor transacional, `production_adapter.py`, transport SSH, runtime,
release, migrations, imagem do control plane ou workflows comerciais.

### 5.1 Testes causais obrigatórios

1. **E1** — o script materializado pelo rehearsal tem modo `0755`, sem bits de escrita para grupo
   ou outros; mutante com `0700` reprova.
2. **E1** — o modo materializado é igual ao modo versionado de `ops/db/init-databases.sh`.
3. **E2** — o healthcheck do `postgresql` contém `-h 127.0.0.1`; mutante sem o host reprova;
   `validate_compose` continua verde.
4. **E3, Ramo B** — pacote reconstruído contém o script executável; materialização idempotente.
5. Nenhum artifact passa a conter path absoluto, owner, modo de produção ou conteúdo do script.

---

## 6. Sequência

Iniciar de imediato, sem confirmação intermediária:

1. verificação read-only da §4.1 e escolha do ramo;
2. reparo da §5 e testes da §5.1;
3. suítes causais do engine workflow, adapter e compose; validadores; `git diff --check`; secret
   scan com `unsupported=0`;
4. um commit documental com esta correction, README e HANDOFF; um commit técnico;
5. um push fast-forward; CI 13/13 verde;
6. **exatamente um** rehearsal remoto, attempt 1, sem inputs, sem rerun;
7. com o run verde, seguir para a rotação única do control root — que carrega `production_adapter`
   da correction-09 e `compose.prod.yml` desta —, fingerprint, private key, dois probes SSH e
   **exatamente uma** operação comercial para `v0.1.1`.

### 6.1 Critérios de aceite do run

```text
trust / rehearse / outcome     success
transactionStatus              SUCCESS
cleanupStatus                  SUCCESS
status                         SUCCESS
causeCode                      null
steps                          PULL, BACKUP, MIGRATE, UPDATE, VERIFY, COMMIT_STATE = SUCCEEDED
                               ROLLBACK = PENDING
databaseRestoreRequired        true
backup                         2/2 com size > 0
services                       7
current / previous             v0.1.1 / null
replay                         journal, backup e containers inalterados
cleanup                        zero recursos criados por esta execução
```

### 6.2 Limites de consumo

```text
commit documental        1
commit técnico           1
push                     1  fast-forward, sem amend, rebase ou force-push
rehearsal remoto         1  nenhuma correção automática após falha
rotação do control root  1  somente após rehearsal verde
operação comercial       1  somente após rehearsal verde
```

---

## 7. Fronteira seguinte, ainda não exercitada

`MIGRATE` é o próximo limite. O que foi possível confrontar estaticamente está verde: `backend` e
`website_back` estão em `emporio-app` **e** `emporio-db` (`compose.prod.yml:107,147`), logo
`compose run --rm --no-deps` alcança o `postgresql`.

O que **não** é verificável sem as imagens reais e permanece risco declarado:

1. existência e contrato de `/app/bin/migrate` em `backend` e `website_back`;
2. `_migration_result` exige que a **última linha do stdout** seja exatamente `MIGRATIONS_APPLIED`
   ou `MIGRATIONS_PENDING`; qualquer log posterior da aplicação reprova o passo;
3. o probe exige o par exato `return_code 10` + `MIGRATIONS_PENDING` para banco novo;
4. `UPDATE`/`VERIFY` dependem dos healthchecks reais dos seis componentes e das quatro sondas
   `curl` em `127.0.0.1:8120`.

Não instrumentar nem alterar esses pontos por antecipação. A evidência estruturada da
correction-09 já localiza o passo exato caso algum falhe.

---

## 8. Autoridade e limites

A entrega desta correction ao executor constitui a janela. Não há frase adicional, aceite
intermediário ou confirmação a solicitar — nem para commitar, empurrar, despachar o rehearsal,
rotacionar o control root ou criar a operação comercial. Tudo está autorizado aqui e governado
pelos critérios objetivos da §6.1.

Parar e reportar apenas se um fato observado contradisser materialmente este contrato. Qualquer
falha do run único encerra a delegação: registrar a evidência, parar fail-closed e devolver ao
orquestrador. Não há segunda janela implícita.

Continuam proibidos: segundo run, correção automática após falha, gate adicional de registry, run
apenas para diagnóstico, rehearsal de stack completa na estação ou na VPS, segundo deploy, rerun,
replay terminal, nova release, imagem do control plane, SQL manual, rollback, restore, edição de
operação/journal/banco para fabricar sucesso, intervenção em outro tenant, reboot, update, aceite
da S46 ou criação da S47 pelo executor.

---

## 9. Relatório e terminais

Acrescentar `Retomada correction-10` ao relatório contínuo, registrando separadamente: causa E1,
defeito E2, verificação read-only da §4.1 e ramo escolhido, reparo, testes, commit e CI, run
técnico com evidência estruturada, rotação única do control root, SSH e operação comercial. O
relatório permanece não rastreado, fora do stage, com secret scan exclusivo.

Em sucesso:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Em bloqueio:

```text
BLOCKED — S46 correction-10 interrompida fail-closed no subestágio corrente
```
