# S44 — Implantação do control plane deployer na VPS

> **Contrato:** `S44-implantacao-control-plane-deployer-vps.task.md`
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Estado:** execução retomada após interrupção do executor anterior
> **Relatório:** não rastreado, não staged e não commitado

## 1. Autorização e integridade

A autorização literal exigida pela seção 2 da task foi fornecida na delegação.
O executor anterior foi confirmado como interrompido antes da retomada.

```text
task SHA-256  0ca6665a5efc26f912d39f563132ee6c90d4270526ab0de5959b61938e70422f
HEAD local    9699214582c8e74ed6c005eb2ad1e04ec950f5aa
origin/main   9699214582c8e74ed6c005eb2ad1e04ec950f5aa
remoto main   9699214582c8e74ed6c005eb2ad1e04ec950f5aa
divergência   ahead 0 / behind 0
stage         vazio
```

Relatórios S39–S43 permanecem os únicos resíduos não rastreados anteriores à
criação deste relatório. A tag `v0.1.0` continuava apontando para
`38385c100ab8b0ae07099b6a5a7b016b7c2b7322`; os workflows de deploy e rollback
tinham zero runs; `DEPLOYER_ACTOR_IDS` estava ausente.

## 2. Estado herdado e ciclos causais

| Ciclo | Commit | Resultado |
|---|---|---|
| patch obrigatório | `1316bb5ddc039ea5213ee95a52249205684aaf38` | CI e candidato verdes; publicação `30927751069` falhou no scan antes de autenticar |
| correção causal 1 | `9699214582c8e74ed6c005eb2ad1e04ec950f5aa` | pin `cryptography` atualizado de 49.0.0 para 50.0.0 por `CVE-2026-69247` |

Resta no máximo um commit corretivo previsto pela seção 9. Nenhuma tentativa de
start havia ocorrido no estado reconciliado.

## 3. Baseline VPS reconciliado

Sessão root estrita: `BatchMode=yes`, `StrictHostKeyChecking=yes`, sem agent/X11,
sem password prompt e usando a host key já confiada.

```text
host/arquitetura        srv1006846 / x86_64
Swarm                   inactive
Docker                  37 running / 39 total / 26 volumes / 18 networks / 31 images
porta 8180              livre
root operacional        ausente
/etc/emporio            ausente
usuário/unidade alvo    ausentes
containers/volumes/redes alvo  0 / 0 / 0
```

O control root da S43 foi revalidado pelo blob canônico transmitido por stdin:

```text
control-root-package:verified:9731954d474fb68ec1384a525e1075f9a5542e24
capabilities: repository greggorio/abaronesa-emporio, user deploy-emporio,
              controlSha 9731954d474fb68ec1384a525e1075f9a5542e24
```

Uma primeira invocação tentou executar o builder/verificador como se estivesse
instalado no host e retornou exit 2 por arquivo ausente. Nenhum alvo foi mutado;
a forma correta por stdin acima fechou com exit 0 e não caracteriza ciclo causal.

## 4. Gates do pacote operacional

```text
CI 30923423701 / commit 1316bb5     success
Candidate 30924301125               success
CI 30929281890 / commit 9699214     success
Candidate 30930060013               success (11/11 jobs)
```

Revalidação local após a retomada:

```text
release_control, ambiente uv locked  332 passed, exit 0
tools/docker/tests                    117 tests, exit 0
tools/ci/tests                         31 tests, exit 0
tools/candidates/tests                 75 tests, exit 0
tools/releases/tests                  300 tests, exit 0
tools/deploy/tests                    431 tests, exit 0
tools/security/tests                   26 tests, exit 0
tools/compose/tests                     4 tests, exit 0
tools/gateway/tests                     4 tests, exit 0
oito suítes canônicas                988 tests, todas OK
30 validadores/contratos             todos exit 0
git diff --check                     exit 0
```

