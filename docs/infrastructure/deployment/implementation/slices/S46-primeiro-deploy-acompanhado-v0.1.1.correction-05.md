# S46 — correction-05: SSH determinístico, ensaio transacional e deploy único

> **Data:** 05/08/2026
> **Estado:** `AUTHORIZED`
> **Natureza:** correção final da S46 após revisão prévia integral do orquestrador
> **Contrato principal:** `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`
> **Correções anteriores:** `correction-01` a `correction-04`
> **Relatório contínuo:** `S46-primeiro-deploy-acompanhado-v0.1.1.report.md`
> **Release comercial:** `v0.1.1`, imutável

## 1. Veredito e checkpoint

O relatório com SHA-256
`5d1ed2dd5806b82ff44687adeba0a8bb78cd4f3aa5fe33f98e0b8f0f25862349` é
aceito como checkpoint factual da correction-04. A execução parou corretamente
no primeiro probe e não criou nova operação de deploy. **S46 não está aceita.**

O checkpoint revalidado é:

```text
HEAD/origin/main             636f09b6484c976dc302559c0e6d8d14dc2947cb
CI                           30999378550, success, 13/13
Publish Candidate            31000195641, success, 11/11
imagem control plane         sha256:41486042…ec255
control root                 636f09b…, íntegro
control plane                live/ready 200/200
current                      404; zero linha
recovery audit               uma, ligada a dep_49980b00…
deploy runs/operações        3/3, todos FAILED e pré-mudança
rollback/restore             0/0
probe SSH                    31001791245, trust verde, probe falho
authorized_keys              2: antiga e nova preservadas
nova fingerprint             SHA256:37cF/VNa2khhTxoL7x7zk3BPH+Xlww7uMhDrAcD3JPw
recursos/backup/migration    0/0/0
Git/stage                    sincronizado/vazio
```

O material privado novo permanece local, `0600`, fora do repositório e do
agent, exclusivamente para fechar a rotação. Não gerar uma terceira chave sem
prova de comprometimento ou corrupção da atual.

## 2. Revisão prévia obrigatória já fechada

Esta correction não delega uma investigação aberta. O orquestrador confrontou
o código, a chave preservada, os workflows, os manifests globais e os caminhos
de deploy/rollback antes de emiti-la.

### 2.1 Causa estrutural do probe

A chave nova preservada possui 452 bytes, termina em LF, é parseável pelo
OpenSSH e deriva a fingerprint instalada. Uma cópia que difere somente pela
ausência do LF terminal é rejeitada por `ssh-keygen` com exit 255.

O código atual possui quatro defeitos conhecidos:

1. probe e deploy materializam SSH por implementações diferentes;
2. ambos gravam o valor do environment sem forma canônica de LF;
3. o probe usa chave textual fictícia e `subprocess` mockado nos testes, sem
   executar o parser OpenSSH real;
4. parsing, host-key, conexão, autenticação e capabilities colapsam em
   `SSH_PROBE_FAILED`, pois o stderr é descartado.

Não atribuir a causa a uma alteração não documentada da VPS. A correção é
tornar a representação da credencial e seu diagnóstico determinísticos.

### 2.2 Prova Docker já existente e lacuna exata

O `Publish Candidate` já usa Docker real para:

- puxar as seis imagens por digest e PostgreSQL pinado;
- executar migrations ERP e website;
- subir os sete serviços;
- exigir todos healthy, executar smoke HTTP e integração WhatsApp;
- limpar projeto, volumes, redes, imagens e login.

A lacuna é somente a camada transacional de produção: bundle, backup, journal,
replay, installed-state e links. Ela será exercitada em runner isolado antes do
POST, sem repetir testes comerciais diretamente na VPS.

### 2.3 Fronteira conhecida da S47

Os manifests remotos foram comparados:

```text
v0.1.1.previousRelease       v0.1.0
seis componentes            todos com digest diferente
ERP migration set           idêntico
website migration set       idêntico
restore v0.1.1 -> v0.1.0    não requerido
```

Também foram identificadas previamente as lacunas que a futura S47 terá de
implementar: `rollback-production.yml` ainda é stub; helper/CLI não executam
rollback comercial nem restore; nenhum código popula `rc_rollback_backup`; o
outcome forward não transporta evidência canônica de backup; e a consulta atual
de backup não está vinculada à operação forward que instalou a release corrente.
Nada disso será descoberto por tentativa em produção nem será alterado na S46.

