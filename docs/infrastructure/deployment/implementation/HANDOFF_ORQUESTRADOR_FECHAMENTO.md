# Handoff do orquestrador — fechamento da implementação

> **Projeto:** Empório A Baronesa
> **Workspace:** `/home/gregorio/git/baronesa/emporio`
> **Repositório:** `greggorio/abaronesa-emporio`
> **Branch:** `main`
> **Data do snapshot:** 02/08/2026
> **HEAD remoto verificado:** `68a3528b563ad0c19819da34ab106396ae679596`
> **Finalidade:** permitir que outro orquestrador conduza o trabalho restante
> até o encerramento técnico, operacional e documental.

> **Atualização pós-snapshot — 03/08/2026:** S30a e S30b foram aceitas; a
> release global `v0.1.0` foi publicada pelo run `30804834574`; o remoto está
> em `67abde48fd4a74de5bcff22bf592bd9005094210`; S37 é o contrato vigente para
> inventário read-only da VPS e plano de preparação, ainda sem autorizar
> mutação de produção.
>
> **Atualização S37/S38 — 03/08/2026:** o inventário read-only da VPS foi aceito.
> O Empório é greenfield no host multi-inquilino; `/opt/sistemas/emporio` segue
> como root canônico. S38 é o contrato vigente para fechar localmente as duas
> falhas do deployer e validar CI/candidato. Nenhum acesso ou ato de produção é
> autorizado pela S38; capacidade, reinício e preparação da VPS permanecem para
> autorização posterior e específica.
>
> **Atualização S38/S39 — 03/08/2026:** Gate A aceito no SHA remoto
> `bd1f51f96866665a3d5f0e43e15d27dab4e94e74`. S39 é o contrato vigente para
> implementar o workflow independente da imagem do `release_control`, publicá-lo
> por push fast-forward e validar CI/candidato. S39 não executa esse workflow,
> não publica imagem GHCR e não acessa a VPS.
>
> **Atualização S39/S40 — 03/08/2026:** S39 aceita no SHA remoto
> `daaa7061ab9f7a722b17e37c0f060f45141225e7`, com base Alpine em zero
> `HIGH/CRITICAL`, CI `30838546384` e candidato `30839217752` verdes. O workflow
> `Publish Release Control Image` está ativo e nunca executado. S40 é o contrato
> vigente para configurar sua allowlist exclusiva e realizar uma única
> publicação inaugural por digest no GHCR. S40 não acessa a VPS e não executa
> deploy ou rollback.
>
> **Atualização S40/S41 — 03/08/2026:** S40 aceita com o run `30855327740` e a
> imagem operacional privada
> `ghcr.io/greggorio/abaronesa-emporio-release-control@sha256:64b6f2be31b8532b870656d401656df2184599921c73ad667c65c36d65022380`.
> S41 é o contrato vigente para fechar somente o bootstrap do transporte:
> usuário não-root, filesystem canônico, chave SSH dedicada e environment
> GitHub. Não instala helper, não inicia serviço e não executa deploy/rollback.
>
> **Atualização S41/S42 — 03/08/2026:** S41 aceita com `deploy-emporio`, árvore
> `/opt/sistemas/emporio` `0700`, SSH dedicado e environment `production`
> configurados. A VPS usa Python `3.10.12` com `jsonschema 3.2.0`, que foi
> comprovado aceitando mutante inválido de `prefixItems`. S42 é o contrato
> vigente para produzir um control root autocontido, determinístico e vinculado
> ao `controlSha`, antes de instalar qualquer helper na VPS.
>
> **Atualização S42/S43 — 04/08/2026:** S42 aceita no SHA remoto
> `9731954d474fb68ec1384a525e1075f9a5542e24`, com pacote determinístico
> `9b5f836289e5cf09a0b8898ff34b1962c077a8cc0193910fb6aab93197e0d8c0`,
> runtime Python vendorizado e binding do helper ao `controlSha`; CI
> `30902014368` e candidato `30902729166` estão verdes. S43 é o contrato
> vigente para reconstruir esse pacote, instalá-lo no control root vazio e
> validar capabilities no host real. S43 não inicia control plane, serviços,
> deploy ou rollback.
>
> **Atualização S43/S44 — 04/08/2026:** S43 aceita com o pacote reproduzido no
> digest `9b5f8362…d8c0`, instalado uma única vez em
> `/opt/sistemas/emporio/shared/control`; verify, vendor Draft 2020-12 e
> capabilities fecharam no host real, com Docker e workflows de produção
> inalterados. S44 é o contrato vigente para corrigir o pacote operacional
> standalone, criar a GitHub App deployer separada e implantar
> `release_control`/PostgreSQL em `127.0.0.1:8180`. Stack comercial, deploy,
> rollback, Nginx/TLS, backup/restore e capacidade permanecem fora da S44.
>
> **Atualização S44 correction-01 — 04/08/2026:** o pacote standalone foi
> corrigido no SHA remoto `9699214582c8e74ed6c005eb2ad1e04ec950f5aa`; CI
> `30929281890`, candidato `30930060013` e imagem `30930719302` estão verdes,
> com immutable ref `sha256:2a214f0c…aff37`. A primeira execução parou antes
> de App/VPS porque combinou a tag multiarch `postgres:16.6-alpine` com o
> digest do filho amd64. O digest permanece ativo e resolve nas duas pontas
> como `postgres@sha256:589f3b24…eb0f`. A correction-01 autoriza essa forma
> canônica e a retomada da mesma S44; não há upgrade de PostgreSQL nem nova
> slice.
>
> **Atualização S44 correction-02 — 04/08/2026:** a App deployer foi criada e
> preservada com instalação exclusiva e `DEPLOYER_ACTOR_IDS=313092947`. Duas
> tentativas chegaram a live/healthy, mas readiness revelou que os schemas
> foram copiados para `/app/ops` enquanto o código instalado resolve o root
> como `/` e exige `/ops`. A VPS foi integralmente revertida ao baseline. A
> correction-02 autoriza o commit causal adicional, exige prova com a imagem e
> os assets reais de `v0.1.0` e remove o teto artificial de correções cobertas,
> sem ampliar autoridade para deploy, rollback ou stack comercial.
>
> **Atualização S44/S45 — 04/08/2026:** S44 aceita no SHA remoto
> `7e84fb95974c77a2a710d73f812a4d6bed1e4eb1`. CI `30943179121`, candidato
> `30943915132` e imagem `30944695626` fecharam verdes; a imagem operacional
> final é `sha256:d0d0cb16…e7040`. A App deployer permanece exclusiva, o control
> plane está `enabled/active`, com dois containers healthy, live/ready 200 em
> `127.0.0.1:8180`, sync sem drift e zero operação/deploy/rollback. S45 é o
> contrato vigente para fechar o Gate C: wiring RS256/deployer, configuração
> comercial protegida, capacidade/swap, Nginx/TLS e ensaio isolado de
> backup/restore. O primeiro deploy de `v0.1.0` continua reservado à S46.
>
> **Atualização S45 correction-01 — 04/08/2026:** capacidade com swap de 8 GiB,
> configuração protegida, TLS/Nginx e ensaio isolado de backup/restore fecharam
> verdes no SHA remoto `de4c4872ee3a6994bb66ecd173c0ffe81fa5fbd4`.
> O aceite foi retido por dois defeitos causais: o backend ainda emite somente
> `deployment:read deployment:execute`, enquanto o frontend exige também
> `deployment:rollback`; e o Nginx encaminha `capabilities`, mas não o namespace
> operacional `/api/deployment-control/v1/`, que o gateway rejeita. Como
> `v0.1.0` é imutável, a correction-01 corrige os contratos, publica `v0.1.1`
> pela UI/runtime e a deixa elegível, ainda com zero deploy/rollback. O primeiro
> deploy acompanhado passa a usar `v0.1.1`, sem criar slice intermediária.
>
> **Atualização S45/S46 — 05/08/2026:** S45 aceita no SHA remoto
> `cf3385f1012b9661ddbc2e83d5241aaa8633f8fd`. CI `30960751303`, candidato
> `30961397124`, imagem operacional `30961863663` e publicação global
> `30962554318` fecharam verdes. A release `v0.1.1` (ID `365219520`) possui BOM
> de seis componentes, está elegível e o `.env` comercial aponta para seus
> digests; scopes e os dois namespaces same-origin convergem. Control plane,
> swap, Nginx/TLS e backup/restore de ensaio estão verdes, com zero deploy e
> rollback. S46 é o contrato vigente para uma única primeira instalação de
> `v0.1.1`; documenta o bootstrap inevitável antes de existir UI/backend e
> exige substituí-lo pela identidade/UI reais logo após o sucesso.
>
> **Atualização S46 correction-01 — 05/08/2026:** a tentativa original parou
> corretamente antes de qualquer mutação porque `boutique-instagram-service`,
> tenant alheio, já estava em loop de restart. O critério foi corrigido para
> preservação diferencial: anomalia externa preexistente não bloqueia nem dá
> autoridade para repará-la. Também foi confirmado que o control root instalado
> ainda representa `9731954d…`, incompatível com o SHA terminal `cf3385f…` do
> workflow. A correction-01 autoriza reconstrução e rotação reversível do pacote
> terminal antes do JWKS/POST e, fechadas as capabilities, retoma a mesma S46
> para uma única instalação de `v0.1.1`. Deploy e rollback permanecem em zero
> no checkpoint; S46 ainda não foi aceita.
>
> **Atualização S46 correction-02 — 05/08/2026:** a correction-01 atualizou o
> control root e criou a primeira intenção, mas o run `30981846816` falhou no
> `prepare`: `deployment_transport.py` ainda exigia `name=Deploy Production` e
> `path=deploy-production.yml@main`, enquanto a API real devolve o `run-name`
> materializado e path sem ref. `deploy` foi `skipped`; há somente o artifact
> trust, zero recurso/backup/migration comercial e a operação permanece
> `INDETERMINATE`, com readiness 503. A correction-02 mantém essa evidência,
> corrige o validador, acrescenta reconciliação estrita de falha pré-deploy,
> atualiza imagem/control root e só então autoriza uma única operação comercial
> substituta. O total terminal esperado passa a ser dois runs — um pré-deploy
> falho e um comercial verde — com zero rollback/restore. S46 ainda não foi
> aceita.
>
> **Atualização S46 correction-03 — 05/08/2026:** a correction-02 reconciliou a
> primeira operação, publicou o plano de controle no SHA `e436190…` e criou a
> operação substituta. O run `30988243119` passou trust/prepare, mas falhou no
> primeiro comando do job deploy: o pacote instala `deployment-remote.py` como
> `0600`, embora o transporte o execute diretamente pelo shebang. Outcome
> `CONFIRMED/FAILED`, restore false, operações terminais, readiness 200 e zero
> recurso/backup/migration comercial foram confirmados. A correction-03 corrige
> o manifesto para helper `0755`, exige prova real como `deploy-emporio` e
> autoriza a conclusão do primeiro deploy. Para evitar nova microcorreção,
> permite um único ciclo adicional apenas diante de falha confirmada anterior a
> upload/install/execute e com zero efeito/resíduo comercial. S46 ainda não foi
> aceita; rollback/restore permanecem em zero.
>
> **Atualização S46 correction-04 — 05/08/2026:** a correction-03 corrigiu o
> helper, publicou `69621c2…`, rotacionou o control root e provou capabilities
> direto como `deploy-emporio`. O run `30993832964` passou trust/prepare, mas o
> runner não autenticou com o secret SSH; outcome `CONFIRMED/FAILED`, restore
> false e zero snapshot/upload/install/recurso comercial foram comprovados. Uma
> observação transitória anterior também deixou o `current` vazio marcado como
> incerto após o outcome terminal, mantendo readiness 503. A correction-04
> corrige essa recuperação sem SQL, rotaciona a chave e a prova por workflow
> dedicado sem inputs antes de qualquer novo POST; só então autoriza a conclusão
> de `v0.1.1`. Os três runs falhos permanecem históricos, rollback/restore em
> zero e S46 ainda não está aceita.

