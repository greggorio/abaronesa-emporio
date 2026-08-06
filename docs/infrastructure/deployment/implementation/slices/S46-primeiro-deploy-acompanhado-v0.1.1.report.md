# S46 — Primeiro deploy acompanhado de v0.1.1

> **Data:** 05/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Contrato vigente:** task S46 + `correction-01` + `correction-02`
> **Estado atual:** correction-02 em execução a partir do bloqueio pré-deploy;
> nenhuma execução comercial ocorreu até este checkpoint

## Resultado executivo da correction-01

A retomada avançou além do bloqueio histórico do boutique, atualizou e validou
o control root em `cf3385f`, instalou o bootstrap inaugural e enviou o único
POST autorizado. A intenção criou a operação
`dep_6bd76dcff84a42ba88705b5448aa5c3c` e o único run
`30981846816`.

O bloqueio terminal não está relacionado ao boutique, à VPS, ao SSH, ao control
root ou à capacidade do host. Ele ocorreu no job `prepare`, antes do job
`deploy` e antes de qualquer acesso SSH comercial. A função
`prepare_handoff()` de `tools/deploy/deployment_transport.py` rejeitou com
`INVALID_DISPATCH` o objeto oficial do próprio run porque o validador exige:

```text
run.name == "Deploy Production"
run.path == ".github/workflows/deploy-production.yml@main"
```

A API oficial retornou para o run real:

```text
run.name = "deploy-production-dep_6bd76dcff84a42ba88705b5448aa5c3c"
run.path = ".github/workflows/deploy-production.yml"
```

Todos os demais bindings do trust estavam corretos: repositório, branch, SHA,
event, actor, operation ID, release, run e attempt. A incompatibilidade foi
reproduzida localmente, em modo read-only, com o artifact `deployment-trust` e a
API oficial: exit 3 e `deployment-transport:INVALID_DISPATCH`.

Consequências observadas:

```text
trust                         success
prepare                       failure
deploy                        skipped
outcome artifact              ausente
workflow                      failure
operação                      QUEUED / dispatch CONFIRMED
transportStatus               INDETERMINATE
errorCode                     DEPLOYMENT_OUTCOME_UNAVAILABLE
stack/backups/migrations      0 / 0 / 0
control plane                 live 200 / ready 503 fail-closed
```

Como a causa apareceu depois do POST aceito, o contrato vigente proíbe corrigir
o workflow nesta execução, fazer rerun, repetir POST, criar segunda operação,
executar rollback ou editar o estado. O bootstrap temporário foi removido e o
Nginx foi restaurado. As evidências detalhadas estão nas seções 11–13; este é o
ponto que requer análise do orquestrador.

## 1. Autoridade, leitura e checkpoint

A autorização literal exigida pela seção 2 foi fornecida na delegação. Task,
relatório predecessor, handoff e tracker foram lidos integralmente antes de
qualquer ação. Integridade e checkpoint inicial:

```text
task S46 SHA-256    531bb870cc927e31d4a9629508cea262d7fb50545aaa4457a85da43fccb1aebe
report S45 SHA-256  cd045f4a4c6c798a6659639e2bf5dbc59cf936444c03280c98f8dadf7b25d90a
HEAD local          3aa0211e8a319872aa42c69de1dc22e2172d7fe0
origin/main/remoto  cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
divergência         ahead 1 / behind 0
stage/diff tracked  vazios
```

Somente os relatórios S39–S45 estavam não rastreados; este relatório tornou-se
o oitavo e permanece fora do stage. O commit local adicional contém o contrato
documental S46; o código que o workflow executará continua sendo o SHA remoto
`cf3385f`.

## 2. Gates externos e identidade deployer

Os quatro runs aceitos foram revalidados no attempt 1:

```text
CI 30960751303                    13/13 success
Publish Candidate 30961397124    11/11 success
Control image 30961863663           4/4 success
Publish Release 30962554318         4/4 success
deploy-production.yml                 0 runs
rollback-production.yml               0 runs
```

A release `v0.1.1`, ID `365219520`, continua estável, não draft e não
prerelease, apontando para `cf3385f`. Seus três assets foram baixados em
temporário, o sidecar conferiu e `global_release.py validate` fechou verde:

```text
release.json SHA-256  6e6ac56089a935c817608a37ab06823e329649a78b7acd6d967c0dfccaecd31e
previousRelease       v0.1.0
deployable             true
componentes            6
BOM/.env refs SHA-256  ba734d26efc12b6c3ff6f33bb4e9b7a4186ca4d1bc608133eb2dd1f758e1cd05
```

A App real foi consultada com JWT App e installation token mantidos somente
em memória:

```text
App                     4487372 / emporio-deployer-1315264421
owner                   greggorio
permissions             actions:write, contents:read, metadata:read
events                  0
installation            151259606; única
repository selection    greggorio/abaronesa-emporio; único
bot/allowlist           313092947 / DEPLOYER_ACTOR_IDS=313092947
```

O environment `production` contém apenas as duas variáveis SSH e os dois
secrets SSH contratados; não possui reviewers ou timer. Nenhum valor protegido
foi aberto.

## 3. Capacidade e baseline da VPS

Três amostras separadas por cinco segundos, antes de qualquer bootstrap:

```text
RAM available bytes   3389984768 / 3428839424 / 3374235648
swap total/used       8589930496 / 1048576 bytes
disco livre bytes     89278177280 / 89278111744 / 89277018112
load 5 min            2.15 / 2.15 / 2.14 em 4 CPUs
memory pressure avg10 0.00 / 0.00 / 0.00
swappiness            10; swapfile ativo; fstab com uma entrada
```

Os limites de 2 GiB, 40 GiB e load sustentado 4 foram respeitados. O host
mantinha 39/41 containers, 27 volumes, 19 redes incluindo as três default e 34
imagens. Porta 8120 estava livre e 8180 escutava exclusivamente em
`127.0.0.1`. Projetos comerciais `emporio` e `compose` possuíam zero
container/volume/rede; `current` e `previous` estavam ausentes; backups
canônicos estavam vazios e em modo `0700`.

O baseline dos 39 containers registrou ID, `StartedAt`, health, limite de
memória e OOM em hash
`7c19ed8599b50f77c4dbaedba91dd128a87a1b1f2138fde4397fb51e15058a1d`;
zero OOM/restarting/dead. Dois healthchecks historicamente degradados
(`community-frontend` e `boutique-collections-api`, iniciados em março/abril)
continuavam unhealthy; `boutique-instagram-service`, reiniciado por atividade
externa durante o preflight, estava `starting` sem failure. A amostra aceita da
S45 permaneceu idêntica: `eventos.abaronesa.net.br=200` e
`erp.smartdataerp.com.br=502`. O tenant em startup será exigido terminal antes
do POST, sem intervenção do executor.

Nginx e control plane estavam active; os dois containers do control plane
healthy; live/ready 200. Banco do control plane:

```text
migration                 0003_commercial_rollback
sync deployments/releases drift=false; error=-; last success presente
releases                  v0.1.0/v0.1.1 PUBLISHED, 6 componentes
eligibility               v0.1.1=true; v0.1.0=false
current                   0
deployment operations     0
deployment idempotency    0
rollback backups          0
```

Metadados protegidos, sem abrir conteúdo:

```text
.env comercial             deploy-emporio:deploy-emporio 0600
chave RS256                10001:10001 0400
fingerprint pública RS256  f14283370d5eff78346b314ed5a1e2235d819f7aca5ed123fb93651b20e62bf7
PEM App                    root:emporio-release-control 0640
fingerprint pública App    6cec7c21cb17abf227c253a3bab0a541e9023c105f4f4eb1abc6003b9279358a
operator bootstrap local   gregorio:gregorio 0600
Nginx config SHA-256       43cdaa4b962678061444f6fa78028a1094b9d65ba161ada84dbbbb2a1d34e7d3
Nginx master PID           1514305
```

## 4. Divergência causal pré-POST

O helper instalado respondeu:

```text
controlSha 9731954d474fb68ec1384a525e1075f9a5542e24
```

O workflow autorizado executará `cf3385f1012b9661ddbc2e83d5241aaa8633f8fd`
e `OpenSshTransport.capabilities()` exige igualdade literal antes de upload,
snapshot ou mutação. Assim o transporte falharia fechado com
`REMOTE_CAPABILITY_MISMATCH`. Nenhum JWT, idempotency key, POST, operação ou
dispatch havia sido criado quando a causa foi encontrada.

A correção será estritamente pré-POST: reconstruir deterministicamente o
control root do SHA remoto já aprovado, provar matriz e gates, substituir a
árvore de modo dirigido e reversível e exigir verify/capabilities no SHA
terminal antes de preparar o bootstrap inaugural.

## 5. Parada fail-closed no gate de preservação do host

Antes de qualquer mutação, a repetição do estado do tenant alheio
`boutique-instagram-service` mostrou que ele não estava apenas atravessando um
healthcheck inicial. Seu `StartedAt` mudou sucessivamente:

```text
2026-08-05T05:44:55.418217452Z
2026-08-05T05:47:53.351107922Z
2026-08-05T05:48:20.111020928Z
```

O inspect terminal registrou `status=running`, `health=starting`, zero OOM e
`RestartCount=854516`. Os eventos read-only dos vinte minutos anteriores
mostraram ciclos repetidos `die -> start` aproximadamente a cada treze
segundos, intercalados com healthchecks que encerravam sem sucesso.

Essa é a primeira causa comprovadamente fora da autoridade: a seção 5 exige
parar antes do POST quando um tenant baseline não estiver saudável, e a S46
proíbe parar, matar, reparar ou alterar sistema alheio para abrir capacidade ou
janela. O executor não inspecionou logs nem modificou o tenant.

A divergência do control root é causal e reparável dentro da fronteira
pré-POST, porém a atualização não foi iniciada porque o gate anterior de
preservação do host já exigia parada. Ela deverá ser retomada, sem novo POST,
somente depois de o orquestrador confirmar um baseline de tenant estável.

## 6. Negativos e estado terminal desta tentativa

```text
JWKS bootstrap criado/instalado        0
JWT/idempotency material criado        0
POST autenticado                       0
deployment operations/idempotency      0 / 0
deploy workflow runs                   0
rollback workflow runs                 0
stack comercial                        0 containers / 0 volumes / 0 redes
porta 8120                             livre
bootstrap S46 sob Nginx/webroot         0 arquivos
control root                           preservado, ainda no SHA 9731954...
```

Não houve pull, migration, backup, start, reload Nginx, alteração de env,
dispatch, deploy, rollback, restore, update ou reboot. A S46 não foi aceita e
a S47 não foi criada.

