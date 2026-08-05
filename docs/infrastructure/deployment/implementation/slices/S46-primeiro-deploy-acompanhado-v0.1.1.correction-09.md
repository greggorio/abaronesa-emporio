# S46 — correction-09: reparo causal completo do rehearsal e do adapter, e conclusão do primeiro deploy de v0.1.1

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED` — a entrega deste documento ao executor é a janela; iniciar de imediato pela §14
> **Natureza:** reparo causal em quatro defeitos provados; sem reinício da S46
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Checkpoint técnico:** `7128293ca265bc11c3a27f626ab9dbb4d6c618bf`
> **Substitui:** correction-08, rejeitada; commit local `6c23d283` não deve ser publicado (§14)
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`

---

## 1. Checkpoint aceito, S46 não aceita

O run `31021376896` preservou trust e outcome, falhou no subestágio `DEPLOYMENT_CLI` e encerrou
sem SSH, probe, operação comercial, backup, migration, serviço, current ou previous. **S46
continua não aceita.**

Não repetir:

```text
planner de first-install                  corrigido em d43d0d1…
root compatível planner/CLI               corrigido em 7128293…
CI 31019564059                            success, 13/13
control root 7128293…                     instalado, verify/capabilities verdes
GHCR opaco do deploy-emporio              6/6 manifests verdes na VPS
POSTGRES_IMAGE de produção                confirmado canônico e íntegro (.env 0600)
credencial SSH/fingerprint/probes         ainda não executados
deploy/rollback comerciais                3 falhos históricos / 0
rehearsal 31021376896                     histórico falho preservado
```

Inventário amplo, release, Gate C, preparação da VPS, App, control plane e checkpoints anteriores
não são refeitos.

---

## 2. Causa fechada — proibido reinvestigar

Quatro defeitos determinísticos, provados por leitura de código e reprodução em processo, sem
dispatch, sem SSH e sem container.

### D1 — `POSTGRES_IMAGE` do rehearsal é rejeitado pelo próprio adapter

`deployment_engine_rehearsal.py:38` usa `postgres@sha256:…`. `production_adapter.py:771-774`
(`_image_probe`) só aceita `ghcr.io/greggorio/abaronesa-emporio-…@sha256:` ou o prefixo literal
`postgres:`. O valor atual não casa nenhum dos dois e retorna `UNKNOWN` **sem executar docker**.

```text
_probe_pull (production_adapter.py:837-846)   states = [UNKNOWN, ABSENT×6] -> UNKNOWN
_run_adapter_action (deployment_executor.py:979-980)   != ABSENT -> PULL_FAILED
_complete_failure   PULLING ∉ COMPENSATED_STATES -> journal FAILED
deployment_cli.main -> exit 21, stdout {"errorCode":"PULL_FAILED",…}
```

Reprodução com CLI, planner e executor reais, substituindo apenas respostas do processo Docker:
`exit_code=21`, `state=FAILED`, `errorCode=PULL_FAILED`, `pull_called=False`.

**Escopo do defeito:** somente o rehearsal. A leitura restrita da VPS confirmou `POSTGRES_IMAGE`
canônico e íntegro no `.env` de produção (`0600`). O adapter está correto; a constante do
rehearsal está errada.

### D2 — lista de passos incompatível

`deployment_engine_rehearsal.py:37` espera 6 passos. `deployment_executor.py:43-51` define 7, e
`_validate_journal_semantics` (`deployment_executor.py:411`) **exige** os 7 nomes nessa ordem em
todo journal válido. As checagens de `deployment_engine_rehearsal.py:444-445` reprovam um deploy
bem-sucedido por dois motivos independentes: tupla de nomes diferente e `ROLLBACK` em `PENDING`.

### D3 — `databaseRestoreRequired` invertido