## 1. Mandato e modo de condução

Assuma como orquestrador, não como mero revisor de documentos. O objetivo é
fazer a implementação convergir até o uso real, preservando segurança e
evidência, sem transformar cada descoberta em uma cadeia de microcontratos.

O usuário explicitamente prefere uma condução orientada ao resultado:

- pensar o caminho completo antes de agir;
- permitir investigação e correção causal pelo executor dentro do objetivo;
- executar a matriz local completa antes de cada push;
- usar o run remoto para provar integração real, não como substituto de testes
  baratos e determinísticos;
- parar quando surgir decisão humana, risco material ou mutação externa não
  autorizada — não por formalismo reparável;
- não reescrever histórico publicado apenas para enquadrar mensagens de commit
  em contratos retrospectivos.

Ao aceitar uma etapa, atualize tracker e relatórios de forma concisa. Não
declare que uma task foi executada quando o caminho real a ultrapassou.

## 2. Ordem inicial de leitura

Leia, nesta ordem:

1. este handoff;
2. [tracker](./README.md);
3. [proposta arquitetural](../proposta-docker-ci-cd-producao-emporio.md);
4. [relatório consolidado S30a](./slices/S30a-paridade-local-fechamento-ci-candidato.report.md);
5. [relatório remoto da authorization-03](./slices/S30a-paridade-local-fechamento-ci-candidato.authorization-03.report.md);
6. [task S35](./slices/S35-migracoes-previas-stack-candidata.task.md), apenas
   para entender a intenção que foi ultrapassada;