O secret scan aplicado exclusivamente ao relatório retornou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`.

> Registro histórico da tentativa original, superado pela autorização da
> correction-01: `BLOCKED — S46 interrompida fail-closed na primeira causa
> técnica`.

## Retomada correction-01

> **Data:** 05/08/2026
> **Autoridade cumulativa:** task S46 e correction-01
> **Correction SHA-256:** `633c98278eb192944b0a511cf34685e71412d9cfddecab0d8a2fe3d400e72d9a`
> **Relatório inicial SHA-256:** `918df3e700914e6aaa69f3b8ede03c0ca9cdfc3775a28a2b4a48ee05a9f77f41`

### 7. Novo checkpoint e baseline diferencial

O checkout foi revalidado em
`c747ddcd5f0ff491632f731da630c5f8c4603085`, exatamente dois commits
documentais à frente de `origin/main`/remoto
`cf3385f1012b9661ddbc2e83d5241aaa8633f8fd`, com stage e diff tracked vazios.
Nenhum commit, push, amend ou rebase foi executado.

Os gates aceitos, release, App, allowlist, control plane, BOM e zero runs foram
preservados. Três novas amostras materiais fecharam:

```text
RAM available bytes   3265744896 / 3266048000 / 3560751104
swap used bytes       1048576 / 1048576 / 1048576
disco livre bytes     89250816000 / 89250803712 / 89248354304
load 5 min            2.37 / 2.33 / 2.38 em 4 CPUs
memory pressure avg10 0.00 / 0.00 / 1.25
swap/swappiness       ativo / 10
Docker                39/41 containers; 27 volumes; 19 redes; 34 imagens
stack/porta 8120      0 recursos / livre
control plane         2 healthy; live/ready 200/200; sync sem drift
current/op/key/rbk     0 / 0 / 0 / 0
eligibility           v0.1.1=true; v0.1.0=false
```

O baseline sanitizado dos 37 containers alheios ao Empório/control plane
registrou ID, nome, project, state, health, `StartedAt`, `RestartCount` e OOM,
hash
`574d2d7965cbd35098a6777b4f6a5b2de41ee45fcabd2cce1aae09ee93e5b762`.
Foram classificados como preexistentes e não bloqueantes:

```text
community-frontend             unhealthy, long-lived, restart 0
boutique-collections-api       unhealthy, long-lived, restart 0
boutique-instagram-service     starting, loop autônomo já documentado
erp.smartdataerp.com.br        502 já documentado
```

`eventos.abaronesa.net.br` permaneceu 200. Nenhum log, configuração ou comando
foi dirigido aos tenants alheios.

### 8. Reconstrução determinística do control root

Builder e lock foram materializados diretamente do objeto Git `cf3385f`, nos
blobs `38e2cd7387cc99d1b0aafaafbe9836f9cd4c37af` e
`c2f3b9ce9cb8e1a9197a7f8b89a56211f4e083d8`, sem ler HEAD/working tree como
fonte. Sete wheels exatos foram baixados sem cache, sdist, dependência extra ou
resolução no host. Nomes e SHA-256 conferiram integralmente com o lock.

As provas focais anteriores ao build retornaram:

```text
validate_control_root_package.py                         exit 0
test_control_root_package + test_deployment_transport   92/92, exit 0
```

Dois builds independentes produziram archives e sidecars byte-idênticos:

```text
sourceSha       cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
archive SHA-256 4f6cb0045c44abe965c50019c68338193e078225904cc8d5d56e6280309f1a82
manifest        canônico; linux/amd64; cp310; 188 arquivos
wheels          7/7 por nome e hash
```

A primeira versão da prova isolada saiu 1 porque o auditor exigiu
incorretamente todos os arquivos em `0600`; o manifesto canônico contrata 159
arquivos `0644`, 28 `0600` e um `0755`. Nenhum artifact foi alterado. O auditor
foi corrigido para exigir igualdade com cada modo do manifesto e ausência de
group/other write. A repetição no container pinado Python 3.10/linux-amd64,
`--network none`, fechou install, 190 arquivos/sidecars, owner/modos, verify,
capabilities e três mutantes Draft 2020-12, todos exit 0. A imagem pinada já
existia localmente e nenhum recurso residual foi criado.

### 9. Rotação transacional do control root

Imediatamente antes do rename foram novamente exigidos zero run, operação,
recurso comercial e processo de deploy. O pacote antigo fechou verify e
capabilities em `9731954d474fb68ec1384a525e1075f9a5542e24`.

A rotação usou holder irmão aleatório root-only, rename atômico do target,
diretório canônico novo real/vazio `0700 deploy-emporio:deploy-emporio`, uma
única instalação pelo instalador do objeto Git e rollback automático armado
até o último gate. Resultado:

```text
install/verify             cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
owner/modos                190 arquivos/sidecars iguais ao manifesto
vendor                     jsonschema 4.x e PyYAML sob control/vendor
Draft 2020-12              3/3 mutantes rejeitados
capabilities controlSha    cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
backup antigo              verificado em 9731954… antes da remoção exata
backup/staging/remote tmp   0 / 0 / 0
rollback acionado           não
efeito Docker/comercial     nenhum / 0 recursos
```

O backup antigo foi removido somente após verify/capabilities verdes, pelo
conjunto exato de seu manifesto e diretórios vazios, sem glob ou `rm -rf`.
Após a rotação, os 37 tenants permaneceram diferenciais; todos exceto o loop
autônomo ficaram byte a byte idênticos no registro sanitizado, zero OOM novo e
o contador do boutique avançou 27 sem ação do Empório. Control plane seguiu
healthy e live/ready 200/200.

### 10. Wiring da identidade e bootstrap inaugural

O preflight encontrou o issuer comercial ainda configurado somente com a raiz
do domínio, enquanto o backend, a documentação de identidade e o runtime
deployer contratam a rota completa
`/api/release-control/identity/deployer`. Antes do POST, a única linha do issuer
foi corrigida atomicamente no `.env` protegido. O hash de todas as demais linhas
permaneceu idêntico e o Compose canônico em modo quiet fechou exit 0; nenhum
container comercial foi criado.

O JWKS temporário foi derivado da chave RS256 real dentro da imagem operacional,
como UID `10001:10001`, read-only, sem rede, capabilities ou cópia da chave. A
forma pública `kty/use/alg/kid/n/e` e a fingerprint pública
`f14283370d5eff78346b314ed5a1e2235d819f7aca5ed123fb93651b20e62bf7`
coincidiram com a chave preparada. O arquivo foi servido somente pela regra
temporária S46; `nginx -t` fechou verde e reload preservou o master PID
`1514305`.

As primeiras tentativas locais de validar o JWT falharam antes de qualquer
request: uma por incompatibilidade de owner no bind read-only da chave privada e
outra porque o auditor usou a audience abreviada incorreta. Nenhum token foi
exibido. A validação definitiva usou somente o JWKS público e exigiu:

```text
alg/kid                 RS256 / kid da chave real
issuer                  rota deployer completa
audience                emporio-release-control-deployer
subject                 bootstrap:first-install
scope                   deployment:read deployment:execute deployment:rollback
iat/nbf/exp              iat=nbf; TTL exato 300
jti                      UUID válido
```

O primeiro JWT chegou a validar os quatro GETs, mas foi substituído em arquivo
antes do POST porque seu TTL remanescente já era insuficiente para a janela
acompanhada. O material novo foi novamente validado criptograficamente pelo JWKS
público e permaneceu em arquivos `0600`. Não houve reutilização de key de
tentativa anterior.

Os gates autenticados, repetidos com o JWT terminal, fecharam:

```text
capabilities   200; deployer; três scopes exatos
releases       200; v0.1.1 eligible=true; v0.1.0 eligible=false
current        404; NOT_FOUND
plan v0.1.1    200; first install; 6 UPDATE; currentDigest=null
backup         required=true
migrations     required=true
```

Imediatamente antes da intenção, deploy/rollback continuavam em zero, RAM
available era `3763585024` bytes, swap usado `1048576` bytes, disco livre
`90076237824` bytes, load 5 min `0.84`, swappiness `10`, porta 8120 livre,
control plane live/ready 200/200 e recursos comerciais 0/0/0. O diretório
canônico `shared/backups` existia em `0700` e estava vazio.

### 11. POST único e workflow

Uma única idempotency key UUID v4 e o payload exato `v0.1.1` foram criados em
arquivos `0600` imediatamente antes do POST. O token e headers nunca passaram
por argv ou stdout. Houve exatamente uma chamada mutante:

```text
HTTP                         202
Idempotency-Replayed         false
operationId                  dep_6bd76dcff84a42ba88705b5448aa5c3c
state / target               QUEUED / v0.1.1
```

O runtime/App criou exatamente um run, sem `gh workflow run` manual:

```text
run                          30981846816 / attempt 1
event / branch / SHA         workflow_dispatch / main / cf3385f...
title                        deploy-production-dep_6bd76dcff84a42ba88705b5448aa5c3c
actor                        emporio-deployer-1315264421[bot] / 313092947
trust                        success
prepare                      failure
deploy                       skipped
outcome                      failure
conclusion                   failure
rollback runs                0
```

O artifact nominal `deployment-trust`, ID `8920438907`, foi o único publicado.
Seu JSON canônico vinculou repositório, operação, release, SHA, actor, run e
attempt corretamente; SHA-256
`70f427e8f28d946a97198b2d4401bd41cb126a67afe9e0dca57d70ab9926b6e4`.
Os artifacts handoff, result e workflow-outcome não existem porque o fluxo
parou antes deles.

### 12. Primeira causa técnica pós-POST

O job `prepare` encerrou exit 3 com
`deployment-transport:INVALID_DISPATCH`. A mesma prova foi reproduzida
read-only localmente contra o artifact trust e a API oficial, também exit 3.

A causa é uma incompatibilidade entre o validador publicado e a forma atual do
objeto oficial do run. `prepare_handoff()` exige simultaneamente:

```text
run.name == "Deploy Production"
run.path == ".github/workflows/deploy-production.yml@main"
```

Para o run real, a API retornou:

```text
name = deploy-production-dep_6bd76dcff84a42ba88705b5448aa5c3c
path = .github/workflows/deploy-production.yml
```

Os demais bindings comparados pelo mesmo bloco estavam corretos. O job outcome,
sem handoff, repetiu `INVALID_DISPATCH`; o upload obrigatório falhou porque
`outcome/` não existia. O control plane reconciliou a única operação como:

```text
state                         QUEUED
dispatchState                 CONFIRMED
workflowRunId / attempt       30981846816 / 1
transportStatus               INDETERMINATE
remoteState                   completed
errorCode                     DEPLOYMENT_OUTCOME_UNAVAILABLE
databaseRestoreRequired       ausente
```

Essa causa surgiu depois do POST aceito. A task proíbe correção por tentativa,
segundo POST/operação, rerun manual, rollback ou edição direta de estado nessa
fase. Portanto, nenhuma correção causal foi implementada e nenhum replay foi
feito.

### 13. Reversão dirigida e estado terminal

O bootstrap temporário foi removido integralmente: JWT, headers, key, payload,
respostas protegidas, JWKS, webroot, snippets e diretório S46 não existem mais.
A configuração Nginx original foi reinstalada com SHA-256
`43cdaa4b962678061444f6fa78028a1094b9d65ba161ada84dbbbb2a1d34e7d3`;
`nginx -t` fechou verde e o master PID permaneceu `1514305`. O control root
terminal `cf3385f` e a correção de issuer pré-POST foram preservados.

Estado final fail-closed:

```text
deploy runs                    1, o run falho documentado
rollback runs                  0
operações/idempotency          1 / 1
stack comercial                0 containers / 0 volumes / 0 redes
backups comerciais             0
current / previous             ausentes / ausente
porta 8120                     livre
migrations / restore           0 / 0
control plane containers       2 healthy; unit active
control live / ready           200 / 503 fail-closed
bootstrap S46 residual         0
```

O baseline diferencial dos 37 containers alheios foi preservado quanto a
identidade e OOM. Durante a janela, os três containers do projeto boutique
mudaram autonomamente para `exited`; o instagram avançou o contador de restart
em 34 antes de sair. Nenhum log, restart, configuração ou recurso desses
containers foi aberto ou alterado pelo executor.

Git permaneceu em `c747ddcd5f0ff491632f731da630c5f8c4603085`, dois commits
documentais à frente de `origin/main`/remoto `cf3385f`, com stage e diff tracked
vazios. Nenhum commit, push, rebase ou amend foi executado. A S46 não foi aceita
e a S47 não foi criada. O secret scan exclusivo do relatório fechou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`.

BLOCKED — S46 correction-01 interrompida fail-closed na primeira causa técnica

## Retomada correction-02

> **Data:** 05/08/2026
> **Autoridade cumulativa:** task S46, correction-01 e correction-02
> **Correction-02 SHA-256:** `398bf7b487664f81faeeae3efacc337d32c9237a4e0665b922286ea10c1de905`
> **Relatório inicial SHA-256:** `09f5bbdd6bbf9356cbe3233e507213b46a5f679c61a2b753545e7db5442b53be`

### 14. Checkpoint e gates de retomada

O SHA completo informado na delegação (`f288fd12e076…`) não existe no object
database. O HEAD real é
`f288fd1b18940c4b332d3b87e6e0c3b4f4fae099`, único commit
`docs: authorize S46 predeploy recovery`, contendo exatamente correction-02,
tracker e handoff. A ancestralidade é `ahead 3 / behind 0` sobre
`origin/main=cf3385f1012b9661ddbc2e83d5241aaa8633f8fd`; remoto confirmado por
`ls-remote`, stage e diff tracked vazios. A divergência foi classificada como
erro de transcrição do checkpoint, sem mutação Git.

Task, correction-01, correction-02 e relatório foram lidos integralmente. O
estado externo inicial confirmou:

```text
deploy runs                    1; 30981846816 failure, attempt 1
rollback runs                  0
jobs históricos               trust success; prepare failure;
                               deploy skipped; outcome failure
artifacts históricos          somente deployment-trust 8920438907
operação histórica            QUEUED / CONFIRMED / INDETERMINATE
errorCode                     DEPLOYMENT_OUTCOME_UNAVAILABLE
activeSlot                    1
current singleton             vazio, não reconciliado, ligado à operação
control root                  cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
control plane                 2 containers healthy; live/ready 200/503
stack/backups/porta 8120       0/0/livre
```

Três amostras separadas por cinco segundos registraram RAM available
`3773710336 / 3777159168 / 3773669376`, swap usado `1048576`, disco livre acima
de `90076729344`, load 5 min `0.43 / 0.42 / 0.43`, swappiness `10` e pressão
`avg10=0.00`. O baseline diferencial contém 39 containers alheios, hash
`ab9b2320cdd18a16035c1978cf8f1155f437a3c1c76f30ac98d2d8ad544eb3f4`,
zero OOM. As anomalias boutique já documentadas permanecem externas e sem
intervenção; `eventos.abaronesa.net.br=200` e
`erp.smartdataerp.com.br=502`.

### 15. Correção causal e matriz pré-commit

`prepare_handoff()` passou a exigir a identidade REST real e integral:

```text
name/display_title  deploy-production-<operationId>
path                .github/workflows/deploy-production.yml
html_url            URL canônica do run
```

A forma antiga e mutantes de name, display title, path, URL, run, attempt,
event, branch, SHA, repositórios e actor são rejeitados antes do acesso aos
assets da release.

O reconciliador ganhou o caminho fechado `WORKFLOW_PRE_DEPLOY_FAILED`. Ele
consulta os jobs do attempt exato, exige o conjunto exclusivo trust/prepare/
deploy/outcome com conclusões success/failure/skipped/failure, valida IDs, SHA,
branch, workflow name, timestamps e URLs, e aceita somente o artifact
`deployment-trust` não expirado. O ZIP tem limite, tamanho e digest
confirmados; contém um único arquivo regular canônico e liga operação, release,
control SHA, run, attempt e actor. Qualquer falha de transporte, paginação,
shape ou evidência mantém a operação ativa e indeterminada.

A transição é uma única transação do serviço chamada exclusivamente pelo
reconciliador. Ela exige a forma histórica exata, preserva idempotência e
bindings, remove somente o singleton vazio/incerto correspondente, libera o
slot e grava um único audit `deployment.predeploy_failed`. Repetição terminal é
idempotente. Rollback não alcança esse caminho.

A evidência real foi baixada read-only pela API oficial e aceita pelo código
novo, sem fixtures:

```text
run                  30981846816 / attempt 1
jobs                 4, conjunto e conclusões exatos
artifacts            1, deployment-trust 8920438907
ZIP                  383 bytes; digest e arquivo regular válidos
targetRelease        v0.1.1
```

Matriz final antes do stage:

```text
testes focais + remoto        156 passed
release_control/tests         356 passed
oito suítes canônicas         1012 testes verdes
  docker 117; ci 31; candidates 75; releases 301
  deploy 447; security 26; compose 6; gateway 9
mypy src                      verde, 20 arquivos
ruff check src/tests          verde
validadores explícitos        16 verdes
invocabilidade               27 comandos verdes
catalog                      valid
secret scan tracked/history   clean; scanned=2503;
                              history_scanned=175260; unsupported=0
secret scan relatórios        clean; scanned=8; unsupported=0
git diff --check              verde
```

O check do formatador Ruff não integra a matriz vigente e indicou 25 arquivos
preexistentes fora do formato desse formatter; nenhum reformat amplo foi feito.
A execução de mypy a partir da raiz também ignora a configuração do pacote e
expõe erros preexistentes de aliases Pydantic; a execução canônica dentro de
`release_control` fechou sem issues. Nenhuma mutação Git remota, GitHub ou VPS
ocorreu até este gate.

### 16. Commit, publicação e atualização do plano de controle

Somente depois da matriz integral verde, foi criado o commit técnico normal:

```text
commit                        e436190725336e34fecb08b818645ee22a0f87f5
mensagem                      fix: reconcile predeploy workflow failures
push                          único, fast-forward cf3385f..e436190
CI                            30985145901 / success / 13 de 13
Publish Candidate             30985899087 / success / 11 de 11
imagem release control        30986475184 / success / 4 de 4
```

