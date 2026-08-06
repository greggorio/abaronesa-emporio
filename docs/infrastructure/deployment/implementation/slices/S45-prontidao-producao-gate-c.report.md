# S45 — Prontidão de produção e fechamento do Gate C

> **Data:** 04/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Contrato:** `S45-prontidao-producao-gate-c.task.md`
> **Resultado:** `IN_PROGRESS — Gate C fechado; aguardando aceite e autorização do primeiro deploy acompanhado da v0.1.0`

## 1. Checkpoint e invariantes iniciais

O contrato foi lido integralmente antes da primeira mutação. Os hashes foram
confirmados:

```text
task S45      5f664860a1436f9533e12dba6603bbb086e07e9b8de10b536c191836a38d7cd8
report S44    2291d97a36c6984ca9e0df2eff05850b980d01bcca249814979218013c266118
HEAD inicial  ae8514893e9deb97e64e4e56bf1f54c0b005a107
origin/main   7e84fb95974c77a2a710d73f812a4d6bed1e4eb1
divergência  ahead 1 / behind 0
stage/diff   vazios
```

Os relatórios S39–S44 estavam não rastreados e fora do stage. GitHub, App,
instalação exclusiva no repositório, bot `313092947`, environment `production`,
transporte SSH, control root e fingerprint do PEM da App foram revalidados sem
exibir material protegido. `DEPLOYER_ACTOR_IDS=313092947` continuava como
variável do repositório. `deploy-production.yml` e `rollback-production.yml`
possuíam zero runs.

Na VPS, antes de qualquer mutação:

```text
control plane          enabled/active; 2 containers healthy
live/ready             200/200
listener               127.0.0.1:8180
porta 8120             livre
Docker                 39 running / 41 total; 27 volumes; 19 redes; 33 imagens
stack comercial        ausente
swap alvo              ausente; nenhum swap ativo
Nginx alvo/certs       ausentes
configuração comercial ausente
```

A verificação canônica do control root fechou verde após remover somente caches
Python gerados por uma leitura anterior; os 188 arquivos esperados estavam
íntegros, sem arquivo ausente ou alterado. As verificações remotas posteriores
usaram Python com `-B`.

## 2. Correção causal versionada

O patch implementou a fronteira fechada pelo contrato:

- quatro propriedades da identidade deployer entregues exclusivamente ao
  backend;
- chave file-backed montada somente no backend, no path fixo
  `/run/secrets/release-control-deployer-identity-private-key`, UID/GID 10001 e
  modo `0400`;
- frontend com `RELEASE_CONTROL_MODE=${RELEASE_CONTROL_MODE:-disabled}`;
- modo real de produção `deployer`, preservando `disabled` fora dela;
- server blocks versionados para os dois hosts;
- validador de Nginx e mutantes de host, porta, rota, TLS e isolamento;
- validadores, testes, documentação operacional e CI atualizados.

Commit principal:

```text
a48788461c28bf61368ad4e8b82a759bb4834c22
feat: prepare production Gate C wiring
13 files changed, 323 insertions(+), 23 deletions(-)
```

O Candidate desse commit revelou um defeito causal real: o secret file-backed
usava expansão obrigatória mesmo com a ponte desabilitada, quebrando a matriz
integrada sem uma chave de produção. Nenhuma mutação da VPS havia começado. A
correção preservou `/dev/null` somente no default `disabled` e fez o validador
rejeitar `/dev/null` quando a identidade está habilitada.

```text
de4c4872ee3a6994bb66ecd173c0ffe81fa5fbd4
fix: preserve keyless disabled deployer mode
4 files changed, 15 insertions(+), 4 deletions(-)
```

Ambos os pushes foram fast-forward. Não houve amend, rebase, force ou push de
relatório.

## 3. Matriz local

As provas locais foram executadas antes de cada commit aplicável. Resultado
terminal da matriz do SHA final:

```text
oito suítes canônicas                 1000 testes verdes
release_control/tests                  332 passed
backend mvn verify                      85 passed
backend identidade deployer             34 passed
website_back                            65 passed
frontend ERP                           163 passed + build
website_front                           34 passed + build
WhatsApp                                 7 passed
validadores registrados                 29 verdes
invocabilidade                           27 entradas
secret scan completo                    clean; scanned=2496; unsupported=0
git diff --check                        verde
```

Um primeiro `mvn verify` concorrente colidiu no banco local de testes; a
reexecução isolada fechou 85/85. Os testes Node que encontraram
`node_modules` local pertencente a root foram executados em cópias temporárias
exatas com Node 24 e tiveram limpeza dirigida integral.

## 4. Gates remotos e artifacts

O primeiro Candidate documenta o defeito causal já corrigido:

```text
SHA a48788461c28bf61368ad4e8b82a759bb4834c22
CI                 30950271959  success  13/13
Publish Candidate  30951186047  failure  job integrado
```

Gates terminais do SHA final:

```text
SHA                de4c4872ee3a6994bb66ecd173c0ffe81fa5fbd4
CI                 30952146064  success  13/13
Publish Candidate  30952948377  success  11/11
```

Os sidecars SHA-256 dos artifacts `candidate-manifest`,
`candidate-integration-result` e `candidate-outcome` foram confrontados. O
manifesto passou pelo validador canônico e todos os objetos ficaram vinculados
ao SHA, run `30952948377` e attempt `1`. A integração provou sete serviços
healthy, nove probes e limpeza com zero containers, volumes e redes; as seis
imagens do candidato ficaram ausentes após a limpeza. O outcome publicado
referenciou o artifact `8910083250` pelo digest esperado.

`deployable=false` foi preservado: a S45 valida um candidato e não publica uma
release. Não houve run de `Publish Release Control Image` para o SHA final nem
alteração de `v0.1.0`.

## 5. Configuração comercial protegida

Somente depois dos gates remotos verdes foram criados:

```text
/opt/sistemas/emporio/shared/.env                                      deploy-emporio:deploy-emporio 0600
/opt/sistemas/emporio/shared/secrets/                                 deploy-emporio:deploy-emporio 0700
release-control-deployer-identity-private-key.pem                     10001:10001                 0400
/opt/sistemas/emporio/shared/backups/                                 deploy-emporio:deploy-emporio 0700
/home/gregorio/.config/emporio/production/operator-bootstrap.env      gregorio:gregorio           0600
```

RSA-3072 PKCS#8, `kid`, três senhas PostgreSQL distintas, token interno, sync
key e bootstrap foram gerados diretamente na VPS sem stdout, argv persistente
ou histórico. O bootstrap foi transferido para a estação e o temporário remoto
foi removido. Nenhum valor foi exibido ou registrado.

A fingerprint pública da chave da ponte é:

```text
f14283370d5eff78346b314ed5a1e2235d819f7aca5ed123fb93651b20e62bf7
```

A chave assinou e verificou uma mensagem offline. Em container read-only, sem
rede, o UID 10001 conseguiu lê-la e o UID 10002 foi negado. No host, o service
account `emporio-release-control` e `nobody` foram negados pelo diretório. Os
containers de prova foram removidos.

O `.env` contém, sem exibição, os seis refs imutáveis do BOM de `v0.1.0`, o
PostgreSQL comercial pelo digest contratado, os sete limites explícitos, URLs
HTTPS, Flyway desabilitado, bootstrap preparado, integrações externas
desabilitadas e a identidade deployer habilitada. O render real como
`deploy-emporio` retornou:

```text
services=7; images-by-digest=7; volumes=4; networks=2; ports=1
porta única=127.0.0.1:8120; build=0; socket=0; Swarm=0; placeholder=0
```

A primeira chamada a `docker compose config --quiet` herdou `/root` e falhou
em `stat .: permission denied`; a repetição a partir do diretório temporário de
`deploy-emporio` fechou verde e o diretório foi removido. Nenhum container,
volume, rede, `current`, `previous` ou bundle comercial foi criado.

## 6. Capacidade e swap

