# S46 — correction-03: helper executável e conclusão do primeiro deploy

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED`
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Correções anteriores:** `correction-01` e `correction-02`
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`
> **Release comercial alvo:** `v0.1.1`, imutável

## 1. Veredito e checkpoint aceito

O relatório com SHA-256
`81e713a906d1674a5c84fedbe64e8beff34f26ac90f9a9498ca434c0707172ea` é aceito
como checkpoint factual. A correction-02 cumpriu o contrato até a primeira
causa técnica posterior ao POST. **S46 continua não aceita.**

O orquestrador confirmou independentemente:

```text
HEAD/origin/main             e436190725336e34fecb08b818645ee22a0f87f5
run substituto              30988243119, attempt 1, failure
trust/prepare               success/success
deploy/outcome              failure/failure
outcome                     CONFIRMED / FAILED
errorCode                   REMOTE_CAPABILITY_MISMATCH
databaseRestoreRequired     false
operação                    dep_5c95bf53ba994cdc872a3d711b7c133b, terminal FAILED
helper instalado            regular 0600 deploy-emporio:deploy-emporio
execução direta             exit 126, Permission denied
control plane               live/ready 200/200
stack/backups/migrations    0/0/0
porta 8120                  livre
rollback/restore            0/0
```

As quatro evidências do run existem e vinculam operação, release, run, attempt
e SHA. `deployment-result` e `deployment-workflow-outcome` confirmam a mesma
falha anterior a snapshot, upload ou execução comercial.

## 2. Causa causal fechada

O transporte executa diretamente via SSH:

```text
/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
```

O helper possui shebang e é a entrada remota canônica, mas
`tools/deploy/control_root_package.py` declara como executável somente:

```text
ops/deploy/deploy-release.sh
```

Assim `_mode_for()` grava `deployment-remote.py` como `0600`. O arquivo está
presente, íntegro e no SHA correto, porém o usuário dedicado não pode executá-lo
como comando. O gate de capabilities ocorre antes de snapshot/upload; nenhuma
mutação comercial foi tentada.

Não corrigir com `chmod` manual na VPS nem mudar o transporte para contornar o
manifesto. O pacote deve materializar e verificar o modo contratado.

## 3. Correção causal do pacote

Adicionar exatamente `ops/deploy/deployment-remote.py` ao conjunto de arquivos
executáveis do control root. O manifesto, archive, instalador e verify devem
exigir `0755` para:

```text
ops/deploy/deployment-remote.py
ops/deploy/deploy-release.sh
```

Preservar `0600`/`0644` dos demais arquivos conforme a classe atual e os
diretórios `0700`. Não tornar `deployment_cli.py`, schemas, Compose, vendor ou
qualquer outro arquivo executável.

Fortalecer validador e testes para provar:

1. helper e shell script são o conjunto executável exato;
2. ausência do bit executável no helper falha build/verify/prova runtime;
3. arquivo extra executável falha;
4. manifesto com modo divergente falha;
5. instalação produz helper regular `0755`, owner dedicado, sem symlink;
6. o comando direto `<helper> capabilities`, como `deploy-emporio`, retorna
   uma única linha JSON canônica e exit 0;
7. execução com Python explícito não pode mascarar a prova direta;
8. adulteração de modo ou bytes continua detectada;
9. nenhum acesso à VPS/GitHub ocorre nos testes locais.

Repetir a prova isolada Python 3.10/linux-amd64, rede bloqueada, incluindo a
invocação direta pelo shebang como usuário não root compatível.

Não alterar workflow, runtime do control plane, release comercial, Compose,
adapter, schemas comerciais, migrations ou state machine.

## 4. Gates, commit e remoto

Revalidar primeiro que:

- as duas operações anteriores permanecem `FAILED`, slots livres e outcomes
  confirmados;
- readiness é 200, current é 404 e `v0.1.1` continua elegível;
- zero recurso comercial, backup, migration, incoming, snapshot e listener
  8120;
- control root íntegro em `e436190…`;
- Git local/remoto sincronizado e stage vazio.

Executar testes causais, `release_control/tests`, oito suítes canônicas, todos
os validadores vigentes, `catalog:valid`, secret scan completo e staged com
`unsupported=0`, e `git diff --check`.

Somente tudo verde:

1. criar commit técnico normal;
2. push fast-forward, sem amend/rebase/force;
3. exigir CI e Publish Candidate verdes no mesmo SHA;
4. não publicar imagem do release control, tag, GitHub Release, candidato
   comercial substituto ou `v0.1.2`.

Até dois ciclos técnicos pré-POST são permitidos se um gate local/CI revelar
defeito estritamente nessa fronteira. Cada ciclo exige teste causal, matriz e
push normal. Nenhuma imagem comercial ou do control plane deve ser publicada.

## 5. Rotação e prova real do control root

Do SHA técnico terminal:

1. materializar builder/lock diretamente do objeto Git;
2. construir duas vezes e exigir archive/sidecar byte-idênticos;
3. exigir manifesto com o conjunto executável exato da seção 3;
4. executar prova isolada completa;
5. rotacionar transacionalmente o root `e436190…` para o SHA terminal, com
   rollback automático armado;
6. executar verify e mutantes Draft 2020-12;
7. provar no host real, como `deploy-emporio`, a invocação direta exata:

```text
/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
```