O workflow de imagem foi disparado uma única vez, sem inputs e sem publicar tag,
GitHub Release ou `v0.1.2`. O manifesto e outcome canônicos, seus sidecars, o
vínculo SHA/run/attempt e a package version `1100610161` foram validados. A
referência instalada é:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:67723699a00fd3ab2c07b2a86f2786eb8e9fc9c98683cf0633c894f616a213f8
```

O control root foi reconstruído duas vezes diretamente do objeto Git técnico.
Os dois arquivos foram idênticos, SHA-256
`28264cfa35dc83c96497981cabadc87b5273efe651d0d13de9a85e19241e20b7`,
com 188 entradas manifestadas e as sete wheels exatas. Uma primeira chamada do
instalador recebeu os argumentos em ordem incorreta; o trap restaurou e
revalidou integralmente `cf3385f`, sem tocar PostgreSQL ou stack comercial. A
segunda chamada rotacionou e verificou o root em `e436190` e removeu somente o
backup dirigido.

Na VPS, somente `RELEASE_CONTROL_IMAGE` mudou. O hash das demais linhas do env
permaneceu
`129c015874deebbed84f88b4e841478250292db15e61136fd0566f286fd4e55b`.
Somente `release_control` foi recriado; o container PostgreSQL
`2f5a1b924d66d46739a293fd7ae8602156f09336a45811c950c7c4cdc1fea8ac`
e seu volume foram preservados. O reconciliador versionado, sem SQL mutante,
encerrou automaticamente a operação histórica:

```text
operation                     dep_6bd76dcff84a42ba88705b5448aa5c3c
state / remoteState           FAILED / FAILED
dispatch / transport          CONFIRMED / CONFIRMED
errorCode                     WORKFLOW_PRE_DEPLOY_FAILED
databaseRestoreRequired       false
activeSlot                    null
run / attempt                 30981846816 / 1
audit novo                    exatamente 1 deployment.predeploy_failed
```

Readiness voltou a `200`, current permaneceu ausente, `v0.1.1` ficou novamente
elegível e não existiam recursos comerciais, backup, migration ou listener
`8120`.

### 17. Gates e intenção substituta

Antes do novo POST, capabilities, releases, current e plan autenticados
responderam `200/200/404/200`. O plano confirmou primeira instalação,
`sourceRelease=null`, seis componentes `UPDATE` com `currentDigest=null`,
`backupRequired=true` e `migrationRequired=true`.

As três amostras finais, separadas por cinco segundos, registraram RAM available
`3718248 / 3719220 / 3719936 KiB`, swap usado `1048576` bytes, disco livre acima
de `89964015616` bytes, load de cinco minutos `0.41 / 0.41 / 0.42`, pressão
`avg10=0.00`, swappiness `10` e o swapfile dedicado ativo. Os 39 containers
alheios permaneceram byte a byte iguais ao baseline desta retomada; nenhum log,
restart ou recurso de outro tenant foi tocado.

O JWKS temporário foi derivado da chave real em container `10001:10001`,
read-only e sem rede. A fingerprint pública/privada coincidiu em
`f14283370d5eff78346b314ed5a1e2235d819f7aca5ed123fb93651b20e62bf7`.
Um primeiro teste isolado foi corretamente recusado porque o arquivo ainda era
`0600 root`; após instalar somente o material público como `0644`, a prova
criptográfica fechou e o Nginx expôs o match exato apenas no ERP, mantendo o
website em `404`. JWT, configuração curl, payload e idempotência ficaram em
arquivos `0600`, sem conteúdo em argv ou stdout.

Houve exatamente um POST substituto:

```text
HTTP                          202
Idempotency-Replayed          false
operationId                   dep_5c95bf53ba994cdc872a3d711b7c133b
state / target                QUEUED / v0.1.1
```

Nenhum `gh workflow run` foi usado. A App deployer real criou exatamente um
novo run, ligado à operação, SHA técnico e actor `313092947`.

### 18. Run substituto e causa técnica bloqueante

O run substituto fechou assim:

```text
run / attempt                 30988243119 / 1
event / branch / SHA          workflow_dispatch / main / e436190...
title                         deploy-production-dep_5c95bf53ba994cdc872a3d711b7c133b
actor                         emporio-deployer-1315264421[bot] / 313092947
trust                         success
prepare                       success
deploy                        failure
outcome                       failure
conclusion                    failure
```

Os quatro artifacts previstos existem e estão vinculados ao run/attempt,
operação, release e control SHA:

```text
deployment-trust              8922975417 / sha256:5ed4beae2cc06fa6ebbd499a135649ce1fc2f0e8e4185025ab9e559e1df2eb4c
deployment-handoff            8922981153 / sha256:80955a2dc2a7ef770a2ffed0e84da3d31b6f80f903838ac091c3535975bd3967
deployment-result             8922986160 / sha256:87687eeb3f31b69e4e659beb2f0085141a9b5172f2ea599bd12807f3a8423533
deployment-workflow-outcome   8922990618 / sha256:c294afbbd2dab418bb057f20d465a0b8044efb1274e235476fcc0850f5aefadc
```

Result e outcome são idênticos semanticamente:

```text
transportStatus               CONFIRMED
deploymentState               FAILED
errorCode                     REMOTE_CAPABILITY_MISMATCH
databaseRestoreRequired       false
```

#### Causa técnica fechada para análise do orquestrador

O transporte chama diretamente, via SSH, o arquivo absoluto:

```text
/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
```

O control root instalado é íntegro e tem `sourceSha=e436190...`, porém o helper
foi instalado como `0600`, owner/group `deploy-emporio`. A execução real como o
usuário dedicado reproduziu antes de qualquer mutação remota:

```text
sudo -u deploy-emporio /opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
sudo: .../deployment-remote.py: command not found
exit 1
```

A causa está no contrato do pacote, não em SSH, host key ou SHA: em
`tools/deploy/control_root_package.py`, `EXECUTABLE_FILES` contém somente
`ops/deploy/deploy-release.sh`. Consequentemente `_mode_for()` grava
`deployment-remote.py` como `0600`, embora `OpenSshTransport.capabilities()` o
execute diretamente e o arquivo tenha shebang. A validação do pacote comprovou
presença e integridade do helper, mas não provou sua invocação real como
`deploy-emporio`.

O check de capabilities precede snapshot, upload e qualquer mutação comercial.
Por isso o run não criou incoming, snapshot, backup, banco, migration, container,
rede, volume ou listener comercial. O reconciliador encerrou a operação sem
intervenção direta no banco:

```text
operation                     dep_5c95bf53ba994cdc872a3d711b7c133b
state / remoteState           FAILED / FAILED
dispatch / transport          CONFIRMED / CONFIRMED
errorCode                     REMOTE_CAPABILITY_MISMATCH
databaseRestoreRequired       false
activeSlot                    null
run / attempt                 30988243119 / 1
controlSha                    e436190725336e34fecb08b818645ee22a0f87f5
```

A correção necessária é pré-deploy e causal — materializar o helper com modo
executável e provar o comando real como `deploy-emporio` —, mas surgiu depois do
único POST substituto. A correction-02 proíbe patch pós-POST, terceiro POST,
rerun, rollback ou restore; portanto nenhum desses atos foi executado.

### 19. Limpeza dirigida e estado fail-closed

O Nginx original foi restaurado byte a byte, SHA-256
`43cdaa4b962678061444f6fa78028a1094b9d65ba161ada84dbbbb2a1d34e7d3`;
`nginx -t` fechou verde, o reload preservou o master PID `1514305` e não restou
referência ao webroot temporário. Após a restauração, ambos os domínios voltaram
ao `502` preexistente enquanto o gateway comercial está ausente. JWKS, JWT,
headers, idempotency key, payload, respostas protegidas, cópia Nginx e diretórios
temporários da S46 foram removidos integralmente.

Estado terminal verificado:

```text
deploy runs                    exatamente 2; ambos preservados
rollback runs                  0
operações / idempotency        2 / 2
operação histórica             FAILED / WORKFLOW_PRE_DEPLOY_FAILED
operação substituta            FAILED / REMOTE_CAPABILITY_MISMATCH
current                        0
stack comercial                0 containers / 0 volumes / 0 redes
backups / migrations / restore 0 / 0 / 0
incoming / snapshot substituto ausentes
porta 8120 / porta 8180        livre / um listener loopback
control root                   e436190...; helper preservado 0600
control plane                  unit active; 2 containers healthy
control live / ready           200 / 200
Nginx                          active; configuração original íntegra
bootstrap S46 residual         0
outros tenants                 39/39 preservados diferencialmente
```

O Git local e remoto permanecem sincronizados em
`e436190725336e34fecb08b818645ee22a0f87f5`; não houve commit ou push depois do
POST. O secret scan final exclusivo deste relatório fechou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`. O
relatório permanece não rastreado e fora do stage. A S46 não foi aceita e a
S47 não foi criada.

BLOCKED — S46 correction-02 interrompida fail-closed na primeira causa técnica

## Retomada correction-03

### 20. Checkpoint, fronteira e causa recebida

O checkpoint documental foi confirmado em
`ac0fff7d650473606d59ff4e190b6df65528ae6c`, com `origin/main` em
`e436190725336e34fecb08b818645ee22a0f87f5`, stage e diff tracked vazios e
relatório inicial com SHA-256
`81e713a906d1674a5c84fedbe64e8beff34f26ac90f9a9498ca434c0707172ea`.
O commit local era exclusivamente documental e não foi publicado isoladamente.

Antes da mutação técnica, a causa da correction-03 foi reproduzida: o helper
instalado pelo pacote anterior era arquivo regular `0600`, e sua invocação
direta pelo usuário `deploy-emporio` encerrava com exit `126` e `Permission
denied`. As duas operações anteriores permaneceram terminalmente `FAILED`, o
control plane estava ready `200`, `current` era `404`, v0.1.1 estava elegível e
não existia recurso, backup, migration ou listener comercial.

### 21. Contrato executável exato do control root

A correção técnica alterou somente:

```text
tools/deploy/control_root_package.py
tools/deploy/validate_control_root_package.py
tools/deploy/tests/test_control_root_package.py
ops/deploy/deployment-remote.py
```

Builder, manifesto, instalador, verificador e validador passaram a exigir o
conjunto executável exato:

```text
ops/deploy/deployment-remote.py 0755
ops/deploy/deploy-release.sh    0755
```

Todos os arquivos de `vendor` permanecem `0644`, todos os demais arquivos do
pacote permanecem `0600` e qualquer executável adicional, modo adulterado,
helper não executável ou divergência entre manifesto, arquivo e instalação é
rejeitado. A execução isolada revelou ainda que imports a partir do pacote
tentavam materializar `__pycache__`; o helper passou a definir
`sys.dont_write_bytecode=True` antes dos imports empacotados, preservando o
conjunto exato em runtime read-only.

As provas causais fecharam `100 passed`. Em imagem real Python 3.10/linux-amd64,
como UID/GID não root, read-only e sem rede, o helper foi executado diretamente
pelo shebang, com exit `0`, JSON canônico e ausência de bytecode residual. A
verificação posterior continuou verde; mutantes de modos, executável adicional
e Draft 2020-12 foram rejeitados.

### 22. Matriz, publicação técnica e workflows

A matriz completa fechou sem falhas:

```text
release_control/tests                  356 passed
docker                                  117 passed
ci                                       31 passed
candidates                               75 passed
releases                                301 passed
deploy                                  453 passed
security                                 26 passed
compose                                   6 passed
gateway                                   9 passed
suítes canônicas                      1018 passed
invocability                             27 passed
mypy                                     20 arquivos; success
ruff / catalog:valid / validadores       exit 0
secret scan completo                     scanned=2504; allowed=1184; unsupported=0
secret scan staged                       scanned=4; unsupported=0
git diff --check                         exit 0
```

Foi criado o commit técnico normal
`69621c275a8da9cb46db05b7fe6497f33e81e117` (`fix: preserve executable control
root contract`) e realizado um único push fast-forward de `e436190...` para
`69621c2...`. No mesmo SHA:

```text
CI                   30991511443   success   13/13
Publish Candidate    30992386795   success   11/11
```

Nenhuma imagem nova do release control, tag, GitHub Release, release comercial
ou v0.1.2 foi publicada.

### 23. Reconstrução e rotação transacional

O control root foi reconstruído duas vezes diretamente do objeto Git terminal.
Os arquivos resultaram byte a byte idênticos:

```text
archive SHA-256     412654fc341e90e264ea8094464031121245710699e4b2e9df099f7c1ff6474f
arquivos            188
modos               27 x 0600; 159 x 0644; 2 x 0755
executáveis         exatamente os dois contratados
controlSha          69621c275a8da9cb46db05b7fe6497f33e81e117
```

A rotação na VPS foi transacional, com rollback armado, sem `chmod` manual. A
instalação terminal foi verificada antes e depois da invocação direta. Como
`deploy-emporio`, o comando exato contratado fechou exit `0`, JSON canônico,
owner dedicado e arquivo regular `0755`:

```text
/opt/sistemas/emporio/shared/control/ops/deploy/deployment-remote.py capabilities
```

O resultado vinculou protocolo `emporio-deployment-transport`, schema version
`1`, deploy root canônico e o `controlSha` terminal.

### 24. Gates, bootstrap e POST único da correction-03

As duas operações históricas continuaram `FAILED` e preservadas; não havia
active slot, current instalado, recurso comercial, backup, migration, arquivo
em incoming/snapshots nem listener 8120. Três amostras de capacidade fecharam
acima dos limites, os dois containers do control plane estavam healthy e
live/ready respondiam `200/200`. Os 39 containers dos demais tenants foram
registrados diferencialmente sem abrir logs, reiniciar ou alterar qualquer um.

O JWKS temporário foi novamente derivado apenas da chave RS256 real e servido
na rota exata do domínio ERP. JWT de cinco minutos, idempotency key, payload e
configs de request ficaram em arquivos `0600`; nenhum valor protegido foi
exibido. Capabilities, releases, current e plano fecharam antes do POST: três
scopes exatos, v0.1.1 elegível, current `404`, primeira instalação com seis
updates, backup e migrations requeridos.

Foi enviado exatamente um POST, sem retry ou replay:

```text
HTTP                    202
Idempotency-Replayed    false
operationId             dep_49980b00ad5d443eb32321efa29e6621
target                  v0.1.1
```

Ele criou exatamente um run novo:

```text
run / attempt           30993832964 / 1
event                   workflow_dispatch
headSha                 69621c275a8da9cb46db05b7fe6497f33e81e117
display title           deploy-production-dep_49980b00ad5d443eb32321efa29e6621
trust                   success
prepare                 success
deploy                  failure
outcome                 failure
```