`deployment_engine_rehearsal.py:441` exige `False`. `deployment_executor.py:823` grava `True` ao
iniciar `MIGRATE` e nunca reverte; `deployment_executor.py:579-584` rejeita como `JOURNAL_CORRUPT`
qualquer journal com `MIGRATE ∈ {RUNNING,SUCCEEDED,FAILED}` e valor diferente de `True`. Na
primeira instalação `migrationRequired` é sempre `True` (`deployment_plan.py:418-428`).

> **D2 + D3:** mesmo com o deploy perfeito, o rehearsal reprovaria em `TRANSACTION_EVIDENCE`.

Journal real de sucesso na primeira instalação:

```text
PULL, BACKUP, MIGRATE, UPDATE, VERIFY, COMMIT_STATE = SUCCEEDED
ROLLBACK                                            = PENDING
databaseRestoreRequired                             = true
state                                               = SUCCEEDED
```

### D4 — janela de readiness insuficiente (defeito de produção, não do rehearsal)

`compose.prod.yml:108` e `:148` declaram, para `backend` e `website_back`:
`start_period 60s, interval 15s, timeout 5s, retries 20` → pior caso 60 + 20×(15+5) = **460s**.

`production_adapter.py:1338-1339` fixa `--wait-timeout 180` no helper compartilhado
`_up_services`, usado por três chamadores:

```text
production_adapter.py:1351   _execute_update    -> _up_services(..., 300, remove_orphans=True)
production_adapter.py:1444   _execute_verify    -> _up_services(..., 300)
production_adapter.py:1493   _execute_rollback  -> _up_services(..., 300)
production_adapter.py:993-1005 _execute_backup  -> "--wait-timeout","180" com timeout 180
```

O `--wait-timeout` é constante compartilhada; o timeout do processo é parâmetro por chamador.
Corrigir só o UPDATE faz VERIFY e ROLLBACK pedirem 480s ao Compose com o processo morrendo em
300s — e `_run_captured` levanta `COMMAND_TIMEOUT` **antes** da checagem de `return_code`,
transformando um deploy saudável em `VERIFY_FAILED` seguido de `ROLLBACK_FAILED`. Em
`_execute_backup`, timeout de processo igual ao wait-timeout é corrida.

### C0 — causa provável do run `31021376896` (inferência declarada, não prova)

O step do rehearsal durou **1,82s**. Orçamento até a primeira ação: startup do Python + import de
`jsonschema` (~0,3-0,5s) + `validate_bundle` duas vezes (~0,2-0,6s). `_effective_docker_config()`
(`production_adapter.py:156-197`) falha **sem executar subprocesso** — caminho mais rápido
possível. `docker compose config --quiet` acrescentaria ~0,5-1,5s e `_probe_pull` um segundo
`compose config --format json`.

1,82s é compatível com parada em `_effective_docker_config` (guard exige `~/.docker` exatamente
`0700` e `config.json` `0600`), apertado para `validate_compose()` e dificilmente compatível com
ter alcançado o probe de PULL. **Tratar a normalização do Docker config como reparo causal, não
como endurecimento preventivo.** D1-D4 permanecem garantidos independentemente de C0.

O ponto interno exato do run `31021376896` é irrecuperável: o código destruiu o journal e gravou
`steps=[]` de forma fixa (`deployment_engine_rehearsal.py:507`). **Não é necessário recuperá-lo.**

---

## 3. Escopo de arquivos

```text
tools/deploy/deployment_engine_rehearsal.py            D1, D2, D3, evidência, cleanup, timeouts
tools/deploy/production_adapter.py                     D4  (pertence ao control root)
.github/workflows/verify-deployment-engine.yml         C0, timeout do job
tools/deploy/validate_deployment_engine_workflow.py    lockstep obrigatório
tools/deploy/tests/test_deployment_engine_workflow.py  testes causais
tools/deploy/tests/test_production_adapter.py          testes causais de D4
tools/deploy/validate_production_adapter.py            somente se o contrato exigir
espelhos documentais estritamente necessários
```

Não alterar: planner, executor transacional, transport SSH, runtime, release, migrations, Compose
canônico, imagem do control plane, workflows comerciais.