## 3. Uma única implementação SSH compartilhada

Criar `tools/deploy/ssh_material.py` como única autoridade de materialização,
importada por `deployment_transport.py` e `production_transport_probe.py`.
Incluir o módulo no pacote fechado do control root pelo fechamento real de
imports.

### 3.1 Chave privada canônica

Aceitar somente uma chave OpenSSH Ed25519 com:

- armor BEGIN/END exato;
- payload base64 não vazio, ASCII e sem whitespace estranho;
- nenhum NUL, CR, linha vazia interna ou dado antes/depois do armor;
- zero ou um LF depois do END na entrada;
- exatamente um LF terminal no arquivo materializado;
- arquivo regular, criado com `O_EXCL|O_NOFOLLOW`, modo `0600`;
- limite de tamanho vigente.

Mais de um LF terminal, CRLF, outro tipo de chave, armor/payload inválido ou
trailing data falham antes de SSH. Não base64-encodar o secret, criar novo nome
ou alterar seu valor para contornar o runtime.

### 3.2 Parse e fingerprint reais

Depois de materializar:

1. validar `/usr/bin/ssh-keygen` como binário root-owned e não gravável;
2. executar `ssh-keygen -y -f` sem shell, stdin ou agent;
3. exigir chave pública `ssh-ed25519`;
4. derivar a fingerprint SHA-256 pelo próprio `ssh-keygen`;
5. comparar exatamente com a nova variável legível do environment:
   `PRODUCTION_SSH_PUBLIC_KEY_SHA256`;
6. nunca imprimir chave pública, privada, base64 ou stderr bruto.

Configurar essa variável com a fingerprint nova já provada. Probe e deploy
devem exigir a mesma variável e a mesma função, impedindo que o probe passe com
uma materialização diferente da usada comercialmente.

### 3.3 known_hosts e transporte

Preservar o secret existente, normalizando apenas seu LF terminal após validar:

- ASCII, sem NUL/CR;
- entradas não vazias e formato conhecido pelo OpenSSH;
- presença do host/porta contratados;
- arquivo regular `0600` e host checking estrito.

Preservar `IdentitiesOnly`, `IdentityAgent none`, `BatchMode`,
`StrictHostKeyChecking`, senhas/interatividade desligadas, forwardings
desligados, timeout e destino fixo `deploy-emporio`.

### 3.4 Diagnóstico seguro

Capturar stderr somente em memória, com limite, e convertê-lo em um único código
fechado sem registrar o conteúdo:

```text
SSH_KEY_FORMAT_INVALID
SSH_KEY_FINGERPRINT_MISMATCH
SSH_KNOWN_HOSTS_INVALID
SSH_CONNECTION_FAILED
SSH_AUTHENTICATION_FAILED
REMOTE_CAPABILITY_MISMATCH
```

O artifact/outcome do probe registra apenas estágio/código, fingerprint
esperada, bindings e status. Não registrar IP de runner, linhas de chave,
config materializada, paths temporários ou mensagem OpenSSH.

### 3.5 Testes causais

Além dos mutantes existentes, exigir:

- chave Ed25519 real gerada em `mktemp`, parseada por `ssh-keygen` real;
- entrada com e sem LF gerando bytes materializados idênticos;
- CRLF, LF duplo, truncamento, trailing data, RSA e payload inválido rejeitados;
- fingerprint correta/errada/ausente;
- probe e deploy chamando a mesma função e produzindo config byte-idêntica;
- classificação de cada estágio por stderr sintético sem emitir stderr;
- cleanup com shred mesmo em falha de parse/fingerprint/SSH;
- ausência de agent, shell, argv secreto, log secreto e fallback permissivo.

## 4. Ensaio transacional real e isolado

Criar workflow persistente
`.github/workflows/verify-deployment-engine.yml`, manual, sem inputs e sem
`environment: production`.

O workflow deve possuir somente `trust -> rehearse -> outcome`, usar default
branch/SHA terminal, allowlist `DEPLOYER_ACTOR_IDS`, actions pinadas, permissões
`contents: read`, `actions: read` e `packages: read` somente onde necessário,
sem secrets SSH, App PEM, VPS ou host de produção.