Os quatro artifacts esperados foram preservados e validados: trust, handoff,
deployment-result e workflow-outcome. Result e outcome foram semanticamente
idênticos e vinculados à operação, release, SHA, run e attempt:

```text
transportStatus             CONFIRMED
deploymentState             FAILED
errorCode                   REMOTE_CAPABILITY_MISMATCH
databaseRestoreRequired     false
deployment-result SHA-256   63a8e574dc76df412654ee8ea620b28517dc7562f94790a4f00abfe8ab384839
workflow-outcome SHA-256    63a8e574dc76df412654ee8ea620b28517dc7562f94790a4f00abfe8ab384839
```

### 25. Causa real do terceiro run

O deploy falhou no primeiro check remoto, antes de snapshot, upload, install ou
execute. A invocação local do mesmo helper como `deploy-emporio` continuou exit
`0`, provando que o contrato corrigido do pacote não é a causa deste run. O log
imutável do host no instante da execução mostrou:

```text
2026-08-05T09:35:15+0000 sshd: Connection closed by authenticating user
deploy-emporio 48.211.212.213 port 22568 [preauth]
```

Portanto o runner alcançou o SSH da VPS, mas não autenticou a identidade antes
de executar qualquer comando. No host:

```text
home / .ssh / authorized_keys       0750 / 0700 / 0600
owner                               deploy-emporio:deploy-emporio
chaves autorizadas                  exatamente 1
fingerprint instalada               SHA256:PotdyucjkzRGEuYfWMAp1fg4vWUK3ZlPHW/ZRWjdFAQ
shell                               /bin/sh
PubkeyAuthentication / StrictModes yes / yes
```

O secret `PRODUCTION_SSH_PRIVATE_KEY` e o known-hosts do environment production
não foram alterados desde `2026-08-03T23:56:00Z`/`23:56:01Z`. O valor privado é
irrecuperável pela API do GitHub e não foi lido ou exibido.

O transporte explica o código superficial: `SubprocessRunner` descarta stderr e
`OpenSshTransport.capabilities()` converte qualquer exit não zero de SSH no
mesmo `REMOTE_CAPABILITY_MISMATCH`. Assim, o artifact é válido e fail-closed,
mas não distingue a falha de autenticação observada no host da divergência de
capabilities.

A remediação capaz de permitir uma nova operação exige reconciliar/substituir a
identidade SSH entre o secret do environment e `authorized_keys`. A seção 5 da
correction-03 não autoriza alteração de SSH, e a continuação causal limitada a
builder/helper/transporte não autoriza rotacionar segredo ou chave do host.
Alterar apenas a classificação do erro não faria a identidade autenticar e
consumiria indevidamente a última operação autorizada. Por isso não houve novo
commit, push, rotação, POST, rerun ou replay.

### 26. Estado terminal e limpeza dirigida

O reconciliador versionado encerrou a operação sem edição direta do banco:

```text
operation                     dep_49980b00ad5d443eb32321efa29e6621
state / remoteState           FAILED / FAILED
dispatch / transport          CONFIRMED / CONFIRMED
errorCode                     REMOTE_CAPABILITY_MISMATCH
databaseRestoreRequired       false
activeSlot                    null
run / attempt                 30993832964 / 1
controlSha                    69621c275a8da9cb46db05b7fe6497f33e81e117
```

O singleton de current permaneceu sem release instalada e passou a registrar a
incerteza de controle `WORKFLOW_RUN_BINDING_INVALID`; o endpoint current
continuou semanticamente sem instalação, mas a readiness caiu para `503`. Essa
condição, somada à autenticação SSH não reconciliada, proíbe a última operação.

As provas negativas finais fecharam:

```text
deploy runs                    exatamente 3; todos failure e preservados
rollback runs                  0
operações / idempotency        3 / 3
active slots                   0
stack comercial                0 containers / 0 volumes / 0 redes
backups / migrations / restore 0 / 0 / 0
incoming / snapshots           diretórios canônicos vazios
porta 8120                     livre
control root                   69621c2... íntegro; helper 0755
control plane                  unit active; 2 containers healthy
control live / ready           200 / 503 fail-closed
outros tenants                 39/39 idênticos ao baseline diferencial
```

O Nginx original foi restaurado com SHA-256
`43cdaa4b962678061444f6fa78028a1094b9d65ba161ada84dbbbb2a1d34e7d3`;
`nginx -t` fechou verde, o reload preservou o master PID `1514305` e ambos os
domínios voltaram ao `502` preexistente para a rota JWKS enquanto o gateway
comercial está ausente. Webroot, JWKS, JWT, headers, idempotency key, payload,
respostas e diretório bootstrap da correction-03 foram removidos integralmente.
Os diretórios locais temporários de build e artifacts, já consolidados acima por
hash e sem material necessário à operação, também foram removidos de forma
dirigida; residual local prefixado `s46-c03` ficou em zero.

O Git local e `origin/main` permanecem sincronizados em
`69621c275a8da9cb46db05b7fe6497f33e81e117`; os relatórios S39–S46 permanecem
não rastreados e fora do stage. A S46 não foi aceita e a S47 não foi criada.

BLOCKED — S46 correction-03 interrompida fail-closed na primeira causa técnica

## Retomada correction-04

### 27. Contrato, checkpoint e revalidação anterior à mutação

Foram relidos integralmente, na ordem contratada, a task S46, as quatro
corrections, este relatório contínuo e o handoff do orquestrador. A
`correction-04` foi validada com SHA-256
`7d1dbb2d76e469d8f49206fe26c8bf20fd1380eb617ca023dd1a5a56be3ad8aa`.

O checkpoint local foi confirmado em
`0b415146d6983e53d0deef6e2fcf867d4616ded6`, exatamente um commit documental à
frente de `origin/main=69621c275a8da9cb46db05b7fe6497f33e81e117`, sem
diff tracked ou stage inicial. Os relatórios S39–S46 continuaram não rastreados
e fora do stage. Os três runs e operações históricos foram revalidados pela API
e pelo banco do control plane:

```text
run 30981846816  FAILED  WORKFLOW_PRE_DEPLOY_FAILED
run 30988243119  FAILED  REMOTE_CAPABILITY_MISMATCH
run 30993832964  FAILED  REMOTE_CAPABILITY_MISMATCH
active slots / rollback / restore       0 / 0 / 0
current                                 vazio, unreconciled, version 1
uncertainty                             WORKFLOW_RUN_BINDING_INVALID
control live / ready                    200 / 503
```

Os artifacts dos três runs foram conferidos quanto a conjunto, vínculo, digest
e presença. O helper instalado permaneceu regular `0755`, owner dedicado e
`capabilities` direto verde no SHA `69621c2...`. Antes de mutar, a VPS ainda
possuía uma chave autorizada `0600`, zero container/volume/rede comercial, zero
backup, incoming, snapshot ou migration e somente o listener loopback
`127.0.0.1:8180`; a porta 8120 permaneceu livre.

O baseline diferencial, calculado sem abrir logs ou configurações de outros
tenants, foi:

```text
containers alheios  beda6dda114769a3d6133ea7002c9e4272b8aa341d9e4d6e837612af26be6ea0
volumes alheios     1640ddb181e292dda2e41c9e734f4409907859e3d89cab240a33b10db08eb3c9
redes alheias       c3062419a0c3496c11ad9f62a2df980033af6898af74ca2b23ded0cd178ffe8d
```

### 28. Correções técnicas implementadas

O `release_control` passou a recuperar o singleton vazio apenas para outcome
`CONFIRMED/FAILED`, restore falso, operação de deploy terminal, slot livre e
campos comerciais integralmente nulos. O caminho prospectivo e o de restart
compartilham a mesma transação bloqueada, removem somente o marker comprovado e
criam uma única auditoria `deployment.current_recovered`.

Para o estado histórico, o reconciliador reconsulta o run e exige identidade
REST, attempt, SHA, ator, conclusão, conjunto exclusivo de quatro artifacts,
metadados não expirados e vinculados ao run, trust canônico e outcome canônico
com digest idêntico ao persistido. Ausência, duplicidade, expiração, adulteração
ou divergência mantém o current e a readiness fechados. Nenhuma migration ou
edição manual de dados foi introduzida.

Foi criado `verify-production-transport.yml`, manual e sem inputs, com grafo
`trust -> probe -> outcome`, concorrência `emporio-production`, permissões
somente de leitura e environment `production` restrito ao probe. O único
comando remoto possível é o helper fixo com `capabilities`; host checking,
identity isolation, artifact/sidecar vinculados e cleanup `always()` são
validados estruturalmente. Inventários, documentação e CI foram atualizados
para os sete workflows ativos.

### 29. Matriz local anterior ao commit

Todos os comandos abaixo encerraram com exit `0` após a versão final do patch:

```text
release_control/tests                         375 passed
docker / ci / candidates / releases          117 / 32 / 75 / 301
deploy / security / compose / gateway         461 / 26 / 6 / 9
oito suítes canônicas                         1027 passed
probe/workflow causal                         incluído na suíte deploy
invocabilidade                                28 comandos
validadores e contratos explícitos            30 verdes
catalog --require-release-ready               valid
ruff release_control src/tests                verde
mypy --strict release_control/src             20 arquivos, verde
git diff --check                              verde
secret scan tracked/history                   clean, unsupported=0
```

O primeiro ciclo focal executado por engano com o Python global parou antes dos
testes por ausência local do driver `psycopg`. A execução canônica subsequente
com `uv` usou o ambiente fixado do módulo e fechou integralmente; nenhuma
dependência foi instalada ou alterada por causa desse erro de invocação.

### 30. Commit, CI, candidato e imagem técnica

Foi criado o commit técnico normal
`636f09b6484c976dc302559c0e6d8d14dc2947cb`
(`fix: recover deployer readiness and verify transport`) sobre o commit
documental do orquestrador. O push foi único e fast-forward de `69621c2...`
para `636f09b...`; stage e diff tracked permaneceram vazios e os relatórios
S39–S46 continuaram não rastreados.

Os gates remotos do mesmo SHA fecharam:

```text
CI                         30999378550   success   13/13
Publish Candidate          31000195641   success   11/11
candidate                  deployable=true; 6 componentes
candidate id               candidate-636f09b6484c976dc302559c0e6d8d14dc2947cb-31000195641-1
```

O candidato foi validado por artifacts canônicos, sidecars, outcome
`published` e vínculos de SHA, run e attempt. Em seguida foi disparado uma única
vez, sem inputs, o workflow técnico do release control:

```text
Publish Release Control Image   31000744642   success   4/4
package version                 1101298477
manifest SHA-256                300d6bf5d5e6ccacc724094a5470c193c1abfbd0ebd76faeca5e1a4568d69593
outcome SHA-256                 634b43feb914d889c92bd4011f3238b5bc44b43bd11bf33fbe7015a876918bf5
image                           ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:41486042ca3f409894a195ecd42d0b53f3b0984165ab0b1637c78112bf5ec255
```

Manifesto, outcome, sidecars, package version e labels de SHA/run/attempt
coincidiram. O manifesto remoto do registry resolveu exatamente para o digest
acima. Não foi criada tag, GitHub Release, release comercial ou `v0.1.2`.

### 31. Control root e atualização isolada do control plane

O control root foi reconstruído duas vezes diretamente do objeto Git terminal,
com sete wheels exatas do lock. Os archives e sidecars ficaram byte a byte
idênticos:

```text
archive SHA-256     633090fce73291eefeb57790294651cc1d159c62e35f81ca792dbe0b9d9c5e2f
controlSha          636f09b6484c976dc302559c0e6d8d14dc2947cb
arquivos            188
modos               27 x 0600; 159 x 0644; 2 x 0755
executáveis         deploy-release.sh e deployment-remote.py, somente
```

A rotação instalou e verificou o novo root e suas capabilities. O primeiro
algoritmo conservador de limpeza recusou um diretório vazio adicional criado
pelo vendor (`vendor/PyYAML.libs`) e manteve simultaneamente o novo root íntegro
e o backup antigo íntegro. Ambos foram revalidados pelos respectivos builders.
A limpeza dirigida foi então concluída exigindo conjunto de arquivos exatamente
igual ao manifesto, somente diretórios regulares e ausência de link/device; os
arquivos foram removidos individualmente e os diretórios vazios, de baixo para
cima. Não houve glob, `rm -rf`, `chmod` no pacote, perda do backup ou efeito
comercial. O estado terminal do helper ficou regular `0755`, owner dedicado e
capabilities canônicas no SHA `636f09b...`, sem staging ou holder residual.

Na atualização do control plane, a primeira invocação de Compose foi recusada
antes de qualquer recriação porque o usuário dedicado não podia fazer `stat` do
diretório corrente `/root`. O trap restaurou atomicamente o env e o container
anterior permaneceu intacto. A repetição partiu do working directory canônico e
alterou somente `RELEASE_CONTROL_IMAGE`; o hash das demais linhas permaneceu
`129c015874deebbed84f88b4e841478250292db15e61136fd0566f286fd4e55b`.
Somente `release_control` foi recriado. PostgreSQL e volume foram preservados:

```text
postgres container   2f5a1b924d66d46739a293fd7ae8602156f09336a45811c950c7c4cdc1fea8ac
postgres volume      emporio_release_control_postgresql_data:/var/lib/postgresql/data
control image        sha256:41486042ca3f409894a195ecd42d0b53f3b0984165ab0b1637c78112bf5ec255
```

O reconciliador versionado recuperou automaticamente o current vazio na
primeira amostra. Consultas exclusivamente read-only e as rotas públicas
provaram:

```text
live / ready                    200 / 200
current                         404; tabela com zero linhas
deployment.current_recovered    exatamente 1; operação dep_49980b00...
sync releases/deployments       drift=false / drift=false
v0.1.1                          snapshot único preservado
operações históricas            3 FAILED; bindings e error codes preservados
active slots / rollback         0 / 0
```

### 32. Rotação Ed25519 e bloqueio do primeiro probe

Foi criada uma Ed25519 nova sem passphrase em temporário local `0700`, privada
`0600`, com fingerprint
`SHA256:37cF/VNa2khhTxoL7x7zk3BPH+Xlww7uMhDrAcD3JPw`. A chave pública foi
acrescentada atomicamente ao `authorized_keys` dedicado, que permaneceu `0600`
e `deploy-emporio:deploy-emporio`, com exatamente duas linhas:

