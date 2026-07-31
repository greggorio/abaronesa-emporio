# S09 — Imagens Node, frontends e contratos de runtime

Estado: **IN_PROGRESS — aguardando revisao do orquestrador**

Data: 28/07/2026  
CWD raiz: `/home/gregorio/git/baronesa/emporio`

## 1. Resultado

Foram endurecidas e validadas localmente, para `linux/amd64`, as imagens de
`frontend`, `website_front` e `whatsapp_service`. Os dois SPAs sao construidos
com Node 24 e servidos por Nginx sem Node/npm. O WhatsApp executa com Node 24,
Chromium da distribuicao, usuario nao-root e liveness independente.

Todos os testes, builds, contratos estaticos, probes e health checks passaram.
Exatamente os 13 gates enumerados pela S09 foram fechados. O readiness global
continua fail-closed, codigo `3`, com os cinco gates reservados para
website uploads e gateway/Compose.

## 2. Arquivos criados ou alterados

### Frontend ERP

- `frontend/Dockerfile`
- `frontend/.dockerignore`
- `frontend/entrypoint.sh`
- `frontend/nginx.conf`
- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vitest.config.js`
- `frontend/runtime-entrypoint.spec.js`

### Website frontend

- `website_front/Dockerfile`
- `website_front/.dockerignore`
- `website_front/entrypoint.sh`
- `website_front/nginx.conf`
- `website_front/package.json`
- `website_front/package-lock.json`
- `website_front/.env.example`
- `website_front/src/config/api.ts`
- `website_front/src/lib/api-client.ts`
- `website_front/src/services/villaApi.ts`
- `website_front/src/services/eventoService.ts`
- `website_front/src/services/clientesDashboardService.ts`
- `website_front/src/pages/admin/TemasPage.tsx`
- `website_front/src/test/runtime-entrypoint.test.ts`

### WhatsApp

- `whatsapp_service/Dockerfile`
- `whatsapp_service/.dockerignore`
- `whatsapp_service/package.json`
- `whatsapp_service/package-lock.json`
- `whatsapp_service/index.js`
- `whatsapp_service/app.js`
- `whatsapp_service/app.test.js`
- `whatsapp_service/README.md`

### Contratos, catalogo e documentacao

- `tools/docker/validate_node_images.py`
- `tools/docker/tests/test_validate_node_images.py`
- `ops/releases/components.yml`
- `tools/releases/tests/test_catalog.py`
- `docs/infrastructure/deployment/images/NODE_IMAGES.md`
- `docs/infrastructure/deployment/release-control/README.md`
- este relatorio

A task S09 e
`docs/infrastructure/deployment/implementation/README.md` nao foram alterados.
Backend Java, website backend, gateway, Compose, workflows e release control
nao foram alterados.

## 3. Bases, digests e plataforma

Base Node dos tres componentes:

- tag `node:24.13.0-alpine3.23`;
- digest do indice
  `sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e`;
- manifesto `linux/amd64`
  `sha256:26eb49fbfdf03bf69f728b73178fa6f9e7c2cef88b06561b65497f5ae8e50a3d`.

Runtime Nginx comum aos SPAs:

- tag `nginx:1.29.5-alpine3.23`;
- digest do indice
  `sha256:1eff5a5f3fcf8431a0abb7eddf5471fec24e5e1905a2581aeacdb07a4479b92b`;
- manifesto `linux/amd64`
  `sha256:123827f4a105eee4054d59a0080f7860b2a7e29fe138d132af7850843b54c833`.

Todos os `FROM` possuem tag completa e digest. Nenhum usa `latest`, alias LTS
ou major flutuante.

Versoes comprovadas:

- Node: `v24.13.0`;
- npm: `11.6.2`;
- Nginx: `1.29.5`;
- Chromium: `149.0.7827.53 Alpine Linux`.

## 4. Contrato comum das imagens

- sintaxe BuildKit e stages nomeados;
- `WORKDIR`;
- manifestos copiados antes do restante do contexto;
- dependencias instaladas exclusivamente por `npm ci`;
- cache npm BuildKit fora da imagem final;
- labels OCI source, revision e version;
- somente `VCS_REF` e `IMAGE_VERSION` como ARGs;
- portas, `STOPSIGNAL SIGTERM`, comando exec-form e health em loopback;
- `.dockerignore` cobrindo ambientes, Git, dependencias locais, builds, logs,
  dumps, sessoes, uploads e chaves;
- nenhum `VOLUME`, Docker socket, URL privada, IP operacional ou segredo;
- nenhuma configuracao publica de API congelada em layer.

Os warnings e vulnerabilidades reportados pelos lockfiles existentes nao foram
ocultados nem corrigidos automaticamente:

- frontend: 34 achados de auditoria;
- website frontend: 25;
- WhatsApp: 11.

Esta slice nao autorizava atualizacao ampla de dependencias ou `npm audit fix`.

## 5. Frontend ERP

O build usa `npm run build`, que chama o Quasar local; a instalacao global da
CLI foi removida. `engines` declara somente Node 24 como linha suportada.

`VITE_BASE_API_URL` permanece o nome canonico. O entrypoint:

- falha fechado quando ausente ou sem esquema HTTP(S);
- usa `jq` instalado no Dockerfile para gerar JSON/JavaScript valido;
- nao registra o valor integral;
- usa arquivos temporarios e `mv`;
- injeta uma unica tag `/runtime-config.js`;
- torna `index.html` e runtime config modo `0444`;
- termina com `exec "$@"`.

O Nginx serve SPA com fallback para `index.html`. `/healthz` retorna
`{"status":"UP"}` sem consultar o backend.

O master Nginx inicia como root exclusivamente para bind da porta canonica 80;
os workers inspecionados executam como UID `101`. PID/cache/temp sao
administrados pela imagem oficial, e o conteudo estatico fica somente leitura
depois do entrypoint.

## 6. Website frontend

O contrato ativo agora distingue:

```text
VITE_ERP_API_URL     -> RuntimeConfig.erpApiUrl
VITE_WEBSITE_API_URL -> RuntimeConfig.websiteApiUrl
```

Nao restou uso ativo dos identificadores legados prescritos pela task. Todos os
consumidores da API do website foram migrados para `websiteApiUrl`; consumidores
ERP continuam usando `erpApiUrl`.

O tema:

- tenta exclusivamente o alvo interno `http://website_back:8085`;
- limita a tres tentativas, timeout de tres segundos cada;
- usa somente `VITE_WEBSITE_API_URL` como fallback externo;
- segue com defaults publicos quando indisponivel;
- valida o JSON recebido;
- nao registra URL, payload ou configuracao;
- serializa config por `jq` e escapa SEO por `jq @html`;
- troca arquivos atomicamente e nao duplica config/SEO em reinicializacao.

