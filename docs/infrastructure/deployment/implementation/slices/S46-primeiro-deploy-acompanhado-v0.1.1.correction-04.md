# S46 — correction-04: reconciliação SSH, recuperação da readiness e deploy final

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED`
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Correções anteriores:** `correction-01`, `correction-02` e `correction-03`
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`
> **Release comercial alvo:** `v0.1.1`, imutável

## 1. Veredito e checkpoint aceito

O relatório com SHA-256
`0627e2e23ba7fea107d516c6e853526230fd5bebefe17777ac31a17bc1c6b14c` é
aceito como checkpoint factual da correction-03. A execução cumpriu a fronteira
fail-closed, restaurou o Nginx original e removeu integralmente o bootstrap.
**S46 continua não aceita.**

O orquestrador revalidou independentemente:

```text
HEAD/origin/main             69621c275a8da9cb46db05b7fe6497f33e81e117
CI                           30991511443, success, 13/13
Publish Candidate            30992386795, success, 11/11
operação correction-03       dep_49980b00ad5d443eb32321efa29e6621
run                          30993832964, attempt 1, failure
trust/prepare                success/success
deploy/outcome               failure/failure
outcome                      CONFIRMED / FAILED
errorCode                    REMOTE_CAPABILITY_MISMATCH
databaseRestoreRequired      false
snapshot/upload/install      não iniciados
recursos/backup/migration    0/0/0
rollback/restore             0/0
control root direto          íntegro, helper 0755, capabilities exit 0
SSH no runner                conexão encerrada em autenticação publickey
bootstrap/Nginx temporário   removido/original restaurado
Git/stage                    sincronizado/vazio
```

As quatro evidências do run vinculam operação, release, run, attempt e SHA. A
terceira operação está terminal e o slot ativo está livre. Os três runs de
deploy permanecem falhos e anteriores a qualquer mutação comercial.

## 2. Causas causais e fronteira desta correção

Há duas causas independentes e recuperáveis.

### 2.1 Identidade SSH não reconciliada

A VPS possui uma única chave autorizada para `deploy-emporio`, com permissões e
owner corretos, e o `sshd` aceita public key. A fingerprint instalada coincide
com a registrada na S41. Porém o secret
`production/PRODUCTION_SSH_PRIVATE_KEY` é opaco e não pode ser relido para
comprovar que ainda contém a chave privada correspondente. O runner falhou na
autenticação enquanto a invocação local do mesmo helper, como
`deploy-emporio`, passou.

Não diagnosticar por nova operação comercial. A identidade deve ser rotacionada
e provada primeiro por um workflow sem inputs que execute somente
`capabilities`.

### 2.2 Readiness residual após outcome terminal válido

Durante a reconciliação da terceira operação, uma observação transitória marcou
o singleton `rc_current_installation` como incerto com
`WORKFLOW_RUN_BINDING_INVALID`. Depois, o outcome imutável válido reconciliou a
operação como `FAILED`, transporte `CONFIRMED`, restore false e slot livre, mas
o marcador vazio não foi removido. O resultado é:

```text
operation dep_49980…         FAILED / CONFIRMED / REMOTE_CAPABILITY_MISMATCH
current commercial fields   todos nulos
current last_operation_id   dep_49980…
current uncertainty_code    WORKFLOW_RUN_BINDING_INVALID
/health/live                 200
/health/ready                503
```

Isso é um defeito de produto: um outcome terminal confirmado e sem restore não
deve deixar um `current` vazio preso em incerteza. Não corrigir com SQL, DELETE
manual, alteração retroativa de operação, fabricação de journal ou bypass da
readiness.

## 3. Recuperação automática do current vazio

Implementar uma recuperação estrita e idempotente, compartilhando os
validadores canônicos do reconciliador.

### 3.1 Correção prospectiva

Ao aplicar outcome `CONFIRMED/FAILED` com
`databaseRestoreRequired=false`, remover transacionalmente o singleton somente
se ele:

1. aponta para a mesma operação;
2. não possui release, source SHA, instalação, estado comercial, backup,
   restore ou qualquer outro campo material;
3. contém apenas o marcador de incerteza produzido durante a reconciliação;
4. a operação está ligada a um run válido, terminal e ao mesmo outcome;
5. o artifact/outcome validado confirma `FAILED`, restore false e os mesmos
   identificadores/digests.

Persistir uma única entrada de auditoria explícita, por exemplo
`deployment.current_recovered`, na mesma transação. Reaplicar o mesmo outcome
ou reiniciar não pode duplicar audit, journal ou mutação.

### 3.2 Recuperação do estado já persistido

No startup/sync, detectar o caso exato acima. Antes de remover o singleton:

- reconsultar o run imutável originalmente vinculado;
- validar repository, workflow path, run/attempt, head SHA, actor, conclusão e
  vínculo com a operação;
- baixar e validar o outcome original pelos hashes/sidecars canônicos;
- exigir igualdade com o outcome persistido e restore false;
- exigir operação terminal, slot livre e ausência de qualquer estado comercial.

Se qualquer evidência estiver ausente, ambígua, expirada, divergente ou
`INDETERMINATE`, manter readiness 503 e parar. A recuperação não se aplica a
rollback, sucesso, restore requerido, current comercial existente ou marcador
de outra operação.

Testes causais devem cobrir, no mínimo: caminho prospectivo; recuperação no
restart; idempotência; audit único; mismatch de cada vínculo; artifact ausente
ou adulterado; run não terminal; transporte indeterminado; restore true;
current parcial/comercial; operação diferente; rollback; concorrência; falha
transacional; e preservação integral das três operações históricas.

## 4. Workflow de prova do transporte

Adicionar `.github/workflows/verify-production-transport.yml`, persistente e
inventariado, com estas propriedades:

- `workflow_dispatch` sem inputs;
- execução somente no default branch e SHA remoto terminal;
- trust fail-closed pela allowlist `DEPLOYER_ACTOR_IDS`, repository, ref, SHA,
  actor e triggering actor;
- `environment: production` somente no job que usa os materiais SSH;
- permissões mínimas; nenhum `packages: write`, deploy, release ou rollback;
- concurrency do ambiente de produção com `cancel-in-progress: false`;
- checkout com `persist-credentials: false`;
- host, port, private key e known_hosts somente do environment;
- opções SSH estritas equivalentes às do transporte de deploy;
- único comando remoto permitido:

```text
/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
```

- proibição estrutural de inputs, comando, host, usuário, path ou arguments
  fornecidos pelo dispatch;
- validação exata de protocol, deploy root, usuário e `controlSha` terminal;
- artifact canônico `production-transport-probe` e sidecar/outcome seguros,
  vinculados a repo, workflow, run, attempt, SHA, actor, controlSha e status;
- cleanup `always()` da chave materializada e logout/ausência de credenciais;
- nenhum snapshot, upload, install, execute, Docker pull/run, migration, banco,
  Nginx ou recurso comercial.

Criar validador e testes causais que rejeitem inputs, comando arbitrário,
permissão ampliada, falta de environment/concurrency, ordem insegura, SSH sem
host checking, outro helper/subcomando, ausência de cleanup ou artifact sem
vínculo. Atualizar os espelhos de inventário e documentação do conjunto de
workflows como consequência mecânica declarada.

## 5. Gates, commits e publicação técnica

Antes de mutar:

- confirmar as três operações/runs históricas, seus artifacts e zero efeito
  comercial;
- confirmar current vazio exatamente como na seção 2.2, slot livre e readiness
  503 pela causa registrada;
- confirmar helper/control root íntegros no SHA `69621c2…`;
- confirmar Git sincronizado, stage vazio, release `v0.1.1` imutável e zero
  rollback/restore.

Implementar as seções 3 e 4. Executar testes causais, `release_control/tests`,
oito suítes canônicas, todos os validadores vigentes e novos, `catalog:valid`,
secret scan completo e staged com `unsupported=0`, e `git diff --check`.

