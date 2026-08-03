# S37 — Inventário read-only da VPS e plano de preparação de produção

> **Data:** 03/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Executor:** CLI
> **Contrato:** `S37-inventario-read-only-vps-plano-preparacao-producao.task.md`
> **Resultado:** `IN_PROGRESS — inventário read-only concluído; aguardando revisão e autorização do plano de preparação`

O inventário foi concluído em duas fases. A primeira, sem rota SSH, produziu o
inventário público e local e classificou `SSH_ROUTE_MISSING`. O usuário então
forneceu o único dado humano que faltava — a rota `root@31.97.251.16`, já
autorizada por `ssh-copy-id` — e o inventário remoto da seção 6 foi executado
integralmente, somente leitura. Ambas as fases estão registradas: a primeira
porque documenta a fronteira respeitada, a segunda porque é o inventário
contratado.

## 1. Identidade e integridade da base

| Item | Exigido | Observado | Estado |
|---|---|---|---|
| `HEAD` | `807a71e4f94adebf757807168779a826f4880894` | idêntico | conforme |
| `origin/main` | `67abde48fd4a74de5bcff22bf592bd9005094210` | idêntico | conforme |
| remoto `main` | `67abde4...` | idêntico | conforme |
| divergência | ahead 7 / behind 0 | `ahead=7 behind=0` | conforme |
| ancestralidade | linear | `git merge-base --is-ancestor` exit `0` | conforme |
| stage e worktree | vazios | `git status --porcelain` sem saída | conforme |
| SHA-256 da task S37 | `d19be911...810dba` | idêntico | conforme |
| SHA-256 do relatório S30b | `f4725104...dbe503` | idêntico | conforme |

Os sete commits documentais em `origin/main..HEAD`:

```text
807a71e docs: accept S30b and open S37
8fd722b docs: reconcile S30b publisher app identity
c149f8f docs: authorize S30b publisher app provisioning
4927946 docs: record S30b publisher identity block
fb405d3 docs: authorize S30b release publication
6a3fb27 docs: accept S30b read-only preflight
db1177e docs: accept S36 and open S30b
```

`git diff --name-only origin/main..HEAD` toca exclusivamente
`docs/infrastructure/deployment/implementation/` e `.../slices/`.
`git diff --check origin/main..HEAD` exit `0`.

Estado remoto exigido pela §2:

```text
tags remotas                 1   refs/tags/v0.1.0 -> 38385c100ab8b0ae07099b6a5a7b016b7c2b7322
GitHub Releases              1   v0.1.0 (Latest), 2026-08-03T10:16:20Z
runs deploy-production.yml   0
runs rollback-production.yml 0
runs queued/in_progress      nenhum nos cinco workflows
```

### 1.1 Divergência da ordem de leitura

O prompt de delegação indicou
`docs/infrastructure/deployment/PROPOSTA_PIPELINE_DEPLOY.md`, que **não existe**
no repositório. O documento equivalente, referenciado pelo tracker e pelo
handoff §2 item 3 como "proposta arquitetural", é
`docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`,
e foi esse o lido. Divergência registrada, sem efeito sobre a execução.

## 2. Rota SSH e confiança do host

### 2.1 Comandos e resultados

| Comando | Exit | Resultado |
|---|---:|---|
| busca do IP literal em `~/.ssh/config`, `/etc/ssh/ssh_config`, `/etc/ssh/ssh_config.d` | 0 | nenhum arquivo contém `31.97.251.16` |
| blocos `Host` em `~/.ssh/config` | 0 | somente `Host github.com` |
| `ssh -G 31.97.251.16` | 0 | `user gregorio`, `port 22`, `stricthostkeychecking ask`, known-hosts padrão |
| `ssh-keygen -F 31.97.251.16 -f ~/.ssh/known_hosts` | 0 | **3 entradas** |
| `ssh-keygen -F` para os dois domínios e `abaronesa.net.br` | 0 | `0` entradas em cada |
| `ssh -G` por domínio | 0 | `user gregorio`, `port 22` — default, não configurado |

Host keys confiadas para `31.97.251.16` (fingerprints públicos):

```text
256  SHA256:oB+4purUbvJRAJo4ZgHLpsUvQZO1CKzSrgiawFXKp/A
3072 SHA256:yOsx7KHIusLLQ+2PyrQ0iCpjuHc/dALIWw/kuYoQ2U8
256  SHA256:FJ/VyIKtp+fLmDZwZ6gjK5M2w5aXbP/mS5YSzC4NkQ0
```

Identidades locais: `~/.ssh/id_rsa` e `~/.ssh/id_ed25519`, ambas modo `600`;
duas identidades carregadas no agente. Nenhuma chave privada foi aberta,
copiada ou transcrita.

### 2.2 Tentativa única, não interativa

Executada exatamente uma vez, com verificação estrita e sem forwarding:

```text
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 \
    -o ForwardAgent=no -o ForwardX11=no -o ControlMaster=no -o ControlPath=none \
    -o NumberOfPasswordPrompts=0 31.97.251.16 'id -un; echo CONNECTED'

gregorio@31.97.251.16: Permission denied (publickey,password).
```

Interpretação: **a verificação de host key passou** — a sessão avançou até a
fase de autenticação sem qualquer erro de confiança, o que confirma que as
entradas de `known_hosts` correspondem ao host real. A falha é exclusivamente
de autorização do usuário efetivo `gregorio`.

Uma única tentativa foi feita, deliberadamente, para não acionar bloqueio
por tentativas repetidas contra o operador legítimo. Não foi usado
`StrictHostKeyChecking=no` nem `accept-new`, e **nenhum outro usuário foi
tentado**: `root`, `deploy-emporio`, `ubuntu` ou similares seriam adivinhação,
expressamente proibida pela §4.

