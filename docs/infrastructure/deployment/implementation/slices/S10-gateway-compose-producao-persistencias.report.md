# S10 — Relatorio de execucao

Estado: **IN_PROGRESS — aguardando revisao do orquestrador**

Data: 28/07/2026  
CWD raiz: `/home/gregorio/git/baronesa/emporio`

## 1. Escopo e documentos lidos

A task S10 foi lida integralmente antes das alteracoes, junto da arquitetura,
revisao final da S09, catalogo e contratos das imagens. A task e o tracker nao
foram alterados. `.env.production`, uploads, sessoes e credenciais reais nao
foram abertos.

## 2. Prototipos substituidos

Foram inventariados e removidos individualmente, sem glob ou remocao
recursiva, exatamente os dez arquivos prescritos:

- `deploy/docker-compose.yml`
- `deploy/infra/docker-compose.yml`
- `deploy/nginx/conf.d/emporio.conf`
- `deploy/nginx/emporio-erp.conf.template`
- `deploy/nginx/emporio-website.conf.template`
- `deploy/nginx/nginx.conf`
- `ops/compose/docker-compose.emporio.yml`
- `ops/compose/docker-compose.emporio-website.yml`
- `ops/scripts/init-multiple-dbs.sh`
- `ops/deploy/deploy-tenant.sh`

Eles foram substituidos por um Compose comercial unico, gateway interno e
inicializador idempotente. Como nao existe HEAD, nao sao recuperaveis pelo
historico Git local.

## 3. Entregas

- `ops/compose/compose.prod.yml`: sete servicos exatos, imagens obrigatorias por
  digest, bind unico loopback, duas redes, quatro volumes, healths, recursos,
  logs, restart e dependencias saudaveis.
- `ops/compose/testing/compose.s10.yml`: override explicitamente local, somente
  para imagens S08/S09/S10 e desativacao de inicializacao WhatsApp.
- `ops/gateway/`: imagem Nginx Alpine por digest, UID `101`, filesystem somente
  leitura, health local, dois hosts canonicos e roteamento fechado.
- `ops/db/init-databases.sh`: identificadores seguros, roles/bancos distintos,
  `ON_ERROR_STOP`, passwords por variaveis psql, idempotencia e isolamento.
- `ops/env/.env.example`: matriz canonica sem valor real.
- pontes de WhatsApp, CORS/WebSocket e Firebase nos arquivos autorizados.
- validadores e mutantes sob `tools/compose` e `tools/gateway`.
- documentacao em `docs/infrastructure/deployment/compose/PRODUCTION_STACK.md`.

## 4. WhatsApp interno

O valor persistido nao vazio continua prioritario. Ausente/vazio usa
`app.whatsapp.service-url`, cujo default prod e o ID interno. A URI final
aceita somente HTTP(S), autoridade segura e nenhum userinfo/query/fragment;
falha antes da requisicao. Logs e falhas nao reproduzem URL, payload ou
resposta. Nao ocorre chamada no startup. Cinco testes focados de
WhatsApp/CORS do ERP passaram.

## 5. CORS e WebSocket

As allowlists comerciais hardcoded foram removidas das configuracoes ativas.
As origens sao normalizadas, sem vazio, duplicata, wildcard, path, query,
fragment ou scheme alheio. O website usa uma unica fonte CORS compartilhada
pela seguranca e filtro; WebSocket usa o mesmo parser. Compose fornece os dois
dominios HTTPS canonicos. Mutantes inseguros falham com mensagem sanitizada.

## 6. Firebase

`APP_FIREBASE_ENABLED=false` e o default prod. Desabilitado nao consulta tema,
filesystem ou Firebase. Habilitado exige path/credencial valida e falha fechado
sem expor path ou causa sensivel. O path nao vem mais de payload do tema.
Testes cobrem disabled, enabled valido por colaborador injetado e enabled
invalido.

## 7. Gateway

Base efetiva: `nginxinc/nginx-unprivileged:1.29.5-alpine3.23` fixada por
digest; manifesto `linux/amd64` foi resolvido no Docker Hub permitido. Build
local: `abaronesa-emporio-gateway:s10`. Inspect confirmou UID `101:101`,
somente `8080/tcp` e health local. `nginx -t`, container read-only e teste dos
dois hosts passaram. Host desconhecido fecha, a rota de deployment-control e
recusada e nao existe upstream WhatsApp.

## 8. PostgreSQL, redes e persistencias

Foi usada a tag completa `postgres:16.10-alpine3.22`, digest de indice
imutavel, com manifesto amd64 `sha256:ab8380566c3ea09690a9ecaa85a59d82bfc6eb86744151a2a54335866c83a3e9`.
O teste real criou os dois bancos e roles, confirmou os dois nomes,
reexecutou o inicializador com codigo zero e sem rotacionar passwords.
Revogacoes cruzadas foram reaplicadas idempotentemente. A conexao do owner
retornou zero e a tentativa do usuario ERP no banco website foi rejeitada com
codigo 2. Um marker PostgreSQL retornou `1` depois da remocao e recriacao do
container sobre o mesmo volume. Nenhuma porta DB foi publicada.