```text
antiga   SHA256:PotdyucjkzRGEuYfWMAp1fg4vWUK3ZlPHW/ZRWjdFAQ
nova     SHA256:37cF/VNa2khhTxoL7x7zk3BPH+Xlww7uMhDrAcD3JPw
```

A identidade nova executou diretamente o comando único de capabilities com
todas as opções SSH estritas do workflow, `IdentityAgent none`, host checking e
JSON canônico exato para `controlSha=636f09b...`. Ela não foi adicionada ao
ssh-agent; as fingerprints já presentes no agente são distintas.

`PRODUCTION_SSH_PRIVATE_KEY` foi atualizado por stdin no environment
`production`, sem material em stdout ou argv. A App deployer criou exatamente o
run de prova `31001791245`, no SHA terminal e com ator `313092947`. Resultado:

```text
trust       success
probe       failure; exit 3; production-transport-probe:SSH_PROBE_FAILED
outcome     success, registrando probeResult=failure e status=FAILED
artifacts   production-transport-trust e production-transport-probe-outcome
ausente     production-transport-probe, como exigido para falha
```

O mesmo arquivo privado e o mesmo comando remoto continuaram verdes localmente,
inclusive com as opções exatas e comparação byte a byte do JSON canônico. Host,
porta e known_hosts não foram alterados. A evidência disponível delimita a
falha à materialização/uso da identidade no runner: o workflow descarta o stderr
do OpenSSH e, portanto, não permite distinguir de forma comprovável serialização
da chave, parsing do arquivo ou rejeição de autenticação. Como evidência causal
auxiliar, a privada local contém newline terminal e uma cópia controlada sem
esse byte é rejeitada pelo parser OpenSSH com exit `255`; isso é hipótese
compatível, não conclusão fabricada sobre o secret inacessível.

Conforme a seção 6 da correction-04, a primeira falha do probe encerra a
execução: as duas chaves foram preservadas, a privada local continua protegida
fora do repositório para diagnóstico dirigido, nenhuma chave antiga foi
removida e nenhum segundo probe ou POST comercial foi executado. As três
operações históricas permanecem as únicas operações de deploy; não há snapshot,
upload, install, execute, recurso, backup, migration, current comercial,
rollback ou restore criado por esta retomada.

Os bundles de trust e outcome foram baixados e validados quanto a forma
canônica, sidecars e vínculos; ambos apontam para run `31001791245`, attempt 1,
SHA `636f09b...` e ator `313092947`. O log integral do run foi comparado em
memória com a privada e o blob público: nenhum material apareceu, o header PEM
não apareceu e somente a máscara `***` foi emitida. Worktree/untracked também
não contêm os bytes da identidade nova, e o ssh-agent não contém sua
fingerprint. O único secret de environment alterado foi
`PRODUCTION_SSH_PRIVATE_KEY`; `PRODUCTION_SSH_KNOWN_HOSTS` conservou sua data e
valor opaco preexistentes.

Snapshot fail-closed terminal:

```text
deploy runs / operações       3 / 3; todos FAILED históricos
rollback runs                 0
transport probes              1; trust verde, probe falho, outcome seguro
authorized_keys               2 linhas; antiga e nova preservadas
control root                  636f09b... íntegro
control plane                 live/ready 200/200; current 404
stack/volumes/redes comerciais 0 / 0 / 0
backups/incoming/snapshots     0 / 0 / 0
porta 8120                    livre
outros tenants                39 containers preservados, sem intervenção
staging/holder de rotação      0
```

BLOCKED — S46 correction-04 interrompida fail-closed na primeira causa técnica

## Retomada correction-05

### 33. Autoridade, leitura e checkpoint final

Foram lidos integralmente a task S46, as corrections 01–05, este relatório e o
handoff do orquestrador. O path abreviado sem `.task` citado na delegação não
existe; o contrato principal existente e referenciado por todas as corrections
é `S46-primeiro-deploy-acompanhado-v0.1.1.task.md`. A correction-05 foi validada
com SHA-256
`84d6b8f4b8188d3ae33adcb4b651e4a422b8a1c6c953351bb31afad9e824bee5`.

O checkpoint anterior a qualquer mutação foi confirmado:

```text
HEAD local             68959e3b3a5e290bb52fb71707fe200de469a0ff
origin/main/remoto     636f09b6484c976dc302559c0e6d8d14dc2947cb
ahead / behind         1 / 0
commit local           somente correction-05, tracker e handoff
stage/diff tracked     vazios
relatórios S39–S46     não rastreados e fora do stage
relatório inicial      5d1ed2dd5806b82ff44687adeba0a8bb78cd4f3aa5fe33f98e0b8f0f25862349
```

GitHub preservava sete workflows ativos, três runs de deploy terminalmente
falhos, zero rollback e somente o probe `31001791245` falho já documentado. A
release `v0.1.1` continuava estável, imutável, com ID `365219520`, três assets e
target `cf3385f...`. O environment possuía host/porta e os dois secrets SSH;
`PRODUCTION_SSH_PUBLIC_KEY_SHA256` ainda estava ausente, como esperado antes da
implementação.

Na VPS, o control root `636f09b...` fechou verify e capabilities direto; as duas
chaves autorizadas permaneceram `0600` e com as fingerprints antiga/nova
documentadas. Control plane ficou active, dois containers healthy, live/ready
`200/200`, current `404`, sync sem drift e uma única auditoria de recuperação.
As três operações históricas continuaram `FAILED`, restore falso, slots livres
e bindings preservados. Stack/volumes/redes comerciais, backups, incoming,
snapshots e listener 8120 permaneceram em zero.

A chave nova preservada localmente continuava em diretório `0700`, privada
`0600`, fora de Git/worktree e do agent, e sua pública derivava a fingerprint
`SHA256:37cF/VNa2khhTxoL7x7zk3BPH+Xlww7uMhDrAcD3JPw`. A amostra inicial do host
registrou RAM available `3998695424` bytes, swap dedicado ativo, swappiness
`10`, disco livre `89824612352` bytes, load de cinco minutos `0.52` e 39
containers externos, sem intervenção em qualquer tenant.

### 34. Implementação SSH compartilhada e ensaio transacional

Foi criada uma única autoridade `tools/deploy/ssh_material.py`, importada pelo
probe e pelo deploy. Ela normaliza a chave OpenSSH para exatamente um LF,
rejeita armor, base64, CRLF, LF duplo, trailing data e tipos divergentes, cria
arquivos com `O_EXCL|O_NOFOLLOW` e `0600`, executa `ssh-keygen` root-owned para
parse e fingerprint e compara a variável legível esperada. `known_hosts` é
validado para host/porta, ASCII, forma de chave e base64 antes da escrita.

O config compartilhado fixa alias, usuário dedicado, host, porta, identidade,
`IdentitiesOnly`, `IdentityAgent none`, `BatchMode`, host checking estrito,
senhas/interatividade desligadas, forwardings desligados e timeouts. Stderr fica
limitado somente em memória e é reduzido ao conjunto fechado de códigos da
correction; chave, linha pública, config, path temporário e diagnóstico bruto
nunca entram em artifact ou log. A privada materializada é removida por
`shred -u` também em falha de parse, fingerprint ou transporte.

O workflow de deploy passou a exigir a mesma
`PRODUCTION_SSH_PUBLIC_KEY_SHA256`; o probe preserva artifact canônico inclusive
em falha, com somente estágio, código, fingerprint esperada e bindings. O módulo
compartilhado foi incluído no conjunto fechado do control root. Testes com chave
Ed25519 real provaram equivalência com/sem LF terminal, rejeição de RSA e de
todos os mutantes contratados, fingerprint correta/incorreta/ausente, config
byte-idêntica no mesmo destino, classificação fechada e cleanup.

Foi criado `verify-deployment-engine.yml`, oitavo workflow ativo, manual, sem
inputs e sem environment de produção. Seu grafo exclusivo é
`trust -> rehearse -> outcome`; ele valida a release `v0.1.1` e seus três assets,
autentica o runner efêmero no GHCR e executa o `deployment_cli.py` real em root
`0700`, projeto/names isolados, porta loopback 8120, credenciais sintéticas e
identidade deployer desabilitada. A evidência fecha bundle, journal, backup dos
dois bancos, migrations, sete serviços, installed-state/current, replay sem
novo efeito e limpeza dirigida de containers, volumes, redes, imagens, env,
identidade e root. Artifacts registram somente hashes, estados e contagens, sem
dumps ou materiais protegidos. Validador e mutantes recusam inputs, production,
SSH, workflows comerciais, referências mutáveis, cleanup amplo ou ausência de
replay.

Inventário, README, CI, release validator e invocabilidade foram atualizados
explicitamente para oito workflows e 29 comandos. Nenhum arquivo de
`release_control`, migration, release comercial, Compose canônico, rollback ou
serviço VPS foi alterado.

### 35. Matriz local anterior ao commit técnico

A primeira passagem da suíte candidates encontrou somente a expectativa
mecânica antiga de 28 comandos no teste de contrato. Ela foi atualizada para o
inventário real de 29 e a suíte completa foi repetida verde. A matriz terminal,
sempre antes de stage, fechou:

```text
release_control/tests                 375 passed
docker                                 117 passed
ci                                      33 passed
candidates                              75 passed
releases                               301 passed
deploy                                 469 passed
security                                26 passed
compose                                  6 passed
gateway                                  9 passed
oito suítes canônicas                 1036 passed
validadores/contratos explícitos         30 verdes
invocabilidade                           29 comandos
catalog --require-release-ready          valid
ruff release_control src/tests           verde
mypy --strict release_control/src        20 arquivos, verde
git diff --check                          verde
secret scan tracked/history               clean; scanned=2511;
                                          history_scanned=190297;
                                          unsupported=0
secret scan patch técnico                 clean; scanned=27; unsupported=0
```

Os avisos de formato obtidos ao aplicar Ruff fora da matriz canônica atingem
também arquivos históricos e não motivaram reformat amplo. Imports não usados e
capturas redundantes introduzidos nesta correction foram removidos. Não houve
stage, commit, push, dispatch, alteração de variável/secret, rotação de control
root ou mutação da VPS até a conclusão desses gates.

### 36. Commit, CI e Publish Candidate

Com a matriz local integralmente verde, foi criado um único commit técnico
normal sobre o commit documental do orquestrador e realizado um único push
fast-forward:

```text
commit técnico       8be7c1ff58b640964bea836d5f886b15c2cb8ee3
mensagem             fix: harden production transport rehearsal
push                 fast-forward 636f09b... -> 8be7c1f...
rebase/amend/force   nenhum
CI                   31009688779; success; 13/13
Publish Candidate    31010684163; success; 11/11
```

Ambos os workflows estão vinculados ao mesmo SHA terminal. O candidato
`candidate-8be7c1ff58b640964bea836d5f886b15c2cb8ee3-31010684163-1` ficou
`deployable=true`, com seis componentes construídos por digest, integração
`passed`, sete serviços healthy e cleanup terminal de zero container, volume e
rede, além das seis imagens dirigidas ausentes.

Os artifacts `candidate-manifest`, `candidate-outcome`,
`candidate-integration-result` e `candidate-pending` foram baixados em
temporário, tiveram seus sidecars confrontados byte a byte e o manifesto foi
validado pelo schema Draft 2020-12. O outcome ficou `published`, run
`31010684163`, attempt `1`, SHA `8be7c1f...` e artifact ID `8932551414`, com o
digest da API idêntico ao binding do outcome. A working tree tracked e o stage
permaneceram vazios; somente os relatórios S39–S46 continuaram não rastreados.

### 37. Reconstrução e rotação do control root

O lock foi materializado diretamente do objeto Git terminal e suas sete wheels
foram baixadas sem dependências, sdist ou cache para CPython 3.10/linux-amd64.
Nomes e SHA-256 coincidiram exatamente com o lock. Dois builds independentes
produziram archive e sidecar byte-idênticos:

```text
controlSha          8be7c1ff58b640964bea836d5f886b15c2cb8ee3
archive SHA-256     a7eef222d3adedc461d18f9c015bc8bfdf431c3e9e5fc4d41f469f83cd145c18
wheels              7/7 verificadas
```

Na primeira prova Docker descartável, install/verify fecharam verdes, mas
`capabilities` saiu `4` porque o root sintético do teste havia sido criado como
`root`, divergindo corretamente do owner dedicado exigido. Sem alterar pacote
ou código, a prova foi repetida com a topologia de owner real: imagem Python
3.10 pinada, linux/amd64, `--network none`, install, verify e chamada direta
pelo shebang como `deploy-emporio` fecharam exit `0`, com controlSha terminal.
Zero container residual permaneceu.

Na VPS, o root `636f09b...` foi verificado pelo builder de seu próprio objeto
Git e respondeu capabilities antes da troca. A rotação usou rename atômico,
root canônico novo vazio `0700`, rollback armado, instalador do objeto terminal,
verify e capabilities direto. Resultado:

```text
root instalado       8be7c1ff58b640964bea836d5f886b15c2cb8ee3
install/verify       exit 0 / exit 0
capabilities direto  exit 0; controlSha terminal
backup anterior      removido por limpeza dirigida após todos os gates
holder/staging       zero residual
rollback acionado    não
control plane        active; readiness 200
```

O temporário remoto continha exclusivamente archive, sidecar e os dois builders
vinculados; os quatro arquivos foram removidos individualmente e o diretório
vazio foi fechado com `rmdir`. Nenhuma imagem do control plane foi publicada ou
alterada.

### 38. Ensaio remoto único e bloqueio fail-closed

A App deployer criou exatamente um run do novo workflow, sem inputs:

```text
run                  31011886682 / attempt 1
actor                emporio-deployer-1315264421[bot] / 313092947
SHA                  8be7c1ff58b640964bea836d5f886b15c2cb8ee3
trust                success
rehearse             failure
outcome              success, registrando FAILED/REHEARSAL_FAILED
conclusão do run     failure
```

O artifact de rehearsal provou zero passo transacional iniciado, zero serviço,
backup, current ou installed-state e cleanup terminal de zero container, imagem,
volume e rede. Trust, rehearsal e outcome tiveram os sidecars conferidos e os
bindings de repositório, workflow, SHA, run, attempt e actor preservados. Os três
artifacts remotos são, respectivamente, IDs `8932786867`, `8932800225` e
`8932808011`.