Antes da criação havia 97.699.672.064 bytes livres em ext4 e nenhum swap
preexistente. Foram criados exclusivamente:

```text
/swapfile-emporio                         root:root 0600 8589934592 bytes
/etc/sysctl.d/90-emporio-swap.conf        vm.swappiness=10
/etc/fstab                                1 entrada marcada emporio-s45
```

`mkswap`, `swapon`, releitura do sysctl, persistência e repetição idempotente
fecharam verdes. Os 39 containers ativos mantiveram exatamente os mesmos IDs e
`StartedAt`; a contagem total permaneceu 41. Memória disponível variou de
3.382.128.640 para 3.381.071.872 bytes e o load de `2.23 2.50 2.69` para
`2.13 2.47 2.68`, sem restart de container, reboot ou alteração de outro swap.

## 7. Nginx e TLS

Os dois hostnames resolviam exclusivamente para `31.97.251.16`. O baseline
possuía 57 arquivos em `sites-available`, 25 sites habilitados, 27 certificados,
duas contas Certbot e timer ativo. Os alvos estavam ausentes.

Foi criado um webroot ACME, instalado primeiro um bloco HTTP-only, executado
`nginx -t` e feito reload. O primeiro probe imediatamente após o reload recebeu
404 durante a troca assíncrona dos workers; sem solicitar certificado, a
revalidação da configuração efetiva retornou 200 para o mesmo arquivo. Foram
então emitidos certificados separados pela conta existente e instalada a
configuração versionada com SHA-256:

```text
a9b1c60bda497cfbac4ab17e248e13f4468b5f0d2c3768e10603f86500fabc8c
```

O PID master do Nginx `1514305` foi preservado; houve somente reload, nunca
restart. Provas finais:

```text
erp-emporio.abaronesa.net.br  HTTP 301; TLS 1.2/1.3; TLS 1.1 rejeitado
emporio.abaronesa.net.br      HTTP 301; TLS 1.2/1.3; TLS 1.1 rejeitado
ACME inexistente              404 sem redirect
ERP /v1/capabilities          401 sem token, vindo do control plane
website /v1/capabilities      404 local, sem proxy ao control plane
ERP identity/deployer         502, gateway comercial ainda ausente
raízes dos dois hosts         502, gateway comercial ainda ausente
```

Os certificados contêm o SAN exato, validam hostname e permanecem válidos por
mais de 30 dias, com expiração em 02/11/2026. Certbot continuou
enabled/active. O estado final é 58 arquivos disponíveis, 26 habilitados e 29
certificados: somente `+1/+1/+2` dos alvos.

O contrato cita `events.abaronesa.net.br`, que já não resolvia antes da S45 e
continuou sem resolver depois. A amostra efetivamente configurada
`eventos.abaronesa.net.br` permaneceu HTTP 200 com certificado idêntico;
`erp.smartdataerp.com.br` permaneceu HTTP 502 com certificado idêntico. Nenhum
dos 25 sites preexistentes foi editado.

## 8. Ensaio isolado de backup e restore

O ensaio usou apenas nomes `emporio-s45-*`, rede interna, volumes descartáveis,
temporário por `mktemp -d` e:

```text
postgres:16.10-alpine3.22@sha256:029660641a0cfc575b14f336ba448fb8a75fd595d42e1fa316b9fb4378742297
```

Foram criados dois bancos sintéticos, schema e sentinelas não sensíveis. Dumps
custom foram gravados diretamente em arquivo; tamanho e SHA-256 foram fechados
em manifesto. A origem e seu volume foram destruídos antes da restauração em
novo volume. Após verificação prévia dos bytes, o restore confirmou um schema,
duas linhas no primeiro banco, uma no segundo e ambos os sentinelas.

A primeira invocação usou por engano a opção `-v ON_ERROR_STOP=1` de `psql` no
`pg_restore`; ele falhou antes do restore e o trap removeu integralmente
containers, volumes, rede, temporário e imagem. A repetição do zero usou
`pg_restore --exit-on-error` e fechou verde.

