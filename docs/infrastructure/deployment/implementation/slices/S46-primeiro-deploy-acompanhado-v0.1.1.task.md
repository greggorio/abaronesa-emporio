# S46 — Primeiro deploy acompanhado de v0.1.1

> **Data:** 05/08/2026
> **Predecessora:** S45 aceita
> **Tipo:** primeira instalação comercial em produção
> **Release alvo:** `v0.1.1`
> **Rollback workflow/restore:** proibidos sem nova decisão

## 1. Objetivo

Executar a primeira instalação comercial do Empório na VPS exclusivamente pelo
plano de controle, workflow autenticado, transporte SSH e CLI transacional já
aceitos:

```text
intenção única -> runtime deployer -> deploy-production.yml
-> transporte autenticado -> CLI transacional -> outcome reconciliado
```

Ao final:

1. `v0.1.1` deve ser a instalação corrente reconciliada;
2. o run de deploy deve ter `trust`, `prepare`, `deploy` e `outcome` verdes;
3. backup dos dois bancos vazios deve preceder migrations;
4. os sete serviços devem estar healthy usando somente os digests publicados;
5. migrations ERP e website devem coincidir com os inventários da release;
6. os dois domínios devem responder por HTTPS e seus smokes devem fechar;
7. JWKS/token reais e a UI de produção devem operar após o deploy;
8. deve existir uma única intenção, uma operação e um dispatch;
9. nenhum workflow de rollback ou restore deve ser executado;
10. todos os outros tenants do host devem permanecer disponíveis.

## 2. Autorização humana

A delegação deve conter literalmente:

```text
Autorizo integralmente a S46 a executar o primeiro deploy acompanhado da release v0.1.1 em produção, incluindo o bootstrap inaugural de uma única intenção com JWKS temporário derivado da chave RS256 real, JWT de cinco minutos, um POST ao runtime deployer, um dispatch de deploy-production.yml, transporte SSH, pulls por digest, backup dos bancos vazios, migrations, start dos sete serviços, verificação HTTPS/UI e desativação do bootstrap root após sucesso. Autorizo somente replay da mesma idempotency key e payload diante de resposta ou outcome ambíguos. Não autorizo segundo deploy, rollback workflow, restore, nova release, reboot, atualização do host ou intervenção manual que contorne journal, lock, state machine ou outcome.
```

Com essa frase, não pedir ao usuário senha, e-mail, token, chave, operation ID,
idempotency key, release, digest, path ou janela adicional. Esses dados já estão
fechados ou são geráveis. O envio desta delegação constitui a janela acompanhada
para uma única implantação.

## 3. Bootstrap inaugural — exceção explícita e limitada

A UI e o backend que emite tokens deployer pertencem à própria stack ainda não
instalada. Exigir que o primeiro POST nasça da UI é circular e impossível.

Somente para a primeira instalação fica autorizado:

```text
chave RS256 real -> JWKS público temporário -> JWT bootstrap curto
-> HTTPS same-origin -> runtime deployer -> fluxo canônico restante
```

Limites obrigatórios:

- o JWKS é derivado da chave real já preparada, nunca uma chave substituta;
- contém somente material público e a mesma forma que o backend serve;
- fica disponível apenas na rota JWKS exata do host ERP;
- o JWT usa RS256, `kid` real, issuer/audience reais, `jti` UUID e TTL 300;
- subject fixo `bootstrap:first-install`;
- scope exato `deployment:read deployment:execute deployment:rollback`;
- JWT e header permanecem em arquivos `0600`, nunca stdout ou argv;
- uma única idempotency key `deployer-ui-<UUID v4>` e payload `v0.1.1`;
- nenhum `gh workflow run` manual: o runtime e a App criam o dispatch;
- depois do backend healthy, confrontar seu JWKS com o temporário e remover
  imediatamente a rota/arquivo temporários;
- nenhum deploy futuro pode reutilizar esse bootstrap.

Após a remoção, provar o caminho permanente:

```text
UI produção -> login ERP -> token ERP -> ponte RS256/JWKS real
-> runtime deployer same-origin
```

A prova pós-deploy é somente leitura; não cria segunda intenção.

## 4. Checkpoint aceito

Revalidar antes de qualquer ação:

```text
HEAD/origin/main/remoto  cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
stage/diff tracked       vazios
reports S39-S45          não rastreados
S45 report SHA-256       cd045f4a4c6c798a6659639e2bf5dbc59cf936444c03280c98f8dadf7b25d90a
CI                       30960751303, success, 13/13
Publish Candidate        30961397124, success, 11/11, deployable=true
Control image            30961863663, success, 4/4
Publish Release          30962554318, success, 4/4
v0.1.1 release ID        365219520
v0.1.1 target            cf3385f1012b9661ddbc2e83d5241aaa8633f8fd
release.json SHA-256     6e6ac56089a935c817608a37ab06823e329649a78b7acd6d967c0dfccaecd31e
control image digest     sha256:9d188caf022bc1e02df9ad8b2739e4cc4152eb887fa60ed811dc144b4a37bc1f
deploy/rollback runs     0/0
```

VPS esperada:

```text
control plane            active/enabled, 2 containers healthy
live/ready               200/200 em 127.0.0.1:8180
v0.1.1                   PUBLISHED, 6 componentes, eligible=true
current/operations       0/0
porta 8120               livre
stack comercial          0 containers, volumes e redes
Docker                   39 running / 41 total; 27 volumes; 19 redes
swap                     8 GiB ativo, swappiness 10
Nginx/TLS                verde; namespaces ERP=401, website=404
```

Materiais protegidos esperados, sem abrir no relatório:

```text
/opt/sistemas/emporio/shared/.env
/opt/sistemas/emporio/shared/secrets/release-control-deployer-identity-private-key.pem
/home/gregorio/.config/emporio/production/operator-bootstrap.env
environment GitHub production
```

Revalidar App deployer, instalação, bot allowlist, SSH, host keys, control root,
release assets e seis refs do `.env` por hashes/fingerprints. Não imprimir
conteúdo protegido.

## 5. Gate de capacidade e preservação do host

Antes do bootstrap, capturar por pelo menos três amostras:

- RAM total/available, swap livre/usado e disco;
- load, CPUs e pressão de memória quando disponível;
- IDs, `StartedAt`, health e memória dos 39 containers ativos;
- Nginx, control plane e amostra de tenants externos;
- porta 8120 livre e somente 8180 em loopback.

Parar antes do POST se:

- swap dedicado não estiver ativo ou `swappiness != 10`;
- RAM available for menor que 2 GiB nas três amostras;
- disco livre for menor que 40 GiB;
- load de 5 minutos superar 4 de forma sustentada;
- algum tenant baseline ou control plane não estiver saudável;
- existir recurso comercial, operação ativa ou run de deploy/rollback;
- `v0.1.1` não for a única release elegível para primeira instalação.

Não resolver falha de capacidade com novo swap, limites relaxados, reboot,
kill de processo alheio ou parada de outro sistema.

## 6. Backup imediatamente anterior

O plano da primeira instalação deve indicar `backupRequired=true` porque ambos
os bancos receberão migrations. Não criar backup manual substituto.

Antes da intenção, exigir:

- diretório canônico de backups vazio e seguro;
- espaço suficiente para staging e dumps;
- PostgreSQL comercial ausente;
- nenhuma migration ou banco comercial existente;
- ensaio S45 preservado apenas no relatório, sem resíduo.

Durante o run, observar que o adapter:

1. inicia somente PostgreSQL;
2. cria os dois bancos vazios;
3. produz dumps não vazios em staging;
4. calcula hashes e manifesto;
5. publica atomicamente o diretório final;
6. só então inicia as migrations ERP e website.

Não abrir dumps nem registrar paths além da raiz canônica e operation ID.

## 7. Preparação do JWKS e da intenção

### 7.1 JWKS temporário

Derivar o JWKS da chave real em container isolado, read-only, `--network none`
e sem copiar a chave. Validar `kty=RSA`, `use=sig`, `alg=RS256`, `kid`, `n` e
`e`, sem campo privado.

Instalar arquivo público canônico e regra Nginx de match exato, ambos com nomes
prefixados para S46. Executar `nginx -t`, reload e nova validação. Preservar o
master PID e todos os sites alheios.

Provar que:

- JWKS público corresponde à fingerprint da chave real;
- website retorna 404 nessa rota;
- identity token continua indisponível antes do backend;
- demais namespaces e TLS não mudaram.

### 7.2 Token, plano e POST único

Gerar JWT em arquivo protegido na VPS. Usá-lo por configuração stdin/arquivo
do cliente HTTPS, nunca por argumento `-H` contendo o token.

Executar, nesta ordem:

1. capabilities autenticada;
2. releases autenticada, exigindo `v0.1.1 eligible=true`;
3. current, exigindo 404/ausente;
4. plano `v0.1.1`, exigindo primeira instalação, seis updates, dois bancos,
   backup e migrations;
5. capturar baseline de runs;
6. criar UUID/idempotency key e persistir em arquivo `0600`;
7. enviar exatamente um POST `{release: "v0.1.1"}`;
8. exigir 202, `Idempotency-Replayed=false` e operation ID `dep_*`;
9. identificar exatamente um run novo pela diferença contra o baseline.

Se a resposta se perder depois de o servidor poder tê-la aceitado, não criar
key nem operação nova. Usar somente replay da mesma key/payload e exigir
`Idempotency-Replayed=true` com o mesmo operation ID.

## 8. Acompanhamento do workflow e da operação

Monitorar sem rerun manual:

```text
trust -> prepare -> deploy -> outcome
```

Exigir event `workflow_dispatch`, branch `main`, SHA terminal, attempt 1,
display title ligado ao operation ID e actor da App deployer real.

Validar em cada job:

- trust: repositório/ref/SHA/actor/operação/release corretos;
- prepare: tag, três assets, BOM e handoff imutável válidos;
- deploy: environment production, SSH dedicado e controlSha correto;
- outcome: artifact canônico, binding de run/attempt/operação/release e estado
  confirmado.

Baixar nominalmente, em `mktemp -d`, os artifacts:

```text
deployment-trust
deployment-handoff
deployment-result
deployment-workflow-outcome
```

Validar allowlist, sidecars quando existentes, schemas, modes após
rematerialização, bindings e ausência de secret. Remover o temporário.

O resultado aceito é exclusivamente:

```text
transportStatus=CONFIRMED
deploymentState=SUCCEEDED
errorCode=null
databaseRestoreRequired=false
```

Se o resultado ficar `INDETERMINATE`, é permitido somente o mecanismo de replay
da mesma intenção/operação para reconciliar. Se ficar `FAILED`, `ROLLED_BACK`,
`UNCERTAIN`, exigir restore, ou houver current não reconciliado, parar e
preservar evidência. Não disparar rollback, nova release ou segunda operação.

## 9. Verificação comercial após sucesso

### 9.1 Recursos e integridade

Exigir:

- exatamente sete containers comerciais healthy;
- seis imagens com RepoDigests idênticos ao BOM de `v0.1.1`;
- PostgreSQL somente interno e gateway somente `127.0.0.1:8120`;
- quatro volumes e duas redes com nomes canônicos;
- limites de memória/CPU/PIDs materializados;
- non-root, healthchecks, mounts e persistências conforme Compose;
- nenhum build, tag móvel, socket, Swarm ou porta adicional;
- `current -> releases/v0.1.1`, `previous` ausente na primeira instalação;
- installed state e manifest ligados à release/source/BOM.

### 9.2 Banco, backup e migrations

Projetar somente estados e contagens não sensíveis. Exigir:

- backup final regular `0700`, manifesto e dumps `0600`;
- dois dumps não vazios com hashes válidos;
- backup criado antes das primeiras migrations;
- inventário Flyway ERP exatamente até a versão publicada;
- inventário Flyway website exatamente até a versão publicada;
- Flyway runtime desabilitado nos dois serviços normais;
- nenhum restore ou migration repetida pelo startup.

### 9.3 Health e HTTPS

Exigir os sete healthchecks e os quatro smokes contratuais pelo gateway, além
de:

- raízes ERP e website 200 por HTTPS;
- healthz dos dois hosts;
- APIs ERP/website esperadas sem resposta de tenant errado;
- websocket upgrade quando houver probe seguro;
- certificados/redirects preservados;
- eventos e amostra de tenants externos inalterados;
- memória, swap, load e OOM counters sem degradação material.

## 10. Substituição do bootstrap pela identidade real

Depois do backend healthy:

1. consultar seu JWKS real pela rota permanente;
2. comparar canonicamente `kty/use/alg/kid/n/e` com o JWKS temporário;
3. remover a regra e o arquivo JWKS temporários;
4. instalar novamente somente o Nginx versionado;
5. executar `nginx -t`, reload e provar ausência completa do bootstrap;
6. confirmar que o JWKS agora vem do backend e permanece idêntico;
7. confirmar que token sem sessão ERP recebe rejeição esperada.

Em navegador real usando Playwright/Chromium já contratado pelo frontend:

- abrir `https://erp-emporio.abaronesa.net.br`;
- autenticar com o arquivo local protegido de bootstrap, sem logar valores;
- confirmar usuário root/SYSTEM;
- navegar ao painel de produção;
- provar troca ERP -> token deployer real;
- validar claims RS256, audience, três scopes e TTL sem persistir token;
- provar capabilities, releases, current `v0.1.1` e operação concluída;
- confirmar que a ação de deploy não oferece `v0.1.1` novamente;
- não clicar deploy ou rollback.

Capturas, traces e vídeos não podem conter senha, token, header, cookies ou
storage. Se não houver navegador local utilizável, pode-se materializar somente
o Chromium da versão Playwright fixada, fora da VPS, com cleanup dirigido.

## 11. Desativação do bootstrap root

Após login real comprovado:

1. atualizar atomicamente o `.env` compartilhado para
   `ROOT_BOOTSTRAP_ENABLED=false`;
2. remover valores de nome/e-mail/password bootstrap do env real;
3. preservar o arquivo local `operator-bootstrap.env` em `0600` para acesso do
   usuário já persistido;
4. recriar somente o container backend pelo Compose canônico, sem build/pull,
   preservando os outros seis;
5. aguardar health e repetir login/JWKS/UI/current;
6. provar que o usuário persiste e nenhum novo usuário/grupo foi criado;
7. provar que não houve operação, dispatch ou migration nova.

Essa é a única mutação manual pós-deploy autorizada e existe apenas para retirar
o secret de bootstrap do ambiente do processo. Não editar banco diretamente.

## 12. Estado terminal e negativos

Exigir ao final:

```text
current installation       v0.1.1, reconciled=true
deployment operation       única, SUCCEEDED
deploy workflow             único, 4/4 success
rollback workflow           0 runs
databaseRestoreRequired     false
commercial containers       7/7 healthy
control plane               healthy, sync sem drift
JWKS                        backend real, bootstrap removido
UI                           login e leitura reais verdes
bootstrap env               disabled, password ausente do processo
v0.1.0/v0.1.1               releases imutáveis preservadas
```

Revalidar Git, GitHub, VPS, Nginx, outros tenants, temporários e secret scan.
Nenhum commit/push é previsto na execução nominal. Defeito causal descoberto
antes do POST pode ser corrigido com matriz completa e gates, dentro da S46.
Depois do POST aceito, não criar nova release/operação nem corrigir produção por
tentativa e erro.

## 13. Proibições

- segundo POST, segunda idempotency key ou segundo deploy;
- `gh workflow run deploy-production.yml` manual;
- rollback workflow, restore ou edição direta de journal/estado/banco;
- Docker/Compose manual durante o deploy transacional;
- alterar componente, digest, migration, ordem ou plano;
- manter JWKS bootstrap depois do backend healthy;
- expor JWT, senha, PEM, cookie, storage, env ou dump;
- pull/build/tag fora do adapter canônico;
- update, reboot, firewall, DNS, Nginx restart ou alteração de tenant;
- prune, limpeza ampla, force, amend, rebase ou exclusão de evidência;
- aceitar S46 ou criar S47.

## 14. Relatório obrigatório

Criar somente:

```text
docs/infrastructure/deployment/implementation/slices/S46-primeiro-deploy-acompanhado-v0.1.1.report.md
```

Manter não rastreado, fora do stage e sem material protegido. Registrar CWD,
hashes, autorização, baselines, bootstrap JWKS/JWT somente por fingerprints e
claims não secretas, plano, intenção, run/jobs/artifacts, operação, backup,
migrations, resources, health, HTTPS, identidade real, UI, desativação do
bootstrap, negativos e qualquer reversão automática.

O executor não aceita S46 e não cria S47.

Em sucesso, terminar exatamente com:

```text
IN_PROGRESS — primeiro deploy de v0.1.1 concluído e reconciliado; aguardando aceite e Gate E de recuperação
```

Na primeira causa fora da autoridade:

```text
BLOCKED — S46 interrompida fail-closed na primeira causa técnica
```

## 15. Critérios de aceite

A S46 somente será aceita com uma única operação/run confirmada `SUCCEEDED`,
`v0.1.1` corrente, backup e migrations comprovados, sete serviços healthy,
HTTPS/JWKS/UI reais, bootstrap removido, zero rollback/restore e preservação dos
outros sistemas do host.