**Consequência assumida:** `production_adapter.py` pertence ao control root. Haverá **uma única**
reconstrução determinística e rotação do pacote, após rehearsal verde e antes da operação
comercial.

---

## 4. Reparo — `deployment_engine_rehearsal.py`

### 4.1 Constantes (D1, D2)

```python
POSTGRES_IMAGE = "postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297"
EXPECTED_STEPS = ("PULL", "BACKUP", "MIGRATE", "UPDATE", "VERIFY", "COMMIT_STATE", "ROLLBACK")
```

`POSTGRES_IMAGE` deve ser byte a byte idêntico ao `.env` de produção já confirmado. `EXPECTED_STEPS`
permanece literal — **não** importar `deployment_executor` em tempo de execução, para não arrastar
`jsonschema`/`vendor` para o processo do rehearsal. A não-divergência passa a ser garantida por
teste causal (§7.3) e marker do validador (§6).

### 4.2 Critério de sucesso da transação (D2, D3)

```text
journal.state                        == "SUCCEEDED"
journal.errorCode                    is None
journal.databaseRestoreRequired      is True
steps[PULL..COMMIT_STATE].status     == "SUCCEEDED"    (seis)
steps[ROLLBACK].status               == "PENDING"
tupla de nomes                       == EXPECTED_STEPS  (sete, na ordem)
state.release/reconciled, current -> releases/v0.1.1, previous ausente, backup 2/2 com size>0
```

### 4.3 Evidência: ordem de captura e gravação

**Invariante:** o journal é capturado e validado **em memória antes do cleanup**; o receipt é
gravado **depois do cleanup**, incorporando o resultado real da limpeza.

Projeção sanitizada, capturada em qualquer desfecho, inclusive falha:

```text
cliExit     inteiro ∈ {0,2,3,4,6,20,21}
causeCode   código estável do CLI, allowlist explícita + ^[A-Z][A-Z0-9_]{2,63}$
            ou CLI_CAUSE_UNAVAILABLE
journal     { state, errorCode, rollbackErrorCode, databaseRestoreRequired,
              steps: [ {name, status, errorCode} ] }   ou null
```

Todos os valores nascem de conjuntos fechados do executor (`STEPS`, statuses, `ACTION_FAILURE`).
Qualquer valor fora da allowlist, chave extra, JSON não canônico, BOM, payload excessivo, path,
newline, caractere de controle, traceback ou conteúdo misto ⇒ `causeCode=CLI_CAUSE_UNAVAILABLE` e
`journal=null`, sem copiar bytes brutos. Nunca gravar path absoluto, comando, token, Docker
config, env, PEM, segredo, stdout/stderr bruto ou traceback em log ou artifact.

O contrato externo `failedStage=DEPLOYMENT_CLI` / `errorCode=DEPLOYMENT_CLI_FAILED` permanece.
`causeCode` e `journal` servem à ação causal e à auditoria; não relaxam fail-closed nem
transformam falha em sucesso.

### 4.4 Resultado global em três campos

```text
transactionStatus = SUCCESS | FAILED
cleanupStatus     = SUCCESS | FAILED
status            = SUCCESS somente se ambos forem SUCCESS
```

Um receipt `SUCCESS` com cleanup incompleto declararia verde uma execução que deixou resíduo ou
material sensível. O `status` global continua sendo o único campo que o `outcome` e o `validate`
consomem para aprovar.

### 4.5 Cleanup por diferença contra baseline

Antes de qualquer ação mutável, registrar baseline de imagens, volumes, redes e containers do
projeto. No cleanup:

1. remover **somente** o que não existia no baseline — imagens preexistentes são preservadas;
2. verificar o retorno de `compose down -v --remove-orphans`, de cada `image rm` e de cada
   `shred`; hoje esses retornos são ignorados (`deployment_engine_rehearsal.py:355-361`);
3. deadline global de **600s** para o cleanup; estouro é `cleanupStatus=FAILED` com resíduo
   declarado, nunca silencioso;