`curl` e `jq` sao instalados no Dockerfile; o startup nao instala pacotes.
Assim como no frontend ERP, o master Nginx usa root apenas para porta 80 e os
workers usam UID `101`. O health `/healthz` e independente dos dois backends.

## 7. WhatsApp: liveness e lifecycle

O codigo foi separado em bootstrap real (`index.js`) e aplicacao injetavel
(`app.js`). O servidor HTTP passa a escutar antes do inicio assincrono do
cliente.

`GET /health/live` responde HTTP 200 com payload exato
`{"status":"UP"}` sem consultar estado, QR, Chromium ou rede. `/status`
permanece separado e retorna `connected`/`hasQr`.

Foram preservadas as rotas:

- `POST /start`;
- `GET /qr`;
- `POST /disconnect`;
- `GET /me`;
- `POST /send-pdf`.

Inicializacoes concorrentes sao rejeitadas pelo estado `initializing`. Falha de
bootstrap limpa estado e produz somente log sanitizado. Nao existe handler de
`uncaughtException` que mantenha processo potencialmente corrompido.

Os testes usam cliente falso, paths inexistentes de Chromium/sessao e apenas
HTTP loopback efemero; nao abrem Chromium, WhatsApp, QR real, sessao real ou
rede externa.

Para a prova de imagem foi usado
`WHATSAPP_INITIALIZATION_DISABLED=true`. O default permanece inicializar o
cliente real, e a chave nao simula conexao nem libera endpoint funcional.

## 8. Usuarios, permissoes, packages e paths

### SPAs

- runtime Nginx sem `node` ou `npm`;
- `curl` apenas no website, por causa do tema;
- `jq` nos dois runtimes para serializacao;
- arquivos `index.html` e runtime config em modo `0444`;
- workers Nginx UID `101`;
- master root apenas para bind em `80`.

### WhatsApp

- usuario e grupo finais `10001:10001`;
- Node/npm presentes por serem runtime do servico;
- Chromium e bibliotecas instalados via Alpine;
- executable path `/usr/bin/chromium-browser`;
- `/data/session` modo `0700` e gravavel pelo usuario final;
- `/app`, codigo e manifestos nao gravaveis pelo usuario final;
- sem sessao local copiada e sem `VOLUME`.

## 9. Validacoes npm sob Node 24

