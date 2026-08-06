# S41 — Bootstrap do transporte de produção: identidade, filesystem e SSH

> **Data:** 03/08/2026
> **CWD:** `/home/gregorio/git/baronesa/emporio`
> **Executor:** CLI
> **Contrato:** `S41-bootstrap-transporte-producao-identidade-ssh.task.md`
> **SHA-256 da task:** `bdc14d88d8259050e815ac4ff5df5bb34c5f464178327de4816a400be5897ada`
> **Resultado:** `IN_PROGRESS — transporte SSH dedicado preparado; aguardando aceite e instalação do control root`

## 1. Autorização humana

A delegação continha literalmente a frase exigida pela §2, cobrindo a criação do
usuário `deploy-emporio`, o preparo de `/opt/sistemas/emporio`, a instalação de
chave SSH dedicada, o acesso ao Docker pelo grupo `docker`, a configuração dos
`PRODUCTION_SSH_*` no environment `production`, e a reversão exata do que a
própria S41 criar em caso de falha.

Ela **não** cobre reinício/atualização/swap do host, Nginx, Certbot, DNS, TLS,
firewall, Docker pull/build/login/run/compose, helper ou control root, App
deployer, `DEPLOYER_ACTOR_IDS`, ponte RS256/JWKS, banco, migration, backup,
restore, deploy, rollback ou leitura de credenciais preexistentes.

## 2. Snapshot inicial

### 2.1 Git e GitHub

| Item | Exigido | Observado |
|---|---|---|
| `HEAD` | `6771eeec223edf2943fdd42b5d6eb03f496c0117` | idêntico |
| `origin/main` e remoto | `daaa7061ab9f7a722b17e37c0f060f45141225e7` | idêntico |
| divergência | ahead 2 / behind 0 | idêntico |
| SHA-256 da task | `bdc14d88...be5897ada` | idêntico |
| stage | vazio | vazio |
| não rastreados | relatórios S39 e S40 | apenas eles |
| `git diff --check` | 0 | 0 |

```text
gh auth status              greggorio (keyring), Active account: true
gh api user                 login=greggorio  id=35626201
environments                0            -> "production" MISSING
vars do env production      MISSING
secrets do env production   MISSING
vars do repositório         RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS=35626201
                            RELEASE_PUBLISHER_ACTOR_IDS=312233471
deploy / rollback runs      0 / 0
```

### 2.2 VPS, somente leitura

Rota bootstrap com `BatchMode=yes`, `StrictHostKeyChecking=yes`,
`ConnectTimeout=10`, `ForwardAgent=no`, `ForwardX11=no`, `ControlMaster=no`,
`ControlPath=none`, `NumberOfPasswordPrompts=0`.

```text
hostname        srv1006846
uptime          144 dias, load 2.21 / 2.32 / 2.39
memória         15 Gi total | 11 Gi usados | 3,7 Gi disponíveis
swap            0 dispositivos
disco /         194 G | 103 G usados | 92 G livres | 53%

deploy-emporio                    id: no such user            AUSENTE
/opt/sistemas/emporio             AUSENTE
/opt/sistemas/emporio-control     AUSENTE
/home/deploy-emporio              AUSENTE
/opt                              directory 755 root:root      (pai, não será tocado)
/opt/sistemas                     directory 755 ubuntu:ubuntu  (pai, não será tocado)
sudoers mencionando deploy-emporio  0 arquivos

grupo docker    docker:999
socket          /var/run/docker.sock  660 root:docker
Docker Engine   28.1.1        Compose 2.35.1

sshd efetivo    permitrootlogin yes | pubkeyauthentication yes
                passwordauthentication yes | kbdinteractiveauthentication no
                (lido, não editado)

host keys       256  SHA256:FJ/VyIKtp+fLmDZwZ6gjK5M2w5aXbP/mS5YSzC4NkQ0  (ECDSA)
                256  SHA256:oB+4purUbvJRAJo4ZgHLpsUvQZO1CKzSrgiawFXKp/A  (ED25519)
                3072 SHA256:yOsx7KHIusLLQ+2PyrQ0iCpjuHc/dALIWw/kuYoQ2U8  (RSA)

containers      37 em execução / 39 total
volumes         26      redes 18      imagens 31
volumes emporio-*   0
nginx           active
unidades systemd emporio/release-control   0
portas 8120 / 8180   0 / 0 listeners
```