O diagnóstico dirigido, sem novo dispatch e sem executar Docker, repetiu apenas
a validação imutável da release e a geração local do bundle. A release `v0.1.1`
validou integralmente; `deployment_plan.generate_bundle()` falhou de forma
determinística em `_validate_chain()` com `RELEASE_CHAIN_MISMATCH`. A causa é
exata: o ensaio chama a primeira instalação com `current=None`, enquanto o
manifesto imutável de `v0.1.1` declara `previousRelease=v0.1.0`; o contrato atual
do plano exige `previousRelease=null` sempre que não existe current. Por isso a
falha ocorreu antes de pull, backup, migration, container, journal ou qualquer
efeito comercial, e o artifact público reduziu corretamente a exceção interna
ao código fechado `REHEARSAL_FAILED`.

Conforme a seção 6 da correction-05, nenhuma correção ou repetição é permitida
após falha do ensaio. Portanto não houve criação/alteração de variável, nova
gravação de secret, probe SSH, remoção de chave antiga, bootstrap, JWT,
idempotency key ou POST comercial. A chave nova protegida permaneceu preservada
para acesso recuperável; ambas as chaves continuam instaladas.

Snapshot final fail-closed:

```text
deploy runs/operações históricas    3 / 3; todos FAILED e preservados
novo run comercial                  0
rollback/restore                    0 / 0
engine rehearsal                    1; trust verde, rehearsal falho
transport probes nesta retomada     0
authorized_keys                     2; antiga e nova preservadas
PRODUCTION_SSH_PUBLIC_KEY_SHA256    ausente
control root                        8be7c1ff... íntegro
control plane                       active; live/ready 200/200
current comercial/link              ausente
stack/porta comercial               0 containers; porta 8120 livre
```

Os temporários locais criados por esta retomada para candidato, control root,
artifacts e diagnóstico foram validados por path explícito e removidos com
limpeza dirigida, sem glob amplo. O diretório da chave Ed25519 nova não pertence
a esses temporários e foi deliberadamente preservado `0700`, com privada `0600`,
porque nenhum probe verde autorizou concluir sua rotação ou destruição.

No fechamento, `HEAD=origin/main=8be7c1ff...`, ahead/behind `0/0`, stage e diff
tracked vazios e somente os relatórios S39–S46 não rastreados. `git diff
--check` fechou verde e o secret scan do relatório retornou `clean`,
`unsupported=0`.

BLOCKED — S46 correction-05 interrompida fail-closed na primeira causa técnica

## Retomada correction-06

### 39. Autoridade, checkpoint incremental e escopo herdado

Foram relidos integralmente a task S46, a correction-05, a correction-06, este
relatório contínuo e o handoff do orquestrador. A correction-06 foi validada com
SHA-256
`d607ff3d274a223dd157be1a7af3fa7bc7c7bf0d32962eb1b7f896c1ff433ce4`.

O checkpoint inicial conferiu exatamente:

```text
HEAD local             678f194c223e7c5091eafa5eae8583675446b936
origin/main            8be7c1ff58b640964bea836d5f886b15c2cb8ee3
ahead / behind         1 / 0
commit local           somente correction-06, tracker e handoff
stage/diff tracked     vazios
relatórios S39–S46     não rastreados e fora do stage
relatório inicial      2cf2205f6faf4aa520145281c1947b4f563f62eb08d116dbe38e93a0802aa89e
```

Foram herdados sem repetição a implementação SSH, workflow e matriz da
correction-05, commit/push `8be7c1f...`, CI `31009688779`, candidato
`31010684163`, control root então instalado, validação da release e o run falho
`31011886682`. Nenhum acesso à VPS, dispatch, variável, secret, probe ou recurso
comercial foi alterado durante o reparo local.

### 40. Reparo causal do planner e matriz incremental

`deployment_plan.py` passou a tratar `manifest.previousRelease` somente como
lineage histórica quando `current=None`. Nessa condição o planner valida a
release global, mas não fabrica uma instalação anterior: produz
`firstInstallation=true` e `sourceRelease=null`. A validação de coerência do
bundle compara `sourceRelease` ao predecessor histórico somente em update real;
SemVer crescente e `target.previousRelease == current.release` permanecem
obrigatórios quando há current.

Os testes causais cobrem primeira instalação com predecessor histórico não
nulo, seis `UPDATE` com digest corrente nulo, os dois inventários completos de
migrations, backup/migration requeridos, generate/validate do bundle e rejeição
de mutante que inventa `sourceRelease`. Os testes de update e pareamento
current/current-manifest foram preservados. O validador do rehearsal agora
exige estruturalmente `current_path=None` e `current_manifest_path=None`, e os
mutantes provam que v0.1.0 não é semeada ou sintetizada.

Matriz incremental anterior ao commit:

```text
deployment_plan focal                         30 passed
plan/contract/executor/transport/rehearsal    189 passed
validate_deployment_plan                      valid
validate_deployment_executor                  valid
validate_deploy_workflow                      valid
validate_production_transport_workflow        valid
validate_deployment_engine_workflow           valid
validate_control_root_package                 valid
```

A prova dirigida baixou em `mktemp` o asset real `release.json` de `v0.1.1`,
SHA-256
`6e6ac56089a935c817608a37ab06823e329649a78b7acd6d967c0dfccaecd31e`.
`generate_bundle()` e `validate_bundle()` fecharam verdes com current ausente,
predecessor histórico `v0.1.0`, source nulo, seis componentes, dois bancos,
backup e migrations requeridos. O plano gerado teve SHA-256
`652878906d2ecc31049386b4ce688b0cdcdab418768b20de3fc0a3dd628f4023`.
O temporário foi removido nominalmente, sem Docker, SSH, VPS, secret ou POST.

### 41. Commit técnico, publicação e CI

Somente após a matriz incremental verde, os quatro arquivos técnicos foram
adicionados ao stage; nenhum relatório entrou no índice. Foi criado um único
commit normal sobre o commit documental e realizado um único push
fast-forward:

```text
commit técnico       d43d0d12338fc324c2caa054b211c2bc0f6bb006
mensagem             fix: support direct first installation
push                 fast-forward 8be7c1f... -> d43d0d1...
rebase/amend/force   nenhum
CI                   31013912579; success; 13/13
Publish Candidate    31014987937; success; 11/11
```

CI e candidato automático estão vinculados ao mesmo SHA terminal. O candidato
não foi usado como pré-condição do rehearsal, conforme a correction-06; sua
conclusão verde ocorreu autonomamente antes do dispatch. Ao fim da publicação,
`HEAD=origin/main=d43d0d1...`, ahead/behind `0/0`, stage e diff tracked vazios,
com somente os relatórios S39–S46 não rastreados.

### 42. Reconstrução e rotação incremental do control root

Builder e lock foram materializados do objeto Git terminal. As sete wheels
CPython 3.10/linux-amd64 foram baixadas sem dependências, sdist ou cache e
conferidas pelo nome e SHA-256 do lock. Dois builds independentes ficaram
byte-idênticos:

```text
controlSha          d43d0d12338fc324c2caa054b211c2bc0f6bb006
archive SHA-256     bc19a6ceed0af6fde82b6fbc367cec4b1fcc3ec3ad6f2a2875e4799087c5358a
wheels              7/7 verificadas
```

A prova descartável usou a imagem Python 3.10 por digest, linux/amd64,
`--network none` e montagem read-only. Uma primeira topologia sintética deixou
o parent deploy root com owner `root` e capabilities rejeitou corretamente o
estado. Sem alterar artifact ou código, a topologia foi refeita com o mesmo
owner dedicado do host; install, verify e invocação direta pelo shebang como
`deploy-emporio` fecharam exit `0`, com zero container residual.

Na VPS, o pacote anterior `8be7c1f...` foi verificado antes do rename. A troca
usou backup irmão, target novo real/vazio `0700`, rollback armado, install,
verify e capabilities direto. Todos os gates fecharam em `d43d0d1...`; o
rollback não foi acionado, o backup anterior foi removido por inventário exato
do manifesto e o temporário remoto foi limpo arquivo a arquivo. Nenhuma imagem
ou serviço do control plane foi alterado. O serviço permaneceu active e
live/ready `200/200`.

### 43. Rehearsal incremental e interrupção fail-closed

Antes do dispatch havia exatamente o run histórico `31011886682` do rehearsal.
A App deployer criou uma única execução nova, sem inputs e sem rerun:

```text
run / attempt        31015462556 / 1
actor ID             313092947
SHA                  d43d0d12338fc324c2caa054b211c2bc0f6bb006
trust                success
rehearse             failure
outcome              success, registrando FAILED/REHEARSAL_FAILED
conclusão do run     failure
```

Os artifacts `deployment-engine-trust`, `deployment-engine-rehearsal` e
`deployment-engine-rehearsal-outcome` têm IDs `8934257307`, `8934264235` e
`8934279675`. Seus três sidecars foram confrontados com o conteúdo, e os
bindings de repositório, workflow, SHA, run, attempt e actor fecharam 3/3.

O receipt do rehearsal prova:

```text
status/errorCode       FAILED / REHEARSAL_FAILED
steps                  0
backup                 0
services               0
journal/installed      null / null
current/previous       null / null
cleanup                containers=0, images=0, networks=0, volumes=0
```

A falha observável não é novamente `RELEASE_CHAIN_MISMATCH`: a reprodução
pré-commit com o asset real já provou generate/validate verdes, e o artifact
remoto não contém esse código. No código versionado, `REHEARSAL_FAILED` nessa
posição é a redução de uma exceção inesperada dentro do bloco transacional. O
job durou cerca de um segundo, não produziu journal, passo ou recurso e o log
público contém somente o exit `3`; o artifact não preserva estágio nem classe
sanitizada da exceção original. Assim, a primeira causa técnica comprovável é
uma nova exceção pré-journal no rehearsal, mas o estágio exato não pode ser
atribuído a planner, ambiente ou preparação de bundle sem fabricar evidência ou
executar outro diagnóstico/rehearsal, ações não autorizadas após esta falha.
Esse limite de observabilidade é registrado explicitamente para a análise do
orquestrador.

Conforme a correction-06, a progressão foi interrompida nesse checkpoint. Não
houve alteração da variável `PRODUCTION_SSH_PUBLIC_KEY_SHA256`, gravação de
secret, probe, remoção de chave, bootstrap, JWT, idempotency key ou POST
comercial. A chave nova protegida continua preservada e as duas chaves seguem
instaladas.

Snapshot final:

```text
control root                       d43d0d1... íntegro; capabilities exit 0
control plane                      active; live/ready 200/200
deploy runs/operações históricas   3 / 3; FAILED e preservados
novo run/operação comercial        0 / 0
rollback/restore                   0 / 0
engine rehearsal correction-06     1; trust verde, rehearsal falho
authorized_keys                    2; antiga e nova preservadas
PRODUCTION_SSH_PUBLIC_KEY_SHA256   ausente
current comercial/link             ausente
stack/porta comercial              0 containers; porta 8120 livre
```