Os comandos foram executados em containers efemeros da base Node fixada, com
bind somente do componente e CWD `/workspace`, correspondente ao diretorio do
componente indicado abaixo.

### Frontend — CWD `frontend`

- `npm ci`: codigo `0`, 666 packages.
- `npm run lint`: codigo `0`, sem erro.
- `npm run test`: codigo `0`, 3 arquivos e 18 testes aprovados.
- `npm run build`: codigo `0`, Quasar SPA compilado.

O runner de testes recebeu `jq` no container efemero porque o teste exercita o
entrypoint e a imagem Nginx final tambem o possui. Nenhum pacote foi instalado
no host ou no startup da aplicacao.

### Website frontend — CWD `website_front`

- primeira tentativa de `npm ci` na base Node pura: codigo `1`; `canvas` nao
  tinha binario musl precompilado para Node 24 e `node-gyp` nao encontrou
  Python;
- repeticao no ambiente Alpine previsto pelo Dockerfile, com Python, make,
  compilador e headers nativos: codigo `0`, 691 packages;
- `npm run test`: codigo `0`, 5 arquivos e 34 testes;
- `npm run build`: codigo `0`, incluindo TypeScript e Vite.

O package possui configuracao ESLint como dependencia, mas nao possui script
`lint`; nenhum comando ou gate artificial foi inventado.

### WhatsApp — CWD `whatsapp_service`

- `npm ci`: codigo `0`, 217 packages;
- `npm run test`: codigo `0`, 7 testes;
- `node --check index.js && node --check app.js`: codigo `0`;
- `node --version && npm --version`: codigo `0`, versoes registradas acima.

## 10. Validador Node e mutantes

CWD: raiz.

- primeira execucao de
  `PYTHONDONTWRITEBYTECODE=1 python3 tools/docker/validate_node_images.py validate`:
  codigo `1`; a extracao regex do handler de liveness era rigida demais;
- o parser foi delimitado entre rotas, sem relaxar o payload ou independencia;
- repeticao: codigo `0`, `node-images-contract:valid`;
- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s tools/docker/tests -p 'test_validate_node_images.py' -v`:
  codigo `0`, 21 mutantes/testes aprovados.

Os mutantes cobrem digest, Node 18/alias flutuante, `npm install`, CLI global,
Node no Nginx, install no startup, health externo, nomes legados, target
interno, liveness acoplado, root, sessao, segredo/ARG, `latest`, Docker socket,
contexto e portas.

## 11. Builds Docker

CWD: raiz. Os tres comandos terminaram com codigo `0`:

```bash
docker buildx build --platform linux/amd64 --load \
  --build-arg VCS_REF=s09-local-validation \
  --build-arg IMAGE_VERSION=s09-local \
  -t abaronesa-emporio-frontend:s09 frontend

docker buildx build --platform linux/amd64 --load \
  --build-arg VCS_REF=s09-local-validation \
  --build-arg IMAGE_VERSION=s09-local \
  -t abaronesa-emporio-website-front:s09 website_front

docker buildx build --platform linux/amd64 --load \
  --build-arg VCS_REF=s09-local-validation \
  --build-arg IMAGE_VERSION=s09-local \
  -t abaronesa-emporio-whatsapp-service:s09 whatsapp_service