### 2.3 Classificação da primeira fase

```text
SSH_ROUTE_MISSING
```

Host key confiável e verificada; rota de acesso inexistente. A ausência **não**
foi tratada como autorização para configurar acesso, criar usuário ou instalar
chave. A execução parou e declarou o dado humano necessário.

### 2.4 Rota fornecida pelo usuário e reabertura do inventário

O usuário informou que a rota já existia e estava autorizada por `ssh-copy-id`:

```text
usuário   root
host      31.97.251.16
porta     22
```

Isso é exatamente o input declarado como indispensável, não descoberta nem
adivinhação do executor. Com ele, a sessão não interativa foi estabelecida:

```text
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 \
    -o ForwardAgent=no -o ForwardX11=no -o ControlMaster=no -o ControlPath=none \
    root@31.97.251.16 'echo CONNECTED; id -un; hostname'
CONNECTED
root
srv1006846
exit 0
```

Todas as invocações remotas subsequentes usaram exatamente essas opções:
`BatchMode`, verificação estrita, sem agent/X11 forwarding e sem master
persistente.

**Ressalva de desenho, registrada:** o acesso disponível é `root`. Ele foi usado
apenas para inspeção. Ele **não** é, e não deve virar, o mecanismo de deploy: o
`deploy-production.yml` conecta como `deploy-emporio`
(`tools/deploy/deployment_transport.py:35`), e o handoff §12 proíbe `root` como
mecanismo normal. A criação desse usuário permanece item do Gate B.

### 2.4 Consequência delimitada

Esta ausência bloqueia apenas o inventário read-only desta estação de
trabalho. Ela **não** é o mecanismo de deploy: o `deploy-production.yml`
materializa sua própria rota a partir de
`vars.PRODUCTION_SSH_HOST`, `vars.PRODUCTION_SSH_PORT`,
`secrets.PRODUCTION_SSH_PRIVATE_KEY` e `secrets.PRODUCTION_SSH_KNOWN_HOSTS`,
conectando como `deploy-emporio` (`tools/deploy/deployment_transport.py:35`).
São caminhos independentes.

## 3. Inventário público — DNS, HTTPS e TLS

### 3.1 DNS

| Domínio | A | AAAA | CNAME | Aponta para a VPS |
|---|---|---|---|---|
| `emporio.abaronesa.net.br` | `31.97.251.16` | — | — | **sim** |
| `erp-emporio.abaronesa.net.br` | `31.97.251.16` | — | — | **sim** |

Ambos os registros já existem e resolvem corretamente para o alvo. Nenhum
registro AAAA.

### 3.2 TLS — conflito material com sistema preexistente

```text
emporio.abaronesa.net.br      subject=CN=eventos.abaronesa.net.br
erp-emporio.abaronesa.net.br  subject=CN=eventos.abaronesa.net.br
issuer  C=US, O=Let's Encrypt, CN=YR2
válido  Jul 28 15:18:49 2026 GMT → Oct 26 15:18:48 2026 GMT
SAN     DNS:eventos.abaronesa.net.br  (somente)
```

**Nenhum dos dois domínios do Empório possui certificado.** O TLS servido é o
do sistema `eventos.abaronesa.net.br`, que já ocupa a VPS.

### 3.3 Comportamento HTTP público

| Domínio | HTTP | HTTPS com verificação | HTTPS sem verificação |
|---|---|---|---|
| `emporio.abaronesa.net.br` | `200`, **sem redirect** | falha TLS (`000`) | `200` |
| `erp-emporio.abaronesa.net.br` | `200`, **sem redirect** | falha TLS (`000`) | `200` |
| `eventos.abaronesa.net.br` | `301` → HTTPS | `200` | `200` |

Servidor anunciado: `nginx/1.18.0 (Ubuntu)` — Nginx de host, coerente com o
desenho `ops/nginx-host/` da proposta.

Interpretação: os dois domínios do Empório caem hoje em um vhost default que
responde `200` em HTTP sem redirecionar para HTTPS e apresenta o certificado
errado. O sistema `eventos` está corretamente configurado e **não pode ser
afetado** pela preparação do Empório.

## 4. Inventário do lado GitHub

| Item | Estado |
|---|---|
| variables do repositório | somente `RELEASE_PUBLISHER_ACTOR_IDS` |
| `DEPLOYER_ACTOR_IDS` | **MISSING** (HTTP 404) |
| environments | **nenhum** — o environment `production` exigido pelo workflow não existe |
| secrets do repositório | **nenhum** |
| `PRODUCTION_SSH_HOST` / `PRODUCTION_SSH_PORT` | **MISSING** |
| `PRODUCTION_SSH_PRIVATE_KEY` / `PRODUCTION_SSH_KNOWN_HOSTS` | **MISSING** |

Nenhum valor foi lido; apenas nomes e presença.

### 4.1 Identidade deployer

```text
/home/gregorio/.config/emporio/release-control/   drwx------ (700)
  publisher-github-app.pem   -rw------- (600)  1675 bytes   PRESENTE
  publisher-github-app.env   -rw------- (600)   199 bytes   PRESENTE
  deployer-github-app.pem                                    AUSENTE
  deployer-github-app.env                                    AUSENTE
/etc/emporio                                                 AUSENTE
/etc/emporio-release-control                                 AUSENTE
```

A GitHub App **deployer não existe**. A separação exigida entre publisher e
deployer está preservada por ausência, não por provisionamento.

### 4.2 GHCR somente leitura

`~/.docker/config.json` presente, modo `600`, **não aberto**. `manifest inspect`
executado nos seis digests de `v0.1.0`, sem login e sem pull:

```text
backend           FALHA  unauthorized
website-backend   FALHA  unauthorized
frontend          FALHA  unauthorized
website-frontend  FALHA  unauthorized
whatsapp-service  FALHA  unauthorized
gateway           FALHA  unauthorized
```

