# S44 — Implantação do control plane deployer na VPS

> **Data:** 04/08/2026
> **Predecessora:** S43 aceita
> **Tipo:** correção operacional, identidade GitHub App e implantação isolada
> **Stack comercial/deploy/rollback:** proibidos

## 1. Objetivo

Implantar na VPS o `release_control` em modo `deployer`, separado da stack
comercial, usando sua imagem operacional por digest, PostgreSQL exclusivo,
GitHub App própria e serviço systemd. Ao final:

1. o pacote operacional deve refletir a topologia real do host standalone;
2. uma GitHub App deployer privada e de privilégio mínimo deve existir,
   instalada somente em `greggorio/abaronesa-emporio`;
3. `DEPLOYER_ACTOR_IDS` deve conter somente o actor ID do bot dessa App;
4. o control plane deve residir em `/opt/sistemas/emporio-control`;
5. application e PostgreSQL devem usar referências imutáveis por digest;
6. o serviço deve responder apenas em `127.0.0.1:8180`;
7. migrations, sync de releases/deployments, readiness e restart devem fechar;
8. nenhuma intenção, operação, dispatch, execução de deploy ou rollback pode
   ser criada.

A S44 entrega o plano de controle funcionando em loopback. Nginx/TLS, ponte
RS256/JWKS do ERP, backup/restore, capacidade/swap e Gate C pertencem à S45.

## 2. Autorização humana

A delegação deve conter literalmente:

```text
Autorizo integralmente a S44: corrigir e publicar o pacote operacional do release control para o host standalone, provisionar uma GitHub App deployer privada e exclusiva do repositório, configurar DEPLOYER_ACTOR_IDS, criar a identidade e os arquivos protegidos do serviço na VPS, puxar somente as imagens aprovadas por digest e iniciar o control plane deployer com PostgreSQL isolado em 127.0.0.1:8180. Autorizo também correções causais e novas publicações imutáveis dentro do mesmo ciclo limitado da S44, além da reversão dirigida dos recursos criados pela própria slice. Não autorizo deploy, rollback, stack comercial, Nginx, TLS, backup, restore, swap, atualização ou reboot do host.
```

Sem essa frase, executar somente snapshots e testes locais. Com ela, não pedir
ao usuário IDs, nomes, paths, permissões ou valores que o executor possa gerar
ou descobrir. A única interação humana admissível é login/2FA e confirmação na
UI oficial do GitHub quando o fluxo de criação/instalação da App exigir.

## 3. Decisões operacionais fechadas

### 3.1 Raiz e identidade do serviço

```text
control plane root    /opt/sistemas/emporio-control
configuration root   /etc/emporio
environment file     /etc/emporio/release-control.env
GitHub App PEM        /etc/emporio/release-control-deployer-app.pem
systemd unit          /etc/systemd/system/emporio-release-control.service
service account       emporio-release-control
loopback              127.0.0.1:8180
```

O usuário é de sistema, senha bloqueada, shell nologin, sem home e sem sudo. Os
grupos suplementares são somente `docker`. Registrar explicitamente que acesso
ao grupo Docker concede capacidade elevada sobre o daemon; não ampliar para
`sudo`, `adm` ou outros grupos.

### 3.2 Imagens

Imagem operacional atualmente publicada:

```text
ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:64b6f2be31b8532b870656d401656df2184599921c73ad667c65c36d65022380
```

Ela deverá ser substituída pela imagem do commit de correção da seção 4. A
identidade efetiva será exclusivamente o novo `repository@sha256:<digest>`
validado pelo artifact do workflow `Publish Release Control Image`.

PostgreSQL fixado para Linux amd64:

```text
postgres:16.6-alpine@sha256:589f3b24f30e60a2b33f79543ed51c8f897589bcde5c59f4dc0e814551eeeb0f
```

Revalidar o descriptor no registry antes da primeira mutação. Se o digest não
resolver mais para `linux/amd64`, parar; não substituir por tag móvel ou versão
escolhida por conveniência.

### 3.3 Banco interno

O PostgreSQL oficial do Compose não está configurado para TLS. A decisão para
o MVP é:

- `RELEASE_CONTROL_DB_SSLMODE=disable` somente quando
  `RELEASE_CONTROL_PROFILE=runtime` e
  `RELEASE_CONTROL_DB_HOST=release_control_postgresql`;
- o banco permanece exclusivamente na rede bridge
  `emporio_release_control_internal`, sem porta publicada;
- qualquer outro hostname runtime continua exigindo `sslmode=require`;
- profiles development/test mantêm seus contratos atuais.

Esse caso fechado deve existir no validador e em testes. Não aceitar
`sslmode=disable` para IP, loopback, hostname arbitrário ou banco comercial.

### 3.4 Secret da GitHub App em Docker standalone

O host tem Docker standalone, Swarm inativo. Portanto:

- é proibido executar `docker swarm init`;
- remover o requisito de secret externo da Compose;
- usar secret Compose baseado no arquivo protegido indicado por variável de
  host, montado somente no target `/run/secrets/github-app-private-key`;
- provar como usuário `10001:10001` que a chave é legível, sem imprimir bytes;
- o arquivo host continua inacessível a outros usuários;
- nenhum PEM entra na imagem, repositório, env file ou log.

### 3.5 Rede e identidade futura

```text
RELEASE_CONTROL_PROFILE=runtime
RELEASE_CONTROL_MODE=deployer
RELEASE_CONTROL_JWT_ISSUER=https://erp-emporio.abaronesa.net.br/api/release-control/identity/deployer
RELEASE_CONTROL_JWT_JWKS_URL=https://erp-emporio.abaronesa.net.br/api/release-control/identity/deployer/jwks
RELEASE_CONTROL_JWT_AUDIENCE=emporio-release-control-deployer
RELEASE_CONTROL_CORS_ORIGINS=https://erp-emporio.abaronesa.net.br
```

A S44 não exige que JWKS já responda; o endpoint será habilitado e confrontado
na S45. Não substituir HTTPS por loopback ou profile development para fazer o
container subir.

## 4. Correção obrigatória do pacote operacional

Antes de qualquer criação de App ou mutação da VPS, corrigir de forma causal:

1. `config.py`: política de `sslmode` da seção 3.3;
2. `ops/compose/release-control.yml`:
   - secret file-backed para standalone;
   - `pull_policy: never` nos dois serviços;
   - nenhum default de imagem flutuante ou placeholder utilizável;
   - app não-root, read-only, sem capabilities e somente loopback preservados;
3. `ops/env/release-control.env.example`:
   - path host do PEM explicitamente declarado;
   - DB interno com `sslmode=disable`;
   - imagens exemplificadas apenas por digest;
4. `ops/systemd/emporio-release-control.service.example`:
   - user/group dedicados;
   - `WorkingDirectory=/opt/sistemas/emporio-control`;
   - paths canônicos da seção 3.1;
   - `up --detach --no-build --pull never --wait` e stop seguro;
5. documentação, validador e testes causais correspondentes.

Adicionar testes que rejeitem ao menos:

- runtime `sslmode=disable` com host diferente do serviço interno literal;
- `require` removido para bancos externos;
- secret `external: true` ou Swarm;
- PEM literal, world-readable ou dentro do repositório;
- image sem digest, `latest`, tag móvel ou build local;
- ausência de `pull_policy: never`;
- path legado `/opt/emporio-release-control`;
- porta pública, socket Docker, privilégio ou Compose comercial;
- systemd como root ou usuário de deploy comercial.

Executar os testes direcionados e depois a matriz completa vigente: oito
suítes canônicas, todos os validadores registrados, secret scan
`unsupported=0`, `catalog:valid` e `git diff --check`.

Somente com tudo verde:

1. stagear exclusivamente o patch causal; relatórios ficam fora;
2. criar um commit técnico, sem amend;
3. reconfirmar remoto e fazer push normal fast-forward;
4. observar CI e Publish Candidate do mesmo SHA até terminais verdes;
5. disparar uma vez `Publish Release Control Image`, sem inputs, em `main`;
6. validar seus quatro jobs, artifacts, manifesto, sidecar, package version e
   novo digest;
