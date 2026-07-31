# S10 — Gateway canonico, Compose de producao e persistencias

> **Estado:** `ACCEPTED` — 29/07/2026  
> **Tipo:** infraestrutura de runtime, gateway, banco, persistencia e integracao local  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S09 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de componentes:** [release-control/README.md](../../release-control/README.md)  
> **Relatorio de saida:** `S10-gateway-compose-producao-persistencias.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a revisao final da S09;
3. as secoes `Arquitetura de producao`, `Gateway interno`, `Docker Compose de
   producao`, `Persistencia`, `PostgreSQL`, `Health checks` e `Estrutura de
   arquivos alvo` da arquitetura aprovada;
4. `ops/releases/components.yml`, schema, validador e testes;
5. os Dockerfiles e contratos de health aprovados nas S08 e S09;
6. todos os Compose, Nginx e scripts de inicializacao atuais sob `deploy/` e
   `ops/`;
7. todos os `application-prod.properties` e `.env.example`, sem abrir
   `.env.production`;
8. `website_back/.../FirebaseConfig.java` e seus testes existentes;
9. a documentacao das imagens Java e Node.

Nao altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

Esta slice cria o gateway e a composicao comercial local/producao. Nao cria
CI, manifesto, candidato, release, scripts transacionais de deploy/rollback,
`release_control`, Nginx do host ou qualquer recurso na VPS.

## Objetivo observavel

Ao final:

- existe exatamente um Compose comercial canonico em
  `ops/compose/compose.prod.yml`;
- nao existe Compose legado concorrente ou operacionalmente ambiguo;
- o Compose canonico nao contem `build:`, tag ou `latest`;
- todas as seis imagens comerciais sao exigidas por referencia completa com
  digest;
- PostgreSQL usa base imutavel, nao publica porta e possui dois bancos com
  usuarios de aplicacao distintos;
- somente o gateway publica porta, em `127.0.0.1:8120:8080`;
- gateway, aplicacoes e banco usam redes com isolamento explicito;
- nenhum servico monta Docker socket;
- health checks, dependencias, limites, reinicio e logs sao declarados;
- uploads ERP, uploads website, sessao WhatsApp e PostgreSQL usam volumes
  nomeados;
- XML fiscal ERP persiste dentro do volume aprovado de uploads;
- inicializacao dos bancos e idempotente, valida nomes e nao registra senhas;
- o gateway versionado roteia os dois dominios sem expor WhatsApp diretamente;
- OAuth, APIs, midia, WebSocket/SSE e headers de proxy possuem contrato;
- o gateway possui imagem imutavel, nao-root, health e teste;
- o website backend consegue iniciar sem Firebase quando a integracao
  opcional esta explicitamente desabilitada;
- Firebase habilitado sem credencial valida continua falhando fechado;
- o backend ERP resolve o WhatsApp pela URL interna do Compose quando a
  configuracao persistida estiver vazia, sem impedir override pela UI;
- CORS e WebSocket dos dois backends aceitam os dominios canonicos por
  configuracao e nao dependem de allowlists legadas hardcoded;
- a stack completa e executada localmente com imagens `:s08`/`:s09`, gateway
  `:s10`, dados e credenciais estritamente efemeros;
- health integrado e persistencia por recriacao sao comprovados;
- nenhum dado ou container efemero permanece ao final;
- exatamente os cinco gates restantes sao fechados;
- o catalogo passa inclusive com `--require-release-ready`;
- nenhuma imagem e publicada e nenhuma producao e acessada.

## 1. Artefatos canonicos

Criar:

```text
ops/compose/compose.prod.yml
ops/gateway/Dockerfile
ops/gateway/nginx.conf
ops/gateway/conf.d/emporio.conf
ops/db/init-databases.sh
ops/env/.env.example
docs/infrastructure/deployment/compose/PRODUCTION_STACK.md
```

Podem ser criados validadores, testes e um harness local sob:

```text
tools/compose/
tools/gateway/
ops/compose/testing/
```

O harness deve ser inequivocamente local/efemero e nao pode virar uma segunda
definicao de producao.

## 2. Saneamento dos prototipos conflitantes

Depois de registrar no relatorio o inventario e a substituicao individual,
remover exatamente os artefatos legados abaixo:

```text
deploy/docker-compose.yml
deploy/infra/docker-compose.yml
deploy/nginx/conf.d/emporio.conf
deploy/nginx/emporio-erp.conf.template
deploy/nginx/emporio-website.conf.template
deploy/nginx/nginx.conf
ops/compose/docker-compose.emporio.yml
ops/compose/docker-compose.emporio-website.yml
ops/scripts/init-multiple-dbs.sh
ops/deploy/deploy-tenant.sh
```

Motivos contratuais:

- `build:` em producao;
- `latest` e tags mutaveis;
- porta PostgreSQL publicada;
- proxy ocupando `80/443`;
- Traefik com Docker socket;
- dois Compose comerciais parciais e divergentes;
- variaveis incorretas;
- script de banco nao idempotente e com um unico usuario;
- deploy manual por tenant/tag incompatível com release global.

Nao usar remocao recursiva, glob amplo ou limpeza de diretorio. Nao remover
arquivos fora da lista. Diretorios vazios podem permanecer. Como ainda nao ha
`HEAD`, registrar explicitamente que esses prototipos nao serao recuperaveis
por historico Git local.

Nao tocar em:

```text
ops/env/.env.production
ops/db/db-sync-to-prod.sh
ops/db/reset-sequences-safe.sql
ops/db/reset-sequences.sql
ops/manual/**
```

## 3. Compose comercial canonico

Arquivo unico:

```text
ops/compose/compose.prod.yml
```

Servicos, com estes IDs exatos:

```text
postgresql
backend
website_back
frontend
website_front
whatsapp_service
gateway
```

### 3.1 Regras gerais

- formato atual do Docker Compose, sem chave `version`;
- nenhum `build:`;
- nenhum `container_name`;
- nenhum `privileged`;
- nenhum `network_mode: host`;
- nenhum Docker socket ou dispositivo do host;
- nenhuma porta publicada por PostgreSQL, backends, frontends ou WhatsApp;
- somente gateway usa `ports`;
- nenhuma tag `latest`, `main`, `sha-*` ou tag sem digest;
- nenhuma imagem recebe default mutavel;
- referencias comerciais vem de variaveis obrigatorias;
- `restart: unless-stopped`;
- `init: true` quando tecnicamente aplicavel;
- `stop_grace_period` explicito;
- health check explicito em todos os sete servicos;
- `depends_on` usa `condition: service_healthy` quando a dependencia participa
  do startup;
- rotacao `json-file` com `max-size` e `max-file`;
- limites de memoria, CPU e PIDs efetivos no Docker Compose nao-Swarm;
- nao confiar somente em `deploy.resources`;
- `security_opt: ["no-new-privileges:true"]` quando compativel;
- `read_only`/`tmpfs` somente quando testados e sem impedir entrypoints;
- timezone uniforme `America/Sao_Paulo`;
- nenhuma variavel imprime segredo em comando ou health;
- nenhuma aplicacao recebe o conjunto inteiro do `.env`;
- usar `environment` explicito; nao usar `env_file` indiscriminado.

Pode usar anchors YAML para reduzir repeticao, desde que o Compose resolvido
continue legivel e validavel.

### 3.2 Imagens imutaveis

Exigir:

```text
POSTGRES_IMAGE
BACKEND_IMAGE
WEBSITE_BACK_IMAGE
FRONTEND_IMAGE
WEBSITE_FRONT_IMAGE
WHATSAPP_IMAGE
GATEWAY_IMAGE
```

As seis imagens comerciais devem obedecer:

```text
ghcr.io/greggorio/abaronesa-emporio-<componente>@sha256:<64 hex>
```

PostgreSQL deve usar tag completa de PostgreSQL 16 Alpine e digest imutavel.
Resolver e registrar a tag/digest `linux/amd64` efetivamente usada no teste.

O Compose nao consegue validar sozinho regex de referencias. O validador
local desta slice deve rejeitar valor vazio, tag sem digest, digest curto,
namespace incorreto, `latest` e imagem trocada entre componentes.

### 3.3 Portas

Contrato:

```text
gateway container: 8080
gateway host:      127.0.0.1:8120
backend:           8080 interno
website_back:      8085 interno
frontend:          80 interno
website_front:     80 interno
whatsapp_service:  3001 interno
postgresql:        5432 interno
```

O mapeamento deve ser parametrizavel apenas para o harness efemero, preservando
`8120` como default canonico:

```text
127.0.0.1:${GATEWAY_LOOPBACK_PORT:-8120}:8080
```

O validador deve provar que o host e sempre `127.0.0.1`, mesmo quando a porta
de teste muda. `0.0.0.0`, portas curtas e publicacao adicional falham.

A disponibilidade real de `8120` na VPS continua pre-condicao da futura slice
de bootstrap do host. O gate atual fecha pela topologia, bind loopback e prova
operacional local; isso nao autoriza assumir a porta livre em producao.

## 4. Redes

Criar pelo menos:

```text
emporio-app
emporio-db
```

Regras:

- `emporio-db` e `internal: true`;
- somente PostgreSQL e os dois backends entram em `emporio-db`;
- PostgreSQL nao entra na rede de frontend/gateway;
- os dois backends entram nas redes necessarias;
- frontends, WhatsApp e gateway entram somente em `emporio-app`;
- gateway resolve servicos por seus IDs canonicos;
- aliases nao podem mascarar nome legado;
- nao usar rede externa preexistente;
- nomes reais de rede podem ser parametrizados para isolamento do harness,
  mantendo defaults canonicos documentados.

Os backends e o WhatsApp precisam de egress futuro. Nao marcar toda a rede de
aplicacao como `internal`.

## 5. Persistencias

Volumes canonicos:

```text
emporio-postgres-data
emporio-backend-uploads
emporio-website-uploads
emporio-whatsapp-session
```

Permitir nomes alternativos somente por variaveis do harness efemero, com
defaults acima.

Mapeamentos:

```text
postgresql      -> /var/lib/postgresql/data
backend         -> /app/uploads
website_back    -> /app/uploads
whatsapp_service -> /data/session
```

Para nao criar um quinto volume fiscal, configurar:

```text
NFE_XML_PATH=/app/uploads/nfe/xmls
```

Schemas continuam somente leitura dentro da imagem em `/app/nfe/schemas`.

Provar, com volumes efemeros de nomes unicos:

- marker PostgreSQL sobrevive a recriacao do container;
- marker em `/app/uploads` do backend sobrevive;
- marker em `/app/uploads` do website backend sobrevive;
- marker em `/data/session` do WhatsApp sobrevive;
- usuario final consegue escrever nos paths;
- codigo, schemas e outros paths permanecem nao gravaveis conforme contratos
  das imagens;
- `docker compose down` sem `-v` preserva;
- limpeza final usa `down -v` somente no projeto/prefixo efemero validado.

Nunca usar os nomes canonicos de producao nos testes locais se eles ja
existirem. O harness deve gerar prefixo exclusivo, verificar o alvo antes de
remover e rejeitar valor vazio ou sem prefixo `s10-`.

## 6. PostgreSQL e bancos

Usar um cluster PostgreSQL 16 com:

```text
POSTGRES_ADMIN_USER
POSTGRES_ADMIN_PASSWORD
ERP_DB_NAME
ERP_DB_USER
ERP_DB_PASSWORD
WEBSITE_DB_NAME
WEBSITE_DB_USER
WEBSITE_DB_PASSWORD
```

Regras:

- `POSTGRES_DB=postgres`;
- administrador nao e usuario de aplicacao;
- usuarios ERP e website sao distintos entre si e do administrador;
- bancos ERP e website sao distintos;
- cada banco pertence ao seu usuario de aplicacao;
- usuario ERP nao conecta no banco website;
- usuario website nao conecta no banco ERP;
- nomes aceitam somente identificadores PostgreSQL seguros definidos pelo
  contrato;
- passwords podem conter espacos/metacaracteres e nunca sao interpoladas como
  SQL cru;
- script usa `psql` com `ON_ERROR_STOP`;
- criacao de roles/bancos e grants e idempotente;
- execucao repetida nao falha nem troca senha silenciosamente;
- script atua automaticamente apenas na primeira criacao do volume;
- repeticao manual controlada e testada;
- nenhum password e impresso;
- nenhuma porta do banco e publicada.

Adicionar testes estaticos e um teste real contra container PostgreSQL
efemero. Confirmar ownership e isolamento com consultas dirigidas, sem
persistir dumps ou credenciais.

## 7. Variaveis dos servicos

Atualizar `ops/env/.env.example` como matriz canonica, sem valor real.

Separar:

- referencias de imagem/release;
- nomes de volumes/redes;
- limites de recursos;
- credenciais PostgreSQL;
- segredo compartilhado entre backends;
- OAuth Google;
- Uber opcional;
- SMTP opcional;
- sincronizacao ERP/website;
- URLs publicas;
- CORS/WebSocket;
- Firebase opcional;
- runtime dos frontends;
- WhatsApp.

### 7.1 Backend ERP

Usar os nomes que Spring realmente consome, incluindo:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
INTEGRATION_SYSTEM_TOKEN_SECRET
JAVA_TOOL_OPTIONS
NFE_SCHEMA_PATH=/app/nfe/schemas
NFE_XML_PATH=/app/uploads/nfe/xmls
STORE_UPLOAD_DIR=/app/uploads
STORE_UPLOAD_CATEGORIA_DIR=/app/uploads/categorias
STORE_UPLOAD_SUBCATEGORIA_DIR=/app/uploads/subcategorias
STORE_UPLOAD_CERTIFICADO_DIR=/app/uploads/certificados
STORE_UPLOAD_PRODUTO_DIR=/app/uploads/produtos
STORE_UPLOAD_SIGNAGE_AI_DIR=/app/uploads/signage/ai
STORE_UPLOAD_SIGNAGE_DIR=/app/uploads/signage
```

Fornecer por ambiente Compose as URLs publicas corretas para impedir que
defaults legados `espresso*` sejam usados. `JAVA_OPTS` e proibido: as imagens
Java consomem `JAVA_TOOL_OPTIONS`.

O backend chama WhatsApp pelo ID interno. A secao 8 autoriza a ponte minima
necessaria porque o codigo atual nao consome diretamente a variavel Compose.
Nao expor o WhatsApp para resolver essa integracao.

### 7.2 Website backend

Usar:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
INTEGRATION_SYSTEM_TOKEN_SECRET
JAVA_TOOL_OPTIONS
ERP_API_URL=http://backend:8080
STORE_UPLOAD_GALERIA_DIR=/app/uploads/galeria
STORE_UPLOAD_THEME_ASSETS_DIR=/app/uploads/theme-assets
STORE_UPLOAD_ANDROID_ASSETS_DIR=/app/uploads/android-assets
STORE_UPLOAD_ANDROID_PRIVATE_DIR=/app/uploads/android-private
```

Configurar por ambiente as URLs/CORS/WebSocket e a chave de sincronizacao.
Nao usar o nome incorreto `ERP_API_BASE_URL`.

### 7.3 Frontends

```text
frontend:
  VITE_BASE_API_URL

website_front:
  VITE_ERP_API_URL
  VITE_WEBSITE_API_URL
```

As URLs sao publicas e entram somente em runtime. Nao usar nomes legados.

### 7.4 WhatsApp

```text
NODE_ENV=production
PORT=3001
SESSION_DIR=/data/session
BASE_COUNTRY_CODE
```

O Compose canonico nao define `WHATSAPP_INITIALIZATION_DISABLED`. Essa chave
somente pode aparecer no override/harness efemero.

## 8. Pontes minimas de runtime de producao

O inventario confrontado pelo orquestrador encontrou tres contratos que
Compose/env, sozinhos, nao conseguem cumprir:

1. URL WhatsApp hoje existe somente na tabela de configuracoes e e semeada
   vazia;
2. CORS dos backends contem allowlists legadas hardcoded;
3. Firebase do website inicia incondicionalmente a partir do tema.

Corrigir somente essas fronteiras, com testes. Nao alterar logica comercial.

### 8.1 WhatsApp interno no backend ERP

Arquivos adicionais autorizados:

```text
backend/src/main/java/com/baronesa/emporio/service/WhatsAppService.java
backend/src/main/resources/application-prod.properties
backend/src/test/**/WhatsAppService*
```

Contrato:

- propriedade `app.whatsapp.service-url`, lida de
  `WHATSAPP_SERVICE_URL`;
- default de producao `http://whatsapp_service:3001`;
- valor nao vazio persistido na tabela continua tendo precedencia, preservando
  configuracao pela UI;