Causa confirmada por metadado público: os oito packages do namespace são
`visibility=private`. Portanto a VPS **exigirá** credencial dedicada com
`read:packages`; imagens públicas sem credencial não são uma opção neste estado.

Os seis `imageRepository@digest` de `v0.1.0`, extraídos do asset `release.json`:

```text
backend           ghcr.io/greggorio/abaronesa-emporio-backend@sha256:032c5499fbae07de8931d2dd0bae96939fa399fe2622eecc3ae8b374398197c6
website_back      ghcr.io/greggorio/abaronesa-emporio-website-backend@sha256:5fb8acf3618b5ee13d96a2b3af53e4c6b470622e96c59fca72fb03b0fb531aae
frontend          ghcr.io/greggorio/abaronesa-emporio-frontend@sha256:e34c138c13d275054c2370c0205b492c4809fb40ee827c271b62b590cf759619
website_front     ghcr.io/greggorio/abaronesa-emporio-website-frontend@sha256:a2abe0297a9ad1ac7ee5e71b8d3245fc0eec200486d889107a5b288faa03b2a1
whatsapp_service  ghcr.io/greggorio/abaronesa-emporio-whatsapp-service@sha256:7d8452b37f6aaf39f064951f9fba33e6968de97d7aa7b9b33ea5bc60ec2ca920
gateway           ghcr.io/greggorio/abaronesa-emporio-gateway@sha256:dbba76ec16731581f28fc91ea624e5fb933f5ad290a3a2f86ea4448f242dbf13
```

## 5. Confronto local do caminho de produção (§7)

| Comando | Exit | Resultado |
|---|---:|---|
| `python3 tools/deploy/validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `python3 tools/deploy/validate_deployer_runtime.py` | 0 | `deployer-runtime:valid` |
| `python3 tools/deploy/validate_rollback_contract.py` | 0 | `rollback-contract:valid` |
| `python3 tools/deploy/validate_rollback_runtime.py` | 0 | `rollback-runtime:valid` |
| `python3 tools/deploy/validate_release_control_package.py` | 0 | `release-control-package:valid` |
| `python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json` | 0 | `global-release:valid` |
| `git diff --check` | 0 | sem erro |

Contratos locais do caminho de produção estão íntegros.

### 5.1 Contrato local declarado

```text
compose.prod.yml        sete serviços: postgresql, backend, website_back,
                        frontend, website_front, whatsapp_service, gateway
tags 'latest'           0 ocorrências
porta publicada         somente 127.0.0.1:${GATEWAY_LOOPBACK_PORT:-8120}:8080
volumes nomeados        emporio-postgres-data, emporio-backend-uploads,
                        emporio-website-uploads, emporio-whatsapp-session
release-control.yml     bind 127.0.0.1:${RELEASE_CONTROL_LOOPBACK_PORT:-8180},
                        user 10001:10001, read_only, PostgreSQL próprio
systemd                 ops/systemd/emporio-release-control.service.example
deploy root             /opt/sistemas/emporio  (transport, CLI e remote script)
usuário remoto          deploy-emporio (deployment_transport.py:35)
ponte deployer no ERP   app.release-control.deployer-identity.* opt-in,
                        default false
segredos                ops/env/.env.example versionado;
                        ops/env/.env.production existe local, modo 600,
                        ignorado por .gitignore:6 — não aberto
```

## 6. Classificação causal das duas falhas preexistentes do deployer

Ambas foram confirmadas preexistentes na S30b, reproduzindo-se de forma idêntica
com o `github.py` de `origin/main`. Aqui são classificadas por causa e impacto,
**sem qualquer correção**.

### 6.1 Falha 1 — `429` antes do `409` esperado

**Teste:** `tests/test_deployer_api.py::test_rollback_persists_dispatches_replays_and_supports_get`
**Sintoma:** `assert 429 == 409`

**Causa.** `rollback_actor` é dependência FastAPI e chama
`rate_limiter.check(sub, "rollback", settings.rollback_rate_per_minute)`
(`deployer_api.py:165`) **na resolução da dependência**, ou seja, antes do corpo
do handler e portanto antes da detecção de conflito idempotente. O default é
`rollback_rate_per_minute = 2` (`config.py:47`). O teste emite três POSTs de
rollback no mesmo minuto — `first`, `replay` e `conflict` — e o terceiro estoura
o limite antes de poder retornar `409`.

**Impacto:** **somente o teste**, quanto a defeito. Não afeta o primeiro deploy
forward: o bucket é distinto e `deploy_rate_per_minute` é `5`.

**Observação operacional que não é defeito, mas deve ser decidida:** um operador
real que emita pedido, replay e um pedido divergente de rollback dentro do mesmo
minuto será limitado na terceira chamada, recebendo `429` em vez do `409`
informativo. Se `2/min` for intencional para rollback, a expectativa do teste é
que está errada; se não for, é o limite. Essa é uma decisão do orquestrador,
não do executor.

### 6.2 Falha 2 — `CheckViolation` em `ck_rc_deployment_workflow_binding`

**Teste:** `tests/test_deployer_api.py::test_rollback_state_machine_restore_recovery_and_terminal_replay`
**Sintoma:** `psycopg.errors.CheckViolation` ao inserir em `rc_deployment_operation`

A constraint (`migrations/versions/0002_deployer_runtime.py:79-87`) é correta por
desenho e exige coerência total:

```text
(dispatch_state = 'CONFIRMED' AND workflow_run_id, workflow_attempt,
 workflow_run_url e control_sha TODOS NOT NULL)