Entre os validadores verdes estão `control-root-package-contract`, deploy,
production adapter, deployer runtime, deployment executor/plan, identidade
deployer, package/workflow da imagem operacional, rollback, CI/invocability,
migrations, workflow inventory, candidato, Compose, imagens Node/Java, gateway,
publisher, release control, bootstrap e catálogo (`catalog:valid`).

O secret scan terminal do CI `30929281890`, sobre exatamente o commit final,
registrou:

```text
secret-scan:clean:scanned=2492:allowed=944:unsupported=0:history_scanned=142783
```

Uma repetição local do scanner foi interrompida com exit 130 somente depois da
descoberta do blocker terminal da seção 7, em observância ao stop fail-closed;
nenhum resultado verde foi inferido dessa execução incompleta.

A tentativa inicial da suíte com o Python global retornou exit 1 porque aquele
ambiente não contém `psycopg`. A mesma suíte foi imediatamente executada pelo
ambiente isolado materializado de `release_control/uv.lock` e passou; não houve
alteração de código, dependência ou lock.

## 5. Publicação da imagem operacional

```text
run 30930719302  commit 9699214582c8e74ed6c005eb2ad1e04ec950f5aa
trust             success
verify            success
publish           success
outcome           success
status            published
artifact manifest 8901092793
artifact outcome  8901100126
package version   1098483203
manifest SHA-256  14df7b95152aeb0c3e0608af8a5450720de09f688b849c28dc852772da1f565d
digest             sha256:2a214f0c575ab4391855a4cd3b1f3727bce8593e90e265bd89a6564171caff37
immutable ref      ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:2a214f0c575ab4391855a4cd3b1f3727bce8593e90e265bd89a6564171caff37
package tag        src-9699214582c8e74ed6c005eb2ad1e04ec950f5aa-run-30930719302-1
```

Os quatro jobs e todos os seus steps fecharam verdes. Manifesto, sidecar e
`manifestSha256` do outcome coincidiram byte a byte. Manifest e outcome também
coincidiram no digest; o validador canônico retornou
`release-control-image:valid`. O package contém duas versões: a anterior foi
preservada e a nova está vinculada unicamente ao SHA/run/attempt acima.

O primeiro download genérico tentou também baixar o build record interno do
BuildKit e recebeu `zip: not a valid zip file`. Os dois artifacts contratuais
foram então baixados nominalmente, validados e removidos do diretório temporário;
nenhum rerun ou republicação foi realizado.

## 6. GitHub App deployer

Não iniciada por causa do blocker anterior à primeira mutação da VPS. Os dois
paths locais canônicos permaneceram ausentes e `DEPLOYER_ACTOR_IDS` permaneceu
ausente. Nenhuma App, instalação, PEM, env local, bot ou variável foi criada.

O PAT atual não possui autorização de GitHub App para listar instalações
(`GET /user/installations` retornou 403), sem mutação. A confirmação humana da
UI administrativa deixou de ser necessária nesta execução após o stop da seção
3.2; não foi feita tentativa de criação por manifesto.

## 7. Blocker técnico terminal antes da primeira mutação

A seção 3.2 manda revalidar no registry o descriptor do PostgreSQL fixado antes
da primeira mutação e determina parar, sem substituir por tag móvel ou versão de
conveniência, se ele não resolver mais para `linux/amd64`.

Referência imutável exigida:

```text
postgres:16.6-alpine@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f
```

Provas:

```text
Docker local: docker manifest inspect <ref>  -> exit 1
VPS aprovada: docker manifest inspect <ref>  -> exit 1
stderr VPS: manifest verification failed for digest sha256:589f3b24...eb0f
app manifest na mesma VPS                   -> exit 0
```

A resolução verde do manifest privado da aplicação na mesma sessão prova que a
rota, a credencial GHCR preexistente e o Docker do host estavam funcionais. O
defeito é específico ao descriptor PostgreSQL fixado. O host não possui `jq`;
isso só afetou a tentativa de resumir o JSON verde da aplicação e não a
resolução dos manifests nem a conclusão sobre o PostgreSQL.