7. documentos correntes em `../release-control/`;
8. os cinco workflows e seus validadores.

Relatórios e tasks históricas não prevalecem sobre Git, GitHub, código e
artefatos atuais.

## 3. Snapshot verificado

No momento deste handoff:

```text
CWD                 /home/gregorio/git/baronesa/emporio
branch              main
HEAD                68a3528b563ad0c19819da34ab106396ae679596
origin/main         68a3528b563ad0c19819da34ab106396ae679596
remoto main         68a3528b563ad0c19819da34ab106396ae679596
worktree            limpo
stage               vazio
tags                zero
releases GitHub     zero
```

O snapshot acima é a base remota e técnica anterior à versionagem deste
documento. O handoff deve ser entregue em um commit local com a mensagem
`docs: hand off implementation closure`; portanto o estado esperado ao assumir
é um commit local à frente de `origin/main`, stage e worktree vazios e remoto
ainda em `68a3528b563ad0c19819da34ab106396ae679596`.

Revalide esse estado antes de qualquer decisão. Não assuma que permaneceu
inalterado depois de 02/08/2026.

Estado das slices:

- S01–S29: aceitas;
- S30: contrato-pai histórico, dividido em S30a e S30b;
- S31–S34: aceitas;
- S30a: objetivo funcional alcançado, aceite formal ainda pendente pelos dois
  itens da seção 7;