Uma cópia de dump recebeu adulteração de um byte; tamanho/hash divergiram e ela
foi rejeitada antes de criar banco ou chamar restore. Ao final ficaram zero
containers, volumes, redes, dumps, temporários ou imagem do ensaio. Nenhum
banco ou volume do control plane ou de outro tenant foi acessado.

## 9. Gate C terminal

Provas finais na VPS:

```text
unit/control plane        enabled/active; 2 containers healthy
live/ready               200/200
listener                 127.0.0.1:8180 exclusivo
porta 8120               livre
migration                0003_commercial_rollback
sync                     deployments/releases, 2/2 verdes, sem drift/erro
release                  v0.1.0; source 38385c1...; PUBLISHED; 6 componentes
elegibilidade            true para primeira instalação
current installation     0
deployment operations    0
publication operations   0
idempotency              0 / 0
rollback backups         0
audit/dispatch audit     0
Docker                   39/41 containers; 27 volumes; 19 redes; 33 imagens
stack comercial          0 containers, volumes e redes
resíduo do ensaio        0
swap                     8 GiB ativo; swappiness 10; fstab 1 entrada
Nginx                    nginx -t verde; TLS e rotas verdes
```

No GitHub e Git:

```text
HEAD/origin/main/remoto  de4c4872ee3a6994bb66ecd173c0ffe81fa5fbd4
divergência              0 / 0
stage/diff tracked       vazios
CI/Candidate             30952146064 / 30952948377, success
image workflow no SHA    0
deploy runs              0
rollback runs            0
v0.1.0                   única e inalterada em 38385c100ab8b0ae07099b6a5a7b016b7c2b7322
DEPLOYER_ACTOR_IDS       313092947
```

Somente os relatórios S39–S45 permanecem não rastreados. A S45 não executou
deploy, rollback, POST autenticado, migration comercial, start comercial,
update, upgrade, reboot, Nginx restart, firewall, DNS ou publicação de release.
Não aceitou a S45 nem criou S46.

O scanner aplicado somente a este relatório terminal retornou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`.

IN_PROGRESS — Gate C fechado; aguardando aceite e autorização do primeiro deploy acompanhado da v0.1.0

## Retomada correction-01

> **Data:** 04/08/2026
> **Autoridade cumulativa:** task S45 e correction-01
> **CWD:** `/home/gregorio/git/baronesa/emporio`

### Checkpoint e reprodução causal

Os hashes da task, correction e relatório conferiram com os valores delegados.
O checkpoint local era `cc2c2f4f3eb0902db10d66228ca6e5ab487428d9`,
`origin/main` e remoto eram
`de4c4872ee3a6994bb66ecd173c0ffe81fa5fbd4`, com ahead 1 / behind 0,
stage e diff tracked vazios e somente os relatórios S39–S45 não rastreados.
CI `30952146064` permanecia 13/13 e Candidate `30952948377` 11/11;
deploy e rollback continuavam com zero runs e `v0.1.0` era a única release,
inalterada.

Os dois bloqueios foram reproduzidos antes do patch:

```text
backend scope       deployment:read deployment:execute
frontend scope      deployment:read deployment:execute deployment:rollback
Nginx ERP           somente /api/release-control/v1/ -> 127.0.0.1:8180
gateway comercial   /api/deployment-control/ -> 404 defensivo
```

A análise de elegibilidade expôs ainda um efeito causal direto: com zero
instalação corrente, o runtime aceitava somente uma release com
`previousRelease=null`. Assim `v0.1.1`, que deve preservar `v0.1.0` como
predecessora histórica, continuaria inelegível. A correção fail-closed passou
a aceitar como primeira instalação somente a release SemVer mais recente cuja
cadeia até a raiz esteja completa, sem ciclo, estritamente crescente e com
migrations prefixais.

### Patch causal

- backend emite exatamente os três scopes na ordem contratada;
- validador cruzado compara backend, resposta Java, frontend, runtime Python,
  schema e S27, com mutantes de ordem, ausência, extra e guards;
- Nginx ERP encaminha os dois namespaces exatos a `127.0.0.1:8180`, enquanto o
  website retorna 404 local nos dois e o gateway preserva o bloqueio defensivo;
- candidatos novos finalizados registram `deployable=true`; lineage histórica
  `false` permanece validável, mas não pode ser finalizada nem publicada;
- digests derivados dos exemplos canônicos foram recalculados;
- primeira instalação corrigida com mutantes para predecessor ausente, ciclo,
  SemVer inválido e migrations não prefixais;
- documentação e validadores históricos foram alinhados à ativação real de
  rollback em S26/S27.

### Matriz local antes do commit

```text
oito suítes canônicas                 1010 testes verdes
  docker 117; ci 31; candidates 75; releases 301
  deploy 445; security 26; compose 6; gateway 9