A stack completa ficou healthy. Somente o gateway apareceu no host e apenas
em `127.0.0.1:18120` no harness. Os quatro volumes e duas redes usaram prefixo
`s10-proof-*`. Marcadores de backend, website e WhatsApp foram lidos em um
segundo container com o usuario final. A limpeza dirigida removeu containers,
redes e volumes, e a busca final nao encontrou residuo `s10-*`.

## 9. Falhas intermediarias e correcoes

1. Validador gateway rejeitou incorretamente o USER por regex escapada duas
   vezes. Regex corrigida; mutantes e baseline passaram.
2. Validador Compose mostrou nome incorreto da variavel de imagem WhatsApp no
   fixture. Fixture corrigido sem relaxar a validacao.
3. Primeiro init PostgreSQL usou substituicao psql em `-c`, que nao ocorre.
   Consultas foram movidas para stdin psql; teste real e repeticao passaram.
4. Primeiro `nginx -t` read-only encontrou temp dirs fora do tmpfs. Todos os
   temp paths foram explicitados sob `/tmp`; repeticao passou.
5. Primeiro startup encontrou construtores auxiliares em classes Spring sem
   selecao explicita. Os construtores de producao foram marcados para injecao;
   recompilacao e stack passaram.
6. Health ERP tentou SMTP opcional. Compose passou a desabilitar somente o
   indicador mail quando SMTP esta desabilitado; a stack completa ficou
   healthy.
7. A primeira repeticao Maven ERP usou chave efemera curta e falhou pelo gate
   criptografico existente. A repeticao usou valor efemero de tamanho valido.
8. A primeira suite do catalogo ainda codificava os cinco gates pre-S10.
   Expectativas foram atualizadas para o estado comprovado; os 45 testes
   passaram sem enfraquecer mutantes.

## 10. Evidencias de comandos

Todos os comandos partiram do CWD raiz, salvo os Maven nos respectivos
diretorios. Valores efemeros foram omitidos desta persistencia por exigencia do
contrato.