Os temporários locais de build e evidência foram removidos pelos dois paths
nominais validados; a chave Ed25519 preservada não fazia parte deles. No
fechamento, `HEAD=origin/main=d43d0d1...`, ahead/behind `0/0`, stage e diff
tracked vazios e somente os relatórios S39–S46 não rastreados. `git diff
--check` fechou verde e o secret scan exclusivo deste relatório retornou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`.

BLOCKED — S46 correction-06 interrompida fail-closed no checkpoint corrente

## Retomada correction-07

### 44. Checkpoint e causa causal preservada

O checkpoint documental foi revalidado antes de qualquer mutação:

```text
HEAD local        ad5993362a8f563e3f9ccea6bb104c8598f5698b
origin/main       d43d0d12338fc324c2caa054b211c2bc0f6bb006
ahead/behind      1/0
stage/diff        vazios
```

O commit local contém somente correction-07, tracker e handoff. Os relatórios
S39–S46 permaneceram não rastreados e fora do stage. Não houve repetição dos
checkpoints verdes da correction-06.

A causa contratada foi mantida como ponto de partida: `RUNNER_TEMP` externo ao
checkout era recusado pelo guard real do planner. A implementação não relaxou
nenhum guard. O root efêmero passou a ser criado diretamente sob `ROOT`, com
prefixo fechado por run, mode `0700`, UID/GID efetivos, componentes sem escrita
por grupo/outros e remoção dirigida pelo path nominal validado.

### 45. Patch causal e matriz incremental

O rehearsal agora reduz falhas somente aos pares fechados abaixo e os propaga
coerentemente entre receipt e outcome, sem traceback, path absoluto ou saída
bruta:

```text
PREPARE_ROOT          PREPARE_ROOT_FAILED
BUNDLE_GENERATION     BUNDLE_GENERATION_FAILED
DEPLOYMENT_CLI        DEPLOYMENT_CLI_FAILED
TRANSACTION_EVIDENCE  TRANSACTION_EVIDENCE_FAILED
CLEANUP               CLEANUP_INCOMPLETE
```

O `ProductionAdapter` resolve o home pela identidade efetiva do SO, deriva
`<home>/.docker`, valida diretório real `0700` e `config.json` regular `0600`
com owner corrente e ausência de symlink, sem abrir o arquivo. `DOCKER_CONFIG`
é passado exclusivamente a subprocessos Docker; Curl e demais comandos mantêm
o ambiente mínimo.

As provas incrementais anteriores ao stage fecharam:

```text
pytest rehearsal/adapter/CLI     82 passed, 62 subtests passed
engine workflow validator        valid
production adapter/CLI validator valid
control-root package validator   valid
py_compile                       exit 0
dois guards no mesmo root        aceitam root 0700 sob ROOT
mutantes de root/stage/cleanup   rejeitados
DOCKER_CONFIG Docker/Curl        presente / ausente
git diff --check                 exit 0
secret scan patch                clean; scanned=6; unsupported=0
secret scan stage                clean; scanned=6; unsupported=0
```

Os testes cobrem `RUNNER_TEMP` externo, `/tmp`, symlink, mode inseguro, root
fora do checkout, as cinco classes fechadas, cleanup dirigido e config Docker
ausente, inseguro, com symlink ou owner divergente. Nenhuma suíte ampla, release
validation ou candidate integration já aceita foi repetida.

### 46. Commit, CI e control root terminal

Após todos os gates locais verdes, foi criado um único commit técnico normal e
realizado um único push fast-forward:

```text
commit técnico  7128293ca265bc11c3a27f626ab9dbb4d6c618bf
mensagem        fix: harden deployment rehearsal isolation
push            d43d0d1... -> 7128293...
rebase/amend    nenhum
force-push      nenhum
CI              31019564059; success; 13/13
```

O secret scan completo do job `contracts` foi o último gate amplo inicial e
fechou verde. O Publish Candidate automático não foi usado como pré-condição,
conforme o contrato.

O lock e o builder foram materializados do objeto Git terminal. Sete wheels
CPython 3.10/linux-amd64 foram baixadas sem dependências, sdist ou cache e
conferidas por filename e SHA-256. Dois builds independentes foram
byte-idênticos:

```text
controlSha          7128293ca265bc11c3a27f626ab9dbb4d6c618bf
archive SHA-256     0ff1847200ea79ea559c8a41306131bb108f6ced02e9c9edd31b590547fe4445
sidecar SHA-256     86dde27446c4df884e16ae040046ed58b1f6756993b40d2545b9fb8efb89975f
wheels              7/7 verificadas
```

A primeira topologia descartável read-only recusou corretamente a criação da
identidade sintética; a segunda revelou que o tmpfs `/opt` herdara `noexec` e
recusou a chamada direta. Sem alterar pacote ou código, a prova contratada foi
executada com tmpfs explicitamente `exec`, mesma imagem Python 3.10 pinada,
`linux/amd64`, `--network none` e usuário não root. Install, verify e execução
direta pelo shebang fecharam exit `0`, com `controlSha` terminal e zero
container residual.

Na VPS, o root anterior `d43d0d1...` foi verificado antes da troca. A rotação
usou rename atômico, rollback armado, target novo real/vazio `0700`, install,
verify e capabilities direto. O backup anterior foi removido pelo conjunto
exato do manifesto e os temporários locais/remotos por seus paths nominais. O
resultado foi:

```text
control root       7128293ca265bc11c3a27f626ab9dbb4d6c618bf
install/verify     exit 0 / exit 0
capabilities       exit 0; JSON canônico; controlSha terminal
rollback           não acionado
control plane      active; live/ready 200/200
```

Um probe inicial somente leitura em `/live` retornou `404` por usar a rota
abreviada incorreta. As rotas reais `/health/live` e `/health/ready` foram
então usadas e fecharam `200/200`; nenhuma mutação decorreu desse erro de
comando.

### 47. Preparo GHCR opaco

A credencial preexistente de root foi validada opacamente por metadata e por
`docker manifest inspect`, sem abrir, imprimir ou hashear seu conteúdo. A cópia
foi instalada em `/home/deploy-emporio/.docker/config.json` sem sobrescrita:

```text
diretório       deploy-emporio:deploy-emporio 0700; real
config.json     deploy-emporio:deploy-emporio 0600; regular
conteúdo        nunca lido ou registrado
```

Como `deploy-emporio`, com ambiente mínimo e `DOCKER_CONFIG` explícito, os seis
immutable refs de `v0.1.1` passaram em `docker manifest inspect` (`6/6`). Não
houve pull, login/logout, criação de imagem ou alteração da credencial original
de root.

### 48. Rehearsal correction-07 e bloqueio terminal

Antes do dispatch existiam somente os runs históricos `31011886682` e
`31015462556`. A App deployer real criou exatamente um novo run, sem inputs e
sem rerun:

```text
run / attempt        31021376896 / 1
actor ID             313092947
SHA                  7128293ca265bc11c3a27f626ab9dbb4d6c618bf
trust                success
rehearse             failure
outcome              success, registrando FAILED
conclusão do run     failure
```

Os artifacts e IDs são:

```text
deployment-engine-trust               8936744951
deployment-engine-rehearsal           8936754730
deployment-engine-rehearsal-outcome   8936769426
```

Os três sidecars `sha256:` coincidiram byte a byte com seus JSONs. Repository,
workflow, SHA, run, attempt e actor fecharam 3/3. Receipt e outcome registram a
primeira causa sanitizada de modo concordante:

```text
failedStage          DEPLOYMENT_CLI
errorCode            DEPLOYMENT_CLI_FAILED
status               FAILED
steps                0
backup               0
services             0
current/previous     null / null
replay               false / false / false
cleanup              containers=0, images=0, networks=0, volumes=0
```

O log público registra somente `deployment-engine-rehearsal:failed` e exit `3`
no comando transacional; o enforcement posterior saiu `1`. Não há traceback,
path absoluto, stdout/stderr bruto ou material protegido nos artifacts. Assim,
a causa comprovada para o orquestrador é uma falha terminal do CLI antes do
primeiro passo/journal e antes de qualquer imagem ou recurso. O código interno
mais específico não é observável sem nova instrumentação ou novo run; inferi-lo
como Docker config, pull, Compose ou outro subestágio fabricaria evidência. A
correction-07 não autoriza patch ou segundo rehearsal depois desta falha.

### 49. Parada e snapshot final

A execução parou imediatamente antes dos checkpoints SSH. Não houve criação ou
alteração de `PRODUCTION_SSH_PUBLIC_KEY_SHA256`, atualização de secret, probe,
remoção de chave, bootstrap, JWT, idempotency key ou POST comercial.

O snapshot somente leitura posterior fechou:

```text
control root                       7128293... íntegro; capabilities exit 0
control plane                      active; live/ready 200/200
deploy runs/operações históricas   3 / 3; FAILED e preservados
rollback runs/restore              0 / 0
engine rehearsal correction-07     1; trust verde, rehearsal falho
authorized_keys                    2; antiga e nova preservadas
environment variables SSH         somente HOST e PORT; fingerprint ausente
environment secrets SSH           KNOWN_HOSTS e PRIVATE_KEY inalterados
current/previous                   ausentes
backups/incoming/snapshots         vazios
stack/volume/rede comercial        0 / 0 / 0
porta 8120                         livre
GHCR dedicado                      cópia opaca presente, modos corretos
```

Os temporários de build, release e artifacts foram removidos por paths nominais.
Nenhum sistema alheio foi investigado ou alterado.

No fechamento, `HEAD=origin/main=7128293ca265bc11c3a27f626ab9dbb4d6c618bf`,
ahead/behind `0/0`, stage e diff tracked vazios, com somente os relatórios
S39–S46 não rastreados. `git diff --check` e a prova de whitespace do relatório
fecharam verdes. O secret scan exclusivo do relatório retornou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`.

BLOCKED — S46 correction-07 interrompida fail-closed no checkpoint corrente

### 50. Retomada correction-09

Em 05/08/2026, a correction-09 foi autorizada integralmente e retomou a S46 sem
reiniciar checkpoints aceitos. A causa está fechada em D1-D4: constante
PostgreSQL incompatível com o adapter no rehearsal, lista esperada de seis
passos incompatível com o journal canônico de sete, expectativa invertida de
`databaseRestoreRequired` e janela de readiness insuficiente nos cinco pontos
do adapter. C0 registra como inferência causal a normalização necessária do
Docker config do runner, sem alegar recuperação do journal destruído pelo run
histórico.

O commit local rejeitado `6c23d2833b3987dc93e449ba3213779ea14f6947` foi
confirmado como um commit à frente de `origin/main`, preservado fora do
repositório com SHA-256
`88fb26693b4a956db5ca8e1fa8709ccd82ba12b305cbdf4c74192d49fc3e9420` e então
descartado por reset nominal para
`7128293ca265bc11c3a27f626ab9dbb4d6c618bf`. Após o reset, `HEAD` e
`origin/main` convergiram, stage e diff tracked ficaram vazios e permaneceram
não rastreados somente os relatórios S39-S46.

Esta retomada registra separadamente, nas subseções seguintes, o reparo do
rehearsal, o reparo do adapter, workflow e validador em lockstep, testes
causais, provas locais sem container, commits e CI, run técnico, eventual
rotação única do control root, SSH e operação comercial.

IN_PROGRESS — correction-09 autorizada; reparo causal em execução

### 51. Reparo causal e provas locais sem container

O commit documental normal `7b464b3` criou a correction-09 no caminho canônico
e atualizou README e handoff sem publicar a correction-08. O reparo técnico
aplicou D1-D4 dentro dos seis arquivos autorizados:

```text
rehearsal       PostgreSQL canônico; sete passos; restore=true; rollback pending
evidência       cliExit/causeCode/journal fechados, capturados antes do cleanup
resultado       transactionStatus + cleanupStatus -> status global
cleanup         baseline de quatro recursos; preservação do preexistente;
                retornos verificados; deadline global 600s
wrapper         CLI 3600s; resolução única não fatal do manifest PostgreSQL
adapter         backup 240/300; update/verify/rollback 480/540
workflow        job 90 min; Docker config 0700/0600 verificado após login
validador       timeout, normalização e markers D1-D3 em lockstep
```

Os testes exercitaram a constante do PostgreSQL contra `_image_probe`, o
mutante digest-only, igualdade literal com `deployment_executor.STEPS`, os
mutantes `databaseRestoreRequired=False` e `ROLLBACK=SUCCEEDED`, os cinco pares
de readiness, três classes de evidência CLI, ordem journal-cleanup-receipt,
tri-status, preservação de imagem baseline e falhas não-zero de down, image rm
e shred. A reprodução em processo usou CLI, planner, executor e adapter reais,
substituindo somente o runner Docker/Curl; produziu journal `SUCCEEDED`, seis
passos `SUCCEEDED`, `ROLLBACK=PENDING` e `databaseRestoreRequired=true`.

Provas terminais locais:

```text
py_compile                              exit 0
rehearsal + production adapter          75 tests; OK
reprodução CLI/planner/executor/adapter 1 test; OK
engine workflow validator               valid
production adapter validator            valid
git diff --check                        exit 0
secret scan patch técnico               clean; scanned=6; unsupported=0
ensaio parcial com containers           não executado; facultativo
```

IN_PROGRESS — gates locais verdes; aguardando commit técnico, CI e rehearsal único

### 52. Commit, CI e rehearsal único correction-09

O segundo e último commit permitido foi criado sobre o documental e os dois
foram publicados num único push fast-forward:

```text
commit documental  7b464b3   docs: authorize S46 correction-09
commit técnico     c9bf081443ac67b9ac1008a056dab00998e0dc77
mensagem técnico   fix: repair deployment rehearsal evidence
push               7128293... -> c9bf081...
amend/rebase/force nenhum
CI                  31031954991; success; 13/13
```

Antes do dispatch, o confronto dos onze critérios da §9 confirmou três runs de
deploy históricos, todos `FAILED`, zero rollback, e a VPS sem current,
previous, installed-state, container, volume, rede, backup, incoming, snapshot
ou listener 8120 comercial. A CI estava verde no SHA terminal.

A App deployer real criou exatamente um run sem inputs e sem rerun:

```text
run / attempt       31033385683 / 1
actor               emporio-deployer-1315264421[bot] / 313092947
SHA                 c9bf081443ac67b9ac1008a056dab00998e0dc77
trust               success
rehearse            failure
outcome             success, registrando FAILED
conclusão global    failure
```

Os artifacts `deployment-engine-trust`, `deployment-engine-rehearsal` e
`deployment-engine-rehearsal-outcome` têm IDs `8941572333`, `8941619021` e
`8941624095`. Os três sidecars coincidiram com seus JSONs e os bindings de
repositório, workflow, run, attempt, SHA e ator fecharam. A projeção estruturada
preservou a causa terminal sem path, comando, stdout/stderr bruto ou traceback:

```text
transactionStatus          FAILED
cleanupStatus              SUCCESS
status                     FAILED
failedStage / errorCode    DEPLOYMENT_CLI / DEPLOYMENT_CLI_FAILED
cliExit / causeCode        21 / BACKUP_FAILED
postgresManifestResolved   true
PULL                       SUCCEEDED
BACKUP                     FAILED / BACKUP_FAILED
MIGRATE..ROLLBACK          PENDING
databaseRestoreRequired    false; MIGRATE não iniciou
backup/services/current    0 / 0 / null
cleanup                    containers=0, images=0, networks=0, volumes=0
```

O receipt tem SHA-256
`9f57df3604c2a74df9b40e54975ac090c2af55913e8de419e149266904407e97`;
o outcome referencia esse valor exatamente. A resolução read-only do manifest
PostgreSQL fechou positiva e PULL concluiu, delimitando a falha ao passo BACKUP
antes de migrations, update, verify ou commit-state. A varredura dos artifacts
não encontrou material protegido nem path absoluto.

Conforme a §10, a primeira falha do run único encerra a delegação. Não houve
segundo run, correção automática, rotação do control root, configuração SSH,
probe, remoção de chave ou POST comercial.

BLOCKED — S46 correction-09 interrompida fail-closed em BACKUP no rehearsal único

### 53. Retomada correction-10 e escolha do ramo

Em 05/08/2026, a correction-10 foi autorizada integralmente e retomou a S46
sem repetir checkpoints aceitos. Antes do reparo, a verificação obrigatória
somente leitura consultou o caminho comercial canônico na VPS e observou:

```text
<deploy_root>/releases/db/init-databases.sh
existência  ausente
tipo        ausente
owner       não aplicável
modo        não aplicável
```

O fato seleciona objetivamente o ramo B da §4.3. O reparo autorizado passa a
abranger E1, E2 e E3: modo `0755` na materialização do rehearsal, healthcheck
PostgreSQL por `127.0.0.1`, inclusão executável do script no pacote do control
root e materialização comercial idempotente fora do bundle. O arquivo de
relatório continua não rastreado e excluído dos commits.

IN_PROGRESS — correction-10 autorizada; ramo B selecionado; reparo causal em execução

### 54. Reparo E1-E3 e provas locais

O reparo técnico permaneceu na fronteira autorizada. E1 passou a materializar
o initializer do rehearsal com modo `0755`; E2 força `pg_isready` por
`127.0.0.1`; E3 incluiu o initializer na allowlist executável do control root
e o helper remoto passou a materializá-lo fora do bundle, com diretório
`0700`, arquivo `0755`, owner efetivo `deploy-emporio`, escrita atômica e replay
idempotente.

As provas locais sem containers fecharam:

```text
testes direcionados package/transport/engine/compose  123; OK
suítes engine workflow/adapter/compose                 99; OK
reexecução engine após prova de artifacts              15; OK
deployment-engine-workflow validator                  valid
production-adapter validator                          valid
control-root-package validator                        valid
compose validator                                     valid
py_compile                                             exit 0
git diff --check                                      exit 0
secret scan técnico                                   scanned=11; unsupported=0
```

Os testes rejeitam o mutante `0700`, exigem paridade com o modo versionado,
rejeitam o healthcheck sem host TCP, verificam o script executável no pacote e
provam que a segunda materialização preserva inode e bytes. Os artifacts do
rehearsal continuam sem path absoluto, owner, modo de produção ou conteúdo do
script.