Não foi permitido escolher outro digest, outra tag ou editar a task imutável.
Também não se consumiu o último commit corretivo: alterar a referência fechada
exige nova decisão/autoridade documental, não uma correção causal implícita.

Tentativas de start: **zero**.

## 8. Invariantes finais

Snapshot final VPS, idêntico ao baseline:

```text
Docker                         37 running / 39 total / 26 volumes / 18 networks / 31 images
porta 8180                     livre
/opt/sistemas/emporio-control  ausente
/etc/emporio                   ausente
unit/user alvo                 ausentes
containers/volumes/redes alvo  0 / 0 / 0
imagens finais localmente      ausentes
```

Snapshot final Git/GitHub:

```text
HEAD/origin/main/remoto  9699214582c8e74ed6c005eb2ad1e04ec950f5aa
stage                    vazio
relatórios S39-S44       não rastreados
DEPLOYER_ACTOR_IDS       ausente
deploy runs              0
rollback runs            0
v0.1.0                   38385c100ab8b0ae07099b6a5a7b016b7c2b7322, inalterada
```

Nenhum deploy, rollback, operação, dispatch, stack comercial, Nginx, TLS,
backup, restore, swap, update, reboot, Swarm, pull ou login foi executado. Os
temporários exatos `/tmp/emporio-s44-artifacts.vJhLWM` e
`/tmp/emporio-s44-uv-cache` foram removidos. Control root da S43 e todos os
outros sistemas foram preservados.

O scanner canônico aplicado somente a este relatório retornou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`.

BLOCKED — S44 interrompida fail-closed na primeira causa técnica

## 9. Retomada correction-01

Autoridade cumulativa lida integralmente na ordem exigida. Integridades antes
da retomada:

```text
task SHA-256          0ca6665a5efc26f912d39f563132ee6c90d4270526ab0de5959b61938e70422f
correction-01 SHA-256 8ed558507e4959ceb36bc60d6fb6e23b732ee5ec2c972303d5750d4d64183630
relatório anterior    bb5893476d209cc57ab35455120461cf5cba41f9fbaf56d5c882cb465039a058
HEAD local            e370b7bc5580492527a344e96b0e951fce5fedd6
origin/main/remoto    9699214582c8e74ed6c005eb2ad1e04ec950f5aa
divergência           ahead 1 / behind 0
stage                 vazio
```

Autorização literal da correction-01 presente na delegação. CI `30929281890`,
Candidate `30930060013`, publicação `30930719302`, scan, artifacts e package
validation foram herdados sem repetição.

### 9.1 Referência OCI canônica do PostgreSQL

Referência exclusiva:

```text
postgres@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f
```

Provas anteriores à primeira mutação:

```text
docker manifest inspect local  exit 0; OCI image manifest v1; schema 2
docker manifest inspect VPS    exit 0
Docker Hub tag metadata        tag 16.6-alpine active; índice sha256:1d04b9ba...;
                               filho único do digest aprovado: linux/amd64 active