4. remover o root efêmero pelo path nominal validado, como hoje;
5. contadores finais continuam obrigatoriamente zero para recursos criados por esta execução.

### 4.6 Timeouts do wrapper

```text
timeout do subprocesso do CLI    3600 s   (60 min)
deadline do cleanup               600 s   (10 min)
```

### 4.7 Resolução única do digest do PostgreSQL — obrigatória

`_run_adapter_action` (`deployment_executor.py:983-984`) colapsa qualquer `ProductionAdapterError`
em `PULL_FAILED`, e `SubprocessRunner` descarta stderr. Logo, defeito de código, `toomanyrequests`
do Docker Hub e indisponibilidade transitória do registry são **indistinguíveis** no journal
preservado. O PostgreSQL é a única dependência anônima do caminho: o `docker login` do workflow
cobre apenas `ghcr.io`.

Implementar **uma** resolução read-only do digest do PostgreSQL, antes do CLI, **não fatal**,
registrada como booleano `postgresManifestResolved` no receipt. Sem pull, sem login adicional, sem
novo estágio de falha, sem persistir stdout ou stderr, sem alterar o fluxo em caso de `false`.
**Não** implementar gate de sete manifests, nem estágio `REGISTRY_ACCESS`, nem qualquer aborto
anterior ao CLI derivado desta verificação.

---

## 5. Reparo — `production_adapter.py` (D4)

**Invariante obrigatório:** em todo ponto, `timeout do processo > --wait-timeout`.

```text
_up_services            --wait-timeout  180 -> 480
_execute_update         timeout         300 -> 540
_execute_verify         timeout         300 -> 540
_execute_rollback       timeout         300 -> 540
_execute_backup         --wait-timeout  180 -> 240   e   timeout 180 -> 300
```

Nenhuma outra alteração no adapter. Não conceder `push`, `login` ou `logout`; não alterar
`MINIMUM_ENV`, `_effective_docker_config`, `_image_probe` ou a resolução de binários.

---

## 6. Reparo — workflow e validador, em lockstep

`.github/workflows/verify-deployment-engine.yml`:

1. após o `docker login ghcr.io` e **antes** do step do rehearsal, normalizar e verificar
   `$HOME/.docker` para `0700` e `$HOME/.docker/config.json` para `0600`, falhando o step se a
   verificação não fechar; nunca ler, imprimir, hashear ou persistir o conteúdo do config;
2. `timeout-minutes` do job `rehearse`: `45` → `90`.

`tools/deploy/validate_deployment_engine_workflow.py` — **mudança acoplada obrigatória**, sob pena
de reprovar o próprio gate:

```text
linha 62   expected["rehearse"] = ("trust", "45")  ->  ("trust", "90")
markers    POSTGRES_IMAGE canônico; EXPECTED_STEPS com sete nomes;
           databaseRestoreRequired True; transactionStatus; cleanupStatus;
           causeCode; normalização do Docker config no workflow
forbidden  preservar integralmente os atuais
```

---

## 7. Testes causais obrigatórios

1. **D1** — `production_adapter._image_probe` aceita a constante `POSTGRES_IMAGE` do rehearsal;
   mutante com a forma digest-only reprova.
2. **D2** — `EXPECTED_STEPS == deployment_executor.STEPS`, comparação por igualdade de tupla.
3. **D3** — journal de sucesso com `databaseRestoreRequired=True` e `ROLLBACK=PENDING` é aceito;
   mutantes com `False` e com `ROLLBACK=SUCCEEDED` reprovam.
4. **D4** — para `_up_services`, `_execute_update`, `_execute_verify`, `_execute_rollback` e
   `_execute_backup`, provar `timeout do processo > --wait-timeout` e os valores exatos da §5.
5. **Evidência** — `causeCode`, `cliExit` e projeção do journal aparecem no receipt em três
   cenários: falha pré-journal, falha terminal do journal e saída inválida do CLI; nenhum path,
   comando, byte bruto ou traceback vaza em nenhum deles.