- valor persistido ausente ou vazio usa a propriedade de runtime;
- URL final deve ser HTTP(S), sem userinfo, query ou fragment;
- URL invalida falha fechado antes da requisicao;
- logs nao reproduzem URL completa, payload ou resposta;
- nenhuma chamada ocorre no startup;
- testes usam servidor HTTP loopback efemero ou cliente injetado, sem WhatsApp
  real;
- provar `/status` pelo backend no harness com o servico fake/desabilitado,
  sem expor a porta 3001.

Nao alterar `ConfigSeeder` ou sobrescrever valor existente no banco.

### 8.2 CORS e WebSocket configuraveis

Arquivos adicionais autorizados:

```text
backend/src/main/java/com/baronesa/emporio/config/SecurityConfig.java
backend/src/test/**/Cors*
website_back/src/main/java/com/baronesa/website/config/SecurityConfig.java
website_back/src/main/java/com/baronesa/website/config/CorsConfig.java
website_back/src/main/java/com/baronesa/website/config/WebSocketConfig.java
website_back/src/test/**/Cors*
website_back/src/test/**/WebSocket*
```

Contrato:

- uma propriedade canonica `app.cors.allowed-origins`;
- uma propriedade canonica `app.websocket.allowed-origins` quando aplicavel;
- Compose fornece explicitamente os dois dominios HTTPS canonicos;
- desenvolvimento local pode manter defaults documentados fora do profile
  prod;