release_control/tests                  336 passed
backend mvn verify                      85 passed
backend identidade deployer             23 passed atingidos
website_back                            65 passed
frontend ERP Node 24                   163 passed + lint + build
website_front Node 24                   34 passed + build
WhatsApp Node 24                         7 passed + syntax checks
validadores/invocabilidade              verdes; 27 entradas invocáveis
catalog:valid e git diff --check         verdes
secret scan staged                       clean; scanned=29; unsupported=0
```

A primeira invocação dos projetos Node encontrou o binário padrão 22; frontend
e WhatsApp passaram, mas o website encontrou resíduo root-owned em
`node_modules`. As três provas foram repetidas com o binário Node 24 explícito;
o website foi executado em cópia temporária sem `node_modules`, depois removida
integralmente. Uma checagem informativa `mypy --strict` exibiu 65 erros
preexistentes de aliases Pydantic em cinco arquivos; ela não integra a matriz
contratada nem o workflow CI e não foi usada como substituta de nenhum gate.

O relatório permanece não rastreado e fora do stage. Nenhum secret, token,
PEM, senha, pepper, JWT, idempotency key ou header foi registrado.

### Commit, push e gates remotos

O scan completo terminou limpo:

```text
secret-scan:clean:scanned=2500:allowed=1072:unsupported=0:history_scanned=162754
```

O commit técnico normal foi criado sem amend/rebase:

```text
058824ed39c7316ef80d1ebe657a39fa3ccf2094
fix: make v0.1.1 ready for the first deploy
29 files changed, 406 insertions(+), 86 deletions(-)
```

Um único push fast-forward publicou os dois commits locais pendentes, de
`de4c4872ee3a6994bb66ecd173c0ffe81fa5fbd4` até `058824e`. O relatório não
foi incluído. A CI correspondente é `30958550796`; os gates seguem registrados
abaixo quando terminais.

### Gates do primeiro patch e ciclo causal 1

O SHA `058824ed39c7316ef80d1ebe657a39fa3ccf2094` fechou CI
`30958550796` em 13/13 e Candidate `30959256781` em 11/11. O artifact
terminal registrou `deployable=true`, seis componentes, sidecars válidos,
outcome `published` e vínculo exato ao SHA/run/attempt. O workflow da imagem
operacional `30959766833` fechou 4/4 e publicou, com manifesto e outcome
válidos, o digest `sha256:5e5e41ee2eb6e858044b9049af050951e51feaeb8a98904bf9dfca878da419b5`.

Antes de qualquer intenção ou dispatch de release global, o publisher foi
montado em loopback com PostgreSQL 16 efêmero, a App preservada e a ponte
RS256 isolada. A sincronização falhou fechada com
`CANDIDATE_BINDING_INVALID`. A causa foi reproduzida diretamente no artifact
imutável que originou a v0.1.0: seu candidato possui `deployable=false`. O
runtime do primeiro patch exigia `true` ao validar todos os artifacts
históricos e, por isso, parava antes de alcançar o candidato novo.

Não houve POST de publicação, intenção, dispatch de release, tag ou release.
Publisher, ERP e PostgreSQL efêmeros foram encerrados e o container/volume
descartável removido antes da correção seguinte.

A correção causal mantém ambos os valores booleanos válidos na evidência
histórica, mas atribui `READY` exclusivamente quando o candidato ainda não foi
publicado e `deployable` é exatamente `true`; candidatos históricos `false`
ficam `NOT_ELIGIBLE`. O teste novo usa a forma real da v0.1.0 e prova sync
verde sem torná-la publicável.

Matriz repetida após o patch:

```text
oito suítes canônicas                 1010 testes verdes
release_control/tests                  337 passed
backend mvn verify                      85 passed
website_back                            65 passed
frontend ERP Node 24                   163 passed + lint + build
website_front Node 24                   34 passed + build
WhatsApp Node 24                         7 passed + syntax checks
validadores/invocabilidade              verdes; 27 entradas invocáveis
catalog:valid e git diff --check         verdes
```

O segundo commit técnico, também normal e sem amend/rebase, foi:

```text
cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
fix: preserve historical candidate eligibility
```

O remoto ainda apontava para `058824e` antes do segundo push. O push foi
fast-forward até `cf3385f`; relatório e demais arquivos não rastreados não
foram incluídos.

### Gates terminais e nova imagem operacional

Todos os gates do SHA terminal fecharam no primeiro attempt:

```text
CI 30960751303                    13/13 success
Publish Candidate 30961397124    11/11 success
imagem operacional 30961863663     4/4 success
```

O candidato canônico
`candidate-cf3385f1012b9661ddbc2e83d5241aaa8633f8fd-30961397124-1`
possui `deployable=true`, seis componentes e manifesto
`sha256:6dcfe304683a84e1e0c7b3fe2f17237750e6c53105188b673426d136a12293d5`.
Manifesto, sidecar e outcome da imagem operacional vinculam exatamente
SHA/run/attempt e publicaram:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:9d188caf022bc1e02df9ad8b2739e4cc4152eb887fa60ed811dc144b4a37bc1f
```