OU
(dispatch_state IN ('NOT_SENT','SENT','UNCERTAIN') AND os quatro TODOS NULL)
```

**Causa direta do teste.** A fixture em `test_deployer_api.py:736` cria a
operação de rollback com `dispatch_state="CONFIRMED"` e sem nenhuma das quatro
colunas de vínculo. É defeito de fixture; o banco está fazendo exatamente o seu
trabalho.

**Risco latente de produção revelado pela mesma constraint.** As quatro colunas
de vínculo são escritas **exclusivamente** em `_bind_run`
(`deployer_reconciliation.py:431-434`), executado pelo reconciliador ao
descobrir o run. Já o serviço marca `dispatch_state = "CONFIRMED"` no caminho de
ingestão do outcome, em `deployer_service.py:977` (rollback) e `:1096`
(deploy), **sem** escrever essas colunas. A inspeção das pré-condições desse
caminho (`deployer_service.py:940-965`) mostra que ele valida estado e
`transport`, mas **não exige** `workflow_run_id IS NOT NULL`.

Consequência: se o outcome for ingerido antes de o reconciliador ter vinculado o
run, produção grava `CONFIRMED` com o vínculo nulo e sofre a **mesma**
`CheckViolation`, abortando a transação. A janela é plausível — o reconciliador
opera por intervalo (`reconcile_interval_seconds`, default `15`), enquanto o
outcome chega pelo transporte.

Há um agravante histórico relevante: até o reparo #10 da S30b (commit
`67abde4`), `_bind_run` **sempre** falhava com `WORKFLOW_RUN_INVALID` por causa
da forma irreal do run (`@main` e `name`). Antes desse reparo, as colunas de
vínculo nunca seriam escritas e a violação seria sistemática, não ocasional. O
reparo removeu a causa sistemática; a janela de ordenação permanece.

**Impacto:** **afeta o primeiro deploy forward e o rollback**, condicionado à
ordenação. Deve ser resolvido ou explicitamente aceito como risco antes do
Gate D. Não é apenas teste.

## 6-bis. Inventário remoto da VPS (§6), somente leitura

Todas as consultas abaixo são de metadado e estado. Não foi executado
`docker inspect`, `docker compose config`, `systemctl cat`, nem `cat` de
arquivo operacional, `.env`, chave, token ou sessão.

### 6-bis.1 Host e capacidade

```text
hostname          srv1006846
OS                Ubuntu 22.04.5 LTS
kernel            Linux 5.15.0-171-generic
arquitetura       x86-64
virtualização     kvm
timezone          Etc/UTC   clock sincronizado: yes   NTP: active
uptime            20 semanas, 3 dias
CPU               4
memória           15 GiB total | 11 GiB usados | 267 MiB livres | 3,7 GiB disponíveis
swap              0 B  (nenhum)
disco /           194 GB total | 102 GB usados | 92 GB disponíveis | 53%
inodes /          29% usados
load average      2,50 / 2,52 / 2,48  em 4 CPUs
processos         470
```

**Riscos de capacidade, materiais.** Restam **3,7 GiB** de memória disponível e
**não há swap**. A stack comercial acrescenta sete serviços, entre eles dois
backends Java, um Chromium/WhatsApp e um PostgreSQL. A proposta arquitetural
registrou 4,7 GiB disponíveis na época da análise; hoje são 3,7 GiB. O load
sustentado em ~2,5 sobre 4 CPUs indica servidor já bastante carregado. O banner
de login declara *System restart required* e 35 atualizações pendentes.

### 6-bis.2 Rede e coexistência

Portas alvo do Empório:

| Porta | Uso previsto | Estado |
|---|---|---|
| `8120` | gateway comercial, loopback | **livre** |
| `8180` | release-control deployer, loopback | **livre** |
| `5432` | PostgreSQL comercial (não publicado) | livre no host |
| `80` / `443` | Nginx de host | **ocupadas** pelo Nginx que serve os demais sistemas |

Firewall: `ufw` **inativo**; 148 regras `iptables` presentes, majoritariamente
geradas pelo Docker. Há dezenas de listeners em `3000-3004`, `5434-5544`,
`6077-8115`, evidenciando um host multi-inquilino denso.

### 6-bis.3 Docker e persistência

```text
Engine            28.1.1   (API 1.49)
Compose           v2.35.1
storage driver    overlay2
logging driver    json-file
docker root       /var/lib/docker
containers        39 total / 39 em execução
imagens           31
security          apparmor, seccomp builtin, cgroupns  (não rootless)
```

O host executa **~10 sistemas distintos**: `marcenaria`, `baronesa`,
`fenestra`, `contente_erp`, `cafe_erp`, `smartdata`, `community`,
`community-hml`, `queonda`, `boutique`, `espresso`, `monicaleila`. Dois
containers estão `unhealthy` (`community-frontend`, `boutique-collections-api`).

Ponto que evita uma conclusão errada: existem containers `baronesa-backend`,
`baronesa-frontend` e `baronesa-db`, mas eles pertencem à rede
`eventos_baronesa_net` e servem `eventos.abaronesa.net.br`. **Não** são o
Empório.

Pegada do Empório na VPS:

```text
containers com nome emporio            0
volume emporio-postgres-data           ausente
volume emporio-backend-uploads         ausente
volume emporio-website-uploads         ausente
volume emporio-whatsapp-session        ausente
rede do emporio                        ausente
```

O Empório é **greenfield** neste host: nenhum recurso a preservar, nenhum a
colidir por nome. O único volume da família é `baronesa-pg-data`, do sistema de
eventos.

### 6-bis.4 Nginx, TLS e domínios

```text
nginx             1.18.0 (Ubuntu), systemd active
sites-enabled     25 arquivos
certbot           1.21.0
certificados      ~20 em /etc/letsencrypt/live/
```

Confronto direto com os dois domínios contratados:

```text
grep -rl "emporio.abaronesa.net.br" /etc/nginx/   →  nenhum arquivo
cert emporio.abaronesa.net.br                     →  ausente
cert erp-emporio.abaronesa.net.br                 →  ausente
```

Isso confirma, do lado do host, o que a §3 observou de fora: os dois domínios
resolvem para a VPS mas não têm server block nem certificado, caindo em vhost
default que responde `200` em HTTP e apresenta o certificado de
`eventos.abaronesa.net.br`.

A família `abaronesa.net.br` já possui `eventos`, `movelariarustica` e
`erp-movelaria` configurados — a preparação do Empório precisa conviver com
eles sem tocá-los.

### 6-bis.5 Paths, usuário operacional e systemd

```text
/opt/sistemas                    directory 755 ubuntu:ubuntu   (existe, ~20 sistemas irmãos)
/opt/sistemas/emporio            AUSENTE
/opt/sistemas/emporio-control    AUSENTE
/etc/emporio                     AUSENTE
/etc/emporio-release-control     AUSENTE
/var/lib/emporio                 AUSENTE
/var/backups/emporio             AUSENTE

