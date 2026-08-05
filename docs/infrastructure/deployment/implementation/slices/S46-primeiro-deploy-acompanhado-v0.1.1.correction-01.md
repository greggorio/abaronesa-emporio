# S46 — correction-01: control root terminal e preservação diferencial do host

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED`
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`
> **Release alvo:** `v0.1.1`

## 1. Veredito sobre a execução original

A interrupção foi correta e segura. A execução original terminou antes de
qualquer mutação, POST, operação, dispatch, deploy ou rollback. O relatório é
aceito como evidência do checkpoint, mas **S46 não está aceita**.

Foram confirmadas duas causas distintas:

1. `boutique-instagram-service`, recurso alheio ao Empório, já estava em loop
   contínuo de restart. Exigir que todo tenant preexistente estivesse saudável
   transformou uma anomalia externa em bloqueio absoluto do Empório;
2. o control root instalado ainda representa `9731954d…`, enquanto o workflow
   terminal de `v0.1.1` executa `cf3385f…`. O transporte recusaria corretamente
   essa divergência como `REMOTE_CAPABILITY_MISMATCH` antes de tocar a stack.

O primeiro item é um defeito de critério da task. O segundo é uma atualização
operacional causal, previsível e reversível. Ambos permanecem dentro da S46;
não criar micro-slice.

## 2. Checkpoint aceito

Preservar sem repetir sem causa:

```text
task SHA-256           531bb870cc927e31d4a9629508cea262d7fb50545aaa4457a85da43fccb1aebe
report SHA-256         918df3e700914e6aaa69f3b8ede03c0ca9cdfc3775a28a2b4a48ee05a9f77f41
remote main/release    cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
CI                     30960751303, success, 13/13
Publish Candidate      30961397124, success, 11/11
Control image          30961863663, success, 4/4
Publish Release        30962554318, success, 4/4
v0.1.1                 release 365219520, íntegra e elegível
deploy/rollback        0/0
control plane          live/ready 200/200, zero operação
stack comercial        ausente
capacidade             RAM, swap, disco e load acima dos gates
```

Na revalidação do orquestrador, ainda em modo leitura:

```text
boutique-instagram-service  running, health=starting, RestartCount=854582
RAM available              3195 MiB
swap livre                 8190 MiB de 8191 MiB
load                       2.07 / 2.70 / 2.49
control root instalado     9731954d474fb68ec1384a525e1075f9a5542e24
control plane              live=200, ready=200
deploy/rollback            0/0
```

Esses valores são snapshot, não novos pins. Revalidar o estado material antes
de agir.

## 3. Correção do gate de preservação

A seção 5 da task principal passa a ser interpretada como **preservação
diferencial**, não como obrigação de reparar ou esperar a saúde perfeita de
todos os sistemas hospedados.

Antes do primeiro ato mutante, capturar novamente o baseline nominal de todos
os containers alheios com ID, state, health, `StartedAt`, `RestartCount` e
`OOMKilled`, além dos endpoints externos já amostrados. Classificar
explicitamente como preexistentes, quando ainda presentes:

- o loop de `boutique-instagram-service`;
- containers long-lived que já constavam `unhealthy`;
- endpoint externo que já constava `502`;
- qualquer outra anomalia que já esteja provada no relatório original.

Esses estados **não bloqueiam o Empório por si sós** e não serão reparados,
investigados por logs ou usados como alvo de comando. O executor não recebe
autoridade sobre `boutique` nem qualquer outro tenant.

Continuam sendo gates duros antes do POST:

- control plane Empório diferente de healthy ou live/ready diferente de
  `200/200`;
- RAM available abaixo de 2 GiB nas três amostras, disco livre abaixo de
  40 GiB, swap dedicado ausente, `swappiness != 10` ou load de 5 minutos acima
  de 4 de forma sustentada;
- OOM novo no host, pressão material nova ou perda de um tenant que estava
  saudável no baseline desta retomada;
- recurso comercial, operação ativa ou run de deploy/rollback inesperado;
- `v0.1.1` não elegível como primeira instalação.

Durante e depois do deploy, provar diferencialmente que:

1. nenhum container, volume, rede, imagem, arquivo, unidade ou configuração
   pertencente a outro sistema foi alterado pelo Empório;
2. tenants saudáveis no baseline da retomada preservaram disponibilidade;
3. anomalias preexistentes não foram tratadas como produto da S46 nem
   mascaradas como saudáveis;
4. o loop já existente pode aumentar seu contador autonomamente sem bloquear,
   desde que não haja OOM, degradação material de capacidade ou evidência de
   ação do Empório sobre ele;
5. control plane e limites de capacidade permanecem dentro dos thresholds.

Uma mudança autônoma de estado externo deve ser registrada e avaliada pela
diferença. Só interrompe a S46 se houver degradação material nova ou se não for
possível distinguir com segurança o impacto do Empório.

## 4. Atualização transacional do control root

Executar esta seção **antes** de preparar JWKS, JWT, idempotency key ou POST.
Nenhum workflow pode estar ativo durante a troca.

### 4.1 Reconstrução confiável

Reaplicar o procedimento aceito nas S42/S43, agora com identidade fixa:

```text
repository     greggorio/abaronesa-emporio
sourceSha      cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
target         /opt/sistemas/emporio/shared/control
platform       linux/amd64
python ABI     CPython 3.10
```

Obrigatório:

1. materializar builder, lock e allowlist diretamente do objeto Git
   `cf3385f…`, nunca de `HEAD`, branch móvel ou working tree;
2. baixar apenas os wheels exatos do lock, verificar nomes/hashes e não aceitar
   sdist ou dependência adicional;
3. construir o pacote duas vezes em temporários separados e exigir igualdade
   byte a byte;
