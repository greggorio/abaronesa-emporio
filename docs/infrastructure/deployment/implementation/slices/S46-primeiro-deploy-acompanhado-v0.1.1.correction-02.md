# S46 — correction-02: falha pré-deploy reconciliável e retomada comercial única

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED`
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Correção anterior:** `S46-primeiro-deploy-acompanhado-v0.1.1.correction-01.md`
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`
> **Release comercial alvo:** `v0.1.1`, imutável

## 1. Veredito e checkpoint aceito

A correction-01 executou corretamente até a primeira causa técnica posterior ao
POST. O relatório com SHA-256
`09f5bbdd6bbf9356cbe3233e507213b46a5f679c61a2b753545e7db5442b53be` é aceito
como checkpoint factual. **S46 continua não aceita.**

A evidência independente do orquestrador confirmou:

```text
operation                    dep_6bd76dcff84a42ba88705b5448aa5c3c
run                          30981846816, attempt 1, failure
trust                        success
prepare                      failure, INVALID_DISPATCH
deploy                       skipped
outcome                      failure sem artifact
artifacts                    somente deployment-trust 8920438907
commercial resources         0
backups/migrations/restore   0/0/0
porta 8120                   livre
operation state              QUEUED / CONFIRMED / INDETERMINATE
errorCode                    DEPLOYMENT_OUTCOME_UNAVAILABLE
control plane                live 200 / ready 503
control root                 cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
```

O run real devolve pela API oficial:

```text
name          deploy-production-dep_6bd76dcff84a42ba88705b5448aa5c3c
display_title deploy-production-dep_6bd76dcff84a42ba88705b5448aa5c3c
path          .github/workflows/deploy-production.yml
```

O `prepare_handoff()` publicado exige incorretamente:

```text
name          Deploy Production
path          .github/workflows/deploy-production.yml@main
```

O workflow declara `run-name`; portanto `name` recebe o título materializado e
`path` não recebe sufixo de ref. O runtime reconciliador já usa a forma real,
mas o transporte e suas fixtures ainda codificam a forma antiga.

## 2. Objetivo da correction

Concluir a S46 sem apagar ou reescrever a intenção falha:

1. corrigir o contrato de identidade REST do transporte;
2. implementar reconciliação causal e fail-closed para falha comprovadamente
   anterior ao job `deploy`;
3. publicar e instalar o plano de controle corrigido;
4. reconciliar automaticamente a operação original como `FAILED`, preservando
   seu run, idempotência, audit e evidência;
5. somente depois de readiness limpa, executar uma única operação comercial
   substituta para `v0.1.1`;
6. concluir backup, migrations, sete serviços, HTTPS, JWKS e UI conforme a task
   principal.

Não criar S47 antes do aceite da S46. Esta é uma correção causal dentro da
mesma slice, não uma nova etapa do roadmap.

## 3. Correção do validador do transporte

Em `tools/deploy/deployment_transport.py`, alinhar `prepare_handoff()` à forma
REST já usada pelo reconciliador:

```text
name          deploy-production-<operationId>
display_title deploy-production-<operationId>
path          .github/workflows/deploy-production.yml
```

Preservar e testar todos os demais bindings:

- run ID e attempt do trust;
- `workflow_dispatch`, branch `main` e `head_sha=controlSha`;
- repository e head repository canônicos;
- actor ID da intenção;
- URL canônica do mesmo run;
- operation ID e target release do trust.

Adicionar fixture reproduzindo literalmente a forma observada no run
`30981846816`. Mutantes de `name`, `display_title`, `path`, operation ID,
repository, actor, SHA, run, attempt, event, branch e URL devem continuar
falhando `INVALID_DISPATCH` antes de release, handoff, SSH ou filesystem remoto.

Não remover validação de identidade nem aceitar simultaneamente a forma antiga.

## 4. Reconciliação segura da falha anterior ao deploy

### 4.1 Evidência remota necessária

O reconciliador pode terminalizar sem outcome somente o caso fechado abaixo,
consultado pela API GitHub com o run/attempt já vinculados:

```text
run status/conclusion       completed/failure
trust                       success
prepare                     failure
deploy                      skipped
outcome                     failure
artifact set                somente deployment-trust
deployment-handoff          ausente
deployment-result           ausente
deployment-workflow-outcome ausente
```

Validar jobs pelo endpoint do attempt exato, com IDs positivos, run ID, SHA,
URLs, nomes, status e conclusões fechados. Exigir exatamente uma ocorrência de
cada job canônico e nenhum job inesperado. Revalidar também o artifact
`deployment-trust`, sua identidade, ZIP fechado, JSON canônico e bindings com a
operação/run/attempt/SHA/ator/release.