config digest                  sha256:5c773214aed7adab0900cd0a05dbc468348f76fe1e4c2ca5b0e222e9531f7811
```

A diagnosis anterior fica corrigida: o formato `tag@digest-filho` era inválido;
o digest e os bytes amd64 nunca desapareceram. Nenhum upgrade de PostgreSQL foi
feito ou autorizado.

### 9.2 GitHub App deployer e allowlist

A reconciliação administrativa inicial mostrou somente a App publisher irmã;
nenhuma App deployer compatível existia. Um único GitHub App Manifest flow
oficial foi concluído com confirmação humana na interface do GitHub. Uma
primeira submissão do manifest foi rejeitada antes da criação porque o GitHub
exigiu `hook_attributes.url`; a inclusão da URL canônica, mantendo o webhook
inativo, fechou a criação única.

```text
App ID                 4487372
nome                   Emporio Deployer 1315264421
slug                   emporio-deployer-1315264421
owner                  greggorio / 35626201 / User
permissões             actions:write, contents:read, metadata:read
events                 []
webhook                 inactive na UI; API não expõe o campo active
instalação              151259606 / repository_selection=selected
repositório             greggorio/abaronesa-emporio / 1315264421, único
bot                     emporio-deployer-1315264421[bot] / 313092947 / Bot
DEPLOYER_ACTOR_IDS      criado uma vez com o ID decimal do bot
```

O installation token existiu somente em memória. As leituras do repositório,
release `v0.1.0`, runs e artifacts retornaram 200; nenhuma escrita de teste foi
feita. A App apresentou exatamente uma instalação e o token as três permissões
fechadas. O PEM local permaneceu `0600`, com fingerprint pública SHA-256:

```text
6cec7c21cb17abf227c253a3bab0a541e9023c105f4f4eb1abc6003b9279358a
```

Nenhum PEM, JWT, installation token ou valor protegido foi exibido ou
registrado neste relatório.

### 9.3 Preparação declarativa e tentativa de start 1

Foram criados exclusivamente os recursos autorizados: user/group dedicados em
UID/GID `10001`, grupo suplementar `docker`, root operacional, `/etc/emporio`,
env protegido, PEM deployer e unit systemd. Modos e owners:

```text
/opt/sistemas/emporio-control             0700 service:service
/opt/sistemas/emporio-control/ops         0700 service:service
/opt/sistemas/emporio-control/ops/compose 0700 service:service
/etc/emporio                              0750 root:service
release-control.env                       0640 root:service
release-control-deployer-app.pem          0640 root:service
emporio-release-control.service           0644 root:root
```

O env continha exatamente 32 chaves. Password do banco e pepper foram gerados
separadamente na VPS, ambos com 32 bytes aleatórios, sem stdout, argv ou
histórico. Fingerprint pública local/remota do PEM coincidiu. Compose e unit
vieram dos blobs publicados e seus hashes foram:

```text
Compose SHA-256  dd8af6a07f4bcb7f2f9838a147ba1f8a2ec44eb91fd2df8d7c78ccb070312547
unit SHA-256     164a10dec8ad27ab76cd7fcd78424fe7baa6a83579f149f211f59376d17b6505
```

Uma primeira chamada a `docker compose config --quiet` herdou `/root` e
retornou `stat .: permission denied`, sem criar recurso. Repetida a partir do
WorkingDirectory contratado, a prova declarativa fechou verde: dois serviços,
uma rede, um volume, dois images somente por digest, `pull_policy: never`, sem
build/external/Swarm/socket/porta pública, e secret no target fixo.

Os dois manifests foram revalidados e os pulls limitados às referências
aprovadas. PostgreSQL e aplicação eram `linux/amd64`; a aplicação declarava
`10001:10001`. A tentativa de start 1 criou somente os dois containers alvo e
chegou a `active/enabled`, ambos healthy no healthcheck live, mas
`/health/ready` permaneceu 503. O banco já estava em
`0003_commercial_rollback`, com zero instalação e zero operações. O sync de
releases falhava antes de materializar snapshot.

Diagnóstico executado dentro da própria imagem, sem imprimir dados remotos:

```text
exception_type  FileNotFoundError
fluxo           sync_releases -> validate_release_bundle -> validate_json -> _schema
causa           schemas canônicos ausentes da imagem runtime
```

A tentativa 1 foi revertida antes da correção: unit desabilitada, containers e
rede removidos, volume migrado preservado e imagem antiga removida.

### 9.4 Último ciclo corretivo, gates e nova imagem

O defeito consumiu o último commit corretivo permitido. A correção mudou o
build para o contexto raiz deny-by-default, admitindo somente os inputs exatos,
e passou a copiar os cinco schemas exigidos para a imagem. Validadores e testes
mutantes passaram a rejeitar contexto incorreto, ausência dos schemas e
ausência da cópia runtime.

```text
commit técnico  c951ceb7f5525505a4d1fe12d04fc9a4ad50fdff
mensagem        fix: package release-control runtime schemas
push            fast-forward 9699214..c951ceb
```

Matriz local completa:

```text
release_control              332 passed
tools/docker/tests           117 passed
tools/ci/tests                31 passed
tools/candidates/tests        75 passed
tools/releases/tests         300 passed
tools/deploy/tests           434 passed
tools/security/tests          26 passed
tools/compose/tests             4 passed
tools/gateway/tests             4 passed
total                       1.323 passed
validadores registrados       28 verdes
imagem causal local            build sem tag; 5 schemas JSON válidos
secret scan                    scanned=2493 allowed=960 unsupported=0
histórico escaneado            145276
git diff --check               exit 0
```

Gates remotos do mesmo SHA:

```text
CI                    30938485570 / success
Publish Candidate     30939132677 / success / 11 jobs
Image workflow        30939873305 / success / 4 jobs
manifest artifact     8904721554
outcome artifact      8904732720
manifest SHA-256      78b5f163e04ce2ccd886900ab4722f98a60afce1d691d27d5ca2740d2762896e
package version       1098845282
image digest          sha256:a12badc37a97c1dab9c7ff8f787320b978a4a76f27b19ecce6843cf4bd0f69a5
```

Manifest, sidecar e outcome foram baixados nominalmente, fecharam canônicos e
concordaram no digest e no SHA do manifesto. A nova referência imutável foi:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:a12badc37a97c1dab9c7ff8f787320b978a4a76f27b19ecce6843cf4bd0f69a5
```