### Publicação global única pela UI/runtime

O publisher descartável foi reconstruído somente depois dos gates verdes, com
PostgreSQL 16 em tmpfs, ponte RS256 efêmera e a App publisher preservada. O
sync inicial classificou o candidato histórico da `v0.1.0` como
`NOT_ELIGIBLE|false`, o candidato novo como `READY|true`, manteve `v0.1.0`
`PUBLISHED` e encontrou zero operações.

A UI em modo publisher selecionou explicitamente o candidato terminal e
preencheu exatamente:

```text
tag/name     v0.1.1
previous     v0.1.0
tipo         PATCH
descrição    Corrige o scope da identidade deployer e o roteamento same-origin do control plane para o primeiro deploy de produção.
```

Houve um único POST, uma única intenção e um único dispatch. A operação
`pub_eb39b0de08fa4f41ad4e9c3a82976dee` terminou `PUBLISHED|CONFIRMED` no run
`30962554318`, 4/4 jobs verdes, attempt 1. A consulta de todos os runs do
workflow encontrou exatamente uma ocorrência desse display title.

A release global resultante foi validada:

```text
release ID       365219520
tag/name         v0.1.1
target           cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
draft/prerelease false / false
assets           metadata.json, release.json, release.json.sha256
release.json     sha256:6e6ac56089a935c817608a37ab06823e329649a78b7acd6d967c0dfccaecd31e
componentes      6; BOM byte/object-identical ao Candidate
```

Sidecars, outcome e os três assets foram revalidados por digest, inclusive o
vínculo SHA/run/attempt. A `v0.1.0` permaneceu integralmente preservada: release
ID `364130074`, target
`38385c100ab8b0ae07099b6a5a7b016b7c2b7322`, `draft=false`,
`prerelease=false`, três assets com os mesmos tamanhos e digests anteriores.

O publisher foi parado e provou indisponibilidade local enquanto ERP e
frontend continuaram em 200. Após restart, readiness e sync voltaram verdes,
o candidato novo passou a `NOT_ELIGIBLE`, ambas as releases permaneceram
publicadas e o run count continuou um. Não houve redispatch. Processos,
container, volume, banco, RSA e secrets efêmeros foram removidos de forma
dirigida; App, instalação, PEM e configuração permanentes não foram alterados.

