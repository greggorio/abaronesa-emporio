# S37 — Inventário read-only da VPS e plano de preparação de produção

> **Estado:** `PLANNED`
> **Tipo:** inspeção operacional somente leitura e fechamento do plano de mutação
> **Executor previsto:** CLI
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`
> **Relatório:** `S37-inventario-read-only-vps-plano-preparacao-producao.report.md`
> **Dependência:** S30b `ACCEPTED`

## 1. Resultado observável

Confrontar, sem alterar, a VPS real destinada ao Empório e produzir o plano
exato de preparação que precederá o primeiro deploy. A execução deve responder
com evidência atual:

1. como o host está acessível e qual identidade operacional existe hoje;
2. quais recursos, serviços, portas, domínios e persistências já ocupam a VPS;
3. se `/opt/sistemas/emporio` e `/opt/sistemas/emporio-control` existem e qual
   seu estado, sem abrir arquivos sensíveis;
4. se Docker, Compose, Nginx, Certbot, systemd, firewall, backup e GHCR estão
   prontos ou precisam de preparação;
5. se a identidade GitHub App deployer, allowlist e configuração do workflow
   existem, sempre verificando apenas presença, owner, modos e nomes;
6. quais bloqueios reais precisam ser corrigidos antes do primeiro deploy;
7. qual sequência de mutações será proposta para uma autorização posterior,
   incluindo parada, rollback e preservação dos sistemas preexistentes.

Esta slice não prepara a VPS, não instala o control plane e não executa deploy.
Ela termina com inventário `as-is`, matriz `as-is -> required`, plano fechado e
lista objetiva dos únicos inputs humanos que realmente não puderem ser
descobertos.

## 2. Base terminal e integridade

O estado aceito pelo orquestrador em 03/08/2026 é:

```text
remote main              67abde48fd4a74de5bcff22bf592bd9005094210
release                  v0.1.0 / id 364130074
release target/tag       38385c100ab8b0ae07099b6a5a7b016b7c2b7322
publish-release run      30804834574 / success / 4 jobs verdes
release candidate run    30803878927 / success
latest candidate run     30806848165 / success / 67abde4
deploy runs              0
rollback runs            0
target VPS               31.97.251.16
website domain           emporio.abaronesa.net.br
ERP domain               erp-emporio.abaronesa.net.br
```

O relatório terminal aceito da S30b possui:

```text
sha256 f472510489409a5778acf42e46eba9cf63a5ca473a4d2c7ac8a13713c6dbe503
```

O prompt fornecerá o HEAD do commit documental que contém este contrato e o
SHA-256 desta task. Antes de qualquer acesso externo, exigir:

- HEAD exatamente no SHA do prompt;
- `origin/main` e remoto ainda em `67abde4...`;
- exatamente sete commits documentais lineares em `origin/main..HEAD`;
- stage e worktree vazios;
- task e relatório S30b com os hashes do prompt;
- `v0.1.0` ainda única tag/release global;
- nenhum run de deploy ou rollback, ativo ou concluído;
- nenhum run concorrente `queued` ou `in_progress` nos cinco workflows.

Divergência deve ser registrada antes do SSH. Não executar pull, merge, rebase,
reset, amend, push ou qualquer reescrita para normalizá-la.

## 3. Leitura obrigatória

Ler antes do inventário:

1. `docs/infrastructure/deployment/implementation/HANDOFF_ORQUESTRADOR_FECHAMENTO.md`;
2. `docs/infrastructure/deployment/implementation/README.md`;
3. `docs/infrastructure/deployment/proposta-docker-ci-cd-producao-emporio.md`;
4. o relatório terminal S30b;
5. tasks e relatórios S18–S29;
6. `docs/infrastructure/deployment/release-control/PLANO_IMPLANTACAO.md`;
7. `OPERACAO_LOCAL_IMPLANTACAO.md`, `TRANSACAO_IMPLANTACAO.md`,
   `WORKFLOW_IMPLANTACAO.md`, `RUNTIME_DEPLOYER.md`, `IDENTIDADE_DEPLOYER.md`,
   `OPERACAO_RELEASE_CONTROL.md` e os documentos de rollback;
8. `.github/workflows/deploy-production.yml` e `rollback-production.yml`;
9. `ops/compose/compose.prod.yml`, `ops/compose/release-control.yml`, exemplos
   de ambiente, systemd e `ops/deploy/`.

Documentos históricos orientam o confronto, mas não prevalecem sobre o host,
Git, GitHub, release, código e payloads atuais.

## 4. Autoridade read-only

É permitido:

- consultas locais a Git/GitHub, DNS, HTTPS e TLS;
- descobrir a rota SSH já configurada sem abrir chave privada;
- conectar à VPS por identidade preexistente e executar apenas comandos de
  inventário;
- usar `sudo -n` somente para consultas read-only se a identidade já possuir
  essa autorização, sem solicitar ou enviar senha;
- consultar manifests GHCR pelos seis digests de `v0.1.0` usando credencial
  já instalada e somente leitura, sem login ou pull;
- criar somente o relatório local desta slice.

Login SSH gera telemetria normal de autenticação do host; isso não autoriza
alteração de configuração ou conteúdo.

É proibido:

- aceitar host key nova com `StrictHostKeyChecking=no` ou `accept-new`;
- adivinhar usuário, senha, porta ou path de chave;
- abrir, copiar ou transcrever chave privada, token, senha, header, `.env`,
  sessão WhatsApp, cookie, PFX ou conteúdo de secret;
- `docker login`, pull, build, run, compose up/down, restart, stop, prune ou rm;
- criar usuário, grupo, diretório, arquivo, symlink, volume, rede ou container;
- chmod, chown, setfacl, firewall, Nginx, Certbot, systemd, package manager,
  migration, banco, backup ou restore;
- editar DNS, GitHub variables/secrets/environments, Apps ou installations;
- iniciar release-control, deploy, rollback, workflow ou dispatch;
- acessar `ops/env/.env.production`;
- commit, tag, push ou alteração de task, tracker, handoff ou outro relatório.

Não usar `docker inspect`, `docker compose config` remoto, `systemctl cat`,
`cat` de arquivos operacionais ou qualquer comando que possa expandir ambiente
ou revelar segredo. Consultar metadados e estados, não conteúdos.

## 5. Rota SSH e confiança do host

Descobrir a rota sem abrir credenciais:

- procurar o IP literal somente em arquivos de configuração SSH;
- usar `ssh -G` para obter host, porta, usuário, identity file e known-hosts;
- mostrar apenas paths e fingerprints públicos, nunca a chave privada;
- confirmar o host key contra entrada preexistente em known_hosts;
- usar `BatchMode=yes`, timeout curto, sem agent forwarding, port forwarding,
  X11 ou master persistente.

Se não houver rota configurada ou fingerprint confiável, não conectar. Completar
o inventário público/local, classificar `SSH_ROUTE_MISSING` ou
`HOST_KEY_UNVERIFIED` e declarar o único dado humano necessário. `ssh-keyscan`
isolado não estabelece confiança e não autoriza conexão.

## 6. Inventário obrigatório da VPS

Com conexão confiável, registrar comandos, exits e resultados sanitizados para:

### 6.1 Host e capacidade

- hostname, OS, kernel, arquitetura, timezone, uptime e relógio;
- CPU, memória, swap, disco e inodes;
- mounts relevantes e espaço disponível;
- usuário efetivo, grupos e permissões sudo read-only;
- processos e load, sem transcrever argv que contenha segredo.

### 6.2 Rede e coexistência

- listeners TCP/UDP, interfaces e rotas públicas relevantes;
- estado read-only de firewall;
- serviços e containers que ocupam portas 80, 443, PostgreSQL e portas do
  Empório/control plane;
- projetos preexistentes em `/opt/sistemas`, somente nome, path, owner, grupo e
  modo;
- nenhuma conclusão de porta livre baseada apenas em documentação.

### 6.3 Docker e persistência

- versões Docker Engine e Compose;
- rootless/rootful, data root, storage driver e logging driver;
- `docker ps`, imagens, volumes, redes e `docker system df -v`;
- existência nominal de volumes PostgreSQL, uploads e sessão WhatsApp;
- existência e saúde de PostgreSQL preexistente sem executar SQL nem abrir env;
- ausência de tags `latest` no plano proposto.

### 6.4 Nginx, TLS e domínios

- presença, versão e estado de Nginx/Certbot;
- server names, listeners, proxy targets e paths de certificados somente para
  os dois domínios, com saída filtrada;
- validade, SAN, issuer e expiração dos certificados públicos;
- DNS A/AAAA atual, HTTPS, redirect e headers públicos;
- conflito com configuração de outro sistema.

Não imprimir configuração Nginx completa, chaves TLS, environment headers ou
credenciais de proxy.

### 6.5 Paths, backup e operação

Confrontar por metadados:

```text
/opt/sistemas/emporio
/opt/sistemas/emporio-control
/etc/emporio
/etc/emporio-release-control
/var/lib/emporio
/var/backups/emporio
```

Inventariar também unidades systemd relacionadas, timers, health, paths de
backup, retenção aparente, destino externo documentado e `known_hosts`. Não
abrir dumps, journals com secrets, arquivos de ambiente ou dados de aplicação.

### 6.6 Release control e identidade deployer

Verificar apenas presença e metadados de:

- usuário/grupo operacional dedicado, esperado `deploy-emporio`;
- pacote, Compose e unidade do `release_control` isolado;
- PostgreSQL e volume exclusivos do control plane;
- bind exclusivamente loopback e health live/ready, se já estiver ativo;
- chave da GitHub App deployer separada da publisher;
- chave RS256/JWKS do emissor ERP deployer;
- env files regulares, não symlink, owner correto e modo esperado;
- GitHub App/installation/repository/permissions por endpoints autenticados
  somente se a credencial já existir, sem imprimir JWT/token;
- variável `DEPLOYER_ACTOR_IDS` e nomes de variables/secrets do environment
  `production`, sem ler valores.

A ausência desses recursos é resultado esperado de inventário, não autorização
para criá-los.

### 6.7 GHCR somente leitura

Se houver credencial Docker já instalada e protegida, sem abri-la, testar
somente `manifest inspect` dos seis `imageRepository@digest` de `v0.1.0`.
Registrar sucesso/falha e igualdade do digest. Não executar login, pull, tag,
push, logout ou alteração de credential store.

## 7. Confronto local do caminho de produção

Sem corrigir código, executar e registrar:

```bash
python3 tools/deploy/validate_deploy_workflow.py
python3 tools/deploy/validate_deployer_runtime.py
python3 tools/deploy/validate_rollback_contract.py
python3 tools/deploy/validate_rollback_runtime.py
python3 tools/deploy/validate_release_control_package.py
python3 tools/releases/global_release.py validate --manifest ops/releases/examples/global-release.example.json
git diff --check
```

Revalidar as duas falhas preexistentes do deployer somente por evidência já
existente e inspeção causal. Classificar separadamente:

1. rate limiter retornando 429 antes do conflito idempotente esperado 409;
2. fixture/estado que viola `ck_rc_deployment_workflow_binding`.

Decidir no relatório se cada uma afeta o primeiro deploy forward, apenas o
rollback ou somente o teste. Não editar código e não esconder a suíte vermelha.

## 8. Plano de preparação a entregar

Produzir tabela `AS_IS`, `REQUIRED`, `ACTION`, `RISK`, `ROLLBACK`, `AUTHORITY`
para, no mínimo:

- usuário/grupo `deploy-emporio` e sudo mínimo;
- diretórios comerciais e do control plane, ownership e modos;
- Docker/Compose, redes e portas;
- persistências PostgreSQL, uploads e WhatsApp;
- Nginx, DNS, TLS e dois domínios;
- backups, retenção e restore verificável;
- imagem e systemd do release-control isolado;
- PostgreSQL do control plane;
- GitHub App deployer, installation e `DEPLOYER_ACTOR_IDS`;
- secrets/variables do environment `production`;
- credencial GHCR somente leitura;
- ponte RS256/JWKS da instância ERP de produção;
- release `v0.1.0`, seis digests e primeiro plano de deploy;
- health, smoke, parada e rollback.

Separar o plano futuro em gates explícitos:

```text
Gate A — correções locais realmente bloqueantes
Gate B — preparação mínima e reversível da VPS/control plane
Gate C — validação de prontidão sem deploy comercial
Gate D — primeiro deploy acompanhado de v0.1.0
Gate E — rollback/restore apenas se necessário ou em ambiente controlado
```

Cada comando futuro que mutaria a VPS deve aparecer no plano com alvo exato,
efeito, verificação e reversão, mas não deve ser executado nesta slice. Não
incluir valores secretos nos comandos; usar nomes de arquivo/secret.

## 9. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S37-inventario-read-only-vps-plano-preparacao-producao.report.md
```