A resolução local desse manifest recebeu `unauthorized`, sem mutação. A rota
de implantação relevante, na VPS e com a credencial GHCR preexistente,
resolveu o descriptor e puxou o digest sem login. O config image obtido foi
`sha256:70f7b6bfb5caf73a547e8b43ab8fe118fa41b966d8ea5094c4002f10b10b7779`,
`linux/amd64`, user `10001:10001`. O env protegido foi atualizado de forma atômica e
o Compose renderizado aceitou somente o novo digest e o PostgreSQL canônico.

### 9.5 Tentativa de start 2 e causa terminal

A tentativa 2 iniciou pela mesma unit e preservou o volume migrado. Os
containers chegaram novamente a healthy no gate live, porém ready permaneceu
503 por 60 segundos. SQL continuava em `0003_commercial_rollback`, com zero
snapshot, zero current installation e zero operação. O diagnóstico preciso
mostrou que o mesmo defeito causal não foi corrigido no path efetivo:

```text
schema esperado pelo código  /ops/releases/global-release.schema.json
schema presente nesse path   false
schema empacotado             /app/ops/releases/global-release.schema.json
exception                    FileNotFoundError
```

`Path(__file__).resolve().parents[3]` resolve para `/` na imagem, enquanto o
teste causal do último commit comprovou incorretamente `/app/ops`. Corrigir o
destino para `/ops` exige outro commit, outra matriz, outro CI/candidato e outro
digest. Isso excederia o máximo cumulativo de dois commits corretivos após o
patch obrigatório. Não houve terceiro commit nem tentativa de start 3.

Os logs das duas tentativas não continham PEM, token, password, pepper, JWT,
traceback ou valor de env. Não houve POST operacional, dispatch, deploy,
rollback, backup, restore ou execução da stack comercial.

### 9.6 Reversão terminal e invariantes finais

Após provar que os roots continham somente os arquivos criados pela S44, foi
executada reversão dirigida integral na VPS: unit stop/disable, Compose down do
projeto alvo com volume, remoção dos dois images ausentes no baseline, unit,
env, PEM remoto, root operacional e user/group dedicados. `userdel` removeu
automaticamente o grupo primário; o `groupdel` subsequente retornou 6 por grupo
já ausente, e a continuação idempotente confirmou o estado final.

```text
Docker VPS              37 running / 39 total / 26 volumes / 18 networks / 31 images
Swarm                   inactive
porta 8180              livre
root /etc/unit/user     ausentes
containers/volume/rede  0 / 0 / 0
images S44 na VPS       0
control root S43        preservado
```