### 4.1 Release e ambiente

- fixar `v0.1.1` e validar tag, release ID, três assets, sidecars, manifesto,
  previous release e os seis digests pelos validadores existentes;
- login GHCR com token efêmero do próprio workflow somente após trust;
- gerar em memória/temporário credenciais sintéticas e mascará-las;
- usar porta loopback 8120 em runner limpo e nomes/volumes do projeto isolado;
- materializar root em `mktemp -d` `0700`, `.env` `0600`, sem copiar o env de
  produção;
- manter identidade deployer desabilitada ou usar chave sintética, nunca a PEM
  operacional.

### 4.2 Caminho exercitado

Gerar o bundle canônico diretamente do asset `release.json` e executar o
`deployment_cli.py` real, sem mocks, exercitando:

```text
FIRST_INSTALL -> PULL -> BACKUP -> MIGRATE -> UPDATE -> VERIFY
-> COMMIT_STATE -> SUCCEEDED
```

Exigir:

- journal canônico terminal e todos os passos/transições corretos;
- backup real dos dois bancos vazios, dumps não vazios, hashes e manifesto;
- migrations reais dos dois bancos;
- sete serviços healthy e smoke dos dois hosts;
- `installed-state.json`, `current -> releases/v0.1.1`, previous ausente;
- digests dos seis serviços iguais ao manifesto;
- `databaseRestoreRequired=false`, error nulo;
- replay da mesma operação devolvendo o mesmo journal sem novo backup,
  migration, container ou efeito;
- nenhuma chamada SSH, VPS, runtime deployer, workflow de deploy ou rollback.

### 4.3 Limpeza e evidência

No `always()`:

- `docker compose down -v --remove-orphans` somente no projeto;
- remover imagens puxadas nominalmente e logout GHCR;
- provar zero container, volume e rede do projeto;
- remover root, env, chave sintética e temporários;
- produzir artifacts canônicos `deployment-engine-rehearsal` e
  `deployment-engine-rehearsal-outcome`, com bindings, hashes, passos,
  installed-state, backup receipt e contagens de cleanup, sem dumps/segredos.

Validador e testes devem impedir inputs, environment production, SSH, host,
comando arbitrário, projeto não dirigido, `latest`, cleanup amplo, artifact com
dump ou ausência de replay.

## 5. Gates e publicação técnica

Revalidar checkpoint antes de editar. Implementar somente seções 3 e 4 e seus
espelhos mecânicos de inventário/documentação. Não alterar `release_control`,
imagem/control plane, migrations, release comercial, Compose canônico,
rollback workflow ou serviços da VPS.

Executar testes causais, oito suítes canônicas, todos os validadores vigentes e
novos, `catalog:valid`, lint/typecheck aplicável, secret scan tracked/history e
staged com `unsupported=0`, e `git diff --check`.

Somente tudo verde:

1. commit técnico normal sobre `636f09b…`;
2. push fast-forward, sem amend/rebase/force;
3. CI e Publish Candidate verdes no SHA terminal;
4. reconstruir duas vezes o control root do objeto Git e exigir igualdade;
5. prova isolada Python 3.10/network-blocked;
6. rotação transacional do control root para o SHA terminal;
7. capabilities direto como `deploy-emporio` e verify verdes;
8. zero publicação de imagem do release control ou nova release comercial.

O conjunto ativo terá oito workflows. Atualizar todos os inventários e testes
de cardinalidade de forma explícita.

## 6. Gates remotos anteriores ao POST

Executar, nessa ordem, sem operação de deploy:

1. disparar uma única vez `verify-deployment-engine.yml` via App/allowlist;
2. exigir os três jobs verdes e validar artifacts/sidecars/outcome;
3. provar cleanup zero e que deploy/rollback continuam 3/0;
4. atualizar por stdin `PRODUCTION_SSH_PRIVATE_KEY` com a chave nova preservada
   e criar/atualizar `PRODUCTION_SSH_PUBLIC_KEY_SHA256` com sua fingerprint;
5. manter as duas chaves instaladas e disparar uma vez
   `verify-production-transport.yml`;