- remover dominios comerciais legados hardcoded das configuracoes ativas;
- nenhuma allowlist de producao contem `*`;
- `allowCredentials=true` nunca e combinado com wildcard;
- origins sao normalizadas, sem path/query/fragment, duplicata ou string
  vazia;
- schemes permitidos: `http`/`https` e os schemes locais nativos ja exigidos
  pelo produto;
- valor invalido interrompe startup com mensagem sanitizada;
- configuracoes CORS duplicadas do website nao podem divergir;
- WebSocket usa a mesma origem publica aprovada, sem `*`;
- testes provam os dois dominios, rejeicao de origem alheia e falha de
  configuracao insegura.

Esta correcao nao cria autenticacao nova nem torna endpoint publico.

### 8.3 Firebase opcional e startup limpo

O codigo atual do website backend inicializa Firebase incondicionalmente a
partir do tema do banco. Um banco novo nao contem o path exigido e, portanto,
uma stack efemera de producao nao consegue ficar saudavel. Corrigir somente
essa fronteira operacional.

Arquivos adicionais autorizados:

```text
website_back/src/main/java/com/baronesa/website/config/FirebaseConfig.java
website_back/src/main/resources/application-prod.properties
website_back/src/test/**/FirebaseConfig*
```

Contrato:

- propriedade `app.firebase.enabled`, lida de
  `APP_FIREBASE_ENABLED`, default `false` em producao;