```

Apos a remocao de `eval`, o website foi reconstruido com o mesmo comando e
codigo `0`. Apos eliminar o download Chromium duplicado do Puppeteer, o
WhatsApp tambem foi reconstruido com codigo `0`.

## 12. Inspect, history e probes

Asserts sanitizados confirmados:

- tres imagens `amd64`;
- SPAs: entrypoint `/entrypoint.sh`, CMD Nginx, porta 80 e health `/healthz`;
- WhatsApp: user `10001:10001`, CMD Node, porta 3001 e
  health `/health/live`;
- labels OCI somente com source/revision/version neutros;
- health dos tres containers chegou a `healthy`;
- respostas internas exatas `{"status":"UP"}`;
- nenhuma ferramenta Node/npm nos SPAs;
- Nginx `1.29.5`, master root e workers UID `101`;
- Node `v24.13.0`, npm `11.6.2` e Chromium
  `149.0.7827.53` no WhatsApp;
- executable path, escrita de sessao e nao escrita do codigo;
- configs runtime com chaves canonicas e uma unica injecao;
- SEO idempotente;
- history dirigido sem segredo, URL publica congelada, IP operacional ou
  Docker socket.

Nao foi persistido inspect ou history integral.

IDs e tamanhos finais:

- frontend:
  `sha256:9e6251af92bbeee8a5b89b6a1a8bc563d086debe953b786be34b3ebaae9853f7`,
  74.586.986 bytes;
- website frontend:
  `sha256:073831b2633d03892671946b1ef181086af2bdfcfa98092c08c729300a9aeee0`,
  137.158.271 bytes;
- WhatsApp:
  `sha256:bb7fb35744676d3b8538ae879f1336f50805620c395f309307da6245db5881ab`,
  1.002.937.484 bytes.

## 13. Falhas intermediarias e correcoes

1. O primeiro teste do frontend resolveu `entrypoint.sh` como `/entrypoint.sh`
   dentro do Vitest. O path passou a usar o CWD contratual.
2. A base Node de teste nao continha `jq`, embora a imagem runtime o contenha.
   A validacao passou a instalar `jq` apenas no container efemero de testes.
3. Escapes excessivos em regex BusyBox `awk` foram corrigidos.
4. O comando ficticio `/bin/printf` nao existe como binario no Alpine; o teste
   de `exec` passou a usar `/bin/echo`.
5. A primeira idempotencia removia uma linha que tambem continha `<head>`.
   A remocao passou a ser substituicao pontual por `gsub`.
6. O primeiro `npm ci` do website falhou por toolchain nativa ausente; foi
   repetido no ambiente completo do stage de build.
7. A imagem WhatsApp inicial tinha 1.937.834.930 bytes porque a dependencia
   baixou Chromium alem do pacote Alpine. Foi configurado
   `PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true` somente no stage de dependencias;
   rebuild e probes passaram com um unico Chromium e tamanho final reduzido.
8. A funcao website que selecionava variaveis fixas usava `eval`; foi
   substituida por `case`, seguida de teste, rebuild e probe verdes.

Nenhuma falha foi omitida, e nenhuma regra de lint/teste foi desabilitada.

## 14. Catalogo e gates

Foram removidos exatamente:

- `FRONTEND_DOCKERFILE_HARDENING`;
- `FRONTEND_NODE24_COMPATIBILITY`;
- `FRONTEND_HEALTH_CHECK_CONFIRMATION`;
- `WEBSITE_FRONT_ENV_NAME`;
- `WEBSITE_FRONT_INTERNAL_TARGET`;
- `WEBSITE_FRONT_DOCKERFILE_HARDENING`;
- `WEBSITE_FRONT_NODE24_COMPATIBILITY`;
- `WEBSITE_FRONT_TEST_COMMAND`;
- `WEBSITE_FRONT_HEALTH_CHECK_CONFIRMATION`;
- `WHATSAPP_NODE18_UNSUPPORTED`;
- `WHATSAPP_LIVENESS_CONTRACT`;
- `WHATSAPP_DOCKERFILE_HARDENING`;
- `WHATSAPP_TEST_COMMAND`.

`frontend`, `website_front` e `whatsapp_service` estao `ready`, com build,
test e health confirmados. `website_back` permanece bloqueado apenas por
uploads, e gateway preserva seus quatro gates.

CWD raiz:

- `python3 tools/releases/catalog.py validate`: codigo `0`;
- `python3 tools/releases/catalog.py validate --require-release-ready`:
  codigo esperado `3`;
- suite do catalogo com bytecode desabilitado: codigo `0`, 45 testes.

Os cinco gates restantes sao exatamente:

```text
WEBSITE_BACK_UPLOAD_PERSISTENCE
GATEWAY_CANONICAL_ARTIFACTS
GATEWAY_HEALTH_CHECK
GATEWAY_LOOPBACK_PORT
GATEWAY_TEST_COMMAND
```

## 15. Acessos externos

Hosts observados e autorizados:

- `docker.io` e `registry-1.docker.io`;
- `registry.npmjs.org`;
- `dl-cdn.alpinelinux.org`;
- frontend publico BuildKit no Docker Hub.

Nao houve acesso a API de negocio, Firebase, WhatsApp, GitHub, GHCR, DNS
operacional, VPS, banco externo ou registry autenticado. Probes usaram apenas
loopback e valores ficticios locais.

## 16. Itens nao determinados

- arquitetura real da VPS;
- persistencia efetiva de `/data/session` e uploads;
- limites de CPU, memoria e processos;
- redes, restart policy e health integrado;
- gateway, Compose, smoke externo e publicacao;
- remediacao ampla dos achados dos lockfiles.

Esses itens permanecem para slices posteriores e nao foram usados para fechar
gate desta slice.

## 17. Escopo negativo

Nao houve gateway, Compose, PostgreSQL, workflow, release control, manifesto,
release, deploy, publicacao, push de imagem, acesso a producao ou instalacao no
host. Nenhuma sessao, QR, upload, PFX, HPROF ou ambiente de producao foi aberto.
As duas imagens `:s08` foram preservadas.

## 18. Estado protegido

CWD raiz:

- `git ls-files --stage`: codigo `0`, sem saida; indice real vazio;
- `git rev-parse --verify HEAD`: codigo esperado `128`; HEAD inexistente;
- `git tag --list`: codigo `0`, sem saida;
- `git reflog show --all`: codigo `0`, sem saida;
- `find .github/workflows -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' \) -print`:
  codigo `0`, sem saida;