7. confirmar que nenhuma tag comercial, release, deploy ou rollback mudou.

## 5. Snapshot inicial

### 5.1 Git e GitHub

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count origin/main...HEAD
git ls-remote origin refs/heads/main
git log --oneline --decorate -15
sha256sum docs/infrastructure/deployment/implementation/slices/S44-implantacao-control-plane-deployer-vps.task.md
git diff --check
git diff --cached --name-only
gh run list --workflow ci.yml --limit 5
gh run list --workflow publish-candidate.yml --limit 5
gh run list --workflow publish-release-control.yml --limit 10
gh run list --workflow deploy-production.yml --limit 100 --json databaseId
gh run list --workflow rollback-production.yml --limit 100 --json databaseId
gh api repos/greggorio/abaronesa-emporio/actions/variables
gh api repos/greggorio/abaronesa-emporio/environments/production
```

Esperado:

- `origin/main` local e remoto em
  `9731954d474fb68ec1384a525e1075f9a5542e24`;
- somente o commit documental da S43/S44 pode estar à frente, sem behind;
- stage vazio e relatórios S39–S43 não rastreados;
- `RELEASE_CONTROL_IMAGE_PUBLISHER_ACTOR_IDS` e
  `RELEASE_PUBLISHER_ACTOR_IDS` presentes e preservados;
- `DEPLOYER_ACTOR_IDS` ausente;
- environment `production` presente e intocado;
- um único run/package version da imagem operacional anterior;
- zero deploy/rollback;
- `v0.1.0` inalterada.

Não imprimir valores de secrets nem abrir materiais publisher existentes.

### 5.2 VPS read-only

Pela rota bootstrap root estrita já confiada, confirmar:

- S43 íntegra: verify e capabilities do control root em `9731954d…`;
- `/opt/sistemas/emporio-control`, `/etc/emporio`, usuário/unidade do control
  plane, containers, volume e rede alvo ausentes;
- Swarm `inactive`;
- porta 8180 livre;
- baseline Docker completo e capacidade atual;
- nenhuma imagem/volume/rede/container alvo;
- nenhuma credencial existente é aberta ou transcrita.

Conteúdo inesperado em qualquer alvo interrompe antes de mutar.

## 6. GitHub App deployer

### 6.1 Identidade fechada

```text
name             Emporio Deployer 1315264421
owner            greggorio / 35626201 / User
homepage         https://github.com/greggorio/abaronesa-emporio
description      Deployer de producao do controle de releases do Emporio
visibility       private / only on this account
webhook          inactive
events           none
OAuth on install false
actions          write
contents         read
metadata         read (implícita)
repository       somente greggorio/abaronesa-emporio, id 1315264421
```

Não prever slug: ele é saída do GitHub. Abrir a página administrativa e
reconciliar primeiro. Reutilizar exatamente uma App compatível eventualmente
existente; não criar duplicata. Se nenhuma existir, executar um único GitHub
App Manifest flow oficial ou criação equivalente pela UI oficial.

Preservar o PEM sem imprimir, em arquivos regulares modo `0600`:

```text
/home/gregorio/.config/emporio/release-control/deployer-github-app.pem
/home/gregorio/.config/emporio/release-control/deployer-github-app.env
```

O segundo contém somente metadados operacionais não secretos e o path do PEM.
Não abrir, alterar ou reutilizar os arquivos publisher irmãos.

Instalar a App somente no repositório canônico. Com JWT e installation token
mantidos em memória, exigir:

- `GET /app` consistente com App ID, slug real, owner e permissões;
- exatamente uma instalação na conta, `repository_selection=selected`;
- `/installation/repositories` com exatamente o repo ID `1315264421`;
- token com `actions:write`, `contents:read`, `metadata:read`;
- leitura de release, runs e artifacts; nenhuma escrita de teste;
- exatamente uma chave pública correspondente ao PEM preservado.

Derivar `<slug>[bot]`, exigir `type=Bot` e ID decimal positivo. Só então criar
uma vez `DEPLOYER_ACTOR_IDS=<BOT_ID>`. O PAT do `gh` pode administrar a variável,
mas nunca substituir a identidade da App no runtime.

## 7. Preparação da VPS

### 7.1 Usuário e filesystem

Criar, se todos os alvos estiverem ausentes:

```text
emporio-release-control                  user/group de sistema, senha bloqueada
grupos suplementares                     docker somente
/opt/sistemas/emporio-control            0700 service:service
/opt/sistemas/emporio-control/ops         0700 service:service
/opt/sistemas/emporio-control/ops/compose 0700 service:service
/etc/emporio                              0750 root:service
release-control.env                       0640 root:service
release-control-deployer-app.pem          0640 root:service
unit systemd                              0644 root:root
```

Transferir Compose e unit exclusivamente dos blobs do commit remoto que
publicou a imagem final. Comparar hashes local/remoto. Não instalar checkout,
`.git`, source Python, relatório ou chave no root operacional.

### 7.2 Configuração real protegida

Gerar na VPS, sem stdout/argv/histórico:

- DB user fixo `release_control_runtime`;
- DB password aleatório forte;
- HMAC pepper aleatório com pelo menos 32 bytes;
- env com as identidades das seções 3 e 6;
- referências imutáveis finais da aplicação e PostgreSQL;
- path host do PEM e `RELEASE_CONTROL_DB_SSLMODE=disable`.

Não copiar valores do publisher. O PEM deployer deve chegar por stdin/arquivo
temporário protegido e ter fingerprint igual ao preservado localmente, sem
exibir conteúdo.

### 7.3 Prova declarativa antes do start

Como o usuário do serviço:

- `docker compose config --quiet` com env e Compose reais;
- config renderizado sem placeholder, tag móvel, build, porta pública,
  `external: true`, Swarm, socket ou Compose comercial;
- os dois images por digest e `pull_policy: never`;
- secret montado no target fixo;
- exatamente dois serviços, uma rede e um volume alvo.

Não imprimir o config renderizado porque contém senha/pepper.

## 8. Pull, start e validação

### 8.1 Pull controlado

Como root, usando a credencial GHCR preexistente sem abri-la:

1. `docker manifest inspect` dos dois digests;
2. `docker pull` somente dos dois immutable refs;
3. provar RepoDigest/config/arquitetura esperados;
4. nenhum login, build, tag auxiliar ou push.

### 8.2 Start único

Instalar a unit exata, executar `systemctl daemon-reload`, `enable` e `start`.
O systemd chama Compose com `--no-build --pull never --wait`.

Exigir:

- exatamente dois containers alvo, ambos healthy;
- PostgreSQL sem porta publicada;
- app somente `127.0.0.1:8180`;
- aplicação `10001:10001`, read-only, capabilities vazias, no-new-privileges;
- secret legível pelo processo não-root e ausente de logs/inspect sanitizado;
- migrations Alembic até `0003_commercial_rollback`;
- `/health/live` e `/health/ready` em 200 com body fechado;
- sync `releases` e `deployments` com `last_success_at`, `drift=false`;
- snapshot de `v0.1.0` presente e íntegro;
- nenhuma current installation e nenhuma operação/audit de dispatch;
- logs sem PEM, token, senha, pepper, JWT, traceback ou valor de env.

Consultas SQL devem projetar apenas contagens, estados e identificadores
operacionais não secretos.

### 8.3 Restart controlado

Registrar baseline de containers, volume, rede, release snapshot, sync e zero
operações. Executar um único `systemctl restart`, aguardar ready e provar:

- mesmos dois containers lógicos, volume e rede;
- migrations não reaplicadas de forma destrutiva;
- `v0.1.0` e sync preservados;
- zero dispatch, operação, deploy e rollback;
- contagens de recursos de outros sistemas inalteradas fora dos dois recursos
  intencionais da S44.

## 9. Loop causal limitado, sem micro-slices

A execução real pode corrigir defeitos dentro desta mesma S44. Limite total:

- commit inicial obrigatório da seção 4;
- até dois commits corretivos adicionais, somente após uma causa real distinta;
- um push fast-forward e uma publicação imutável por commit;
- no máximo três tentativas de start, cada uma precedida por cleanup/reversão
  da tentativa falha e gates completos.

Cada correção exige teste causal, matriz completa, CI/candidato verdes e nova
imagem validada. É proibido amend, rebase, force, rerun do mesmo artifact,
reutilizar digest antigo após mudança ou relaxar health/security. Causa que
exija arquitetura diferente, outro host/repo, permissão adicional ou mutação
proibida encerra fail-closed.

## 10. Reversão dirigida

Antes do primeiro ready verde, se uma tentativa falhar:

1. parar/desabilitar somente a unit alvo;
2. executar Compose `down` somente no projeto alvo;
3. preservar o volume se migrations ou dados já existirem durante um ciclo de
   correção; removê-lo somente numa reversão terminal e se o baseline provou
   que foi criado pela S44;
4. remover rede/container alvo e somente imagens ausentes no baseline quando
   isso for necessário à correção;
5. preservar App/instalação/PEM/allowlist válidos para retomada; nunca criar
   segunda App;
6. remover arquivos temporários exatos.

Se for necessária reversão terminal integral da VPS, remover unit, env, PEM,
root operacional e usuário/grupo somente após provar que cada alvo foi criado
pela S44 e não contém recurso adicional. Não tocar no control root da S43,
usuário `deploy-emporio`, environment `production`, chave SSH ou outros
sistemas. Exclusão da App/instalação ou de sua variável não é automática:
preservar identidade válida e reportar o estado para retomada.

Depois de ready e restart verdes, não reverter: esses são os resíduos
intencionais aceitos da S44.

## 11. Proibições

- executar `deploy-production.yml` ou `rollback-production.yml`;
- chamar POST de deployment/rollback ou fabricar token de operador;
- iniciar qualquer serviço da stack comercial;
- habilitar ponte ERP, gerar sua chave RS256 ou configurar JWKS no backend;
- Nginx, Certbot, DNS, TLS, firewall, backup, restore, swap, update ou reboot;
- Docker Swarm, build na VPS, tag móvel, `latest` ou imagem por nome sem digest;
- compartilhar DB, volume, network, App, PEM ou pepper com publisher/comercial;
- ler `/root/.docker/config.json`, chave SSH ou segredo preexistente;
- expor secret em stdout, argv, relatório, inspect, env dump ou repositório;
- prune, remoção ampla, `rm -rf` de raiz, rebase, amend ou force push;
- alterar tag/release `v0.1.0`;
- aceitar S44 ou criar S45.

## 12. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S44-implantacao-control-plane-deployer-vps.report.md
```