6. **Ordem** — journal capturado antes do cleanup; receipt gravado depois; prova por sequência de
   chamadas, não por inspeção de arquivo.
7. **Tri-status** — `status=SUCCESS` exige `transactionStatus=SUCCESS` **e**
   `cleanupStatus=SUCCESS`; transação verde com cleanup falho produz `status=FAILED`.
8. **Cleanup por baseline** — imagem preexistente no baseline sobrevive; recurso criado pela
   execução é removido; retorno não-zero de `down`, `image rm` ou `shred` produz
   `cleanupStatus=FAILED`.
9. **Não vazamento** — nenhum artifact contém path absoluto, `RUNNER_TEMP`, token, PEM, Docker
   config, env protegido ou stdout/stderr bruto.
10. **Mutantes** do validador e do workflow, incluindo o `timeout-minutes` e a normalização do
    Docker config.

Os testes existentes de `test_deployment_engine_workflow.py` mockam `_run`, `_cleanup`, `_release`
e `generate_bundle`, e o CLI real nunca é executado. Foi essa cegueira que deixou D1, D2 e D3
passarem por sete tentativas. Os testes novos devem exercitar o CLI, o planner e o executor reais,
substituindo **apenas** as respostas do processo Docker.

---

## 8. Provas locais sem container

A estação não comporta a stack completa (memória disponível 3091 MiB; swap em uso 7749 MiB). O
E2E acontece **uma única vez** no runner isolado do GitHub. Antes dele, obrigatoriamente:

1. suítes causais da §7;
2. reprodução em processo do caminho `CLI → planner → executor → adapter`, com runner de processo
   substituído, provando o journal de sucesso da §4.2 e cada `causeCode` esperado;
3. validadores do engine workflow e do adapter;
4. `git diff --check`;
5. secret scan do patch e do stage com `unsupported=0`.

**Facultativo, padrão = não executar.** Se — e somente se — a estação estiver sem pressão de
memória, é permitido subir apenas `postgresql` + `backend` (≈1,8 GiB pelos limites declarados) e
exercitar PULL de duas imagens, BACKUP e MIGRATE do `erp`. Isso validaria o comando gerado por
`_database_dump_command`, a existência de `/app/bin/migrate` na imagem real e a alcançabilidade do
`postgresql` pela rede `emporio-db` a partir de `compose run --no-deps`. **Não é pré-condição do
dispatch e pular esta prova não exige justificativa.** Proibido subir a stack completa aqui ou
usar a VPS como laboratório.

Não repetir as oito suítes canônicas locais, Publish Candidate ou release validation já aceitos.
A CI remota é o gate amplo.

---

## 9. Critérios objetivos para autorizar o dispatch

Todos verdadeiros, sem exceção:

1. `POSTGRES_IMAGE` do rehearsal byte a byte igual ao `.env` de produção confirmado;
2. `EXPECTED_STEPS` com sete nomes, idêntico a `deployment_executor.STEPS`;
3. critério de sucesso da §4.2 implementado e coberto por teste positivo e negativo;
4. §5 aplicada aos cinco pontos, com o invariante `timeout > wait-timeout` provado;
5. `timeout-minutes` do workflow e `validate_deployment_engine_workflow.py` alterados juntos, com
   o validador verde;
6. normalização do Docker config presente no workflow, antes do step do rehearsal;
7. evidência estruturada (`cliExit`, `causeCode`, `journal`) e tri-status implementados e testados;
8. cleanup por baseline com verificação de retornos e deadline, testado;
9. suítes causais, validadores, `git diff --check` e secret scan `unsupported=0` verdes;
10. CI 13/13 verde no SHA a ser despachado;
11. estado comercial reconfirmado read-only: deploy 3/3 FAILED, rollback 0, sem `current`/
    `previous`, zero containers/volumes/redes/backups comerciais, porta 8120 livre.