Qualquer paginação ambígua, job extra/ausente/duplicado, `deploy` diferente de
`skipped`, handoff/result/outcome presente, artifact expirado, attempt/SHA/run
divergente ou falha HTTP mantém `INDETERMINATE`. Não inferir segurança de logs
textuais.

### 4.2 Transição local permitida

Somente com toda a evidência da seção 4.1 e transação/lock da operação:

```text
state                    FAILED
dispatchState            CONFIRMED
transportStatus          CONFIRMED
remoteState              FAILED
databaseRestoreRequired  false
errorCode                WORKFLOW_PRE_DEPLOY_FAILED
activeSlot               null
finishedAt               preenchido
```

Preservar imutáveis `workflowRunId`, attempt, URL, control SHA, request hash,
idempotency hash, actor, target release e timestamps históricos. Reter a chave
de idempotência original pelo prazo contratado.

Persistir em `evidence_json` somente metadados não sensíveis suficientes para
reproduzir a decisão: run, attempt, SHA, conclusões dos quatro jobs, artifact
trust e motivo `deploy_skipped`. Acrescentar audit explícito
`deployment.predeploy_failed`. Reexecução do reconciliador deve ser idempotente
e não duplicar transição ou audit.

O singleton incerto de `rc_current_installation` pode ser removido
transacionalmente apenas quando:

- release, source commit, previous release, installedAt e state hash estão
  todos ausentes;
- `last_operation_id` aponta para a operação original;
- `uncertainty_code=DEPLOYMENT_OUTCOME_UNAVAILABLE`;
- não existe recurso comercial, backup ou evidência de execução do job deploy.

Qualquer campo comercial preenchido ou divergência interrompe fail-closed. Não
editar banco por SQL manual, migration de dados ad hoc, console ou script de
operação. A transição deve ocorrer exclusivamente pelo reconciliador versionado.

### 4.3 Testes causais

Cobrir no mínimo:

1. reprodução integral do run real;
2. transição atômica `QUEUED/INDETERMINATE -> FAILED/CONFIRMED`;
3. slot liberado e readiness limpa somente depois da prova completa;
4. operação, idempotência, binding e audit históricos preservados;
5. singleton vazio/incerto removido somente pela forma exata;
6. idempotência de reconciliações repetidas;
7. `deploy=success|failure|cancelled|in_progress` nunca terminalizado por esse
   caminho;
8. handoff, result ou outcome presente rejeitado;
9. jobs/artifacts extra, ausente, duplicado, expirado ou de outro attempt
   rejeitados;
10. current parcial/comercial ou código de incerteza divergente rejeitado;
11. erro/paginação/shape GitHub mantendo operação ativa e readiness 503;
12. rollback não alcançando esse caminho;
13. nenhum dispatch, SSH, Docker ou acesso externo nos testes.

## 5. Gates, commit e publicação do plano de controle

Executar testes focais, `release_control/tests`, oito suítes canônicas, todos os
validadores vigentes, `catalog:valid`, secret scan completo e staged com
`unsupported=0` e `git diff --check`.

Somente tudo verde:

1. criar um commit técnico normal sobre os commits documentais locais;
2. push fast-forward, sem amend/rebase/force;
3. exigir CI e Publish Candidate verdes no SHA terminal;
4. executar uma única vez `Publish Release Control Image`, sem inputs;
5. validar quatro jobs, artifacts, manifesto, sidecars, package privado e
   digest imutável;
6. não criar tag, GitHub Release ou nova release comercial.

Até dois ciclos causais de commit/push são permitidos **antes** de publicar a
imagem e antes da nova intenção. Cada ciclo exige novamente matriz e gates
remotos. Depois da publicação da imagem, nenhum novo patch é autorizado nesta
execução.

## 6. Atualização do control root e control plane

O SHA terminal do commit técnico torna-se o novo `controlSha`.

1. reconstruir duas vezes o pacote do control root diretamente desse objeto
   Git, com o protocolo S42/S43;
2. rotacionar transacionalmente `cf3385f…` para o SHA terminal, com rollback
   armado até verify/capabilities;
3. atualizar atomicamente somente o immutable ref da imagem do release control
   no `.env` protegido;
4. pull por digest e recriar somente o serviço `release_control`, preservando o
   PostgreSQL e seu volume;
5. exigir migrations já correntes, health live e observar o reconciliador.