Somente tudo verde:

1. criar commit técnico normal e push fast-forward;
2. exigir CI e Publish Candidate verdes no mesmo SHA;
3. publicar uma única nova imagem do release control pelo workflow canônico,
   validar scan, manifesto, sidecar, outcome e digest;
4. reconstruir deterministicamente e rotacionar o control root para o SHA
   terminal, com rollback automático armado e capabilities direto verde;
5. atualizar somente o digest da imagem do control plane na VPS, executar
   migration normal, recriar/reiniciar apenas seus serviços e provar sync;
6. exigir a recuperação automática da seção 3: readiness 200, current 404,
   audit único e operações históricas byte/semanticamente preservadas.

São permitidos até dois ciclos técnicos antes da rotação SSH, sempre com commit
normal, matriz e remoto verdes. Não publicar nova release comercial, alterar
manifesto/BOM de `v0.1.1` ou tocar nos sete serviços comerciais nessa fase.

## 6. Rotação da identidade e prova pelo runner

Gerar uma chave Ed25519 nova, sem passphrase, em diretório temporário local
`0700`. Não pedir chave, fingerprint ou secret ao usuário.

Executar em ordem:

1. obter baseline do `authorized_keys`, permissions, owner, fingerprint atual,
   environment e contagens remotas;
2. pela rota bootstrap root já autorizada, acrescentar a nova chave pública
   como segunda linha exata, sem remover a anterior;
3. provar localmente a chave nova com SSH estrito e o comando único de
   capabilities;
4. atualizar `production/PRODUCTION_SSH_PRIVATE_KEY` por stdin com a privada
   correspondente; preservar host, port e known_hosts se continuarem válidos;
5. disparar uma vez o workflow de prova via identidade App/allowlist canônica;
6. exigir trust/probe/outcome verdes e validar artifact/sidecar;
7. somente depois, remover da VPS exatamente a chave pública antiga,
   permanecendo uma única linha com a nova fingerprint;
8. disparar novamente o mesmo workflow para provar o estado final de uma chave;
9. exigir segundo run verde e identidade/contagens corretas;
10. destruir com `shred -u` privada, pública e temporários locais; provar que a
    chave não entrou em argv, logs, worktree, stage, ssh-agent ou outro secret.

Os dois runs do probe são intencionais: o primeiro prova a chave nova antes da
remoção recuperável da antiga; o segundo prova que o environment funciona após
o fechamento final. Se o primeiro falhar, preservar ambas as chaves, não fazer
POST comercial e encerrar. Se o segundo falhar, restaurar o secret para uma
nova chave comprovável ou manter ambas durante diagnóstico, sem POST; nunca
deixar o acesso dedicado sem uma chave localmente provada.

Não alterar `sshd_config`, sudoers, grupos, firewall, packages ou host keys.

## 7. Última retomada comercial

Somente após:

```text
control plane readiness     200
current                     404
v0.1.1                      elegível
controlSha                  SHA técnico terminal
SSH probe final             success com uma única chave instalada
recursos comerciais         0
backup/migrations           0/0
porta 8120                  livre
operações históricas        3 FAILED, preservadas, slots livres
```

criar novo bootstrap curto, nova idempotency key e enviar um POST para
`v0.1.1`. Não reutilizar qualquer key/operação anterior. Acompanhar o novo run
completo:

```text
trust -> prepare -> deploy -> outcome
```

Exigir uma única execução comercial `SUCCEEDED`, incluindo snapshot/backup dos
bancos vazios, upload/install/execute, migrations, sete serviços por digest,
health, HTTPS, JWKS, autenticação e UI real. Remover o bootstrap temporário e
desativar a rota root conforme a task principal.

### 7.1 Continuação causal final e estritamente pré-mutação

Fica autorizado no máximo um último ciclo adicional somente se a nova operação
terminar `CONFIRMED/FAILED`, restore false, antes de snapshot/upload/install ou
execute, com zero recurso, backup, migration, current, incoming e resíduo. A
causa deve ser nova, causalmente identificada e coberta por teste; repetir
matriz, commit/push, imagem/control root/probe conforme o componente alterado e
usar outra operação/key.