A identidade GitHub App válida foi preservada deliberadamente para retomada:
uma App, uma instalação selecionada, um repositório, PEM local `0600` e a
allowlist com o bot real. Verificação final repetiu leituras 200 e fingerprint
sem gerar escrita. Estado Git/GitHub final:

```text
HEAD/origin/main/remoto  c951ceb7f5525505a4d1fe12d04fc9a4ad50fdff
divergência              0 / 0
stage                    vazio
relatórios S39-S44       não rastreados
deploy runs              0
rollback runs            0
v0.1.0                   38385c100ab8b0ae07099b6a5a7b016b7c2b7322, inalterada
tentativas de start      2; ambas revertidas
```

Os temporários exatos `/tmp/emporio-s44-app-flow` e
`/tmp/emporio-s44-uv-cache` foram removidos. O scanner aplicado somente ao
relatório final retornou `scanned=1`, `allowed=0`, `unsupported=0` e
`history_scanned=0`.

BLOCKED — S44 correction-01 interrompida fail-closed na primeira causa técnica

## 10. Retomada correction-02

A task, a correction-01, a correction-02 e este relatório contínuo foram lidos
integralmente, na ordem contratada. O checkpoint anterior à primeira ação foi:

```text
HEAD local             8bb2498854af76e64d64720ae9b404b9493edd5a
origin/main/remoto     c951ceb7f5525505a4d1fe12d04fc9a4ad50fdff
divergência            ahead 1 / behind 0
stage                  vazio
task SHA-256           0ca6665a5efc26f912d39f563132ee6c90d4270526ab0de5959b61938e70422f
correction-01 SHA-256  8ed558507e4959ceb36bc60d6fb6e23b732ee5ec2c972303d5750d4d64183630
correction-02 SHA-256  dd076433d4b6cc99a53704758a7aba19d8f3ac2bb9e495e5da4361e2ed35fe0b
relatório anterior     fdc33c674340f8c13c7139724d4d197d65f76796e48d8855600f06ab29d04062
relatórios S39-S44     não rastreados e fora do stage
```

A VPS foi novamente provada no baseline revertido: `37/39` containers,
`26` volumes, `18` redes e `31` imagens, Swarm `inactive`, porta 8180 livre e
todos os alvos da S44 ausentes. O control root da S43 permaneceu íntegro em
`9731954d474fb68ec1384a525e1075f9a5542e24`.

### 10.1 Correção causal e provas na imagem real

O Dockerfile passou a instalar a allowlist já existente de cinco schemas no
path runtime absoluto, sem alterar os paths calculados pelo código:

```dockerfile
COPY --from=builder --chown=10001:10001 /build/ops /ops
```

O validador passou a exigir a allowlist exata do contexto, os cinco sources e
constantes absolutos, e a rejeitar `/app/ops`, destino relativo, fallback,
schema ausente e cópia ampla de `ops/`. Os testes mutantes correspondentes
foram adicionados.

Antes do commit e novamente sobre o SHA final foi construída a imagem real pelo
contexto raiz, sem tag auxiliar. Em ambas as provas, o container executou como
`10001:10001`, read-only, `--network none`, sem capabilities e sem secret. O
resultado fechado foi:

```text
imports                    /app/src/emporio_release_control/*.py
schemas                    cinco arquivos regulares sob /ops
owners                     10001:10001; sem escrita por grupo/outros
/app/ops                   ausente
rede interna da prova      somente lo
release real               v0.1.0 / 6 componentes / sidecar e metadata válidos
sourceCommit               38385c100ab8b0ae07099b6a5a7b016b7c2b7322
outcome deploy fixture     válido
outcome rollback fixture   válido
temporários/imagem         removidos de forma dirigida
```

Os três assets reais foram baixados fora do container e conferiram com seus
digests publicados. A primeira invocação do harness pré-commit encontrou
somente o modo `0700` criado por `mktemp` no diretório de assets; a imagem já
estava correta e foi removida pelo trap. A repetição mudou apenas o modo desse
diretório efêmero para leitura e fechou verde.

Matriz local terminal:

```text
release_control              332 passed
tools/docker/tests           117 passed
tools/ci/tests                31 passed
tools/candidates/tests        75 passed
tools/releases/tests         300 passed
tools/deploy/tests           439 passed
tools/security/tests          26 passed
tools/compose/tests             4 passed
tools/gateway/tests             4 passed
oito suítes canônicas        996 passed
validadores executáveis       29 verdes
release-control-package       valid
catalog                       valid
secret scan árvore/histórico  clean; scanned=2495; allowed=992;
                              unsupported=0; history_scanned=150265
secret scan staged            clean; scanned=3; allowed=0; unsupported=0
git diff checks               verdes
```

Commit e publicação Git:

```text
commit causal   7e84fb95974c77a2a710d73f812a4d6bed1e4eb1
mensagem        fix: install release-control schemas at runtime root
push único      c951ceb..7e84fb9 main -> main, fast-forward
amend/rebase    nenhum
```

### 10.2 CI, candidato e imagem imutável

Os gates do mesmo SHA terminaram verdes, sem rerun de SHA anterior:

```text
CI                       30943179121 / success
Publish Candidate        30943915132 / success / 11 jobs
Image workflow           30944695626 / success / 4 jobs / attempt 1
manifest artifact        8906671272
manifest artifact digest sha256:7ac00062227a6afecd16bcd78a073ff2feb77ee4154d6af2e40306d969115173
outcome artifact         8906683433
outcome artifact digest  sha256:f3afd67f2c532faf217488feeb8e389b49b13fe79772eb88382ce6dba732b4a3
manifest SHA-256         2563ab2ddec4422f8577ef24d6f3b30fa3a765c8ec19f481f68fcfd8e206f23e
package version          1099034724
image digest             sha256:d0d0cb16eb9834767a9d30549fc86ffc37f44a963f274739b7f0c5ddaefe7040
```

Manifest e outcome foram baixados nominalmente, continham exatamente dois
arquivos regulares cada, eram JSON canônico e tinham sidecars concordantes.
`sourceSha`, `runId`, `attempt`, digest e `manifestSha256` fecharam entre si;
o outcome foi `published` e `release-control-image:valid` retornou exit 0. A
package version traz somente a tag de transporte derivada do mesmo
SHA/run/attempt. As três versões anteriores foram preservadas.

Referência operacional final:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:d0d0cb16eb9834767a9d30549fc86ffc37f44a963f274739b7f0c5ddaefe7040
```

### 10.3 Identidade preservada e preparação da VPS

A App não foi aberta na UI, recriada, reinstalada nem rekeyed. O PEM local
permaneceu `0600`, e sua fingerprint pública continuou:

```text
6cec7c21cb17abf227c253a3bab0a541e9023c105f4f4eb1abc6003b9279358a
```

A revalidação em memória confirmou App `4487372`, instalação `151259606`,
único repositório `1315264421`, permissões `actions:write`, `contents:read` e
`metadata:read`, zero events, bot `313092947` e leituras de release/runs/
artifacts. `DEPLOYER_ACTOR_IDS` permaneceu inalterada com o bot real. Nenhuma
escrita de teste foi feita.

As referências PostgreSQL e release control resolveram na VPS antes da
mutação. O PostgreSQL também resolveu localmente, e a metadata oficial da tag
continuou marcando o filho aprovado como `linux/amd64 active`. Foram recriados
somente usuário/grupo, roots, Compose, unit, env e PEM da S44, com os modos e
owners contratados. Compose e unit vieram dos blobs do commit publicado:

```text
Compose SHA-256  dd8af6a07f4bcb7f2f9838a147ba1f8a2ec44eb91fd2df8d7c78ccb070312547
unit SHA-256     164a10dec8ad27ab76cd7fcd78424fe7baa6a83579f149f211f59376d17b6505
env              32 chaves; 0640 root:service
PEM remoto       0640 root:service; fingerprint coincidente
service account  UID/GID 10001; suplementar somente docker; senha bloqueada
```

Password do banco e pepper foram gerados separadamente na VPS com 32 bytes
aleatórios, sem stdout, argv ou histórico. Nenhum valor protegido foi aberto
ou registrado. A prova declarativa real, como o service account, fechou com
dois serviços, uma rede, um volume e um secret file-backed; images somente por
digest e `pull_policy: never`; sem build, socket, Swarm, porta pública ou stack
comercial. Um assert inicial do harness esperava mode numérico/target absoluto,
enquanto o JSON do Compose os representa como `"0400"` e nome relativo; após
inspecionar somente essa estrutura não secreta, a prova correta passou. Ainda
havia zero recurso Docker alvo.

Os únicos pulls foram:

```text
postgres@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:d0d0cb16eb9834767a9d30549fc86ffc37f44a963f274739b7f0c5ddaefe7040
```

Ambos foram `linux/amd64`, com RepoDigest exato. A aplicação declarou
`10001:10001`, labels do SHA/run/attempt e repetiu na VPS a prova dos cinco
schemas em `/ops`, com `/app/ops` ausente. Não houve login, build, tag ou push
na VPS.

### 10.4 Start, readiness e restart

A única tentativa da correction-02 foi executada pela unit systemd. A unit
ficou `enabled/active`, e os dois containers ficaram healthy. Provas antes do
restart:

```text
app                        10001:10001; read-only; cap-drop ALL;
                           no-new-privileges; 127.0.0.1:8180 somente