As três fingerprints conferem exatamente com as entradas já confiadas em
`~/.ssh/known_hosts` desde a S37. A contagem de containers passou de 39 em
execução (S37) para 37 em execução e 39 no total — variação legítima de outros
sistemas, não tratada como falha.

Todos os alvos da S41 estavam **ausentes**, o que habilita tanto a criação
quanto, se necessário, a reversão exata prevista pela §8.

## 3. Gates locais antes da mutação

| Gate | Exit | Resultado |
|---|---:|---|
| `tools.deploy.tests.test_deployment_transport` | 0 | `Ran 59 tests` OK |
| `tools.deploy.tests.test_deploy_workflow_contract` | 0 | `Ran 18 tests` OK |
| `validate_deploy_workflow.py` | 0 | `deploy-workflow-contract: ok` |
| `validate_production_adapter.py` | 0 | `production-adapter-contract:valid` |
| `validate_deployer_runtime.py` | 0 | `deployer-runtime:valid` |
| `git diff --check` | 0 | sem saída |

Confirmado no código versionado (`tools/deploy/deployment_transport.py`):

```text
:35  REMOTE_USER = "deploy-emporio"
:36  DEPLOY_ROOT = "/opt/sistemas/emporio"
:806 User <REMOTE_USER> | BatchMode yes | IdentitiesOnly yes
:807 StrictHostKeyChecking yes | UserKnownHostsFile <dedicado>
:809 ForwardAgent no
:810 ClearAllForwardings yes | PasswordAuthentication no
     ocorrências de "sudo": 0
     PRODUCTION_SSH_HOST, PRODUCTION_SSH_PORT,
     PRODUCTION_SSH_PRIVATE_KEY, PRODUCTION_SSH_KNOWN_HOSTS
```

## 4. Plano exato, escrito antes da primeira mutação (§6)

### 4.1 Recursos que serão criados

```text
VPS
  grupo   deploy-emporio                      (GID alocado pelo host)
  usuário deploy-emporio                      (UID alocado pelo host)
          home /home/deploy-emporio, shell POSIX existente, senha bloqueada
          grupo suplementar: apenas docker
  /home/deploy-emporio/.ssh                   0700 deploy-emporio:deploy-emporio
  /home/deploy-emporio/.ssh/authorized_keys   0600 deploy-emporio:deploy-emporio
  /opt/sistemas/emporio                       0700 deploy-emporio:deploy-emporio
  /opt/sistemas/emporio/releases              0700 deploy-emporio:deploy-emporio
  /opt/sistemas/emporio/shared                0700 deploy-emporio:deploy-emporio
  /opt/sistemas/emporio/shared/control        0700 deploy-emporio:deploy-emporio
  /opt/sistemas/emporio/shared/deploy         0700 deploy-emporio:deploy-emporio
  /opt/sistemas/emporio/shared/deploy/incoming    0700 deploy-emporio:deploy-emporio
  /opt/sistemas/emporio/shared/deploy/snapshots   0700 deploy-emporio:deploy-emporio

GitHub
  environment production
  var    PRODUCTION_SSH_HOST=31.97.251.16
  var    PRODUCTION_SSH_PORT=22
  secret PRODUCTION_SSH_PRIVATE_KEY
  secret PRODUCTION_SSH_KNOWN_HOSTS

Local (temporário, destruído ao final)
  diretório mktemp -d modo 0700 com a chave Ed25519 dedicada e known_hosts
```

Nada além disso. Em particular: nenhum sudoers, nenhum grupo `sudo`/`adm`,
nenhum `/opt/sistemas/emporio-control`, nenhum `.env`, helper, bundle, release,
link `current`/`previous`, backup, journal ou installed state.

### 4.2 Comandos remotos exatos, com valores não sensíveis

```text
groupadd --system deploy-emporio
useradd  --system --gid deploy-emporio --create-home \
         --home-dir /home/deploy-emporio --shell /bin/sh deploy-emporio
passwd --lock deploy-emporio
usermod --append --groups docker deploy-emporio

install -d -o deploy-emporio -g deploy-emporio -m 0700 \
        /opt/sistemas/emporio \
        /opt/sistemas/emporio/releases \
        /opt/sistemas/emporio/shared \
        /opt/sistemas/emporio/shared/control \
        /opt/sistemas/emporio/shared/deploy \
        /opt/sistemas/emporio/shared/deploy/incoming \
        /opt/sistemas/emporio/shared/deploy/snapshots

install -d -o deploy-emporio -g deploy-emporio -m 0700 /home/deploy-emporio/.ssh
install -m 0600 -o deploy-emporio -g deploy-emporio /dev/null \
        /home/deploy-emporio/.ssh/authorized_keys
# a chave pública é escrita por stdin, nunca em argv
```