- propriedade de path, lida de `FIREBASE_CREDENTIALS_PATH`, com default
  `/app/uploads/android-private/firebase-adminsdk.json`;
- quando desabilitado, nao consulta tema, filesystem ou Firebase;
- quando habilitado, path ausente, ilegivel ou credencial invalida interrompe
  o startup;
- erro/log nao reproduz path, conteudo, chave, tenant ou excecao sensivel;
- nao obter path secreto de payload mutavel do tema;
- nenhuma credencial entra em imagem, teste, exemplo ou relatorio;
- nao alterar endpoints ou logica de notificacao;
- adicionar testes focados para disabled, enabled valido e enabled invalido;
- executar `mvn -B verify` do website backend com valores efemeros.

O Compose monta o mesmo volume de uploads que contem o path default. Para
habilitar Firebase, o operador futuro deve provisionar o arquivo no volume e
somente entao definir `APP_FIREBASE_ENABLED=true`.

Se outro defeito de aplicacao impedir a stack, nao ampliar silenciosamente o
escopo. Registrar o bloqueio para decisao do orquestrador.

## 9. Gateway interno

Criar imagem:

```text
ops/gateway/Dockerfile
```

Contrato:

- Nginx Alpine com tag completa e digest imutavel;
- target `linux/amd64`;
- runtime sem Node/Java;
- processo final nao-root;
- listener interno `8080`;
- `USER` numerico nao zero;
- config e conteudo nao gravaveis pelo usuario;
- PID/cache/temp em paths explicitamente gravaveis ou `tmpfs`;
- logs em stdout/stderr;
- labels OCI por `VCS_REF` e `IMAGE_VERSION`;
- nenhum ARG adicional;
- `STOPSIGNAL SIGTERM`;
- health local exato, por exemplo `/healthz`;
- nenhum TLS, Certbot, segredo, dominio parametrizado ou Docker socket;
- `.dockerignore` se o contexto puder incluir material alheio;
- imagem local:

```text
abaronesa-emporio-gateway:s10
```

### 9.1 Roteamento

Hosts:

```text
emporio.abaronesa.net.br
erp-emporio.abaronesa.net.br
```

Website:

```text
/           -> website_front:80
/api/       -> website_back:8085
/media/     -> website_back:8085
/ws         -> website_back:8085
```

ERP:

```text
/                    -> frontend:80
/api/                 -> backend:8080
/media/               -> backend:8080
/ws                    -> backend:8080
/oauth2/               -> backend:8080
/login/oauth2/         -> backend:8080
```

Regras:

- preservar path e query sem duplicar/remover `/api` acidentalmente;
- configurar `Host`, `X-Real-IP`, `X-Forwarded-For`,
  `X-Forwarded-Proto` e `X-Forwarded-Host`;
- WebSocket usa HTTP/1.1, Upgrade/Connection e timeouts;
- SSE/streaming aplicavel desabilita buffering;
- limites de body correspondem aos limites dos backends;
- timeouts sao finitos e documentados;
- headers de seguranca basicos sem quebrar SPA/WebSocket;
- host desconhecido nao recebe aplicacao;
- `/healthz` do gateway nao consulta upstream;
- `/api/deployment-control/` e reservado ao Nginx do host/release control e
  deve ser rejeitado no gateway comercial;