PostgreSQL                 sem porta publicada
secret no container       regular; legível por 10001:10001; bytes não exibidos
health/live                200 / {"status":"ok"}
health/ready               200 / {"status":"ok"}
migration                  0003_commercial_rollback
sync deployments           last_success presente; drift=false; error=null
sync releases              last_success presente; drift=false; error=null
snapshot                   v0.1.0; source commit correto; 6 componentes
current installation       0
deployment operations      0
publication operations     0
idempotency/backups        0
audit/dispatch audit       0 / 0
logs                       sem secret, JWT, traceback ou valor de env
```

Uma asserção inicial do harness de rede usou o campo incorreto do `ss`; a
escuta real era `127.0.0.1:8180`, e a repetição do probe correto fechou sem
mutação. Foi então executado exatamente um `systemctl restart`. Containers e
IDs lógicos, network ID, mountpoint do volume, snapshot de `v0.1.0` e todos os
containers externos ao projeto permaneceram idênticos. Após o restart,
live/ready voltaram a 200, migrations e sync permaneceram verdes, o PEM seguiu
legível e todas as contagens zero continuaram zero.

Estado Docker intencional final da S44:

```text
containers VPS       39 running / 41 total (37/39 externos preservados)
volumes              27 (26 externos + 1 control plane)
networks             19 (18 externas + 1 control plane)
images               33 (31 externas + 2 digests aprovados)
Swarm                inactive
control root S43     verificado byte a byte; capabilities válidas como deploy-emporio
```

Uma primeira invocação final de capabilities como root retornou exit 4, como
previsto pelo fail-closed do control root; a invocação contratada como
`deploy-emporio` retornou o SHA e o protocolo esperados. Nenhum alvo foi
mutado por essa checagem.

### 10.5 Invariantes finais

```text
HEAD/origin/main/remoto  7e84fb95974c77a2a710d73f812a4d6bed1e4eb1
divergência              0 / 0
stage/diff tracked       vazios
relatórios S39-S44       não rastreados
deploy runs              0
rollback runs            0
control-plane POST       0
operações/dispatches     0
v0.1.0                   inalterada em 38385c100ab8b0ae07099b6a5a7b016b7c2b7322
stack comercial          não iniciada nem alterada
Nginx/TLS/backup/restore não executados
swap/update/reboot       não executados
```

A S44 não foi aceita, tracker/task não foram alterados e nenhuma S45 foi
criada.

O scanner aplicado somente a este relatório terminal retornou
`secret-scan:clean:scanned=1:allowed=0:unsupported=0:history_scanned=0`.

IN_PROGRESS — control plane deployer implantado e estável; aguardando aceite e Gate C de prontidão
