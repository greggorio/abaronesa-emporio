# Stack comercial de producao

## Contrato canonico

`ops/compose/compose.prod.yml` e a unica definicao comercial. Ela consome sete
imagens imutaveis por digest e nunca constroi artefatos. O gateway e o unico
servico publicado no host, em `127.0.0.1:8120`; TLS e Nginx do host pertencem a
uma etapa futura.

| Servico | Rede app | Rede DB interna | Health | Persistencia |
|---|---:|---:|---|---|
| postgresql | nao | sim | `pg_isready` | dados PostgreSQL |
| backend | sim | sim | `/actuator/health` | `/app/uploads` |
| website_back | sim | sim | `/actuator/health` | `/app/uploads` |
| frontend | sim | nao | `/healthz` | nenhuma |
| website_front | sim | nao | `/healthz` | nenhuma |
| whatsapp_service | sim | nao | `/health/live` | `/data/session` |
| gateway | sim | nao | `/healthz` | nenhuma |

Os volumes canonicos sao `emporio-postgres-data`,
`emporio-backend-uploads`, `emporio-website-uploads` e
`emporio-whatsapp-session`. Os processos de aplicacao gravam somente nos
mounts autorizados; schemas fiscais permanecem somente leitura na imagem.

## Banco e inicializacao

Um PostgreSQL 16 Alpine fixado por digest hospeda dois bancos e dois usuarios
de aplicacao distintos. `ops/db/init-databases.sh` valida identificadores,
cria roles e databases idempotentemente com `ON_ERROR_STOP`, nao troca a senha
de role existente e revoga conexao cruzada. Passwords seguem por variaveis
`psql`, nunca por SQL interpolado ou log.

## Imagens e variaveis

As imagens comerciais devem usar seus repositorios
`ghcr.io/greggorio/abaronesa-emporio-<componente>@sha256:<digest>`.
PostgreSQL usa tag completa `16.10-alpine3.22` e digest. A matriz sem valores
reais esta em `ops/env/.env.example`; cada servico recebe apenas seu subconjunto
explicito. Limites de CPU, memoria e PIDs, rotacao `json-file`, restart
`unless-stopped`, grace period e health sao declarados no Compose.

Firebase fica desabilitado por default. Para habilita-lo, provisione primeiro
uma credencial valida dentro do volume do website em
`/app/uploads/android-private/firebase-adminsdk.json` e somente depois defina
`APP_FIREBASE_ENABLED=true`. A falha habilitada e fechada e sanitizada.

## Gateway

O gateway Nginx roda como UID numerico nao-root em 8080. O host
`emporio.abaronesa.net.br` roteia website e o host
`erp-emporio.abaronesa.net.br` roteia ERP. APIs, midia, OAuth e WebSocket
preservam path/query; host desconhecido recebe conexao fechada. `/healthz` e
local e `/api/deployment-control/` e recusado. Nao existe rota direta ao
WhatsApp. O gateway nao implementa TLS nem conhece release control.

`/ws` e todos os seus subpaths usam HTTP/1.1, upgrade e timeouts finitos nos
dois hosts. ERP aceita bodies ate 10 MiB e website ate 2 MiB. APIs e streaming
desabilitam buffering; OAuth existe somente no host ERP. O forwarded proto usa
o header confiado do proxy do host quando presente e faz fallback para o
scheme da conexao. Headers `nosniff`, `SAMEORIGIN` e referrer policy sao
aplicados aos hosts comerciais.

## Matriz de ambiente

- PostgreSQL recebe somente admin, dois nomes, dois usuarios e suas passwords.
- Backend ERP recebe datasource ERP, token compartilhado, OAuth, URL interna
  WhatsApp, URLs publicas, CORS, SMTP opcional, Uber opcional, sync com website,
  paths fiscais/uploads e bootstrap root opt-in.
- Website backend recebe datasource website, token compartilhado, ERP interno,
  CORS/WebSocket, Firebase opcional, Uber/sync e paths de uploads.
- Frontends recebem exclusivamente suas URLs publicas de runtime.
- WhatsApp recebe porta, session dir e codigo de pais.
- Gateway nao recebe environment de aplicacao.

`ERP_WEBSITE_SYNC_KEY` e o segredo operacional unico de sincronizacao. O
Compose o encaminha como `ESPRESSO_SYNC_API_KEY` e `WEBSITE_SYNC_API_KEY` ao
backend ERP e como `WEBSITE_ERP_SYNC_KEY` ao website backend. Os tres aliases
devem permanecer presentes e resolver para o mesmo valor.

`MAIL_ENABLED` tambem controla o health SMTP: desabilitado nao tenta SMTP;
habilitado inclui a disponibilidade SMTP na saude. O harness substitui URLs
publicas por dominios `.invalid` e nunca publica backends, banco ou WhatsApp.

## Validacao e operacao

Validacao sem segredo:

```bash
python3 tools/gateway/validate_gateway.py
python3 tools/compose/validate_compose.py
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/gateway/tests -p 'test_*.py' -v
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/compose/tests -p 'test_*.py' -v
```

O harness local deve usar override temporario, imagens locais `:s08`, `:s09` e
gateway `:s10`, uma porta livre e nomes com prefixo exclusivo `s10-`. Nunca
deve usar volumes canonicos. A limpeza e dirigida pelo mesmo project name com
`docker compose down -v`; `prune` e proibido.

Em producao, valide a configuracao resolvida antes de `up`; não use override
local. A manutencao de uma base exige resolver o digest `linux/amd64`, repetir
build, inspect, health e testes mutantes, e atualizar documentacao/evidencias.

## Fronteiras futuras

Ainda nao existem CI canonico, manifesto candidato, publicacao, deploy,
backup, rollback, TLS ou configuracao do Nginx do host. Readiness do catalogo
atesta somente estes contratos tecnicos. Os gates da S10 fecham apenas quando
gateway, health/bind loopback, teste do gateway e persistencia do website forem
comprovados pela integracao efemera.