Sem SQL manual, restart do PostgreSQL, alteração de App/allowlist/JWT, nova
chave, update do host ou toque na stack comercial.

O reconciliador corrigido deve, sem endpoint mutante e sem intervenção no
banco:

- terminalizar a operação original exatamente como seção 4.2;
- preservar run/artifact/audit/idempotência;
- remover somente o singleton vazio de incerteza;
- devolver readiness 200, current 404 e `v0.1.1 eligible=true`;
- manter zero recursos comerciais, backups, migrations e porta 8120 livre.

Se isso não ocorrer, parar. Não fabricar a transição nem avançar para novo POST.

## 7. Operação comercial substituta

Somente depois de todos os gates da seção 6, retomar as seções 5–12 da task
principal e a preservação diferencial da correction-01.

Gerar novo bootstrap curto e uma nova idempotency key. Enviar exatamente um
POST para `v0.1.1`, produzindo uma única operação substituta e um único novo
run. Essa não é repetição da operação antiga: a anterior deve estar terminal
`FAILED` por prova de que o job comercial nunca iniciou.

Exigir no novo run:

```text
trust -> prepare -> deploy -> outcome    success
controlSha                              SHA técnico terminal
commercial deployment                   uma única execução
rollback/restore                         zero
```

Concluir integralmente backup, migrations, sete serviços, digests, HTTPS,
JWKS, login/UI, remoção do bootstrap temporário e desativação do bootstrap root.

Se a operação substituta falhar ou ficar incerta, parar e preservar evidência.
Não há terceiro POST, terceira operação, rerun manual, rollback ou patch
pós-POST autorizado.

## 8. Estado terminal esperado

```text
operação original       FAILED / WORKFLOW_PRE_DEPLOY_FAILED, preservada
run original            30981846816 failure, preservado
operação substituta     SUCCEEDED, única execução comercial
novo run                success, attempt 1
v0.1.1                  current e reconciled=true
commercial containers   7/7 healthy
backup/migrations       comprovados
HTTPS/JWKS/UI            verdes; bootstrap removido/desativado
deploy runs             exatamente 2 no total: 1 pré-deploy falho + 1 sucesso
rollback/restore        0/0
control plane           ready 200, sync sem drift
outros sistemas         preservação diferencial comprovada
```

O critério anterior de “uma única operação/run” fica substituído por esta
contabilidade explícita. Continua existindo uma única execução comercial do
adapter; o primeiro run nunca alcançou o job `deploy`.

## 9. Autorização cumulativa

A delegação deve conter literalmente:

```text
Autorizo integralmente a correction-02 da S46 a corrigir a identidade REST do run de deploy, implementar a reconciliação fail-closed da operação dep_6bd76dcff84a42ba88705b5448aa5c3c como FAILED somente mediante prova imutável de que o job deploy do run 30981846816 foi skipped e nenhum artifact ou recurso comercial existiu, versionar e publicar o plano de controle corrigido, atualizar transacionalmente o control root e somente a imagem do control plane na VPS. Depois de a operação original estar terminal, a readiness voltar a 200 e v0.1.1 ficar novamente elegível, autorizo uma única operação substituta e um único novo run para concluir o primeiro deploy comercial de v0.1.1, incluindo bootstrap, backup, migrations, sete serviços, HTTPS, JWKS, UI e desativação do bootstrap root. Não autorizo apagar ou editar manualmente a operação anterior, terceiro POST, rerun, rollback, restore, nova release comercial, intervenção em outro tenant, reboot, update ou patch depois do novo POST.
```

Com essa frase, não pedir ao usuário dado, credencial, ID, release, digest ou
janela adicional. Todos são fechados, descobríveis ou geráveis.

## 10. Relatório e terminal

Não criar relatório novo. Acrescentar `Retomada correction-02` ao relatório
contínuo da S46, mantendo-o não rastreado, fora do stage e sem material
protegido.

Registrar código/testes/commits/runs, imagem/control root, reconciliação da
operação histórica, audit e invariantes, readiness, nova intenção/run, execução
comercial completa, negativos, cleanup e secret scan do relatório.

O executor não aceita S46 e não cria S47.

Em sucesso, terminar exatamente:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S46 correction-02 interrompida fail-closed na primeira causa técnica
```

## 11. Critérios de aceite

A S46 somente será aceita com a falha pré-deploy original reconciliada por
evidência imutável, uma única execução comercial substituta `SUCCEEDED`,
`v0.1.1` corrente, backup/migrations/serviços/HTTPS/JWKS/UI comprovados, zero
rollback/restore e preservação diferencial dos demais sistemas.