usuário deploy-emporio           AUSENTE
unidades systemd emporio/release-control   nenhuma
timers de backup do Empório      nenhum (apenas dpkg-db-backup do SO)
```

`/var/backups` contém apenas artefatos do sistema operacional. **Não existe
infraestrutura de backup para o Empório**, e portanto o pré-requisito de backup
antes de migration não está atendido.

### 6-bis.6 GHCR a partir da VPS — pronto

```text
/root/.docker/config.json        presente, modo 600, root:root  (não aberto)
entrada "ghcr.io" no store       presente (apenas a chave; nenhum valor lido)

manifest inspect dos seis digests de v0.1.0, sem login e sem pull:
  backend           OK
  website-backend   OK
  frontend          OK
  website-frontend  OK
  whatsapp-service  OK
  gateway           OK

imagens abaronesa-emporio presentes na VPS após a inspeção:  0
```

Este é o item mais favorável do inventário: a VPS **já lê os seis digests da
`v0.1.0`**, apesar de os packages serem privados. Nenhuma imagem foi baixada,
nenhum `docker login`, `pull`, `tag` ou `logout` foi executado, e o credential
store não foi alterado nem lido.

Contraste registrado: a mesma inspeção **falha** na estação de trabalho local
(`unauthorized`), porque lá não há credencial GHCR. A prontidão é da VPS, não do
workstation.

## 7. Matriz `AS_IS -> REQUIRED`

| # | Item | AS_IS | REQUIRED | ACTION | RISK | ROLLBACK | AUTHORITY |
|---|---|---|---|---|---|---|---|
| 1 | rota SSH de inventário | **resolvida** — `root@31.97.251.16`, host key confiada, `ssh-copy-id` já feito | rota de inspeção utilizável | nenhuma; `root` não vira mecanismo de deploy | usar `root` como deploy violaria o handoff §12 | — | atendida |
| 2 | usuário `deploy-emporio` | **AUSENTE** (`id` não resolve) | usuário dedicado, sem root, sudo mínimo | criar usuário/grupo e sudoers restrito | conflito com os ~10 sistemas do host | `userdel`/remover sudoers | Gate B |
| 3 | raiz de deploy | **conflito**: o código fixa `/opt/sistemas/emporio` (ausente); o operador designou `/opt/sistemas/baronesa/emporio`, que **existe**, vazio, `755 root:root` | um único path, coerente entre código e host | reconciliar o path — ver §7.1 | transporte publica em diretório diferente do que o host prepara | reverter o commit de path | **humano** + Gate A |
| 4 | `/opt/sistemas/emporio-control` | **AUSENTE** | control plane isolado | criar | idem | remover | Gate B |
| 5 | Docker/Compose | **Engine 28.1.1, Compose v2.35.1**, overlay2, json-file, não rootless | adequado, sem mudança | nenhuma | — | — | atendida |
| 6 | portas 80/443 | **ocupadas** pelo Nginx 1.18.0 com 25 sites-enabled | coexistência; Empório atrás do mesmo Nginx | adicionar dois server blocks dedicados | derrubar `eventos` e outros 24 sites | remover blocks e recarregar | Gate B |
| 7 | porta do gateway | **`8120` livre** | `127.0.0.1:8120` | nenhuma verificação adicional | colisão futura | ajustar variável | atendida |
| 8 | porta do control plane | **`8180` livre** | `127.0.0.1:8180` | nenhuma verificação adicional | colisão futura | ajustar variável | atendida |
| 9 | volumes de persistência | **os quatro ausentes**; só existe `baronesa-pg-data`, de outro sistema | `emporio-postgres-data`, `emporio-backend-uploads`, `emporio-website-uploads`, `emporio-whatsapp-session` | criar pelo Compose no primeiro up | nenhuma colisão de nome detectada | remover volumes vazios criados | Gate D |
| 10 | PostgreSQL comercial | host tem 8 PostgreSQL de outros sistemas; **`5432` livre** | serviço do Compose, sem porta publicada | subir pelo Compose | publicar porta por engano | `docker compose rm` do serviço | Gate D |
| 10b | memória e swap | **3,7 GiB disponíveis, swap 0 B**, load 2,5/4 CPUs | folga para 7 serviços, incluindo 2 JVM e Chromium | dimensionar limites por serviço; decidir sobre swap | **OOM em produção atingindo os outros ~10 sistemas** | reduzir limites / não subir | **humano** + Gate B |
| 10c | atualizações do host | *System restart required*, 35 updates, 2 zumbis | host estável antes do primeiro deploy | janela de manutenção do host | reinício derruba todos os sistemas | — | **humano** |
| 11 | DNS dos dois domínios | **já apontam para a VPS** | inalterado | nenhuma | — | — | nenhuma |
| 12 | TLS dos dois domínios | **ausente**; servido cert de `eventos` | certificado válido por domínio | emitir via Certbot | rate limit Let's Encrypt; interromper `eventos` | revogar/remover e recarregar | Gate B |
| 13 | redirect HTTP→HTTPS | **ausente** nos dois domínios | `301` para HTTPS | configurar nos server blocks | — | remover blocks | Gate B |
| 14 | Nginx de host | **1.18.0 ativo, 25 sites-enabled**; nenhum menciona os domínios do Empório | preservado + dois blocks novos | acrescentar arquivos, `nginx -t`, reload | quebrar 25 sites de terceiros | remover arquivos e reload | Gate B |
| 15 | backup pré-migration | **inexistente**; `/var/backups` só tem artefatos do SO; nenhum timer | backup obrigatório, retenção e cópia externa | definir script, destino e retenção | deploy irreversível sem backup | — | Gate B |
| 16 | restore verificável | inexistente | restore testado | ensaiar fora de produção | — | — | Gate E |
| 17 | imagem do release-control | placeholder no Compose | imagem publicada por digest | publicar e referenciar | usar `latest` | apontar para digest anterior | Gate B |
| 18 | systemd do control plane | **nenhuma unidade** `emporio`/`release-control` no host; só `.service.example` no repo | unidade instalada e habilitada | instalar a partir do exemplo | conflito de nome | `systemctl disable` e remover | Gate B |
| 19 | PostgreSQL do control plane | **inexistente** | instância e volume exclusivos | subir pelo Compose isolado | compartilhar banco comercial | remover stack isolada | Gate B |
| 20 | GitHub App deployer | **inexistente** | App separada da publisher, permissões mínimas | registrar App e instalar no repo | reusar a publisher | desinstalar App | **humano** + Gate B |
| 21 | `DEPLOYER_ACTOR_IDS` | **MISSING** | id decimal do bot deployer | configurar variable | allowlist errada barra o deploy | remover variable | Gate B |
| 22 | environment `production` | **inexistente** | environment com secrets/vars | criar environment | expor secret a job errado | remover environment | Gate B |
| 23 | `PRODUCTION_SSH_HOST`/`PORT` | **MISSING** | variables do environment | configurar | — | remover | Gate B |
| 24 | `PRODUCTION_SSH_PRIVATE_KEY`/`KNOWN_HOSTS` | **MISSING** | secrets do environment | configurar | chave com escopo excessivo | remover secret e revogar chave | **humano** + Gate B |
| 25 | credencial GHCR na VPS | **PRONTA** — `/root/.docker/config.json` modo `600` com entrada `ghcr.io`; os seis digests de `v0.1.0` respondem `OK` | leitura dos seis digests | nenhuma para o primeiro deploy; auditar escopo do token | token pode ter escopo de escrita — não verificável sem abrir o valor | remover credencial | atendida, escopo a auditar |
| 26 | ponte RS256/JWKS deployer no ERP | opt-in, default `false` | habilitada na instância de produção | configurar issuer, chave e `kid` | expor emissor | desabilitar flag | Gate C |
| 27 | release `v0.1.0` e seis digests | publicada e validada | inalterada | nenhuma | — | — | nenhuma |
| 28 | contratos locais de deploy | seis validadores verdes | mantidos | nenhuma | — | — | nenhuma |
| 29 | `ck_rc_deployment_workflow_binding` | risco de ordenação (§6.2) | `CONFIRMED` só com vínculo | corrigir ou aceitar risco | transação abortada no primeiro deploy | — | Gate A |
| 30 | rate limit de rollback | teste vermelho; limite `2/min` | expectativa e limite coerentes | decidir limite ou teste | rollback limitado em incidente | — | Gate A |

### 7.1 Conflito da raiz de deploy — bloqueante

O operador informou que o Docker do Empório será configurado em:

```text
/opt/sistemas/baronesa/emporio
```

Inspeção confirma que esse diretório **existe**, está **vazio**, `755
root:root`, irmão de `/opt/sistemas/baronesa/eventos` (`ubuntu:ubuntu`). Já
`/opt/sistemas/emporio` — o path que o código usa — **não existe**.

O confronto com o código mostra que a divergência não é cosmética:

| Local | Forma | Ajustável sem código? |
|---|---|---|
| `tools/deploy/deployment_transport.py:36` | `DEPLOY_ROOT = "/opt/sistemas/emporio"`, constante; `REMOTE_HELPER` e `INCOMING_ROOT` derivam dela | **não** |
| `tools/deploy/deployment_cli.py:476` | lê `EMPORIO_DEPLOY_ROOT`, default `/opt/sistemas/emporio` | sim |
| `ops/deploy/deployment-remote.py:34` | `DEPLOY_ROOT = Path("/opt/sistemas/emporio")` | **não** |
| `tools/deploy/validate_production_adapter.py:223,334` | exige o literal `/opt/sistemas/emporio` no fonte e na documentação | **não** |

Consequência prática: definir apenas `EMPORIO_DEPLOY_ROOT` no host **não
resolve**. O transporte, que roda no GitHub Actions e é quem envia o bundle,
calcula `REMOTE_HELPER` e `INCOMING_ROOT` a partir da sua própria constante e
publicaria em `/opt/sistemas/emporio/...` enquanto o host preparou
`/opt/sistemas/baronesa/emporio`. O deploy falharia no transporte, ou pior,
criaria uma segunda árvore.

Duas saídas, e a escolha é do orquestrador:

- **A.** adotar `/opt/sistemas/baronesa/emporio` e alterar as quatro referências
  acima de forma coerente, incluindo o validador e a documentação — trabalho de
  Gate A, com suíte de deploy revalidada;
- **B.** manter `/opt/sistemas/emporio` e criar essa árvore no Gate B, deixando
  `/opt/sistemas/baronesa/emporio` sem uso.

Este relatório não escolhe. Registra que o primeiro deploy **não pode ocorrer**
enquanto as duas pontas discordarem.

## 8. Plano por gates

Nenhum comando abaixo foi executado nesta slice. Nenhum contém valor secreto;
segredos aparecem por nome de arquivo ou de secret.

### Gate A — correções locais realmente bloqueantes

| Ação | Alvo | Verificação | Reversão |
|---|---|---|---|
| **decidir e aplicar a raiz de deploy (§7.1)** | `deployment_transport.py:36`, `deployment-remote.py:34`, `deployment_cli.py:27`, `validate_production_adapter.py:223,334` e docs | `validate_production_adapter.py` verde; path idêntico nas duas pontas | `git revert` do commit |
| decidir §6.2: exigir vínculo antes de `CONFIRMED` ou aceitar o risco de ordenação | `deployer_service.py:977` e `:1096` | suíte deployer verde; teste causal de outcome antes do bind | `git revert` do commit |
| decidir §6.1: limite de rollback ou expectativa do teste | `config.py:47` ou o teste | suíte verde sem mascarar a causa | `git revert` |
| revalidar matriz local completa | 16 validadores e suítes canônicas | todos verdes | — |

Gate A é o único gate que **não** toca a VPS. É pré-requisito de D, não de B.

### Gate B — preparação mínima e reversível

Ordem proposta, cada passo com verificação e reversão:

```text
B1  usuário/grupo deploy-emporio + sudoers mínimo
    verificação: id deploy-emporio; sudo -n -l
    reversão:    remover sudoers; userdel