`/opt`, `/opt/sistemas`, o socket Docker e recursos de outros sistemas não são
alterados.

### 4.3 Plano de verificação

```text
id -un / id -G / groups                     == deploy-emporio, grupos primário + docker
passwd --status                             senha bloqueada (L)
grep -rl deploy-emporio /etc/sudoers*       0 arquivos
sudo -n true como deploy-emporio            deve falhar
lstat de cada diretório                     tipo dir, 0700, owner dedicado, sem symlink
ssh dedicado com IdentitiesOnly + strict    id, docker version, docker compose version
docker ps / volume ls / network ls          contagens idênticas ao baseline
```

### 4.4 Plano de reversão, em ordem inversa (§8)

```text
1. gh secret delete PRODUCTION_SSH_PRIVATE_KEY  --env production
   gh secret delete PRODUCTION_SSH_KNOWN_HOSTS  --env production
   gh variable delete PRODUCTION_SSH_HOST       --env production
   gh variable delete PRODUCTION_SSH_PORT       --env production
   remover o environment production criado pela slice
2. remover /home/deploy-emporio/.ssh/authorized_keys e o home criado
3. rmdir da árvore /opt/sistemas/emporio, somente se continuar vazia
4. userdel deploy-emporio e groupdel deploy-emporio
5. destruir o temporário local
6. reprovar o baseline
```

A reversão só se aplica ao que o snapshot da §2.2 provou ausente e a S41 criou.
Nada preexistente será removido. Nenhuma reversão automática ocorre após uma
conclusão integral verde.

### 4.5 Temporário local e fingerprints

```text
diretório   mktemp -d, modo 0700, fora do repositório
chave       Ed25519 sem passphrase, comentário operacional
            private 0600, public 0644, apenas no temporário
```

As fingerprints públicas esperadas do host são as três da §2.2. A chave privada
não é transcrita neste relatório, não é copiada para a VPS e não aparece em
argv, log ou histórico.

## 5. Execução

### 5.1 Usuário e grupo

```text
groupadd --system deploy-emporio                                   exit 0
useradd --system --gid deploy-emporio --create-home \
        --home-dir /home/deploy-emporio --shell /bin/sh …          exit 0
passwd --lock deploy-emporio                                       exit 0
usermod --append --groups docker deploy-emporio                    exit 0

resultado: uid=998(deploy-emporio) gid=998(deploy-emporio)
           groups=998(deploy-emporio),999(docker)
           passwd --status -> L  (senha bloqueada)
```

O UID/GID `998` foi alocado pelo host, não escolhido. O usuário pertence apenas
ao próprio grupo primário e a `docker`.

**Declaração exigida pela §3.2:** pertencer ao grupo `docker` concede capacidade
efetivamente elevada sobre o daemon, equivalente a root no que o daemon pode
fazer. Isso é o mínimo necessário para os comandos Docker/Compose do adapter,
está limitado a esta identidade dedicada e **não** foi ampliado para `sudo`,
`adm` ou qualquer outro grupo.

### 5.2 Árvore de diretórios

`install -d -o deploy-emporio -g deploy-emporio -m 0700` para os sete caminhos
da §3.3, `exit 0`. Validação por `stat`, item a item:

```text
/opt/sistemas/emporio                          directory 700 deploy-emporio:deploy-emporio  symlink=nao
/opt/sistemas/emporio/releases                 directory 700 deploy-emporio:deploy-emporio  symlink=nao
/opt/sistemas/emporio/shared                   directory 700 deploy-emporio:deploy-emporio  symlink=nao
/opt/sistemas/emporio/shared/control           directory 700 deploy-emporio:deploy-emporio  symlink=nao
/opt/sistemas/emporio/shared/deploy            directory 700 deploy-emporio:deploy-emporio  symlink=nao
/opt/sistemas/emporio/shared/deploy/incoming   directory 700 deploy-emporio:deploy-emporio  symlink=nao
/opt/sistemas/emporio/shared/deploy/snapshots  directory 700 deploy-emporio:deploy-emporio  symlink=nao

arquivos na árvore: 0     symlinks: 0
/opt/sistemas/emporio-control: ausente (correto)
pais inalterados: /opt 755 root:root | /opt/sistemas 755 ubuntu:ubuntu
```