6. exigir fingerprint, SSH, capabilities e outcome verdes;
7. remover atomicamente somente a chave antiga;
8. provar uma única chave instalada, a nova;
9. disparar uma segunda e última vez o probe;
10. exigir tudo verde novamente e destruir com `shred -u` a privada/pública
    locais e temporários.

Falha do ensaio ou de qualquer probe encerra antes do POST. Não corrigir e
redisparar dentro desta execução; o diagnóstico novo já deve ser específico.
Preservar acesso recuperável e nunca remover ambas as chaves.

## 7. Único deploy comercial restante

Somente depois de todas as provas abaixo:

```text
engine rehearsal             success, Docker real, cleanup zero
SSH probe com duas chaves    success
SSH probe com uma chave      success
fingerprint runner/host      idêntica
control plane                live/ready 200/200
current                      404
v0.1.1                       elegível
control root                 SHA técnico terminal
operações históricas         exatamente 3 FAILED, slots livres
recursos/backup/migration    0/0/0
porta 8120                   livre
```

preparar bootstrap curto, nova idempotency key e enviar **exatamente um** POST
para `v0.1.1`. Não há autorização para tentativa adicional, rerun ou replay de
operação terminal nesta correction.

Acompanhar `trust -> prepare -> deploy -> outcome` e exigir:

- run/operação `SUCCEEDED`, transporte confirmado e outcome reconciliado;
- snapshot first-install, bundle, backup real, migrations e sete serviços;
- digests da release, installed-state/current e control plane sem drift;
- HTTPS, JWKS, autenticação, UI real e persistências;
- remoção do bootstrap temporário e desativação da rota root;
- uma única execução comercial no histórico;
- outros tenants byte/diferencialmente preservados;
- zero rollback/restore.

Qualquer falha encerra fail-closed com a evidência disponível. Não aplicar patch
pós-POST, não repetir operação e não converter incerteza em sucesso.

## 8. Autorização literal

A delegação deve conter literalmente:

```text
Autorizo integralmente a correction-05 final da S46 a unificar a materialização SSH do probe e do deploy, normalizar de forma canônica a chave Ed25519, validar sua fingerprint com ssh-keygen real, introduzir diagnóstico seguro por estágio e criar o workflow sem inputs de ensaio transacional da v0.1.1 em Docker isolado. Autorizo commit técnico normal, push fast-forward, CI, Publish Candidate, rotação transacional do control root, criação da variável legível PRODUCTION_SSH_PUBLIC_KEY_SHA256, atualização por stdin do secret PRODUCTION_SSH_PRIVATE_KEY, um ensaio remoto isolado e dois probes SSH — antes e depois de remover somente a chave antiga. Somente com todas essas provas verdes, readiness 200, current 404, v0.1.1 elegível e as três operações históricas preservadas, autorizo exatamente uma nova operação para concluir o primeiro deploy comercial de v0.1.1. Não autorizo segunda tentativa, rerun, replay terminal, patch pós-POST, nova release, imagem do control plane, SQL manual, rollback, restore, intervenção em outro tenant, reboot ou update.
```

Não pedir chave, fingerprint, secret, ID, release, digest ou janela ao usuário.

## 9. Relatório e terminal

Não criar relatório novo. Acrescentar `Retomada correction-05` ao relatório
contínuo S46, mantendo-o não rastreado e fora do stage.

Registrar patch, testes, commit/runs, control root, rehearsal, artifacts,
fingerprints, dois probes, rotação/cleanup da chave, intenção comercial, run,
backup, migrations, serviços, HTTPS/JWKS/UI, negativos e preservação
diferencial. Nunca registrar segredo, dump, JWT, PEM, token ou stderr bruto.

O executor não aceita S46 e não cria S47.

Em sucesso, terminar exatamente:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Em qualquer falha:

```text
BLOCKED — S46 correction-05 interrompida fail-closed na primeira causa técnica
```

## 10. Critérios de aceite

A S46 somente será aceita com rehearsal transacional real verde, identidade
SSH final provada pelo mesmo materializador usado no deploy, exatamente quatro
operações de deploy — três falhas pré-mudança e uma `SUCCEEDED` —, uma única
execução comercial, `v0.1.1` corrente/reconciliada, backup, migrations, sete
serviços, HTTPS/JWKS/UI comprovados, zero rollback/restore e preservação do host.
