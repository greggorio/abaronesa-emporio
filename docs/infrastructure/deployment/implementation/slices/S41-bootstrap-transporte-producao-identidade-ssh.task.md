# S41 — Bootstrap do transporte de produção: identidade, filesystem e SSH

> **Data:** 03/08/2026
> **Predecessora:** S40 aceita
> **Gate:** B1–B4, limitado à fronteira de transporte
> **Produção comercial:** nenhum serviço iniciado e nenhum deploy executado

## 1. Objetivo

Preparar a fronteira mínima, reversível e verificável para que o workflow de
deploy possa futuramente alcançar a VPS como o usuário literal
`deploy-emporio`, sem usar `root` como mecanismo normal:

1. criar o usuário/grupo operacional dedicado na VPS;
2. criar a raiz canônica `/opt/sistemas/emporio` com ownership e modos seguros;
3. criar e instalar uma chave SSH Ed25519 exclusiva, sem senha e sem reuse;
4. provar autenticação não interativa, host key estrita e ausência de sudo;
5. provar acesso do usuário ao Docker Engine e Compose sem iniciar nada;
6. criar o environment GitHub `production` e configurar exclusivamente os dois
   vars e dois secrets de transporte;
7. remover todo material privado temporário local depois da configuração.

S41 não instala o helper remoto, não sobe o `release_control`, não cria App
deployer e não executa workflow de deploy.

## 2. Autorização humana obrigatória

As mutações somente estão autorizadas se a mensagem de delegação enviada
diretamente pelo usuário contiver literalmente:

```text
Autorizo integralmente a S41: usar o acesso bootstrap root já existente em root@31.97.251.16 para criar o usuário deploy-emporio, preparar /opt/sistemas/emporio, instalar uma chave SSH dedicada, conceder acesso ao Docker pelo grupo docker e configurar no environment GitHub production os vars e secrets PRODUCTION_SSH_*. Autorizo também a reversão exata do que a própria S41 criar caso ela falhe antes de concluir.
```

Sem essa frase, executar apenas o inventário read-only e parar antes da primeira
mutação.

A autorização não cobre:

- reinício, atualização ou swap do host;
- Nginx, Certbot, DNS, TLS ou firewall;
- pull, build, login, run, Compose up/down ou criação de volume/rede;
- instalação do helper/control root ou do `release_control`;
- App deployer, `DEPLOYER_ACTOR_IDS` ou ponte RS256/JWKS;
- banco, migration, backup, restore, deploy ou rollback;
- leitura de credenciais preexistentes.

## 3. Decisões fechadas

### 3.1 Host e path

```text
bootstrap SSH       root@31.97.251.16:22
deploy SSH          deploy-emporio@31.97.251.16:22
deploy root         /opt/sistemas/emporio
GitHub repository   greggorio/abaronesa-emporio
environment         production
```

Não usar `/opt/sistemas/baronesa/emporio`, alias, symlink ou path alternativo.

### 3.2 Privilégio do usuário

O workflow e o transporte proíbem `sudo`. Portanto:

- não criar arquivo sudoers;
- não adicionar `deploy-emporio` aos grupos `sudo` ou `adm`;
- adicionar somente ao grupo suplementar `docker`, necessário aos comandos
  Docker/Compose do adapter;
- bloquear autenticação por senha;
- permitir login somente pela chave dedicada;
- manter shell não interativo no protocolo, embora o usuário precise de um
  shell POSIX válido para o comando remoto fixo.

Pertencer a `docker` concede capacidade elevada sobre o daemon; isso deve ser
declarado no relatório, limitado à identidade dedicada e nunca ampliado para
outros grupos.

### 3.3 Filesystem inicial

Criar somente:

```text
/opt/sistemas/emporio/
/opt/sistemas/emporio/releases/
/opt/sistemas/emporio/shared/
/opt/sistemas/emporio/shared/control/
/opt/sistemas/emporio/shared/deploy/
/opt/sistemas/emporio/shared/deploy/incoming/
/opt/sistemas/emporio/shared/deploy/snapshots/
```

Todos pertencem a `deploy-emporio:deploy-emporio`, são diretórios reais, sem
symlink, e usam modo `0700`. Não criar `.env`, helper, bundle, release, link
`current`/`previous`, backup, journal ou installed state.

`/opt/sistemas/emporio-control` pertence a uma etapa posterior e não deve ser
criado nesta slice.

## 4. Snapshot inicial obrigatório