- `find tools/docker tools/releases \( -type d -name __pycache__ -o -type f -name '*.pyc' \) -print`:
  codigo `0`, sem saida;
- `docker ps --filter ancestor=abaronesa-emporio-frontend:s09`: codigo `0`,
  zero container;
- `docker ps --filter ancestor=abaronesa-emporio-website-front:s09`: codigo
  `0`, zero container;
- `docker ps --filter ancestor=abaronesa-emporio-whatsapp-service:s09`:
  codigo `0`, zero container;
- `docker image inspect` dirigido: codigo `0` para as tres imagens `:s09` e
  para as duas imagens `:s08`.

Nao houve `git add`, commit, tag, push ou push de imagem.

## 19. Bloqueios

Nao ha bloqueio de execucao da S09. Os cinco gates restantes e os itens nao
determinados sao fronteiras deliberadas da proxima slice.

Estado final: **IN_PROGRESS — aguardando revisao do orquestrador**

---

## 20. Revisao final do orquestrador

> **Resultado:** `ACCEPTED`  
> **Data:** `2026-07-28`

A S09 atende integralmente ao contrato.

Evidencias aceitas:

- o frontend ERP passou por lint, 18 testes e build Quasar sob Node 24;
- o website frontend passou por 34 testes e build TypeScript/Vite sob Node 24;
- o WhatsApp passou por 7 testes deterministas, sem Chromium, QR, sessao ou
  rede reais;
- as tres imagens `linux/amd64` foram construidas, inspecionadas e chegaram a
  `healthy`;
- todos os `FROM` usam tags completas e digests imutaveis;
- os dois SPAs usam Nginx sem Node/npm no runtime;
- os entrypoints nao instalam pacotes, serializam configuracao por JSON,
  falham fechado, sao idempotentes e terminam com `exec`;
- `VITE_BASE_API_URL` foi preservada no frontend ERP;
- o website usa exclusivamente `VITE_ERP_API_URL`/`erpApiUrl` e
  `VITE_WEBSITE_API_URL`/`websiteApiUrl` no codigo ativo da imagem;
- o unico alvo interno de tema e `http://website_back:8085`, e o fallback usa
  somente a URL publica do website;
- os healths dos SPAs sao locais e independentes dos backends;
- o servidor HTTP do WhatsApp inicia antes da integracao externa;
- `/health/live` retorna o payload sanitizado sem depender de autenticacao,
  QR, Chromium ou conectividade;
- `/status` e as rotas funcionais permanecem separadas;
- o WhatsApp usa Node 24, Chromium 149, UID/GID `10001:10001` e sessao em
  `/data/session`;
- o validador Node e seus 21 mutantes terminaram com codigo `0`;
- somente os treze gates autorizados foram removidos;
- `frontend`, `website_front` e `whatsapp_service` estao `ready`;
- catalogo e seus 45 testes passaram;
- readiness global permanece fail-closed com codigo `3` e exatamente os cinco
  gates reservados para uploads e gateway/Compose;
- indice Git, `HEAD`, tags, reflog, ausencia de workflows, caches e containers
  foram preservados;
- nao houve publicacao, commit, push ou acesso a producao.

Os artefatos Android gerados fora do contexto Docker nao participam do runtime
validado nesta slice. Sua eventual regeneracao pertence ao ciclo Android e nao
reabre os contratos da imagem web.

Os achados de auditoria dos lockfiles foram registrados sem `audit fix`
automatico. Eles exigirao politica propria de manutencao e nao invalidam a
prova de compatibilidade executada nesta slice.

Os estados `IN_PROGRESS` anteriores permanecem como historico. A autoridade
final desta secao altera o estado da S09 para `ACCEPTED`.

A S10 pode agora substituir os prototipos conflitantes pelo gateway interno e
pelo Compose canonico, provar persistencias e health integrado em ambiente
efemero e fechar somente os cinco gates restantes.