Não usar esta continuação se houver `INDETERMINATE`, falha de SSH já coberta,
upload iniciado, snapshot/backup criado, install/execute iniciado, recurso
comercial, restore requerido, readiness incerta ou cleanup incompleto. Nesses
casos, parar imediatamente.

Não autoriza rerun, replay de operação terminal, alteração manual das quatro
operações, rollback, restore, nova release, intervenção em outro tenant,
reboot, update ou contorno do fluxo canônico.

## 8. Estado terminal esperado

No caminho nominal:

```text
runs/operações de deploy    4: 3 FAILED pré-mudança + 1 SUCCEEDED
execuções comerciais        exatamente 1
v0.1.1                      current, reconciled=true
containers comerciais       7/7 healthy, digests da release
backup/migrations           comprovados
HTTPS/JWKS/UI               verdes; bootstrap removido/desativado
SSH dedicado                uma chave nova, runner probe verde
control plane               ready 200, sync sem drift
rollback/restore            0/0
outros sistemas             preservação diferencial comprovada
```

Se a seção 7.1 for legitimamente usada, admitir cinco runs/operações, sendo
quatro falhas confirmadas anteriores à mutação comercial e exatamente uma
execução `SUCCEEDED`.

## 9. Autorização cumulativa

A delegação deve conter literalmente:

```text
Autorizo integralmente a correction-04 da S46 a corrigir a recuperação automática do current vazio após outcome terminal confirmado, criar e publicar o workflow sem inputs que prova exclusivamente o transporte SSH de produção, versionar as correções, validar CI e Publish Candidate, publicar uma nova imagem técnica do release control, rotacionar o control root e atualizar somente o control plane na VPS. Autorizo gerar uma nova chave Ed25519, instalá-la temporariamente ao lado da anterior, atualizar por stdin o secret PRODUCTION_SSH_PRIVATE_KEY do environment production, executar dois probes pelo runner — antes e depois de remover a chave antiga — e destruir o material local. Com readiness 200, current 404, probe final verde, v0.1.1 elegível e as três falhas históricas preservadas sem efeito comercial, autorizo uma nova operação para concluir o primeiro deploy de v0.1.1. Se, e somente se, ela falhar CONFIRMED antes de snapshot, upload, install ou execute, sem qualquer efeito ou resíduo comercial, autorizo um último ciclo causal e uma última operação. Não autorizo SQL manual, edição retroativa de operação/journal, rerun, reutilização de chave, nova release comercial, rollback, restore, intervenção em outro tenant, reboot, update ou continuação após mutação comercial incerta.
```

Não pedir ao usuário chave, fingerprint, secret, ID, digest, release ou janela.

## 10. Relatório e terminal

Não criar relatório novo. Acrescentar `Retomada correction-04` ao relatório
contínuo da S46, mantendo-o não rastreado, fora do stage e sem segredo.

Registrar diagnóstico, patch, testes, commits/runs, publicação/rotação técnica,
recuperação automática e audit, fingerprints públicas, mutações do environment,
dois probes, cleanup do material, operação/run comercial, backup, migrations,
serviços, HTTPS/JWKS/UI, negativos e preservação diferencial. Incluir comandos,
exits e hashes sem reproduzir private key, JWT, PEM, tokens ou secrets.

O executor não aceita S46 e não cria S47.

Em sucesso, terminar exatamente:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S46 correction-04 interrompida fail-closed na primeira causa técnica
```

## 11. Critérios de aceite

A S46 somente será aceita com identidade SSH final provada pelo runner,
readiness recuperada automaticamente sem edição manual, três falhas históricas
preservadas, exatamente uma execução comercial `SUCCEEDED`, `v0.1.1` corrente,
backup/migrations/serviços/HTTPS/JWKS/UI comprovados, zero rollback/restore e
preservação diferencial do host.