### 4.1 Git e GitHub

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
sha256sum docs/infrastructure/deployment/implementation/slices/S41-bootstrap-transporte-producao-identidade-ssh.task.md
gh auth status
gh api user --jq '{login,id}'
gh api repos/greggorio/abaronesa-emporio/environments
gh variable list --env production --json name,value,updatedAt
gh secret list --env production --json name,updatedAt
gh variable list --json name,value,updatedAt
gh run list --workflow deploy-production.yml --limit 100 --json databaseId
gh run list --workflow rollback-production.yml --limit 100 --json databaseId
git diff --check
git diff --cached --name-only
```

Environment inexistente deve ser classificado `MISSING`. Não criar nada ainda.

### 4.2 VPS somente leitura

Usar a rota bootstrap conhecida com:

```text
BatchMode=yes
StrictHostKeyChecking=yes
ConnectTimeout=10
ForwardAgent=no
ForwardX11=no
ControlMaster=no
ControlPath=none
NumberOfPasswordPrompts=0
```

Registrar sem abrir conteúdo sensível:

- hostname, uptime, load, memória, swap e disco;
- `id deploy-emporio`;
- existência/tipo/owner/mode dos paths alvo;
- existência de qualquer sudoers com `deploy-emporio`;
- grupo/socket Docker e versões de Engine/Compose;
- configuração efetiva de `PubkeyAuthentication`, `PasswordAuthentication` e
  `PermitRootLogin`, sem editar `sshd_config`;
- fingerprints públicas das host keys oferecidas;
- contagem e nomes dos containers, volumes e redes;
- estado Nginx e systemd, portas `8120`/`8180`;
- zero runs de deploy/rollback.

Baseline esperado: usuário e paths alvo ausentes, environment `production`
ausente e nenhuma unidade Empório. A S37 observou 39 containers; recontar e
registrar o estado corrente, sem tratar variação legítima dos outros sistemas
como falha automática. Se algum alvo da S41 já existir, não sobrescrever nem
adotar: inventariar e parar.

## 5. Gates locais antes da mutação

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest \
  tools.deploy.tests.test_deployment_transport \
  tools.deploy.tests.test_deploy_workflow_contract -v
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_production_adapter.py
python3 tools/deploy/validate_deployer_runtime.py
git diff --check
```

Todos os exits devem ser `0`. Confirmar no código versionado:

- usuário remoto literal `deploy-emporio`;
- raiz literal `/opt/sistemas/emporio`;
- `StrictHostKeyChecking yes`, `IdentitiesOnly yes`, `BatchMode yes`;
- senha, forwarding, `sudo`, root e shell arbitrário proibidos;
- nomes exatos dos quatro materiais `PRODUCTION_SSH_*`.

## 6. Plano exato antes de executar

Antes da primeira mutação, escrever no relatório:

- comandos remotos exatos, com valores não sensíveis;
- lista dos recursos que serão criados;
- snapshot dos owners/modes dos pais existentes;
- plano de verificação;
- plano de reversão em ordem inversa;
- diretório temporário local exato para a chave;
- fingerprints públicas esperadas, nunca chave privada.

Não transcrever token GitHub, private key, authorized key completa, Docker
config, `.env` ou conteúdo de secret.

## 7. Execução autorizada

### 7.1 Usuário e diretórios

Pela conexão bootstrap root:

1. criar grupo e usuário `deploy-emporio` com UID/GID alocados pelo host, home
   `/home/deploy-emporio`, password bloqueado e shell POSIX existente;
2. adicionar somente ao grupo suplementar `docker`;
3. provar que não pertence a `sudo`/`adm` e não possui regra sudoers;
4. criar a árvore exata da §3.3 com owner/grupo dedicado e modo `0700`;
5. validar cada componente com `lstat`/`stat`, recusando symlink, group-write ou
   other-write.

Não alterar owner/mode de `/opt`, `/opt/sistemas`, socket Docker ou recursos de
outros sistemas.

### 7.2 Chave e host keys

1. criar `mktemp -d` local em path dedicado, modo `0700`;
2. gerar uma chave nova Ed25519 sem passphrase e com comentário operacional;
3. private key `0600`, public key `0644` apenas no temporário;
4. preparar `/home/deploy-emporio/.ssh` `0700` e `authorized_keys` `0600`, ambos
   pertencentes ao usuário;
5. inserir exatamente a public key recém-gerada, sem `ssh-copy-id` genérico;
6. obter as host keys oferecidas na porta 22 e confrontar seus fingerprints
   com as chaves públicas do host lidas pela rota root e com o known_hosts já
   confiado; qualquer divergência interrompe antes de usar a nova chave;
7. materializar known_hosts dedicado somente com host/porta aprovados.

### 7.3 Prova local do transporte

Usando apenas o temporário dedicado:

- conectar como `deploy-emporio` com private key explícita, identities-only,
  batch mode, host key checking estrito e todos os forwardings desabilitados;
- executar somente probes fixos: identidade/grupos, path, Docker Engine e
  Compose version;
- exigir `id -un == deploy-emporio`;
- exigir acesso ao daemon Docker e Compose sem `sudo`;
- exigir que `sudo -n` não esteja autorizado;
- confirmar que nenhum container, volume, rede, imagem ou serviço mudou.