B2  árvore /opt/sistemas/emporio e /opt/sistemas/emporio-control
    verificação: stat de owner e modo
    reversão:    remover somente o que foi criado

B3  chave SSH dedicada do deploy + authorized_keys do deploy-emporio
    verificação: ssh -o BatchMode=yes deploy-emporio@<host> 'id -un'
    reversão:    remover a linha do authorized_keys

B4  environment production no GitHub + PRODUCTION_SSH_* e DEPLOYER_ACTOR_IDS
    verificação: nomes presentes; nenhum valor lido
    reversão:    remover environment/variables/secrets

B5  GitHub App deployer separada + instalação restrita ao repositório
    verificação: GET /app e /installation/repositories com JWT em memória
    reversão:    desinstalar App

B6  credencial GHCR read:packages na VPS
    verificação: docker manifest inspect dos seis digests
    reversão:    remover credencial

B7  server blocks Nginx dos dois domínios + certificados
    verificação: nginx -t; curl -I https:// com verificação estrita;
                 eventos.abaronesa.net.br permanece 200
    reversão:    remover arquivos e recarregar Nginx

B8  imagem do release-control por digest + systemd + PostgreSQL do control plane
    verificação: health live/ready em 127.0.0.1:8180
    reversão:    systemctl disable --now; remover stack isolada