### Control plane na VPS

A VPS puxou somente a nova imagem operacional por digest. A troca atômica
alterou exclusivamente `RELEASE_CONTROL_IMAGE` em
`/etc/emporio/release-control.env`; o hash das demais linhas, owner e modo
foram preservados. O restart controlado manteve o mesmo container PostgreSQL e
iniciou o runtime terminal:

```text
unit                         active / enabled
release control image        ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:9d188caf022bc1e02df9ad8b2739e4cc4152eb887fa60ed811dc144b4a37bc1f
image revision               cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
containers                   release_control healthy; PostgreSQL healthy
listener                     127.0.0.1:8180 exclusivo
live / ready                 200 / 200
migration                    0003_commercial_rollback
sync deployments/releases    drift=false; error=-; last success presente
releases                     v0.1.0 e v0.1.1 PUBLISHED; 6 componentes cada
eligibility                  v0.1.1=true; v0.1.0=false
current installation         0
deployment operations        0
deployment idempotency       0
rollback backups             0
publication operations       0
publication idempotency      0
```

A elegibilidade foi lida pelo `DeployerService.list_releases` real dentro da
imagem, sem autenticação fabricada nem POST. Existe um único audit event
histórico `sync.releases|invalid`, sem `operation_id`, gravado pelo digest
anterior no instante em que a `v0.1.1` apareceu globalmente. Ele não representa
operação ou dispatch e foi preservado como evidência; o runtime atual está
sincronizado e verde.

### Nginx e configuração comercial

O arquivo versionado corrigido foi instalado exatamente em
`/etc/nginx/sites-available/emporio.conf`, hash
`43cdaa4b962678061444f6fa78028a1094b9d65ba161ada84dbbbb2a1d34e7d3`.
`nginx -t` passou antes e depois do reload; o master PID `1514305` e o agregado
dos demais sites foram preservados. Probes TLS com resolução fixada à VPS:

```text
ERP /api/release-control/v1/capabilities    401
ERP /api/deployment-control/v1/releases     401
ERP /api/deployment-control/v1/current      401
website nos mesmos três paths               404
```

Somente os seis refs de imagem do `.env` comercial foram substituídos
atomicamente pelo BOM da `v0.1.1`. O hash das seis linhas instaladas e o hash
derivado diretamente de `release.json` coincidem em
`ba734d26efc12b6c3ff6f33bb4e9b7a4186ca4d1bc608133eb2dd1f758e1cd05`;
todas as demais linhas, owner e modo foram preservados.

A primeira prova `docker compose config --quiet` herdou o CWD `/root` e foi
negada por `stat .: permission denied`, sem mutação Docker. Ela foi repetida
como `deploy-emporio` a partir de `/tmp`, apontando explicitamente para o
Compose e `.env` protegidos, e fechou verde com sete serviços, seis refs do BOM
mais PostgreSQL por digest, quatro volumes e duas redes.

As provas negativas finais retornaram:

```text
porta 8120                              0 listeners
projeto comercial emporio               0 containers / 0 volumes / 0 redes
projeto default compose                  0 containers / 0 volumes / 0 redes
seis refs v0.1.1 presentes localmente    0
deploy-production.yml                    0 runs
rollback-production.yml                  0 runs
```

Portanto não houve pull, start, migration, deploy ou rollback comercial.

### Checkpoint terminal

Os artifacts temporários de auditoria do candidato, imagem e publicação foram
removidos de forma dirigida. A última reconciliação Git, após `fetch`, fechou:

```text
HEAD/origin/main/remoto  cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
divergência              ahead 0 / behind 0
stage/diff tracked       vazios
não rastreados           somente os sete relatórios S39–S45
```

A correction-01 não aceitou a S45, não criou a S46, não alterou tracker/task e
não executou deploy, rollback, migrations comerciais, start ou pull da stack
comercial.

IN_PROGRESS — Gate C corrigido e v0.1.1 elegível; aguardando aceite e autorização do primeiro deploy acompanhado