IN_PROGRESS — E1-E3 e gates locais verdes; aguardando commit técnico, push e CI

### 55. Commits, push e CI correction-10

Os limites de consumo documental e técnico foram preservados:

```text
commit documental  6d5ced3  docs: authorize S46 correction-10
commit técnico     622e03fa20312717b23fb499bc3ab9e9b3c8f83f
mensagem técnico   fix: materialize database initializer safely
push               c9bf081... -> 622e03f...; fast-forward único
amend/rebase/force nenhum
CI                  31035128620; success; 13/13
```

A CI executou os sete jobs básicos e os seis builds/scans de imagens; todos
fecharam `success` no SHA técnico terminal. Até este checkpoint nenhum run da
correction-10 foi despachado e não houve rotação do control root, alteração SSH
ou operação comercial.

IN_PROGRESS — CI 13/13 verde; aguardando o rehearsal remoto único

### 56. Rehearsal remoto único correction-10

A App deployer criou exatamente um run, sem inputs e sem rerun:

```text
run / attempt       31036336828 / 1
ator ID             313092947
SHA                 622e03fa20312717b23fb499bc3ab9e9b3c8f83f
trust               success
rehearse            success
outcome             success
conclusão global    success
```

Os artifacts de trust, rehearsal e outcome têm IDs `8942710819`,
`8942794310` e `8942802817`. Seus três sidecars coincidiram com os JSONs; os
bindings de repository, workflow, run, attempt, SHA e ator fecharam. O receipt
tem SHA-256
`b71fad17ef355fc7531bab213f463bc10919b3f4542da07a4724d449a5346332`
e o outcome referencia esse valor exatamente.

Todos os critérios da §6.1 foram observados:

```text
transactionStatus / cleanupStatus / status  SUCCESS / SUCCESS / SUCCESS
failedStage / errorCode / causeCode          null / null / null
PULL..COMMIT_STATE                           seis SUCCEEDED
ROLLBACK                                     PENDING
databaseRestoreRequired                      true
backup                                       2/2; sizes 851 e 871
services                                     7
current / previous                           v0.1.1 / null
replay                                       journal/backup/containers true
cleanup                                      containers/images/networks/volumes 0
postgresManifestResolved                     true
```

O scan exclusivo dos três JSONs ficou limpo com `unsupported=0`.

### 57. Rotação do control root e bloqueio no primeiro probe SSH

As sete wheels foram novamente verificadas pelo lock. Dois builds
independentes do objeto Git terminal produziram archive e sidecar
byte-idênticos:

```text
controlSha        622e03fa20312717b23fb499bc3ab9e9b3c8f83f
archive SHA-256   3059cf7b6b760cf45e46e2ac90de4ba5491d459bf49f9b74838a4ffc88966536
wheels            7/7 verificadas
```

A prova descartável usou CPython 3.10 por digest, `linux/amd64`,
`--network none`, tmpfs executável e owner dedicado. Install, verify e
capabilities fecharam no SHA terminal. A única rotação da VPS verificou antes
o root `7128293...`, fez rename atômico com rollback armado, instalou e
verificou o novo pacote, confirmou capabilities direto e removeu backup e
staging; rollback não foi acionado. O control plane permaneceu live/ready
`200/200`.

A chave Ed25519 preservada autenticou diretamente contra o novo control root.
O environment recebeu a variável legível
`PRODUCTION_SSH_PUBLIC_KEY_SHA256=SHA256:37cF/VNa2khhTxoL7x7zk3BPH+Xlww7uMhDrAcD3JPw`
e o secret `PRODUCTION_SSH_PRIVATE_KEY` foi atualizado por stdin. As chaves
antiga e nova permaneceram instaladas, como exigido para o primeiro probe.

A App criou uma única execução do primeiro probe:

```text
run / attempt   31036986009 / 1
SHA             622e03fa20312717b23fb499bc3ab9e9b3c8f83f
trust           failure; job não iniciado
probe           skipped
outcome         failure; job não iniciado
artifacts       0
causa GitHub    pagamentos recentes falharam ou limite de gastos insuficiente
```

Nenhum runner iniciou código do workflow. Ainda assim, a execução terminou
`failure` e o contrato determina parada imediata antes da remoção da chave
antiga, do segundo probe e do POST comercial. O snapshot read-only posterior
confirmou control root `622e03f...`, duas linhas em `authorized_keys` com owner
e modo corretos, control plane `200/200`, porta 8120 livre e zero resíduo da
rotação. Os três runs comerciais históricos permanecem os únicos, todos
`FAILED`; não houve nova operação, deploy, rollback ou restore.

BLOCKED — S46 correction-10 interrompida fail-closed no subestágio corrente

### 60. Retomada correction-10 no gate de capacidade e operação comercial única

A janela foi retomada sem repetir rehearsal, rotação do control root, probes
SSH ou troca de chaves. A emenda autorizada à §5 restringiu o bloqueio de
saúde ao control plane; a saúde de tenants alheios passou a ser somente
observação, preservadas todas as proibições de intervenção. As três novas
amostras fecharam:

```text
amostra  RAM available  swap total/usado       disco livre   load5  swappiness
1        4009361408     8589930496 / 1572864   89783726080   0.88   10
2        4012949504     8589930496 / 1572864   89782829056   0.87   10
3        4016140288     8589930496 / 1572864   89782644736   0.85   10
```

O control plane respondeu `200/200`; `current` e `previous` estavam ausentes,
a porta 8120 livre, backups vazios e recursos comerciais em `0/0/0`. O tenant
alheio `community-frontend` foi observado `running/unhealthy`, restart count
zero. Nenhum log foi aberto e nenhuma intervenção, restart, stop ou correção
foi executada nesse tenant.

O bootstrap inaugural derivou JWKS e JWT da chave RS256 real dentro da imagem
ativa do control plane, com `--network none`, filesystem read-only e chave
montada read-only. A primeira preparação descartável parou antes do Nginx por
permissão de leitura do gerador pelo UID não-root; seus temporários foram
destruídos e a configuração foi comprovada inalterada. A preparação efetiva
usou root somente dentro do container isolado. O JWKS público temporário teve
SHA-256 `211d2b6d14c32e541358bfcb06202c92fecd930089a9f2920be56b16d011efec`;
a fingerprint DER da pública foi
`sha256:f14283370d5eff78346b314ed5a1e2235d819f7aca5ed123fb93651b20e62bf7`.
As claims não secretas fecharam `RS256`, `kid` real, issuer e audience
contratuais, subject `bootstrap:first-install`, os três scopes exatos, UUID
`jti` e TTL 300. JWT, configs HTTP, respostas e idempotency key permaneceram
`0600`.

O Nginx aceitou a rota ERP exata e o negativo 404 no website, com `nginx -t`
verde, reload e master PID `1514305` preservado. A preflight autenticada final
observou:

```text
capabilities                         200; deployer; três scopes exatos
releases                             200; somente v0.1.1 eligible=true
current                              404; NOT_FOUND
plan v0.1.1                          200; source null; seis UPDATE
currentDigest dos seis componentes   null
migrationRequired / backupRequired   true / true
```

O baseline continha somente os três runs comerciais históricos
`30981846816`, `30988243119` e `30993832964`. Uma sentinela protegida foi
persistida antes do envio e exatamente um POST foi realizado:

```text
HTTP / replay       202 / Idempotency-Replayed=false
operation           dep_17f769a41de7433db01a964d7b9b3129
estado inicial      QUEUED
release             v0.1.1
segundo POST        nenhum
```

O runtime criou exatamente um run novo, sem dispatch manual e sem rerun:

```text
run / attempt       31044897704 / 1
event / SHA         workflow_dispatch / 622e03fa20312717b23fb499bc3ab9e9b3c8f83f
display title       deploy-production-dep_17f769a41de7433db01a964d7b9b3129
trust / prepare     success / success
deploy / outcome    failure / failure
conclusão global    failure
```

Os artifacts foram preservados sob IDs `8946020854` (trust), `8946028252`
(handoff), `8946047191` (result) e `8946055872` (outcome). Result e outcome
coincidem e localizam o terminal:

```text
transportStatus          INDETERMINATE
deploymentState          null
databaseRestoreRequired  null
errorCode                REMOTE_RESULT_UNAVAILABLE
operation/run/attempt     binding correto
```

A inspeção somente leitura posterior provou que o bundle imutável `v0.1.1` e
o initializer comercial regular `0755` foram materializados, mas nenhum
journal da operação foi criado. Incoming e snapshots da operação foram limpos;
`current` e `previous` permaneceram ausentes, assim como containers, volumes e
redes comerciais. A operação no control plane permaneceu `QUEUED`, com
`REMOTE_RESULT_UNAVAILABLE`, e o control plane passou a `live/ready=200/503`.
Esses fatos foram somente registrados: não houve replay, correção, rollback,
restore, edição de operação/journal/banco ou intervenção na stack.

O bootstrap foi retirado fail-closed. O Nginx original foi restaurado
byte a byte, `nginx -t` e reload fecharam, o master PID permaneceu `1514305`,
o negativo do website voltou a 404 e não restou referência S46 na
configuração. JWKS público foi removido e JWT, headers, respostas, payload,
idempotency key e demais temporários protegidos foram destruídos por paths
nominais.

BLOCKED — S46 correction-10 interrompida fail-closed no subestágio corrente

### 59. Retomada com billing liberado, dois probes e bloqueio de capacidade

A correction-10 foi novamente retomada antes do primeiro probe. A mudança
administrativa para repositório público foi apenas observada; nenhum código,
workflow ou validador foi alterado. A decisão futura sobre privacidade e a
fragilidade do `publish-candidate.yml` permaneceram fora desta janela.

A reconciliação somente leitura repetiu todos os gates herdados: control root
`622e03f...` com capabilities válido, control plane `200/200`, três deploys
históricos `FAILED`, zero rollback, `current/previous` ausentes, duas chaves,
porta 8120 livre e release `v0.1.1` imutável no ID `365219520`.

O novo primeiro probe iniciou runner, provando o fim do bloqueio de billing, e
fechou integralmente verde:

```text
run / attempt    31042450721 / 1
trust/probe/outcome  success / success / success
artifacts        trust 8945131491; probe 8945138096; outcome 8945146705
fingerprint      SHA256:37cF/VNa2khhTxoL7x7zk3BPH+Xlww7uMhDrAcD3JPw
controlSha       622e03fa20312717b23fb499bc3ab9e9b3c8f83f
```

Os três sidecars e os bindings fecharam. Somente então a chave antiga
`SHA256:PotdyucjkzRGEuYfWMAp1fg4vWUK3ZlPHW/ZRWjdFAQ` foi removida
atomicamente. `authorized_keys` ficou com exatamente a chave nova, owner e modo
preservados; autenticação direta e capabilities continuaram verdes.

O segundo e último probe também fechou verde:

```text
run / attempt    31042692665 / 1
trust/probe/outcome  success / success / success
artifacts        trust 8945199663; probe 8945223791; outcome 8945228873
fingerprint      idêntica ao primeiro probe
controlSha       622e03fa20312717b23fb499bc3ab9e9b3c8f83f
```

Os seis JSONs dos probes passaram sidecars e secret scan com `unsupported=0`.
A privada e pública locais preservadas foram destruídas com `shred -u` e os
temporários de artifacts foram removidos por paths nominais; a identidade
operacional permanece no secret do environment e a pública única na VPS.

Antes de criar bootstrap, JWT, idempotency key ou POST, as três amostras de
capacidade fecharam RAM available entre `4240572416` e `4257734656` bytes,
swap dedicado ativo com swappiness `10`, disco livre acima de `89865875456`
bytes e load de cinco minutos entre `0.58` e `0.60`. Entretanto, o inventário
de containers encontrou um tenant preexistente fora do Empório em estado
incompatível com o gate:

```text
container       community-frontend
state           running
health          unhealthy
restartCount    0
```

O contrato manda parar antes do POST quando qualquer tenant baseline não está
saudável e proíbe intervir em outro tenant. Nenhum log foi aberto, nenhum
container foi reiniciado ou alterado e nenhuma tentativa de reparo foi feita.
Os três runs comerciais históricos continuam sendo os únicos; não houve
bootstrap, JWT, idempotency key, POST, operação ou novo run comercial.

BLOCKED — S46 correction-10 interrompida fail-closed no subestágio corrente

### 58. Retomada após recusa de billing

A janela da correction-10 foi retomada no ponto anterior ao primeiro probe,
sem repetir rehearsal ou rotação. A reconciliação somente leitura fechou:

```text
HEAD / origin/main       622e03fa20312717b23fb499bc3ab9e9b3c8f83f / idêntico
control root             622e03f...; capabilities válido
control plane            live/ready 200/200
deploys / rollbacks      3 FAILED / 0
current / previous       ausentes
authorized_keys          2; owner deploy-emporio; modo 0600
porta 8120               livre
release v0.1.1           ID 365219520; três assets e digests imutáveis
```

O run `31036986009`, recusado antes de iniciar jobs, foi preservado e não
recebeu rerun. A App deployer criou um dispatch novo, sem inputs:

```text
run / attempt   31037625456 / 1
SHA             622e03fa20312717b23fb499bc3ab9e9b3c8f83f
trust           failure; job não iniciado
probe           skipped
outcome         failure; job não iniciado
artifacts       0
causa GitHub    pagamentos recentes falharam ou limite de gastos insuficiente
```

O billing voltou a impedir qualquer runner de iniciar. Conforme a retomada
autorizada, a execução parou imediatamente: a chave antiga não foi removida,
o segundo probe não foi despachado e nenhuma operação comercial ou POST para
`v0.1.1` foi criado.

BLOCKED — S46 correction-10 interrompida fail-closed no subestágio corrente

### 61. Estado terminal corrente

O estado terminal corrente é o registrado na §60: houve exatamente uma
operação comercial e um run, encerrado `failure` com transporte
`INDETERMINATE/REMOTE_RESULT_UNAVAILABLE`. Não houve replay, segundo POST,
rollback, restore ou correção automática. O bootstrap efêmero foi removido e
o control plane permaneceu `live/ready=200/503` para investigação em nova
autoridade. A reconciliação final confirmou quatro runs comerciais históricos,
todos `failure`, zero rollback, uma chave autorizada, zero recurso comercial,
`current/previous` ausentes e `HEAD == origin/main == 622e03f...`. O scan
exclusivo fechou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0` e o
diff check do relatório ficou limpo.

BLOCKED — S46 correction-10 interrompida fail-closed no subestágio corrente