Não chamar o helper remoto, pois ele ainda não foi instalado.

### 7.4 Environment GitHub

Somente depois da prova SSH verde:

1. criar o environment `production`;
2. configurar vars:
   - `PRODUCTION_SSH_HOST=31.97.251.16`
   - `PRODUCTION_SSH_PORT=22`
3. configurar secrets por stdin, sem interpolá-los em argv/log:
   - `PRODUCTION_SSH_PRIVATE_KEY`
   - `PRODUCTION_SSH_KNOWN_HOSTS`
4. confirmar por API somente nomes e timestamps dos secrets;
5. ler e validar os valores não sensíveis dos vars;
6. confirmar que `DEPLOYER_ACTOR_IDS` continua ausente e nenhum App foi criado.

Não configurar secret/var em escopo de repositório quando o workflow exige o
environment.

### 7.5 Limpeza do material local

Depois dos secrets confirmados:

- destruir de forma dirigida private key, public key e known_hosts temporários;
- remover o diretório temporário exato;
- provar ausência;
- não deixar chave no repositório, `/tmp`, shell history, clipboard ou relatório.

## 8. Reversão autorizada em falha

Se qualquer etapa falhar antes da conclusão, e somente se o snapshot provar que
os recursos eram ausentes e foram criados pela S41, reverter em ordem:

1. remover os quatro materiais `PRODUCTION_SSH_*` e o environment `production`
   criado pela slice;
2. remover somente a chave adicionada e o home do usuário criado;
3. remover somente a árvore `/opt/sistemas/emporio` se continuar sem arquivos
   além dos diretórios vazios da §3.3;
4. remover o usuário e grupo criados;
5. destruir o temporário local;
6. provar retorno ao baseline.

Nunca remover environment, usuário, key, diretório ou grupo preexistente. Se
qualquer alvo adquirir conteúdo externo, parar e preservar para decisão humana.

Não executar reversão automática depois de uma conclusão integral verde.

## 9. Validação final

Provar:

- usuário dedicado, password bloqueado, grupos primário + `docker` apenas;
- zero sudo e zero regra sudoers;
- árvore exata, owner/grupo e modos `0700`;
- login pela chave nova com SSH estrito e nenhuma autenticação por senha;
- Docker Engine e Compose acessíveis como `deploy-emporio` sem mutação;
- environment e quatro materiais presentes nos escopos corretos;
- private key local ausente depois do envio seguro;
- `origin/main`, package, `v0.1.0`, imagem operacional e allowlists existentes
  inalterados;
- zero deploy/rollback e nenhum run novo;
- containers, volumes, redes, Nginx, systemd, portas, memória e swap iguais ao
  baseline salvo variação normal de métricas;
- nenhum serviço Empório iniciado;
- nenhuma alteração no repositório e stage vazio.

## 10. Proibições

- editar ou reiniciar SSH daemon;
- criar sudoers ou conceder sudo;
- usar senha, agent forwarding, `StrictHostKeyChecking=no` ou `accept-new`;
- copiar a chave privada para a VPS;
- abrir Docker config ou credenciais preexistentes;
- instalar pacote do SO, aplicar update, reboot ou swap;
- instalar helper, Compose, `.env`, systemd ou control plane;
- executar Docker pull/login/build/run/compose;
- tocar Nginx, TLS, DNS, firewall, bancos ou backups;
- executar workflow, deploy, rollback, commit ou push;
- apagar logs, runs, artifacts, packages ou imagens.

## 11. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S41-bootstrap-transporte-producao-identidade-ssh.report.md
```

O relatório permanece não rastreado, não staged e não commitado. Deve conter:

- autorização literal;
- snapshots Git/GitHub/VPS inicial e final;
- SHA-256 da task;
- comandos e exits, sanitizados;
- identidade, grupos, paths, owners e modos;
- fingerprints públicas, sem materiais SSH completos;
- prova SSH estrita e acesso read-only ao Docker/Compose;
- nomes/timestamps dos secrets e valores dos vars não sensíveis;
- inventário literal das mutações;
- cleanup do temporário e prova de ausência de segredo;
- reversão executada, se necessária;
- negativos e resíduos finais.

O executor não aceita S41, não cria a próxima slice, não instala helper e não
inicia serviços.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — transporte SSH dedicado preparado; aguardando aceite e instalação do control root
```

Na primeira causa não resolvida:

```text
BLOCKED — S41 interrompida fail-closed na primeira causa técnica
```

## 12. Critérios de aceite

S41 somente será aceita quando identidade, filesystem, chave, SSH estrito,
Docker/Compose e environment GitHub estiverem comprovados ponta a ponta, sem
segredo residual, sudo, serviço iniciado, workflow executado ou impacto nos
demais sistemas da VPS.