---

## 10. Execução e limites de consumo

```text
commit documental           1  correction-09 + README + HANDOFF          (§14)
commit técnico              1  código, testes, workflow e validador
push                        1  fast-forward, sem amend, rebase ou force-push
runs remotos de rehearsal   1  attempt 1, sem inputs, sem rerun de run histórico
correção automática         0
rotação do control root     1  somente após rehearsal verde              (§10.2)
operação comercial          1  somente após rehearsal verde
```

Os dois commits podem ser empurrados num único push fast-forward. Nenhum outro commit é
autorizado, em nenhuma circunstância.

Qualquer falha do run retorna ao orquestrador com o journal preservado e a projeção sanitizada. O
executor **não** inicia outro ciclo, não aplica correção especulativa e não reinicia a S46.

### 10.1 Critérios de aceite do run

```text
trust                success
rehearse             success
outcome              success
transactionStatus    SUCCESS
cleanupStatus        SUCCESS
status               SUCCESS
failedStage          null
errorCode            null
causeCode            null
steps                seis SUCCEEDED + ROLLBACK PENDING
databaseRestoreRequired  true
backup               2/2 com size > 0
services             7
current / previous   v0.1.1 / null
replay               journal, backup e containers inalterados
cleanup              zero recursos criados por esta execução
```

### 10.2 Após rehearsal verde

1. identificar o SHA técnico terminal;
2. como `production_adapter.py` mudou, reconstruir o control root deterministicamente **duas
   vezes**, provar Python 3.10 com rede bloqueada e rotacionar o pacote **uma única vez**;
3. `verify` e `capabilities` diretos apontando para o SHA exigido pelo transport;
4. retomar na fingerprint, private key e nos dois probes SSH pendentes da correction-05;
5. remover a chave SSH antiga somente após o primeiro probe verde;
6. com segundo probe, readiness, `current` vazio, elegibilidade e capacidade verdes, criar
   **exatamente uma** operação comercial para `v0.1.1`;
7. acompanhar deploy e outcome até estado terminal, sem replay de operação terminal e sem segundo
   POST.

---

## 11. Riscos aceitos e declarados

Estes não são mitigáveis antes do run único e **não** constituem escândalo se ocorrerem:

1. **Rate limit anônimo do Docker Hub** no pull do PostgreSQL a partir do IP compartilhado do
   runner. Mitigação parcial: §4.7.
2. **Diferenças específicas do runner GitHub-hosted** não observáveis localmente, além do Docker
   config já tratado.
3. **Orçamento de tempo total** — pull de sete imagens, start, backup, migrations, verify e replay
   completo dentro de 90 min; nunca medido de ponta a ponta.
4. **`PULL_FAILED` estruturalmente ambíguo** enquanto `_run_adapter_action` colapsar códigos do
   adapter; abrir esse colapso exigiria tocar o executor transacional, o que está fora de escopo.

Uma falha do run único por qualquer destes é **desfecho previsto**. Com a evidência da §4.3 ela
volta ao orquestrador como diagnóstico completo — precisamente o que os sete runs anteriores não
produziram.

---

## 12. Proibições

- expor ou abrir token, Docker config, PEM, chave, JWT ou env protegido;
- alterar permissões da App ou de package, visibilidade ou release por inferência;
- gate adicional de registry, run apenas para diagnóstico, segundo run automático, terceiro run,
  rerun de run histórico, terceiro commit corretivo;
- rehearsal Docker de stack completa nesta estação ou na VPS;
- nova release, nova imagem do control plane, segundo deploy, rollback, restore ou SQL manual;
- editar operação, journal ou banco para fabricar sucesso;
- relaxar guards de path, `_validate_output_path`, `_validate_root` ou fail-closed;
- intervir em outro tenant, reiniciar ou atualizar o host;
- aceitar a S46 ou criar a S47 pelo executor.

---

## 13. Autoridade e limites