- nao criar rota externa direta para `whatsapp_service`;
- `/api/whatsapp/**` segue pelo backend ERP e sua autenticacao;
- gateway nao conhece `release_control`.

O Nginx do host, TLS e prioridade real da rota de controle permanecem fora da
slice.

### 9.2 Testes

Provar:

- `nginx -t`;
- validador estatico do roteamento;
- mutantes para host/path/upstream incorretos;
- default host fechado;
- health independente;
- headers;
- WebSocket/SSE;
- ausencia de rota WhatsApp direta;
- ausencia de 80/443 no gateway;
- container nao-root;
- health `healthy`;
- gateway recebe requests apenas pelo bind loopback durante o teste.

## 10. Health e ordem da stack

Declarar:

```text
postgresql:      pg_isready
backend:         /actuator/health
website_back:    /actuator/health
frontend:        /healthz
website_front:   /healthz
whatsapp_service:/health/live
gateway:         /healthz
```

Ordem minima:

- backends aguardam PostgreSQL saudavel;
- website backend aguarda tambem backend ERP saudavel se seu startup realmente
  depender dele; se nao depender, documentar e evitar acoplamento artificial;
- gateway aguarda os cinco servicos comerciais saudaveis;
- frontends nao precisam aguardar backends para ficar saudaveis;
- backend nao precisa aguardar WhatsApp para iniciar;
- falha WhatsApp nao deve impedir health ERP, embora gateway exija a stack
  comercial completa no `up --wait`.

Executar `docker compose up -d --no-build --remove-orphans --wait` no harness
local. Nao usar `docker compose down` no fluxo futuro de deploy; `down -v`
somente e permitido na limpeza do projeto efemero S10 validado.

## 11. Harness de integracao efemero

O Compose de producao permanece imutavel. Para testes, usar override ou env
temporario que substitua somente:

- imagens GHCR por `:s08`, `:s09` e `gateway:s10`;
- nomes de volumes/redes por prefixo `s10-<aleatorio>`;
- porta loopback por uma porta livre;
- credenciais por valores efemeros fortes;
- URLs por `.invalid`;
- `WHATSAPP_INITIALIZATION_DISABLED=true`;
- limites, se necessario ao host local.

O override pode ser criado em diretorio temporario pelo harness. Se for
versionado, deve conter somente estrutura, nunca valor/credencial, e estar
marcado como teste.

Antes:

- verificar imagens locais S08/S09;
- construir somente gateway S10;
- verificar porta escolhida;
- verificar que nomes efemeros ainda nao existem;
- nao reutilizar volumes canonicos;
- nao acessar `.env.production`.

Durante:

- validar config resolvida;
- subir a stack completa;
- aguardar todos os healths;
- provar resolucao e isolamento das redes;
- consultar gateway por loopback com ambos os headers `Host`;
- testar rotas publicas sem operacao destrutiva;
- provar que PostgreSQL e demais servicos nao possuem bind no host;
- provar persistencias por recriacao;
- repetir o init de banco e confirmar idempotencia;
- inspecionar limites, logs, restart, networks e mounts;
- registrar somente asserts sanitizados.

Depois:

- coletar logs apenas quando necessario e sanitiza-los;
- remover containers/redes/volumes apenas do prefixo efemero;
- confirmar zero residue `s10-*`;
- preservar as cinco imagens S08/S09;
- preservar gateway `:s10`;
- nao executar prune.

## 12. Validadores e testes locais

Criar validacao fail-closed sem dependencia nova nao justificada. Cobrir:

- lista exata de servicos;
- ausencia de Compose concorrente;
- ausencia de `build`, `latest`, socket, porta indevida e `container_name`;
- imagens comerciais obrigatorias por digest;
- PostgreSQL exato por digest;
- loopback 8120;
- redes e memberships;
- volumes e mount paths;
- healths;
- dependencias;
- limites, logs e restart;
- environment allowlist por servico;
- nomes runtime corretos;
- Firebase;
- script SQL seguro/idempotente;
- gateway Dockerfile/config;
- remocao dos artefatos legados.