- S35: `SUPERSEDED`; task não executada conforme o contrato e ultrapassada pela
  correção exploratória remota;
- S30b: ainda não materializada;
- preparação e operação reais da VPS: ainda não executadas.

O tracker foi reconciliado com esse estado no mesmo checkpoint do handoff. Não
apagar o histórico dos runs reprovados.

## 4. Evidência remota aceita

### 4.1 CI final

```text
workflow     CI
run          30751925552
event        push
attempt      1
headSha      68a3528b563ad0c19819da34ab106396ae679596
conclusion   success
jobs         13/13 success
```

URL:
`https://github.com/greggorio/abaronesa-emporio/actions/runs/30751925552`

### 4.2 Publish Candidate final

```text
workflow     Publish Candidate
run          30752210806
event        workflow_run
attempt      1
headSha      68a3528b563ad0c19819da34ab106396ae679596
conclusion   success
jobs         11/11 success
```

URL:
`https://github.com/greggorio/abaronesa-emporio/actions/runs/30752210806`

Todos os jobs `trust`, `predecessor`, seis `build`, `assemble`, `integrated` e
`publish` concluíram em `success`. Foi a primeira conclusão verde dos jobs
`integrated` e `publish` na história do repositório.

### 4.3 Candidato publicado

```text
candidateId
candidate-68a3528b563ad0c19819da34ab106396ae679596-30752210806-1

candidate-manifest artifact id
8834868927

candidate-manifest artifact digest
sha256:0bfbbd4bce1110cd8d75e01170a107e9db488dc64f0d333cb671fabd1575ef89

manifestSha256
sha256:af89b6b636d53bc15e9ba79bf7ec65a9e372e53b2226def3f7119d9df7251b5f

candidate-outcome artifact id
8834869091

candidate-outcome artifact digest
sha256:89c3352be0d79b82861841bd17ae53905d59b9428612f3d06f91b60fd98b01b4
```