**A entrega desta correction ao executor constitui a janela.** Não há frase adicional, aceite
intermediário ou confirmação a solicitar. O executor inicia de imediato pela §14 e segue até um
dos terminais da §15.

Fica autorizado: reparo de D1-D4 e C0 nos termos das §§4-6; evidência estruturada e tri-status;
cleanup por diferença contra baseline; um commit documental; um commit técnico; um push
fast-forward; CI; exatamente um rehearsal remoto. Após rehearsal verde, e somente então: uma única
reconstrução e rotação do control root, configuração de fingerprint e private key SSH, dois probes
e exatamente uma operação comercial para `v0.1.1`.

Não pedir ao usuário senha, e-mail, token, chave, operation ID, idempotency key, release, digest,
path ou janela adicional — todos já estão fechados ou são geráveis. Não pedir confirmação para
executar o `reset --hard` da §14, para commitar, para empurrar, para despachar o rehearsal ou para
criar a operação comercial: tudo isso já está autorizado aqui e é governado pelos critérios
objetivos das §§9 e 10.1. As autorizações não consumidas da S46 e da correction-05 continuam
vigentes nos mesmos termos.

Perguntar só é admissível se um fato observado contradisser materialmente este contrato — por
exemplo, `origin/main` diferente de `7128293…`, ou `POSTGRES_IMAGE` de produção diferente do valor
da §4.1. Nesse caso, parar fail-closed e reportar, sem improvisar.

Qualquer falha do run único encerra a delegação: registrar a evidência, parar fail-closed e
devolver ao orquestrador. Não há segunda janela implícita.

---

## 14. Estado do repositório antes de começar

Situação atual: `HEAD = 6c23d2833b3987dc93e449ba3213779ea14f6947`, um commit à frente de
`origin/main = 7128293ca265bc11c3a27f626ab9dbb4d6c618bf`, sem push. Esse commit é puramente
documental e contém a correction-08 rejeitada:

```text
A  docs/infrastructure/deployment/implementation/slices/S46-…correction-08.md
M  docs/infrastructure/deployment/implementation/README.md
M  docs/infrastructure/deployment/implementation/HANDOFF_ORQUESTRADOR_FECHAMENTO.md
```

Sequência obrigatória, nesta ordem, iniciada de imediato e sem confirmação intermediária:

1. preservar cópia da correction-08 **fora do repositório**, para auditoria;
2. confirmar que o commit não foi empurrado: `git rev-parse origin/main` = `7128293…` e
   ahead/behind `1/0`;
3. `git reset --hard 7128293ca265bc11c3a27f626ab9dbb4d6c618bf`;
4. confirmar `HEAD == origin/main`, stage vazio, diff tracked vazio, e que permanecem não
   rastreados **apenas** os relatórios S39-S46;
5. criar o arquivo `S46-primeiro-deploy-acompanhado-v0.1.1.correction-09.md` com este conteúdo e
   **reaplicar** as atualizações de `README.md` e `HANDOFF_ORQUESTRADOR_FECHAMENTO.md` — elas
   foram revertidas no passo 3 e devem apontar para a correction-09, não para a 08;
6. commit documental normal, sem amend, rebase ou force-push;
7. só então iniciar o reparo técnico das §§4-7.

A correction-08 não é publicada, não é corrigida e não é referenciada como vigente. O relatório
contínuo permanece não rastreado, fora do stage, com secret scan exclusivo.

---

## 15. Relatório e terminais

Acrescentar `Retomada correction-09` ao relatório contínuo existente, registrando separadamente:
causa fechada (D1-D4 e C0), reparo do rehearsal, reparo do adapter, workflow e validador em
lockstep, testes causais, provas locais sem container, commit e CI, run técnico com sua evidência
estruturada, rotação única do control root, SSH e operação comercial. O relatório permanece não
rastreado, fora do stage, e passa por secret scan exclusivo.

Em sucesso:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Em bloqueio:

```text
BLOCKED — S46 correction-09 interrompida fail-closed no subestágio corrente
```