```

### Gate C — prontidão sem deploy comercial

```text
C1  portas 8120 e 8180 livres e ligadas somente a loopback
C2  ponte RS256/JWKS deployer habilitada e JWKS respondendo
C3  release-control deployer com /health/ready verde e sync sem drift
C4  v0.1.0 visível como elegível, sem dispatch
C5  backup e restore ensaiados fora de produção
```

Gate C não sobe a stack comercial e não executa `deploy-production.yml`.

### Gate D — primeiro deploy acompanhado de `v0.1.0`

Exclusivamente pelo caminho canônico
`UI produção -> runtime deployer -> deploy-production.yml -> transporte -> CLI transacional -> outcome`.
Exige janela acordada, backup verificado imediatamente antes, e observação sem
intervenção. Nenhum `docker` manual, nenhum bypass de journal, lock ou máquina
de estados.

### Gate E — rollback e restore

Somente se necessário ou em ambiente controlado. Não provocar falha em produção
para satisfazer checklist.

## 9. Inputs humanos indispensáveis

A rota SSH, que era o item 1, **foi fornecida e está atendida**. Permanecem
cinco decisões, nenhuma descobrível por inspeção:

0. **Raiz de deploy (§7.1)** — adotar `/opt/sistemas/baronesa/emporio`, com a
   alteração de código que isso implica, ou manter `/opt/sistemas/emporio`. É o
   bloqueio mais imediato: sem ele, transporte e host apontam para árvores
   diferentes.
1. **Capacidade de memória.** Restam 3,7 GiB disponíveis e não há swap, num host
   que já roda 39 containers de ~10 sistemas. Subir sete serviços — dois JVM,
   Chromium/WhatsApp e PostgreSQL — sem decidir limites por serviço arrisca OOM
   que atingiria **os outros sistemas**, não só o Empório. É preciso decidir
   entre: definir limites conservadores, habilitar swap, ampliar a VPS, ou
   aceitar formalmente o risco.
2. **Janela do host.** O servidor pede reinício e tem 35 atualizações
   pendentes, incluindo 3 de segurança. Reiniciar derruba todos os sistemas
   co-hospedados; não reiniciar mantém o kernel desatualizado sob o primeiro
   deploy. A janela é decisão do usuário.
3. **Decisão sobre §6.2** — corrigir a ordenação `CONFIRMED`/vínculo antes do
   primeiro deploy ou aceitar formalmente o risco.
4. **Decisão sobre §6.1** — se `rollback_rate_per_minute = 2` é o comportamento
   desejado.

Itens 3 e 4 pertencem ao Gate A; 1 e 2 condicionam o Gate D.

Tudo o mais no plano é executável sob autorização de gate, sem novo dado humano.

## 10. Prova de que nenhuma mutação proibida ocorreu

```text
mutação na VPS                      nenhuma — somente leitura de metadados
                                    e estados; nenhum arquivo, usuário, grupo,
                                    diretório, serviço, container, volume, rede,
                                    firewall, Nginx, certificado, pacote,
                                    migration, banco ou backup criado ou alterado