Manter não rastreado, não staged e não commitado. Registrar:

- autorização, CWD, SHA-256 da task e snapshots;
- incompatibilidades corrigidas, testes, commits, pushes, CI/candidato;
- runs/artifacts/digests das publicações da imagem;
- App ID, nome, slug, owner, bot ID, permissões e instalação, sem secrets;
- recursos VPS antes/depois, paths, owners e modes;
- hashes dos arquivos operacionais e fingerprints não reversíveis do PEM;
- referências imutáveis dos dois images;
- Compose config sanitizado, migrations, health, sync e restart;
- operações/dispatch/deploy/rollback em zero;
- tentativas, causas e reversões, se houver;
- resíduos finais e prova de preservação dos outros sistemas.

O executor não aceita S44 e não cria S45.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — control plane deployer implantado e estável; aguardando aceite e Gate C de prontidão
```

Na primeira causa não resolvida:

```text
BLOCKED — S44 interrompida fail-closed na primeira causa técnica
```

## 13. Critérios de aceite

S44 somente será aceita com pacote operacional corrigido e versionado, imagem
nova publicada e validada por digest, GitHub App deployer exclusiva e
allowlist correta, serviço isolado em `127.0.0.1:8180`, migrations e sync
verdes, `v0.1.0` sincronizada, restart comprovado e nenhuma operação, dispatch,
execução de deploy/rollback ou mutação da stack comercial.