O relatório deve conter:

- CWD, identidade da task, hashes e snapshot inicial/final;
- comandos completos, exit codes, resultados e interpretação;
- rota SSH e prova de host key, ou causa segura da não conexão;
- inventário sanitizado das seções 6 e 7;
- matriz `AS_IS -> REQUIRED`;
- conflitos, ausências e riscos reais;
- classificação das duas falhas do deployer;
- plano Gate A–E com rollback e autoridade;
- lista curta de inputs humanos não descobríveis;
- prova de que nenhuma mutação proibida ocorreu;
- arquivos criados/alterados e resíduos locais.

O relatório permanece local, não staged e não commitado. O executor não aceita
S37, não cria S38 e não pede autorização para executar o plano futuro.

Terminar exatamente com um dos estados:

```text
IN_PROGRESS — inventário read-only concluído; aguardando revisão e autorização do plano de preparação
```

ou, se a confiança SSH impedir a parte remota:

```text
BLOCKED — inventário remoto não iniciado; rota ou host key confiável ausente
```

## 10. Critério de aceite

O orquestrador aceitará S37 somente quando o estado real da VPS estiver
confrontado sem vazamento ou mutação, os bloqueios estiverem causalmente
classificados e o próximo gate material possuir comandos, reversão e fronteira
de autorização exatos. Aceitar S37 não autoriza automaticamente Gate A, B, C,
D ou E.