Exigir exit 0 e `controlSha` igual ao commit terminal. Também provar modo
`0755`, owner dedicado, arquivo regular, shebang válido e ausência de
temporários/staging. Só então remover o backup anterior pelo manifesto exato.

Não usar `chmod` pós-instalação, Python explícito como substituto da prova,
symlink, wrapper, alteração de SSH ou edição manual do manifesto.

## 6. Retomada comercial

Depois da seção 5, repetir capacidade, preservação diferencial, control plane,
release, current, plano e zero resíduos. Preparar novo bootstrap curto e nova
idempotency key.

Enviar um POST para `v0.1.1`, criando uma nova operação e um novo run. As duas
operações anteriores permanecem históricas e intocadas:

```text
dep_6bd76dcff84a42ba88705b5448aa5c3c  FAILED / WORKFLOW_PRE_DEPLOY_FAILED
dep_5c95bf53ba994cdc872a3d711b7c133b  FAILED / REMOTE_CAPABILITY_MISMATCH
```

No caminho nominal, exigir um run verde no SHA técnico terminal:

```text
trust -> prepare -> deploy -> outcome
```

Concluir integralmente backup, migrations, sete serviços, digests, HTTPS,
JWKS, login/UI, remoção do bootstrap temporário e desativação do bootstrap root.

### 6.1 Continuação causal limitada antes da mutação comercial

Para evitar nova microcorreção administrativa, fica autorizado no máximo **um**
ciclo adicional dentro desta correction se a nova operação terminar:

- `CONFIRMED/FAILED` e `databaseRestoreRequired=false`;
- antes de upload/install/execute do bundle comercial;
- com zero container, volume, rede, backup, migration, current e listener 8120;
- sem incoming/snapshot/staging residual após cleanup dirigido;
- com operação terminal, slot livre, readiness 200 e evidência canônica.

Nesse caso estrito, o executor pode corrigir apenas a nova causa no
builder/helper/transporte, executar matriz, commit/push normal, rotacionar o
control root para o novo SHA e criar **uma última operação** com nova chave.

Se houver `INDETERMINATE`, upload iniciado, bundle instalado, execute iniciado,
recurso comercial, backup, migration, restore requerido, cleanup incompleto ou
incerteza de fronteira, parar imediatamente. Não usar essa autorização.

Assim, correction-03 permite no máximo duas novas operações: a nominal e uma
última somente diante de falha confirmada anterior a qualquer mutação
comercial. Não autoriza rerun nem reutilização de operation/key.

## 7. Estado terminal esperado

Em sucesso nominal:

```text
runs de deploy             3 no total: 2 falhos pré-mudança + 1 verde
operações                  3, todas preservadas; somente a última SUCCEEDED
execuções comerciais       exatamente 1
v0.1.1                     current, reconciled=true
containers comerciais      7/7 healthy
backup/migrations          comprovados
HTTPS/JWKS/UI              verdes; bootstrap removido/desativado
rollback/restore           0/0
control plane              ready 200, sync sem drift
outros sistemas            preservação diferencial comprovada
```

Se a continuação limitada da seção 6.1 for usada, admitir quatro runs/operações
no total, sendo três falhas confirmadas e anteriores à mutação comercial, e
exatamente uma execução comercial `SUCCEEDED`. Registrar a justificativa
integralmente.

## 8. Autorização cumulativa

A delegação deve conter literalmente:

```text
Autorizo integralmente a correction-03 da S46 a corrigir o pacote do control root para instalar ops/deploy/deployment-remote.py como executável 0755, versionar a correção, validar CI e Publish Candidate, rotacionar transacionalmente o control root para o SHA técnico terminal e provar a invocação direta de capabilities como deploy-emporio. Com as duas operações anteriores preservadas como FAILED, readiness 200, v0.1.1 elegível e zero efeito comercial, autorizo uma nova operação para concluir o primeiro deploy de v0.1.1. Se, e somente se, essa operação falhar CONFIRMED antes de upload, install ou execute, sem qualquer recurso, backup, migration, current ou resíduo remoto, autorizo um único ciclo causal adicional e uma última operação. Não autorizo rerun, reutilização de chave, alteração manual das operações históricas, nova release, rollback, restore, intervenção em outro tenant, reboot, update ou continuação depois de qualquer mutação comercial incerta.
```

Não pedir dado, credencial, ID, release, digest ou janela adicional ao usuário.

## 9. Relatório e terminal

Não criar relatório novo. Acrescentar `Retomada correction-03` ao relatório
contínuo da S46, mantendo-o não rastreado, fora do stage e sem segredo.

Registrar patch, testes, commits/runs, pacote/modos, rotação, prova direta,
intenção/run terminal, execução comercial, backup, migrations, serviços,
HTTPS/JWKS/UI, negativos, cleanup, preservação diferencial e secret scan.

O executor não aceita S46 e não cria S47.

Em sucesso, terminar exatamente:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S46 correction-03 interrompida fail-closed na primeira causa técnica
```

## 10. Critérios de aceite

A S46 somente será aceita com helper diretamente executável e íntegro, todas as
falhas históricas preservadas, exatamente uma execução comercial `SUCCEEDED`,
`v0.1.1` corrente, backup/migrations/serviços/HTTPS/JWKS/UI comprovados, zero
rollback/restore e preservação diferencial do host.