Adicionar mutantes negativos para as invariantes acima. Nao depender apenas de
busca de strings quando o Compose resolvido puder ser inspecionado.

Pode usar `docker compose config --format json` para validacao sem adicionar
PyYAML ao projeto.

## 13. Catalogo e gates

Apos todas as provas, atualizar juntos:

```text
ops/releases/components.yml
tools/releases/tests/test_catalog.py
docs/infrastructure/deployment/release-control/README.md
```

Fechar exatamente:

```text
WEBSITE_BACK_UPLOAD_PERSISTENCE
GATEWAY_CANONICAL_ARTIFACTS
GATEWAY_HEALTH_CHECK
GATEWAY_LOOPBACK_PORT
GATEWAY_TEST_COMMAND
```

Entao:

- `website_back` muda para `ready`;
- persistencia `/app/uploads` muda para `confirmed`;
- `gateway` muda para `ready`;
- build, teste e health do gateway ficam `confirmed`;
- todos os seis componentes comerciais ficam `ready`;
- `readiness_gates` global fica vazio;
- `python3 tools/releases/catalog.py validate --require-release-ready`
  termina com codigo `0`.

Readiness do catalogo significa que componentes possuem contratos tecnicos
para entrar no futuro pipeline. Nao significa que CI, manifesto, release,
deploy, backup ou VPS ja existam.

Se a stack completa, persistencia ou gateway nao passarem, nao fechar o gate
correspondente. Nao inventar novo gate sem registrar o achado no relatorio
para decisao do orquestrador.

## 14. Documentacao

Criar:

```text
docs/infrastructure/deployment/compose/PRODUCTION_STACK.md
```

Documentar:

- topologia, servicos, redes e portas;
- matriz de imagens por digest;
- matriz de variaveis por consumidor;
- volumes e ownership;
- bancos/roles e inicializacao;
- Firebase opcional;
- health e ordem;
- limites/logs/restart;
- gateway e rotas;
- como validar Compose sem segredo;
- como executar e limpar o harness;
- diferenca entre config de producao e override local;
- fronteira com Nginx do host;
- itens ainda nao implementados;
- manutencao de base/digest;
- criterio para fechar cada gate.

Atualizar a arquitetura aprovada somente se uma decisao operacional for
confirmada por esta slice. Nao reescrever o plano futuro de CI/deploy.

## 15. Validacoes obrigatorias

Registrar CWD, comando exato, codigo de saida, resultado e interpretacao:

### Gateway

- validador e mutantes;
- build `linux/amd64`;
- `nginx -t`;
- inspect/history sanitizados;
- usuario/permissoes;
- health;
- rotas.

### Compose

- validador e mutantes;
- `docker compose config`;
- config JSON resolvida;
- verificacao de referencias por digest;
- stack completa `up --wait`;
- todos os healths;
- portas, networks, mounts, recursos, logs e restart;
- persistencia por recriacao;
- limpeza dirigida.

### Banco

- testes estaticos;
- criacao real dos dois roles/bancos;
- ownership e isolamento;
- repeticao idempotente;
- persistencia apos recriacao;
- zero password em saida.

### Backends

```bash
cd backend && mvn -B verify
cd website_back && mvn -B verify
```

Com valores estritamente efemeros e testes focados WhatsApp/CORS/WebSocket e
Firebase.

O comando equivalente para cada diretorio deve ser registrado separadamente;
nao executar a linha combinada literalmente.

### Catalogo

```bash
python3 tools/releases/catalog.py validate
python3 tools/releases/catalog.py validate --require-release-ready
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_catalog.py' -v
```

Falha intermediaria deve registrar comando, codigo, causa, correcao e
repeticao. Nao omitir warning ou relaxar validador para obter verde.

## 16. Fora de escopo

Nao:

- alterar frontends ou WhatsApp;
- alterar backend ERP fora das pontes WhatsApp/CORS autorizadas;
- alterar website backend fora das pontes CORS/WebSocket/Firebase autorizadas;
- criar workflow GitHub Actions;
- criar manifesto/candidato/release;
- criar scripts de deploy, backup, smoke externo ou rollback;
- criar ou alterar `release_control`;
- criar Nginx do host, TLS ou Certbot;
- acessar DNS, GitHub, GHCR, VPS ou banco externo;
- usar dominio real em requisicao de rede;
- migrar ou copiar dado real;
- abrir `.env.production`, PFX, HPROF, upload ou sessao real;
- instalar ferramenta no host;
- autenticar em registry;
- publicar imagem;
- usar `git add`;
- criar commit, tag ou push;
- alterar esta task ou o tracker;
- remover imagens S08/S09;
- executar prune;
- remover volume/rede/container fora do prefixo efemero validado.