### 5.3 Chave dedicada e confronto de host keys

```text
temporário   mktemp -d, modo 0700, fora do repositório
chave        Ed25519, sem passphrase
             comentário deploy-emporio@abaronesa-emporio-production
             private 0600 | public 0644
fingerprint  256 SHA256:PotdyucjkzRGEuYfWMAp1fg4vWUK3ZlPHW/ZRWjdFAQ (ED25519)
```

A chave pública foi instalada por **stdin**, sem `ssh-copy-id` e sem aparecer em
argv:

```text
/home/deploy-emporio/.ssh                 modo 700 deploy-emporio:deploy-emporio
/home/deploy-emporio/.ssh/authorized_keys modo 600 deploy-emporio:deploy-emporio
linhas em authorized_keys                 1
fingerprint instalada                     idêntica à gerada
```

Host keys confrontadas por **três** fontes independentes, todas coincidentes,
antes de usar a nova chave:

| Fonte | ECDSA | ED25519 | RSA |
|---|---|---|---|
| `ssh-keyscan` na porta 22 | `FJ/VyIK…NkQ0` | `oB+4pur…Kp/A` | `yOsx7KH…2U8` |
| `/etc/ssh/ssh_host_*_key.pub` pela rota root | idem | idem | idem |
| `~/.ssh/known_hosts` já confiado desde a S37 | idem | idem | idem |

O `known_hosts` dedicado foi materializado apenas com host e porta aprovados,
sem linhas de comentário: 3 entradas (`ssh-rsa`, `ecdsa-sha2-nistp256`,
`ssh-ed25519`), modo `0600`.

### 5.4 Prova do transporte

Conexão com `BatchMode=yes`, `StrictHostKeyChecking=yes`, `IdentitiesOnly=yes`,
`UserKnownHostsFile` dedicado, `PasswordAuthentication=no`,
`NumberOfPasswordPrompts=0`, `ForwardAgent=no`, `ForwardX11=no`,
`ClearAllForwardings=yes`, `ControlMaster=no`, `ControlPath=none`, `-F /dev/null`
e chave explícita:

```text
id -un            deploy-emporio
id                uid=998(deploy-emporio) gid=998(deploy-emporio) groups=998,999(docker)
groups            deploy-emporio docker
cd /opt/sistemas/emporio && pwd     /opt/sistemas/emporio
docker version    28.1.1
docker compose    2.35.1
sudo -n true      "sudo: a password is required"   → NÃO autorizado
ssh_exit          0
```

Somente probes fixos foram executados. O helper remoto não foi chamado, pois
ainda não existe. Nenhum container, volume, rede, imagem ou serviço foi iniciado
ou alterado:

```text
antes   37/39 containers | 26 volumes | 18 redes | 31 imagens | 0 volumes emporio-*
depois  37/39 containers | 26 volumes | 18 redes | 31 imagens | 0 volumes emporio-*
```

### 5.5 Environment GitHub

```text
gh api -X PUT …/environments/production      exit 0   → environment "production"
gh variable set PRODUCTION_SSH_HOST  --env production   exit 0
gh variable set PRODUCTION_SSH_PORT  --env production   exit 0
gh secret   set PRODUCTION_SSH_PRIVATE_KEY --env production < <arquivo>   exit 0
gh secret   set PRODUCTION_SSH_KNOWN_HOSTS --env production < <arquivo>   exit 0
```

Os secrets foram enviados por **redirecionamento de stdin a partir de arquivo**,
nunca interpolados em argv, log ou variável de shell.

```text
vars do environment production
  PRODUCTION_SSH_HOST = 31.97.251.16    updatedAt 2026-08-03T23:55:46Z
  PRODUCTION_SSH_PORT = 22              updatedAt 2026-08-03T23:55:46Z

secrets do environment production (apenas nomes e timestamps)
  PRODUCTION_SSH_PRIVATE_KEY            updatedAt 2026-08-03T23:56:00Z
  PRODUCTION_SSH_KNOWN_HOSTS            updatedAt 2026-08-03T23:56:01Z

escopo de repositório: nenhum secret; vars continuam sendo apenas as duas
allowlists preexistentes
DEPLOYER_ACTOR_IDS: MISSING          App deployer: não criada
```

### 5.6 Limpeza do material local