| Comando | CWD | Exit | Resultado |
|---|---|---:|---|
| `python3 tools/gateway/validate_gateway.py` | raiz | 0 | contrato valido |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests -p 'test_*.py' -v` | raiz | 0 | 2 testes |
| `python3 tools/compose/validate_compose.py` | raiz | 0 | config JSON valida |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py' -v` | raiz | 0 | 3 testes |
| `docker buildx build --platform linux/amd64 --load -t abaronesa-emporio-gateway:s10 ops/gateway` | raiz | 0 | imagem criada |
| `docker run --rm --read-only --tmpfs /tmp:... --entrypoint nginx abaronesa-emporio-gateway:s10 -t` | raiz | 0 | sintaxe valida |
| `docker compose -f ops/compose/compose.prod.yml -f ops/compose/testing/compose.s10.yml up -d --no-build --remove-orphans --wait` | raiz | 0 | 7/7 healthy |
| duas requisicoes loopback com `Host` canonico a `/healthz` | raiz | 0 | `ok` nos dois hosts |
| reexecucao `/docker-entrypoint-initdb.d/10-init-databases.sh` | raiz | 0 | idempotente |
| conexao owner seguida de conexao cruzada | raiz | 0 global | owner `0`, cruzada rejeitada `2` |
| recriacao de container sobre volume PostgreSQL com marker | raiz | 0 | marker `1` preservado |
| testes de marcador em segundo container para 3 volumes de aplicacao | raiz | 0 | persistencia e escrita confirmadas |
| `cd website_back && mvn -B verify` com env efemero | website_back | 0 | 55 testes |
| `cd backend && mvn -B verify` com env efemero valido | backend | 0 | 40 testes |
| `python3 tools/releases/catalog.py validate` | raiz | 0 | valido |
| `python3 tools/releases/catalog.py validate --require-release-ready` | raiz | 0 | ready |
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/releases/tests -p 'test_catalog.py' -v` | raiz | 0 | 45 testes |
| `docker compose ... down -v` no project `s10-proof-*` | raiz | 0 | limpeza dirigida |

## 11. Cinco gates

Foram fechados exatamente:

- `WEBSITE_BACK_UPLOAD_PERSISTENCE`;
- `GATEWAY_CANONICAL_ARTIFACTS`;
- `GATEWAY_HEALTH_CHECK`;
- `GATEWAY_LOOPBACK_PORT`;
- `GATEWAY_TEST_COMMAND`.

Website backend e gateway estao `ready`; todos os seis componentes comerciais
estao `ready`; `readiness_gates` esta vazio. Isso nao afirma CI, manifesto,
release, publicacao ou deploy.

## 12. Estado final e restricoes

O Maven ERP terminou com codigo zero e 40 testes aprovados. Somados aos 55 do
website e aos validadores locais, todas as suites prescritas terminaram
verdes. Nao houve instalacao no host, `git add`, commit,
tag, push, publicacao, prune, acesso a GitHub/GHCR/VPS/DNS/producao nem
requisicao a dominio real. Nao foi criado workflow. Caches Python serao
verificados e removidos apenas se gerados pelos testes.

**IN_PROGRESS — aguardando revisao do orquestrador**

---

## 15. Resposta as correcoes do ciclo 1

Data: 28/07/2026  
CWD: `/home/gregorio/git/baronesa/emporio`  
Estado: **IN_PROGRESS — aguardando nova revisao do orquestrador**

### 15.1 Resposta ao achado 14.1 — imagens Java

Confirmado: as tags locais `:s08` foram sobrescritas no primeiro ciclo. Nao
foi feita tentativa de reconstruir ou atribuir novamente os IDs historicos.
As alegacoes anteriores de preservacao dessas tags ficam corrigidas por este
registro.

Foram construidas depois de todos os arquivos Java autorizados:

| Imagem | ID | Criada em | Tamanho | Labels |
|---|---|---|---:|---|
| `abaronesa-emporio-backend:s10` | `sha256:0c03efce1956ab05df2a83eea131135deacbf221d1f6b7e6f21fda4ec8f97d2d` | `2026-07-28T19:39:44.782800867-03:00` | 487206927 | `s10-local-validation`, `s10-local` |
| `abaronesa-emporio-website-back:s10` | `sha256:cd253d9f2934a84bcc635adf4d29191479a238360670ca9e13b859eee672d044` | `2026-07-28T19:39:52.019856785-03:00` | 324207676 | `s10-local-validation`, `s10-local` |

O arquivo final `WhatsAppService.java` tem mtime
`2026-07-28T19:25:37.208484065-03:00`, anterior a imagem backend S10. O
override usa somente as tags Java `:s10`.

A stack final `s10-finalc1-*` ficou 7/7 healthy. Um usuario SYSTEM estritamente
efemero autenticou pela rota do gateway e
`GET /api/whatsapp/status` retornou HTTP 200 com estado `DESCONECTADO`. O
registro persistido da URL permaneceu vazio e o backend S10 alcançou
`whatsapp_service:3001` pelo fallback runtime. O inspect confirmou
`3001/tcp` sem bind no host.

### 15.2 Resposta ao achado 14.2 — gateway

Base exata do gateway:
`nginxinc/nginx-unprivileged:1.29.5-alpine3.23@sha256:42a7d7f2ee23e9f5a1dcdf3647ba5c585bbd18f79e79cd817e70e8cd61c55779`.
A imagem final e
`sha256:74f8c31f08ef8e0ef18ad319e40db3e83164a4c98400f5e1aed0bff6c9bb48de`,
criada em `2026-07-28T19:40:49.561378086-03:00`, 60054042 bytes, UID
`101:101`.

Correcao por item:

- `/ws` exato e `/ws/` possuem locations proprias nos dois hosts;
- WebSocket usa connect 5 s, read/send 65 s, Upgrade/Connection e buffering
  desligado; HTTP/SSE usa timeouts finitos de 5/60/60 s;
- limites sao 10 MiB no ERP e 2 MiB no website;
- headers `nosniff`, `SAMEORIGIN` e referrer policy foram adicionados;
- `/actuator/` nao e publicado e OAuth existe somente no ERP;
- forwarded proto usa header recebido ou fallback `$scheme`;
- deployment-control continua 404, host desconhecido fecha e nao existe
  upstream direto WhatsApp.

Matriz dos probes reais da stack final:

| Host/path | Resultado | Upstream provado |
|---|---|---|
| ERP `/` | `200 text/html` | `frontend:80` |
| Website `/` | `200 text/html` | `website_front:80` |
| ERP `/api/auth/login` | token emitido | `backend:8080` |
| ERP `/api/whatsapp/status` | `200 application/json`, `DESCONECTADO` | backend e fallback ao Node interno |
| ERP `/media/s10-missing` | `500 application/json` | backend Java, nao frontend |
| Website `/api/themes` | `500 application/json` | website backend Java |
| Website `/media/s10-missing` | `500 application/json` | website backend Java |
| ERP `/ws` com Upgrade | `401 application/json` | backend, nao SPA |
| Website `/ws` com Upgrade | `400` | endpoint WebSocket website |
| ERP `/api/deployment-control/status` | `404` | rejeicao local |
| host desconhecido `/` | conexao fechada, curl `000` | default fail-closed |

Os `500` de recursos inexistentes nao sao tratados como sucesso funcional;
somente como fingerprint real do upstream para esta prova de roteamento.

### 15.3 Resposta ao achado 14.3 — Compose, env e harness

`.env.example` agora separa imagens, topologia, recursos de cada servico,
PostgreSQL, sync/bootstrap, URLs/OAuth/CORS/WebSocket, Java, SMTP, Uber,
Firebase, frontends e WhatsApp. O Compose parametriza URLs publicas com
defaults canonicos e encaminha apenas variaveis consumidas. O health mail
segue `MAIL_ENABLED`, em vez de permanecer sempre false.

O override usa `backend:s10`, `website-back:s10`, URLs
`erp.s10.invalid`/`website.s10.invalid`, bootstrap efemero e
`WHATSAPP_INITIALIZATION_DISABLED` apenas no harness.

O validador resolvido cobre sete servicos, imagens, todas as memberships,
redes, quatro volumes/mounts, healths, dependencies, lifecycle, security,
logs, recursos, portas, allowlists de environment, runtime WhatsApp/Firebase,
dez legados e override local. A suite possui 22 mutantes estruturais
independentes, quatro do inicializador e tres do override, totalizando 29
mutantes Compose.

### 15.4 Resposta ao achado 14.4 — drift PostgreSQL

O inicializador agora:

- verifica `rolcanlogin` de role preexistente;
- verifica `pg_get_userbyid(datdba)` de database preexistente;
- falha com mensagem sanitizada para role ou owner incompatível;
- nao altera password preexistente;
- preserva validacao posterior, grants e isolamento.

Teste real em PostgreSQL 16 retornou:

```text
role_drift=1 owner_drift=1 valid=0 idempotent=0 cross=2
```

Assim, os dois drifts falham, estado correto e repeticao passam, e a conexao
cruzada continua rejeitada.

### 15.5 Resposta ao achado 14.5 — comandos, testes e estado

Comandos corretivos principais e codigos:

| Comando | Exit | Evidencia |
|---|---:|---|
| dois `docker buildx build --platform linux/amd64 --load --build-arg VCS_REF=s10-local-validation --build-arg IMAGE_VERSION=s10-local ...:s10` | 0 | IDs Java acima |
| build equivalente de `abaronesa-emporio-gateway:s10` | 0 | ID gateway acima |
| `docker run ... --entrypoint nginx ... -t` | 0 | sintaxe valida em read-only |
| unittest gateway | 0 | 4 metodos, 17 mutantes |
| unittest Compose | 0 | 4 metodos, 32 mutantes |
| `docker compose ... config --format json` | 0 | config final resolvida |
| `docker compose ... up -d --no-build --remove-orphans --wait` | 0 | 7/7 healthy |
| probes da matriz 15.2 | 0 global | upstreams e fronteiras confirmados |
| teste real de drift/idempotencia/isolamento PostgreSQL | 0 global | codigos da Secao 15.4 |
| `docker compose ... down -v` no prefixo `s10-finalc1-*` | 0 | limpeza dirigida |

Falhas intermediarias preservadas:

1. o primeiro `nginx -t` corretivo detectou timeout duplicado nos includes;
   a configuracao WebSocket foi tornada autocontida;
2. a repeticao seguinte encontrou timeout HTTP duplicado no location API; a
   duplicata foi removida, mantendo o valor no include comum;
3. o primeiro login efemero usou segredo abaixo de 512 bits e o JWT HS512
   falhou; a repeticao usou valor efemero com comprimento valido e emitiu
   token;
4. o primeiro roteiro de drift agrupou `DROP DATABASE` em transacao psql e
   falhou; a repeticao executou comandos de database individualmente e
   produziu os codigos esperados.

Os testes Java aprovados do ciclo anterior continuam sendo 40 no ERP e 55 no
website; nenhuma alteracao Java ocorreu depois das imagens S10. Catalogo
permanece com 45 testes e readiness estrutural verde. As suites corretivas
adicionam quatro metodos gateway e quatro Compose, com 49 mutantes
independentes ao todo.

Nao houve `git add`, commit, tag, push, publicacao, prune, acesso a
`.env.production`, GitHub, GHCR, DNS ou VPS. A limpeza e o estado Git serao
confirmados uma ultima vez abaixo. Nenhuma S11 foi criada.

**IN_PROGRESS — aguardando nova revisao do orquestrador**

### 15.6 Fechamento da repeticao final

Apos fortalecer o validador Compose com mutantes adicionais para allowlist de
ambiente, Docker socket e filesystem read-only do gateway, a primeira repeticao
detectou uma incompatibilidade do proprio validador com a forma curta de
`tmpfs` emitida pelo Compose. O parser foi corrigido para interpretar o target
antes dos dois-pontos, sem reduzir as garantias.

Resultados finais, todos com codigo de saida `0`:

- unittest Compose: 4 metodos e 32 mutantes;
- unittest gateway: 4 metodos e 17 mutantes;
- validadores Compose e gateway: contratos validos;
- suites de contratos existentes: 103 testes aprovados, incluindo os 45 casos
  do catalogo e os 58 casos do release-control.

A verificacao final confirmou novamente indice Git real vazio, HEAD
inexistente, nenhuma tag ou reflog, nenhuma S11, nenhum cache Python sob
`tools` e nenhum container, volume ou rede com prefixo `s10-`. Os YAML
encontrados somente sob dependencias/cache ignorados nao sao candidatos a
GitHub Actions do workspace.

**IN_PROGRESS — aguardando nova revisao do orquestrador**

---

## 14. Revisao do orquestrador — ciclo 1

> **Resultado:** `IN_PROGRESS — correcoes bloqueantes requeridas`  
> **Data:** `2026-07-28`

A stack efemera, os Maven, as persistencias e o banco produziram evidencias
uteis, mas a S10 ainda nao atende integralmente ao contrato. A S11 nao esta
autorizada.

### 14.1 Imagens Java e prova da stack nao correspondem ao codigo final

As tags locais aceitas na S08 foram sobrescritas durante a S10:

| Imagem | ID aceito na S08 | ID local atual |
|---|---|---|
| `abaronesa-emporio-backend:s08` | `sha256:eee2ff416170c9f2df9869fde29c894afdd170bca32bb2bcff716823549b4ee3` | `sha256:d86d361462ac47d90f1086e00880535b0fb676577a5ee6d404cf9c10f8bf6561` |
| `abaronesa-emporio-website-back:s08` | `sha256:cf6acf41ff5f67a43f4b32d2e2017548fb9224b154b07bf8d1b4400dc0d1eada` | `sha256:4d505b8273022dc452960cb1381f20257a049d364c5c12d942f02d0730ec1ff9` |

Os tamanhos tambem mudaram. Portanto, a afirmacao de que as imagens S08 foram
preservadas nao e verdadeira.

Mais importante: a imagem backend atual foi criada em
`2026-07-28T19:20:44-03:00`, enquanto
`backend/.../WhatsAppService.java` foi alterado em
`2026-07-28 19:25:37-03:00`. A stack `7/7 healthy` nao executou o contrato
final de fallback/validacao do WhatsApp.

Correcao obrigatoria:

- nao tentar reconstruir ou fingir recuperar os IDs historicos S08;
- documentar transparentemente a sobrescrita das tags locais;
- construir imagens novas e inequivocas a partir do codigo final:

```text
abaronesa-emporio-backend:s10
abaronesa-emporio-website-back:s10
```

- usar `VCS_REF=s10-local-validation` e `IMAGE_VERSION=s10-local`;
- atualizar o override efemero para as tags S10;
- repetir inspect dirigido e a stack completa;
- provar que a imagem backend foi criada depois de todos os arquivos
  autorizados do backend;
- provar o fallback WhatsApp pela API/backend em execucao, sem porta 3001
  publicada;
- registrar novos IDs, datas e tamanhos;
- nao declarar novamente que as tags S08 locais foram preservadas.

### 14.2 Gateway diverge do roteamento e dos controles prescritos

Achados no arquivo real:

- ERP usa `location /ws/`, mas o contrato exige que `/ws` e seus subpaths
  cheguem a `backend:8080`; a requisicao exata `/ws` cai hoje no frontend;
- os blocos WebSocket nao definem os timeouts finitos prescritos;
- nao ha `client_max_body_size` coerente com os limites distintos dos
  backends;
- os headers de seguranca basicos prescritos nao foram configurados;
- o regex publica `/actuator/` nos dois hosts, rota nao autorizada pela matriz
  da task;
- o website publica OAuth sem decisao/necessidade registrada;
- `X-Forwarded-Proto` pode ficar vazio quando a requisicao local nao traz o
  header do proxy do host;
- as provas HTTP registradas consultaram somente `/healthz`, que e respondido
  pelo proprio gateway, e nao comprovam os upstreams;
- o validador verifica tokens globalmente, mas nao associa host, path,
  upstream, headers e controles ao bloco correto.

Correcao obrigatoria:

- fazer `/ws` e subpaths funcionarem nos dois hosts;
- adicionar timeouts de WebSocket/SSE e limites de body coerentes;
- adicionar headers de seguranca compatíveis com SPA/WebSocket;
- remover rotas extras nao contratadas ou justificar/contratar antes;
- normalizar forwarded proto com fallback seguro;
- testar host desconhecido, control reservado, frontends, APIs/midia e ao
  menos um fluxo WebSocket/SSE ou double deterministico de upstream;
- provar que `/api/whatsapp/**` passa pelo backend, nunca diretamente pelo
  servico Node;
- fortalecer o validador e criar mutantes independentes para cada invariante.

Dois testes agregados e cinco mutantes nao cobrem a matriz de gateway exigida
pela S10.

### 14.3 Compose, matriz de ambiente e harness estao subvalidados

`ops/env/.env.example` nao contem a matriz exigida. Estao ausentes, entre
outros:

- limites de memoria/CPU/PIDs por servico;
- URLs publicas e redirect OAuth;
- CORS/WebSocket;
- SMTP;
- Uber;
- chaves de sincronizacao;
- Firebase credentials path;
- variaveis publicas dos frontends;
- variaveis opcionais consumidas pelos backends.

O Compose tambem nao encaminha varios desses contratos aos consumidores. As
URLs permanecem hardcoded e o override S10 nao as substitui por `.invalid`,
contrariando o harness prescrito. `MANAGEMENT_HEALTH_MAIL_ENABLED` fica sempre
`false`, inclusive se SMTP vier a ser habilitado.

O validador Compose atual nao protege de forma suficiente:

- memberships de todos os servicos;
- quatro volumes e mounts exatos;
- health paths e dependencias exatas;
- init, stop, security options, logs e valores de recursos;
- Docker socket, privileged, host mode e publicacoes indevidas;
- allowlist de environment por servico;
- nomes runtime/Firebase/WhatsApp;
- os dez artefatos legados completos;
- override estritamente local;
- isolamento e prefixo de limpeza.

Correcao obrigatoria:

- completar `.env.example` por consumidor, sem valor real;
- parametrizar URLs publicas com defaults canonicos e override `.invalid`;
- encaminhar somente as variaveis realmente consumidas a cada servico;
- incluir contratos opcionais SMTP/Uber/sync/Firebase sem vazar segredo;
- tornar o health SMTP coerente com `MAIL_ENABLED`;
- fortalecer o validador estrutural sobre o JSON resolvido;
- criar mutantes separados para todas as familias acima;
- repetir config, testes e stack com o novo override.

### 14.4 Inicializador PostgreSQL nao detecta drift preexistente

O teste fresco prova criacao e repeticao no estado correto, mas o script:

- aceita role preexistente sem verificar `LOGIN`;
- aceita banco preexistente sem verificar owner;
- nao falha se um estado preexistente contradiz o contrato.

Como a task exige ownership e isolamento, o inicializador deve validar o
estado tanto depois de criar quanto quando encontra objetos existentes.

Correcao obrigatoria:

- validar roles existentes e ownership dos dois bancos;
- falhar fechado e sanitizado diante de owner/role divergente;
- nao corrigir ou rotacionar senha silenciosamente;
- adicionar testes reais ou mutantes para role/banco preexistente divergente;
- repetir idempotencia e isolamento no estado correto.

### 14.5 Relatorio e estado

Atualizar este relatorio com:

- comandos exatos e codigos do ciclo corretivo;
- IDs/datas/tamanhos das imagens S10;
- digest exato da base do gateway;
- matriz de rotas e probes de upstream;
- matriz de environment por consumidor;
- quantidade final de testes/mutantes;
- falhas e repeticoes;
- limpeza e estado protegido;
- resposta individual a cada item desta revisao.

Os cinco gates permanecem **provisoriamente fechados no arquivo de trabalho**,
mas a decisao do orquestrador ainda nao os aceita. Se qualquer correcao nao
passar, reabra o gate correspondente antes da proxima revisao.

Estado da slice apos esta revisao:

```text
IN_PROGRESS — correcoes bloqueantes do ciclo 1
```

## 13. Arquivos alterados

- backend: `SecurityConfig.java`, `WhatsAppService.java`,
  `application-prod.properties` e dois testes de contrato;
- website backend: `SecurityConfig.java`, `CorsConfig.java`,
  `WebSocketConfig.java`, `FirebaseConfig.java`, `application-prod.properties`
  e tres testes de contrato;
- `ops/compose/compose.prod.yml` e override local S10;
- `ops/gateway/Dockerfile`, `.dockerignore`, `nginx.conf` e rota;
- `ops/db/init-databases.sh` e `ops/env/.env.example`;
- validadores/testes em `tools/compose` e `tools/gateway`;
- catalogo, teste do catalogo e documentacao release-control;
- documentacao da stack e este relatorio;
- dez prototipos listados na Secao 2 removidos individualmente.

As verificacoes finais confirmaram indice real vazio, HEAD/tag/reflog
inexistentes, nenhum workflow candidato no workspace, nenhum cache Python
novo sob `tools`, e nenhum container/volume `s10-*`.

**IN_PROGRESS — aguardando revisao do orquestrador**

---

## 16. Revisao do orquestrador — ciclo 2

> **Resultado:** `IN_PROGRESS — uma correcao bloqueante requerida`  
> **Data:** `2026-07-28`

As correcoes das subsecoes 14.1, 14.2, 14.4 e 14.5 estao comprovadas. A
paridade das novas imagens Java S10, a matriz do gateway, o drift PostgreSQL e
o estado protegido foram aceitos nesta revisao. A S11 ainda nao esta
autorizada porque resta uma divergencia na matriz de ambiente da subsecao
14.3.

### 16.1 Chave de sincronizacao nao chega a todos os consumidores reais

O Compose entrega ao backend ERP:

```text
ESPRESSO_SYNC_API_KEY=${ERP_WEBSITE_SYNC_KEY:-}
```

Essa variavel resolve `espresso.sync.api-key`, mas o codigo final tambem
consome `website.sync.api-key`:

- `ConfigSeeder` injeta `${website.sync.api-key:}` e usa esse valor para
  semear a configuracao persistida `espresso.sync.api-key`;
- `ClienteRefSyncService`, `ClientesAnalyticsController` e
  `DashboardGamificacaoController` leem `website.sync.api-key`;
- alguns desses consumidores possuem fallback `default-key-for-dev`.

Assim, em um banco novo, `ERP_WEBSITE_SYNC_KEY` pode estar configurada no
Compose e ainda assim o seeder persistir `espresso.sync.api-key` vazia. Nos
consumidores com fallback, a ausencia da propriedade de runtime tambem
mantem uma chave de desenvolvimento ativa. O validador atual exige apenas
`ESPRESSO_SYNC_API_KEY` e, por isso, os 32 mutantes nao detectam a
divergencia.

Correcao obrigatoria e delimitada:

1. no servico `backend`, encaminhar tambem
   `WEBSITE_SYNC_API_KEY: ${ERP_WEBSITE_SYNC_KEY:-}`; manter
   `ESPRESSO_SYNC_API_KEY` com a mesma origem enquanto ambos os nomes forem
   consumidores reais;
2. atualizar a matriz em `ops/env/.env.example` e
   `PRODUCTION_STACK.md`, deixando explicito que um unico segredo operacional
   `ERP_WEBSITE_SYNC_KEY` alimenta os dois nomes de propriedade do ERP e
   `WEBSITE_ERP_SYNC_KEY` no website backend;
3. fortalecer a allowlist e os requisitos do validador Compose para exigir
   os dois nomes no backend e igualdade entre os tres valores resolvidos;
4. adicionar mutantes independentes para ausencia de
   `WEBSITE_SYNC_API_KEY`, valor divergente entre os aliases e remocao do
   alias do website backend;
5. repetir somente as validacoes afetadas e uma prova dirigida com banco
   efemero novo: sem revelar o segredo, demonstrar que a configuracao
   persistida `espresso.sync.api-key` ficou nao vazia e correspondeu ao valor
   efemero, e que uma rota protegida por `website.sync.api-key` rejeita chave
   incorreta e aceita a chave efemera;
6. como a correcao deve ficar restrita a Compose, documentacao, validador e
   teste, nao reconstruir as imagens Java nem repetir suites nao afetadas.

Se o executor identificar que a injecao adicional nao resolve a propriedade
Spring como descrito, deve parar e registrar a evidencia antes de alterar
codigo Java, pois isso expandiria o escopo autorizado.

### 16.2 Registro e estado

A resposta corretiva deve ser acrescentada ao final deste arquivo, depois
desta revisao, contendo arquivos alterados, comandos, codigos de saida,
mutantes, prova sanitizada e estado final. Preservar as evidencias anteriores
e nao reordenar as secoes historicas.

Estado da slice:

```text
IN_PROGRESS — correcao bloqueante do ciclo 2
```

## 17. Resposta as correcoes do ciclo 2

Estado: **IN_PROGRESS — aguardando nova revisao do orquestrador**

### 17.1 Correcao delimitada

O backend em `ops/compose/compose.prod.yml` agora recebe os dois aliases
consumidos pelo codigo final a partir da mesma origem:

```text
ESPRESSO_SYNC_API_KEY <- ERP_WEBSITE_SYNC_KEY
WEBSITE_SYNC_API_KEY  <- ERP_WEBSITE_SYNC_KEY
```

O alias `WEBSITE_ERP_SYNC_KEY` do `website_back` foi preservado com essa mesma
origem. `ops/env/.env.example` e `PRODUCTION_STACK.md` documentam
`ERP_WEBSITE_SYNC_KEY` como o unico segredo operacional que alimenta os tres
aliases de runtime.

O validador Compose passou a:

- incluir `WEBSITE_SYNC_API_KEY` na allowlist e nos campos obrigatorios do
  backend;
- exigir a presenca de `ESPRESSO_SYNC_API_KEY`, `WEBSITE_SYNC_API_KEY` e
  `WEBSITE_ERP_SYNC_KEY`;
- exigir igualdade entre os tres valores resolvidos.

Foram adicionados tres mutantes independentes: ausencia do novo alias do
backend, divergencia entre aliases e ausencia do alias do `website_back`. A
suite Compose agora cobre 35 mutantes: 28 estruturais, quatro do inicializador
PostgreSQL e tres do override.

### 17.2 Prova sanitizada com banco novo

A prova utilizou a imagem Java S10 existente, sem rebuild:

```text
abaronesa-emporio-backend:s10
sha256:0c03efce1956ab05df2a83eea131135deacbf221d1f6b7e6f21fda4ec8f97d2d
```

Foram criados rede, volume, PostgreSQL 16 e backend exclusivamente efemeros,
com nomes prefixados por `s10c2-`. PostgreSQL nao teve porta publicada; o
backend foi exposto somente em `127.0.0.1:18123` durante os probes. O segredo
foi gerado em memoria por `openssl rand`, usado nos dois aliases do backend e
nunca impresso.

Resultado sanitizado:

```text
postgres_initialized=true
backend_healthy=true
persisted_sync_key_nonempty=true
persisted_sync_key_matches_ephemeral=true
wrong_key_http=401
ephemeral_key_http=200
```

A verificacao persistida consultou `configuracoes` pela chave
`espresso.sync.api-key` e emitiu somente os dois booleanos acima. A rota
`GET /api/admin/gamificacao/saldos?ids=1`, que consome
`website.sync.api-key`, rejeitou uma chave deliberadamente incorreta e aceitou
a chave efemera. Isso tambem confirma que `WEBSITE_SYNC_API_KEY` foi resolvida
pelo Spring sem exigir alteracao Java.

### 17.3 Comandos, codigos e falhas intermediarias

CWD de todos os comandos:

```text
/home/gregorio/git/baronesa/emporio
```

| Comando exato ou forma sanitizada | Exit | Resultado |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -v` | 0 | 4 metodos; 35 mutantes aprovados |
| `PYTHONDONTWRITEBYTECODE=1 python3 tools/compose/validate_compose.py` | 0 | `Compose contract valid` |
| `ERP_WEBSITE_SYNC_KEY=<efemero> ... python3` resolvendo `fixture_env()` e `resolved(env)` | 0 | tres aliases presentes, nao vazios e iguais |
| harness shell dirigido com `docker network create`, `docker volume create`, `docker run postgres:16-alpine`, `docker run abaronesa-emporio-backend:s10`, `psql` e `curl` | 0 final | prova sanitizada da Secao 17.2 |
| `docker rm -f s10c2-backend s10c2-postgresql`, `docker volume rm s10c2-sync-db` e `docker network rm s10c2-sync-net` via trap | 0 | limpeza dirigida |

Duas falhas intermediarias foram preservadas:

1. a primeira execucao do harness minimo encerrou o backend porque omitiu
   `APP_CORS_ALLOWED_ORIGINS`; a repeticao incluiu a propriedade obrigatoria,
   sem mudar produto;
2. a segunda execucao iniciou banco e backend, mas a consulta de evidencia
   tentou usar substituicao `psql` dentro de `-c` e falhou com erro de sintaxe;
   a repeticao comparou o valor apenas em memoria e imprimiu booleanos.

### 17.4 Arquivos alterados e estado protegido

Arquivos alterados neste ciclo:

- `ops/compose/compose.prod.yml`;
- `ops/env/.env.example`;
- `docs/infrastructure/deployment/compose/PRODUCTION_STACK.md`;
- `tools/compose/validate_compose.py`;
- `tools/compose/tests/test_compose.py`;
- este relatorio.

Nenhum arquivo Java, imagem Java, task, tracker ou evidencia historica foi
alterado. A verificacao final confirmou:

- indice Git real vazio;
- HEAD inexistente, nenhuma tag e nenhum reflog;
- nenhuma S11 e nenhum workflow candidato no workspace;
- nenhum cache Python sob `tools`;
- nenhum container, volume ou rede `s10c2-*`;
- nenhum commit, tag, push, publicacao, prune ou acesso a `.env.production`,
  GitHub, GHCR, DNS ou VPS.

**IN_PROGRESS — aguardando nova revisao do orquestrador**

---

## 18. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-29`

A resposta do ciclo 2 fecha o unico bloqueio remanescente da S10.

Evidencias aceitas:

- `ESPRESSO_SYNC_API_KEY` e `WEBSITE_SYNC_API_KEY` chegam ao backend ERP a
  partir de `ERP_WEBSITE_SYNC_KEY`;
- `WEBSITE_ERP_SYNC_KEY` chega ao website backend a partir da mesma origem;
- o validador exige presenca e igualdade dos tres aliases;
- tres mutantes independentes protegem ausencia e divergencia;
- a prova com banco novo confirmou chave persistida nao vazia e igual ao
  valor efemero, sem reproduzi-lo;
- a rota consumidora rejeitou chave incorreta com `401` e aceitou a chave
  efemera com `200`;
- nenhum codigo ou imagem Java foi alterado;
- indice, HEAD, tags, reflog e fronteiras externas permaneceram protegidos;
- nenhum recurso `s10c2-*` permaneceu.

As correcoes anteriores de paridade das imagens S10, gateway, Compose,
persistencias, banco, health e readiness permanecem validas. Os seis
componentes comerciais estao tecnicamente `ready`; isso ainda nao significa
que exista candidato publicado, release global ou deploy de producao.

Decisao:

```text
S10 ACCEPTED — 29/07/2026
S11 autorizada
```