## 17. Acesso externo permitido

Excepcionalmente, somente:

- pull anonimo da base Nginx do gateway;
- pull anonimo da base PostgreSQL 16;
- frontend BuildKit publico;
- dependencias Maven publicas exigidas pelos testes dos dois backends.

Nao acessar GitHub, GHCR, DNS operacional, API comercial, Firebase, WhatsApp,
VPS ou registry autenticado. Registrar hosts observados quando visiveis.

## 18. Estado protegido

Ao final registrar:

```bash
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f \
  \( -name '*.yml' -o -name '*.yaml' \) -print
find tools/compose tools/gateway tools/releases \
  \( -type d -name __pycache__ -o -type f -name '*.pyc' \) -print
docker ps -a --format '{{.Names}}' | grep '^s10-' || true
docker volume ls --format '{{.Name}}' | grep '^s10-' || true
docker network ls --format '{{.Name}}' | grep '^s10-' || true
```

Esperado:

- indice real vazio;
- `HEAD` inexistente;
- zero tags/reflog/workflow;
- zero cache Python;
- zero container, volume ou rede `s10-*`;
- imagens S08/S09 preservadas;
- imagem local gateway `:s10`, sem push.

## 19. Relatorio obrigatorio

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S10-gateway-compose-producao-persistencias.report.md
```

Incluir:

1. arquivos criados, alterados e removidos;
2. substituicao dos prototipos;
3. Compose e config resolvida sanitizada;
4. imagens, tags, digests e plataforma;
5. redes e isolamento;
6. portas e bind loopback;
7. volumes e provas de persistencia;
8. bancos, roles, ownership e idempotencia;
9. matriz de environment;
10. Firebase;
11. gateway, rotas e seguranca;
12. healths e ordem;
13. limites, logs e restart;
14. validadores/mutantes;
15. build/inspect/probes;
16. stack efemera e cleanup;
17. Maven e catalogo;
18. cinco gates removidos e readiness final;
19. falhas intermediarias;
20. acessos externos;
21. itens nao determinados;
22. escopo negativo;
23. estado Git, workflows, caches, imagens e residuos;
24. bloqueios.

Nao reproduzir segredo, valor efemero, credencial Firebase, config resolvida
integral, inspect/history integral ou log sensivel.

Estado final:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao declarar `ACCEPTED`.

## 20. Criterios de aceite do orquestrador

A S10 somente podera ser aceita se:

- existir somente o Compose comercial canonico;
- todos os prototipos listados forem removidos individualmente;
- Compose nao tiver build, tag mutavel, socket ou publicacao indevida;
- todas as imagens comerciais forem exigidas por digest;
- PostgreSQL usar base exata/digest e nenhuma porta;
- roles/bancos forem distintos, seguros e idempotentes;
- gateway for o unico bind, sempre em `127.0.0.1`;
- redes e memberships estiverem isolados;
- os quatro volumes estiverem corretos;
- persistencia sobreviver a recriacao;
- XML fiscal estiver sob volume persistente;
- environment corresponder aos consumidores reais;
- URL interna WhatsApp funcionar como fallback sem sobrescrever configuracao
  persistida;
- CORS/WebSocket forem configuraveis, sem dominios legados ou wildcard
  inseguro;
- Firebase disabled permitir startup limpo;
- Firebase enabled invalido falhar fechado e sanitizado;
- gateway for imutavel, nao-root, saudavel e testado;
- rotas, headers, WebSocket/SSE e host default estiverem corretos;
- WhatsApp nao estiver exposto diretamente;
- todos os sete healths e dependencias estiverem corretos;
- limites, logs, restart e stop estiverem configurados;
- validadores e mutantes passarem;
- stack completa efemera chegar a healthy;
- cleanup deixar zero residue S10;
- Maven dos dois backends passar;
- exatamente os cinco gates forem removidos;
- todos os seis componentes ficarem `ready`;
- catalogo estrutural, readiness e testes passarem;
- nenhuma producao, publicacao, commit ou push ocorrer;
- documentacao e implementacao permanecerem alinhadas.

A proxima slice prevista, apos aceite, sera a S11: CI canonico e contrato de
manifesto candidato, sem publicacao de release e sem deploy de producao.