4. validar manifesto, sidecars, conjunto fechado, modos e `sourceSha`;
5. executar a prova isolada Python 3.10/linux-amd64 com rede bloqueada e Draft
   2020-12 antes da transferência;
6. registrar o novo digest do archive. Não exigir o digest histórico da S43,
   pois o conteúdo allowlisted mudou legitimamente depois de `9731954…`.

Não há mudança de código prevista e, portanto, nenhum commit/push. Se a
reconstrução revelar defeito de produto, parar antes da VPS e reportar; esta
correction não autoriza ciclo corretivo remoto antes do deploy.

### 4.2 Rotação reversível no host

Como o instalador fail-closed exige target vazio, a troca deve ser
transacional e dirigida:

1. revalidar integralmente o control root antigo em `9731954…` e confirmar
   ausência de operação/run/processo de deploy;
2. criar nome irmão aleatório e root-only sob
   `/opt/sistemas/emporio/shared`, provar que não existe e renomear
   atomicamente `control` para esse backup;
3. recriar somente o diretório canônico `control`, real, vazio, `0700`, owner
   `deploy-emporio:deploy-emporio`;
4. transferir o instalador, archive e sidecar novos por temporário root-only e
   executar uma única instalação canônica;
5. executar `verify`, imports vendorizados, mutantes Draft 2020-12 e
   `deployment-remote.py capabilities` como `deploy-emporio`;
6. exigir `controlSha=cf3385f1012b9661ddbc2e83d5241aaa8633f8fd` e a forma exata das
   capabilities;
7. somente depois de todas as provas, remover o backup antigo pelo conjunto
   exato de seu manifesto e diretórios vazios, sem glob nem remoção recursiva
   ampla;
8. remover somente os temporários conhecidos e revalidar ausência de staging,
   processos e efeitos Docker.

Se qualquer passo depois do rename falhar:

1. não avançar para JWKS/POST;
2. remover apenas a nova árvore se seu conjunto estiver integralmente
   explicado pelo manifesto confiável;
3. restaurar atomicamente o backup antigo para `control`;
4. verificar novamente `9731954…` e suas capabilities;
5. preservar evidência e encerrar `BLOCKED`.

É proibido sobrescrever o target, usar symlink, `rm -rf`, alterar Python global,
parar o control plane ou iniciar recursos comerciais durante essa rotação.

## 5. Retomada do primeiro deploy

Depois de o novo control root fechar verde, retomar a task principal a partir
do gate anterior ao JWKS. Todo o contrato original continua vigente, com duas
únicas substituições:

1. preservação dos tenants usa a regra diferencial da seção 3;
2. o control root esperado passa a ser `cf3385f…`, conforme a seção 4.

Executar então a cadeia integral já autorizada:

```text
bootstrap JWKS/JWT -> um POST -> uma operação -> um dispatch
-> trust -> prepare -> deploy -> outcome
-> backup -> migrations -> sete serviços
-> HTTPS/JWKS/UI -> desativação do bootstrap root
```

Não reutilizar idempotency key ou operation ID da tentativa anterior: nenhum
deles foi criado. Gerar a única key da S46 somente imediatamente antes do POST.

Continuam proibidos segundo deploy, nova release, rerun manual, rollback,
restore, edição de banco/journal/state machine, reparo de tenant, reboot,
update, prune ou contorno manual do adapter.

## 6. Autorização cumulativa

A delegação deve conter literalmente:

```text
Autorizo integralmente a correction-01 da S46 a substituir de forma transacional e reversível o control root 9731954d pelo pacote determinístico reconstruído do SHA remoto cf3385f1012b9661ddbc2e83d5241aaa8633f8fd, validar suas capabilities e então retomar uma única primeira instalação de v0.1.1 conforme a task S46. Autorizo tratar a saúde dos sistemas alheios por preservação diferencial: anomalias comprovadamente preexistentes, inclusive o loop de boutique-instagram-service, não bloqueiam o Empório, mas não podem ser investigadas, alteradas ou agravadas. Permanecem autorizados o bootstrap inaugural, um POST, uma operação, um dispatch, transporte SSH, backup, migrations, sete serviços, validação HTTPS/JWKS/UI e desativação do bootstrap root. Não autorizo segundo deploy, rollback, restore, nova release, intervenção em outro tenant, reboot, update ou contorno do fluxo canônico.
```

Essa frase complementa e substitui, apenas nos dois pontos acima, a autorização
da seção 2 da task principal. Não pedir ao usuário nenhum dado adicional.

## 7. Relatório e terminal

Não criar outro relatório. Acrescentar uma seção `Retomada correction-01` ao
relatório contínuo da S46. Manter o arquivo não rastreado, fora do stage e sem
material protegido.

Registrar:

- hash desta correction e novo hash inicial/final do relatório;
- baseline diferencial e classificação explícita das anomalias externas;
- dois builds, digest do pacote, rotação, verify/capabilities e cleanup;
- toda a evidência ainda exigida pelas seções 6–12 da task principal;
- comandos sanitizados, exits individuais, negativos e reversões;
- secret scan isolado do relatório com `unsupported=0`.

O executor não aceita S46 e não cria S47.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S46 correction-01 interrompida fail-closed na primeira causa técnica
```

## 8. Critérios de aceite

A S46 somente será aceita com:

- control root terminal íntegro e vinculado a `cf3385f…`;
- uma única operação/run `SUCCEEDED` e `v0.1.1` corrente/reconciliada;
- backup, migrations, sete serviços e digests comprovados;
- HTTPS, JWKS e UI reais, com bootstrap removido;
- zero rollback/restore/segunda intenção;
- nenhum efeito do Empório sobre recursos de outros sistemas;
- anomalias externas preexistentes registradas honestamente, sem bloquear ou
  fabricar sua correção.