```text
private key, public key, known_hosts e o scan   destruídos com shred -u
diretório temporário                            removido
prova de ausência                               path inexistente
chaves privadas em /tmp com padrão s41          0
material SSH no repositório                     0
chave no ssh-agent                              0 entradas
```

A chave privada existe agora somente como secret do environment `production`.

## 6. Validação final (§9)

| Exigência | Resultado |
|---|---|
| usuário dedicado, senha bloqueada, grupos primário + `docker` apenas | `uid=998 gid=998 groups=998,999(docker)`, `passwd --status = L` |
| zero sudo e zero sudoers | `sudo`/`adm` não contêm o usuário (0); 0 arquivos sudoers; `sudo -n` negado |
| árvore exata, owner e modos `0700` | sete diretórios reais, sem symlink, sem arquivos |
| login pela chave nova com SSH estrito, sem senha | `ssh_exit 0`, `PasswordAuthentication=no`, host key estrita |
| Docker Engine e Compose acessíveis sem mutação | `28.1.1` / `2.35.1`; contagens idênticas ao baseline |
| environment e quatro materiais nos escopos corretos | dois vars e dois secrets em `production`; nada no repositório |
| private key local ausente após o envio | comprovado |
| `origin/main`, package, `v0.1.0`, imagem e allowlists inalterados | `daaa7061`, 1 versão do package, `v0.1.0` intacta, duas allowlists preservadas |
| zero deploy/rollback e nenhum run novo | 0 / 0; `publish-release-control` continua com 1 run (o da S40) |
| host igual ao baseline | 37/39 containers, 26 volumes, 18 redes, 31 imagens, nginx `active`, 0 unidades Empório, portas 8120/8180 livres, swap 0 |
| nenhum serviço Empório iniciado | 0 unidades, 0 containers Empório |
| repositório inalterado e stage vazio | apenas os três relatórios não rastreados |

`sshd_config` não foi editado: mtime permanece `2025-12-26 11:32:42`. A
configuração efetiva do daemon (`passwordauthentication yes`,
`permitrootlogin yes`) é preexistente do host e **não** foi alterada por esta
slice; o bloqueio de senha aplica-se à identidade criada, via `passwd --lock`.

## 7. Inventário literal das mutações

Na VPS:

1. grupo de sistema `deploy-emporio` (GID 998);
2. usuário de sistema `deploy-emporio` (UID 998), home `/home/deploy-emporio`,
   shell `/bin/sh`, senha bloqueada;
3. associação ao grupo suplementar `docker`;
4. `/home/deploy-emporio/.ssh` (0700) e `authorized_keys` (0600) com uma única
   chave pública;
5. os sete diretórios da §3.3, todos `0700` e do usuário dedicado.

No GitHub:

6. environment `production`;
7. var `PRODUCTION_SSH_HOST`;
8. var `PRODUCTION_SSH_PORT`;
9. secret `PRODUCTION_SSH_PRIVATE_KEY`;
10. secret `PRODUCTION_SSH_KNOWN_HOSTS`.

Nada além disso. Nenhuma reversão foi necessária: a execução concluiu
integralmente verde, e a §8 proíbe reversão automática após conclusão.

## 8. Negativos e resíduos

```text
sshd daemon                      não editado, não reiniciado
sudoers / sudo                   nenhum
senha, agent forwarding          não usados; StrictHostKeyChecking sempre yes
chave privada na VPS             nunca copiada
Docker pull/login/build/run/compose   nenhum
pacote do SO, update, reboot, swap    nenhum
helper, Compose, .env, systemd, control plane   nenhum
/opt/sistemas/emporio-control    não criado
Nginx, TLS, DNS, firewall, bancos, backups      intocados
workflow, deploy, rollback, commit, push        nenhum
logs, runs, artifacts, packages, imagens        nada apagado
```

Estado final do repositório:

```text
HEAD         6771eeec223edf2943fdd42b5d6eb03f496c0117
origin/main  daaa7061ab9f7a722b17e37c0f060f45141225e7   (inalterado)
ahead 2 / behind 0        stage vazio
não rastreados            relatórios S39, S40 e S41
```

Nenhum resíduo local: o temporário da chave foi destruído e nenhum material
sensível aparece neste relatório.

O executor não aceita a S41, não cria a próxima slice, não instala helper e não
inicia serviços.

IN_PROGRESS — transporte SSH dedicado preparado; aguardando aceite e instalação do control root