comandos remotos usados             hostnamectl, timedatectl, uptime, nproc,
                                    free, swapon --show, df, ps, cat /proc/loadavg,
                                    ss -ltn, ufw status, iptables -S | wc,
                                    docker version/info/ps/volume ls/network ls,
                                    docker manifest inspect, nginx -v,
                                    systemctl is-active/list-units/list-timers,
                                    certbot --version, ls, stat, id, grep -l
comandos proibidos                  nenhum docker inspect, compose config,
                                    systemctl cat, cat de arquivo operacional,
                                    login, pull, build, run, up, down, restart,
                                    stop, prune, rm
deploy / rollback                   runs deploy-production.yml = 0
                                    runs rollback-production.yml = 0
dispatch de workflow                nenhum
push / tag / release                nenhum; origin/main permanece 67abde4
                                    tags = 1 (v0.1.0), releases = 1 (v0.1.0)
secrets / variables / App           nenhuma alteração; DEPLOYER_ACTOR_IDS segue
                                    MISSING e nenhum environment foi criado
usuários, arquivos, serviços        nenhum criado ou alterado
StrictHostKeyChecking               sempre yes; nunca no nem accept-new
usuário SSH                         somente o efetivo do ssh -G; nenhum adivinhado
docker login / pull / build / run   nenhum; apenas manifest inspect, que falhou
docker inspect / compose config     não executados
systemctl cat / cat de operacional  não executados
ops/env/.env.production             não aberto
chaves, tokens, .env, sessões       nenhum conteúdo aberto ou transcrito
```

Snapshot final idêntico ao inicial, exceto pela criação deste relatório:

```text
## main...origin/main [ahead 7]
?? docs/.../S37-inventario-read-only-vps-plano-preparacao-producao.report.md
HEAD        807a71e4f94adebf757807168779a826f4880894
origin/main 67abde48fd4a74de5bcff22bf592bd9005094210
```

## 11. Arquivos e resíduos

Criado exclusivamente:

```text
docs/infrastructure/deployment/implementation/slices/S37-inventario-read-only-vps-plano-preparacao-producao.report.md
```

Nenhum arquivo temporário, diretório, credencial ou processo foi deixado.
Nenhum outro arquivo do repositório foi alterado. O relatório permanece local,
não staged e não commitado. O executor não aceita a S37, não cria a S38 e não
solicita autorização para executar o plano.

IN_PROGRESS — inventário read-only concluído; aguardando revisão e autorização do plano de preparação

---

## 12. Revisão e aceite do orquestrador — 03/08/2026

### 12.1 Evidência aceita

O orquestrador confrontou o relatório com a task S37, o estado Git, os
contratos versionados e o código do deployer. Foram confirmados:

- relatório com 772 linhas, único arquivo não rastreado antes desta revisão;
- task íntegra em
  `d19be9111fc370e2fa5ecb1a48d44bea0ab59a6f4709df708b717ed421810dba`;
- `HEAD` em `807a71e4f94adebf757807168779a826f4880894`, remoto
  `67abde48fd4a74de5bcff22bf592bd9005094210`, `ahead 7 / behind 0`;
- ausência de deploy e rollback e preservação integral da fronteira read-only;
- inventário remoto suficiente para distinguir o Empório greenfield dos
  containers `baronesa-*` do sistema de eventos;
- acesso da VPS aos seis digests de `v0.1.0` sem pull e sem leitura do arquivo
  de credenciais;
- matriz `AS_IS -> REQUIRED`, plano Gate A-E e reversões adequados para orientar
  as próximas slices.

### 12.2 Decisões fechadas pelo orquestrador

1. A raiz canônica permanece `/opt/sistemas/emporio`, como congelada na
   proposta, S20, S21, transporte, helper remoto, CLI, validadores e operação.
   O diretório vazio `/opt/sistemas/baronesa/emporio` não redefine esse contrato
   e não será utilizado. Nenhuma alteração dos quatro pontos de código é
   necessária.
2. `rollback_rate_per_minute=2` permanece como política conservadora de
   produção. O teste de idempotência deve isolar o contrato sob teste, sem
   aumentar ou enfraquecer o limite real; deve existir cobertura própria do
   `429` na terceira mutação dentro da janela.
3. O risco de outcome anterior ao bind foi reclassificado. A busca integral de
   chamadas mostrou que `apply_outcome` e `apply_rollback_outcome` são chamados
   em produção somente por `DeployerReconciler`, que executa `_bind_run` antes
   de baixar, validar e aplicar o artifact. Portanto a falha observada é causada
   pela fixture inválida, não por uma corrida alcançável no fluxo atual.
4. Mesmo não sendo uma corrida alcançável hoje, o serviço será endurecido para
   recusar de forma causal e sem mutação um outcome `CONFIRMED` quando as quatro
   colunas de vínculo não estiverem completas. A constraint do banco permanece
   inalterada como última linha de defesa.
5. A capacidade e a janela de reinício não são inputs do Gate A. Nenhum primeiro
   deploy será autorizado com 3,7 GiB disponíveis e sem um envelope de recursos
   aceito; swap, ampliação da VPS e reinício continuam sendo mutações de host a
   submeter ao usuário em uma slice posterior, com alvo e reversão concretos.

### 12.3 Decisão terminal

O inventário contratado foi concluído, os achados foram causalmente
classificados e nenhuma mutação externa ocorreu. A S37 está aceita. O próximo
contrato é a S38, restrita ao fechamento local do Gate A e à validação remota
por CI/candidato, sem acesso ou mutação da VPS.

ACCEPTED — inventário read-only concluído; S37 encerrada e S38 aberta