Validação independente já executada:

- sidecars do manifesto e outcome conferem;
- `candidate_manifest.py validate` retorna `candidate:valid`;
- `outcome.validate` retorna lista vazia;
- predecessor `first`;
- seis `buildComponents`, `inheritedComponents = []`;
- seis componentes em `built`, checks `build/test/scan = passed`;
- `immutableRef = imageRepository@digest` em todos;
- `integration.status = passed`;
- `outcome.status = published`;
- bindings de CI, Publish Candidate, commit, artifact id e digest conferem;
- `provenance` e todos os campos de attestation estão ausentes.

Não invente nova invalidação desse candidato. Ele é funcionalmente válido; o
item de segurança da seção 7 exige endurecer execuções futuras.

## 5. Correção exploratória que levou ao verde

Depois do checkpoint `1824cf6`, sete commits técnicos foram publicados:

| Commit | Resultado |
|---|---|
| `13fda32` | aplica migration ERP antes do startup integrado |
| `8f4ecc0` | registra `compose ps` e logs limitados quando a integração falha |
| `e2eedaa` | semeia idempotentemente o grupo Admin em banco novo |
| `6d18c38` | regenera fixtures e hashes após a nova migration |
| `47ac25f` | aceita JSON Lines real de `docker compose ps --format json` |
| `44758a2` | usa token válido nos probes de rotas de controle ausentes |
| `68a3528` | devolve 404 para rota API inexistente em vez de 500 |

Não fazer rebase, squash, amend ou force push para mudar essas mensagens. O
histórico remoto é evidência causal útil.

Defeitos revelados em sequência:

1. migration ERP ausente no banco efêmero;
2. grupo Admin inexistente em banco novo, combinado com `id=1` explícito numa
   entidade `IDENTITY`;
3. parser incompatível com JSON Lines do Compose atual;
4. probe sem autenticação incapaz de distinguir 401 de rota ausente;
5. `NoResourceFoundException` tratado pelo handler genérico como 500.

Houve também uma CI reprovada no commit `e2eedaa` porque as fixtures de release
não haviam sido regeneradas. O commit `6d18c38` fechou essa divergência. Use
essa ocorrência para exigir a matriz local completa antes do próximo push,
sem criar nova burocracia documental.

## 6. Runs intermediários

Preserve como histórico; não reutilize artefatos de runs reprovados:

| SHA | CI | Publish Candidate |
|---|---:|---:|
| `13fda32` | `30747925929` success | `30748153320` failure |
| `8f4ecc0` | `30748515262` success | `30748718048` failure |
| `e2eedaa` | `30749174374` failure | `30749241187` failure |
| `6d18c38` | `30749655445` success | `30749916445` failure |
| `47ac25f` | `30750432188` success | `30750649542` failure |
| `44758a2` | `30751150117` success | `30751442252` failure |
| `68a3528` | `30751925552` success | `30752210806` success |

Não apagar runs, logs ou artifacts sem autorização explícita do usuário.

## 7. Dois itens obrigatórios antes do aceite da S30a

### 7.1 Mascarar credenciais efêmeras nos logs

`tools/candidates/compose_env.py` gera valores aleatórios e os grava em
`GITHUB_ENV`, mas não chama `::add-mask::`. O runner mostra esses valores no
bloco de ambiente dos passos seguintes.

No run vencedor, os valores destas sete variáveis aparecem sem máscara três
vezes cada:

```text
CANDIDATE_ROOT_PASSWORD
POSTGRES_ADMIN_PASSWORD
ERP_DB_PASSWORD
WEBSITE_DB_PASSWORD
INTEGRATION_SYSTEM_TOKEN_SECRET
ERP_WEBSITE_SYNC_KEY
GOOGLE_CLIENT_SECRET
```

Não registrar os valores no relatório ou em comandos. Eles são efêmeros,
distintos por run e ficaram inutilizáveis após o cleanup comprovado de
containers e volumes. Não há indicação de segredo de produção ou credencial
persistente vazada; não rotacionar credenciais reais sem evidência de reuso.

Correção mínima esperada:

- definir explicitamente o conjunto de chaves sensíveis;
- emitir `::add-mask::<valor>` para cada uma antes de gravar `GITHUB_ENV`;
- manter stdout sem mensagens que reproduzam o valor fora do comando de mask;
- testar que todas as sete chaves são mascaradas e que nenhuma chave pública é
  tratada como segredo por aproximação;
- no run remoto seguinte, auditar somente a classificação do log e exigir que
  os valores apareçam como `***`, nunca imprimir os valores encontrados.

O scanner de arquivos versionados não cobre logs do GitHub Actions. Não usar
`secret-scan:clean` como prova desse comportamento.

### 7.2 Migrar também o banco website no candidato

O harness atual executa somente:

```text
docker compose ... run --rm -T --entrypoint /app/bin/migrate backend migrate
```

Antes do `up`, executar também:

```text
docker compose ... run --rm -T --entrypoint /app/bin/migrate website_back migrate
```

Ordem obrigatória:

```text
pull -> migrate backend -> migrate website_back -> up
```

Motivo: produção migra os bancos `erp` e `website`. Na stack candidata,
`website_back` passa hoje porque `DDL_AUTO` assume `update` enquanto
`FLYWAY_ENABLED=false`; isso pode esconder migration ausente ou inválida.

Preservar:

- exatamente sete serviços canônicos;
- nenhum serviço auxiliar de migration no Compose;
- os entrypoints existentes nas imagens candidatas;
- abortar antes do `up` quando qualquer migration falhar;
- cleanup dirigido completo;
- recibo e lista de probes sem alteração.

Testar sucesso, ordem e falha independente na primeira e na segunda migration.

### 7.3 Forma recomendada de execução

Tratar os dois itens como um único fechamento técnico do candidato. Não abrir
uma slice por variável, teste ou run. O executor pode investigar e ajustar
livremente dentro desses dois resultados, mas antes do push deve executar os
16 validadores e as oito suítes canônicas, além de `git diff --check` e secret
scan com `unsupported=0`.

Fazer um único push normal. Observar a CI e o Publish Candidate do novo SHA.
Aceite remoto exige:

- CI verde;
- candidato verde;
- ambas as migrations antes do `up`;
- sete valores mascarados nos logs;
- artefatos finais válidos e vinculados ao novo SHA;
- nenhum release, deploy ou efeito de produção.

## 8. Fechamento documental da S30a e S35

Depois da seção 7:

1. registrar o ciclo final no relatório S30a;
2. marcar S30a `ACCEPTED` no tracker;
3. preservar S35 como `SUPERSEDED` e não criar relatório fictício dizendo que
   ela foi executada;
4. registrar os sete commits reais, runs, candidato e risco de logs efêmeros;
5. não reescrever relatórios históricos;
6. criar S30b no mesmo ciclo, com fronteira focada em publisher/release.

O candidato atual permanece evidência válida, mas o candidato do fechamento
será o predecessor mais recente para a S30b.

## 9. S30b — release global pelo publisher

S30b deve provar o caminho já implementado, sem usar dispatch manual como
atalho:

```text
UI de desenvolvimento
  -> runtime publisher
  -> publish-release.yml
  -> release global imutável
  -> outcome reconciliado
```

Antes da mutação externa, confirmar:

- configuração e permissões mínimas da identidade publisher;
- candidato mais recente e válido;
- usuário ERP autorizado;
- dados humanos da release: bump SemVer, descrição e changelog;
- ausência de operação concorrente;
- nenhum acesso a deploy/rollback/produção.

Resultado obrigatório:

- uma única solicitação pela UI/runtime;
- `operationId`, workflow run/attempt, release, tag e artifacts cruzados;
- BOM global com os seis digests do candidato;
- release e assets imutáveis;
- outcome terminal reconciliado;
- replay idempotente não cria segunda release;
- restart controlado do publisher recupera a operação sem redispatch;
- nenhum workflow de deploy ou rollback executado.

Publicar release, criar tag e testar restart são mutações externas; confirme a
autorização do usuário e os metadados concretos antes de agir.

## 10. Caminho de produção até o encerramento

Os números S31–S35 previstos no handoff histórico foram consumidos por
correções de segurança e candidato. Use novos números a partir de S36/S37 ou
nomes explícitos; não reutilize esses identificadores com outro significado.

### 10.1 Preparação segura da VPS

Começar por inspeção somente leitura e confrontar o host real:

- usuário operacional dedicado;
- paths, ownership e modos;
- Docker/Compose, disco, memória, portas e redes;
- Nginx/Certbot atuais e os dois domínios;
- PostgreSQL, bancos, volumes, uploads e sessão WhatsApp;
- systemd e health do `release_control` deployer fora da stack comercial;
- autenticação GHCR somente leitura;
- identidade GitHub App deployer separada da publisher;
- backup, retenção, known hosts e secrets sem transcrição.

Produza plano exato e obtenha autorização antes da primeira mutação da VPS.

### 10.2 Primeira implantação acompanhada

Executar somente pelo caminho canônico:

```text
UI produção -> runtime deployer -> deploy-production.yml
-> transporte autenticado -> CLI transacional -> outcome reconciliado
```

Exigir:

- release global elegível e plano determinístico;
- backup real antes das migrations;
- seis imagens pelos digests da release;
- migrations dos dois bancos;
- health, smoke e HTTPS dos dois domínios;
- instalação reconciliada no control plane;
- persistência de PostgreSQL, uploads e sessão WhatsApp;
- nenhuma execução manual contornando journal, lock ou state machine;
- nenhum SSH root como mecanismo normal de deploy.

Essa etapa exige autorização explícita, janela de manutenção e plano de
parada/rollback.

### 10.3 Falha, rollback e recuperação

Em ambiente controlado e com autorização:

- provar compensação após falha de pull, migration, health ou smoke;
- provar rollback comercial e restore conforme os contratos S25–S27;
- provar retomada após restart sem inventar sucesso;
- comprovar disponibilidade independente do release control;
- verificar retenção de backup, release anterior e artifacts necessários.

Não provocar falha em produção apenas para satisfazer checklist. Use ambiente
controlado ou obtenha aceitação explícita do risco residual quando a prova real
não for segura.

### 10.4 Encerramento operacional e documental

Antes de declarar o programa concluído:

- configurar ou documentar monitoramento, alertas, retenção e rotação;
- documentar atualização independente do `release_control`;
- produzir arquitetura real da VPS e runbooks de deploy, rollback, restore e
  incidente;
- reconciliar proposta histórica, contratos e comportamento instalado;
- executar auditoria final de Git, workflows, releases, deploy, segurança,
  resíduos e produção;
- listar riscos residuais aceitos pelo usuário;
- marcar todas as slices aplicáveis como aceitas/superseded com justificativa.

## 11. Dívidas conhecidas que não bloqueiam S30a

### GrupoAdminInitializer

`GrupoAdminInitializer.run()` mantém `try/catch` dentro de `@Transactional`.
A migration `V20260802120000__seed_admin_group.sql` removeu o gatilho atual,
mas uma falha futura pode marcar a transação como rollback-only e depois ser
engolida, produzindo `UnexpectedRollbackException` menos informativa.

Corrigir separadamente, preferencialmente removendo o catch ou isolando o
tratamento fora da fronteira transacional, com teste causal. Não alterar a
migration já publicada sem necessidade.

### Handler 404

O probe integrado comprovou que rotas autenticadas inexistentes retornam 404.
Ainda é recomendável acrescentar teste unitário/web focal para
`NoResourceFoundException`, mas isso não invalida o candidato publicado.

### Logs históricos

Runs intermediários e o run vencedor contêm valores efêmeros já expirados. A
decisão de apagar logs é do usuário porque remove evidência de auditoria. Não
apagar runs, artifacts ou logs por iniciativa própria. Se houver prova de que
algum valor foi reutilizado fora da stack efêmera, então tratar como incidente
e rotacionar o alvo correspondente.

## 12. Limites permanentes

- nunca abrir ou transcrever `ops/env/.env.production`;
- nunca registrar token, senha, header, PFX, chave privada ou sessão WhatsApp;
- actions permanecem pinadas por SHA;
- `latest` é proibido em candidato, release e produção;
- GitHub Apps publisher e deployer são separadas;
- aplicação não recebe Docker socket ou SSH;
- registry da VPS é somente leitura;
- PostgreSQL e serviços internos não publicam porta externa;
- falha ou estado incerto nunca vira sucesso presumido;
- não executar force push, reescrever main ou apagar evidência remota;
- não acessar ou mutar produção sem autorização explícita para o alvo exato.

## 13. Retomada mínima

Comece apenas com leitura:

```bash
cd /home/gregorio/git/baronesa/emporio
git status --short --branch
git rev-parse HEAD origin/main
git log --oneline --decorate -12
git diff --check
git ls-remote origin refs/heads/main
gh run list --commit 68a3528b563ad0c19819da34ab106396ae679596 --limit 10
```

Depois confira o código atual de:

```text
tools/candidates/compose_env.py
tools/candidates/integrated_harness.py
tools/candidates/validate_candidate_workflow.py
tools/candidates/tests/
ops/compose/compose.prod.yml
website_back/src/main/resources/application.properties
```

Não comece pela task S35 como se o worktree ainda contivesse o patch local que
ela descreve; o worktree atual está limpo e o histórico remoto já avançou.

## 14. Primeira mensagem sugerida ao assumir

```text
Assumi a orquestração no SHA remoto 68a3528b563ad0c19819da34ab106396ae679596.
A CI e o Publish Candidate finais estão verdes, e o candidato publicado foi
validado. Antes de aceitar S30a, vou fechar em um único ciclo dois pontos:
mascarar as sete credenciais efêmeras nos logs e migrar também o banco website
antes do startup integrado.

Não reescreverei os sete commits publicados nem fingirei que a task S35 foi
executada. Depois do novo candidato verde, reconciliarei tracker/relatórios,
aceitarei S30a e abrirei S30b para publicação da release pela UI/runtime.
Mutações em release, VPS, deploy e rollback continuarão condicionadas à
autorização explícita do usuário.
```
