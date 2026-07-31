# S09 — Imagens Node, frontends e contratos de runtime

> **Estado:** `ACCEPTED` — `2026-07-28`  
> **Tipo:** infraestrutura de imagem, configuracao runtime, liveness e testes  
> **Executor previsto:** CLI  
> **Diretorio de trabalho obrigatorio:** `/home/gregorio/git/baronesa/emporio`  
> **Dependencias:** S01 a S08 `ACCEPTED`  
> **Contrato arquitetural:** [proposta-docker-ci-cd-producao-emporio.md](../../proposta-docker-ci-cd-producao-emporio.md)  
> **Contrato de componentes:** [release-control/README.md](../../release-control/README.md)  
> **Relatorio de saida:** `S09-imagens-node-frontends-contratos-runtime.report.md`

## Instrucao para delegacao

Execute integralmente esta slice no diretorio indicado. Leia primeiro:

1. esta task;
2. a revisao final da S08;
3. as secoes `Node e frontends`, `WhatsApp`, `CI` e `Health checks` da
   arquitetura aprovada;
4. `ops/releases/components.yml` e seus testes;
5. Dockerfiles, `.dockerignore`, `package.json`, lockfiles e entrypoints de
   `frontend`, `website_front` e `whatsapp_service`;
6. `frontend/src`, especialmente o consumo de `window.RuntimeConfig`;
7. `website_front/src`, especialmente todos os usos de `RuntimeConfig`,
   `VITE_VILLA_API_URL`, `VITE_WEBSITE_API_URL` e `VITE_ERP_API_URL`;
8. `whatsapp_service/index.js` e sua documentacao;
9. a documentacao de imagens criada nas slices anteriores.

Nao altere esta task nem o tracker
`docs/infrastructure/deployment/implementation/README.md`.

Esta slice cobre somente `frontend`, `website_front` e `whatsapp_service`.
Nao implemente gateway, Compose, PostgreSQL, workflows, `release_control`,
manifesto, publicacao, deploy ou VPS.

## Objetivo observavel

Ao final:

- os tres builds usam Node 24 LTS comprovado e bases imutaveis;
- dependencias sao instaladas exclusivamente por `npm ci`;
- os dois SPAs sao construidos em stage Node e servidos sem Node no runtime;
- configuracoes publicas de API continuam injetadas em runtime;
- scripts de entrypoint nao instalam pacotes ao iniciar;
- o frontend ERP tem teste, build, imagem e health confirmados;
- o website frontend usa exclusivamente o nome canonico
  `VITE_WEBSITE_API_URL`/`websiteApiUrl` para a API do website;
- o website frontend aponta internamente para `http://website_back:8085`;
- o website frontend ganha comando de teste deterministico;
- o WhatsApp usa Node suportado, Chromium inspecionado e usuario nao-root;
- a sessao do WhatsApp permanece em `/data/session`;
- liveness HTTP independe de autenticacao, QR ou disponibilidade do WhatsApp;
- o WhatsApp ganha teste deterministico sem rede, QR real ou sessao real;
- as tres imagens possuem health check interno;
- nenhum segredo, URL privada, IP de VPS ou dado de tenant entra em layer;
- contratos estaticos, testes, builds e probes locais sustentam os gates
  removidos;
- exatamente os treze gates desta slice sao fechados;
- readiness global continua falhando fechado com os cinco gates reservados
  para Compose/gateway;
- gateway, Compose, CI e publicacao continuam ausentes.

## 1. Plataforma e bases

### 1.1 Target

Construir e validar:

```text
linux/amd64
```

A arquitetura real da VPS continua nao determinada ate a slice de preparacao
do host.

### 1.2 Node

Usar Node 24 LTS nos stages de build dos frontends e no runtime do WhatsApp.

Regras:

- selecionar tags existentes com versao completa do Node e distribuicao;
- cada `FROM` deve conter tag legivel e `@sha256:<digest>`;
- nao usar `latest`, `current`, `lts`, somente `24` ou alias flutuante;
- usar o mesmo base de build nos frontends quando tecnicamente compativel;
- registrar tag, digest, plataforma, `node --version` e `npm --version`;
- atualizar `engines.node` quando existente para refletir Node 24 suportado;
- nao declarar compatibilidade com linhas antigas sem prova nesta slice;
- `npm ci` deve consumir o lockfile versionado, sem reescrever dependencias
  silenciosamente durante o build;
- qualquer mudanca intencional de dependencia ou lockfile deve ser minima,
  explicada e comprovada por diff e testes;
- nao instalar Node no runtime Nginx.

### 1.3 Nginx dos SPAs

Selecionar uma base Alpine oficial e estavel do Nginx, com tag completa e
digest imutavel. Os dois frontends devem usar a mesma base quando compativel.

Preservar nesta slice a topologia aprovada:

```text
frontend:80
website_front:80
```

Nao alterar silenciosamente as portas canonicas para acomodar outra imagem.
Como a arquitetura condiciona o usuario nao privilegiado ao suporte da
imagem, registrar separadamente:

- UID do master e dos workers em execucao;
- justificativa tecnica caso o master precise iniciar como root para bind na
  porta 80;
- quais arquivos/diretorios permanecem gravaveis no runtime;
- ausencia de shell, compilador e gerenciador Node alem do fornecido pela
  propria base Nginx.

Nao criar uma decisao arquitetural nova nesta slice. O minimo obrigatorio e:
workers Nginx sem privilegios, conteudo estatico somente leitura depois da
geracao da configuracao runtime, PID/cache/temp com permissoes minimas e
nenhum pacote instalado no startup.

## 2. Contrato comum dos Dockerfiles

Os tres Dockerfiles devem:

- usar sintaxe Dockerfile compativel com BuildKit;
- usar stages nomeados de forma explicita;
- usar `WORKDIR`;
- copiar primeiro `package.json` e lockfile;
- instalar dependencias por `npm ci`;
- aproveitar cache BuildKit do npm sem copiar cache para a imagem final;
- usar `COPY --chown` ou ownership explicito quando aplicavel;
- conter labels OCI `source`, `revision` e `version`, usando somente
  `VCS_REF` e `IMAGE_VERSION` com defaults neutros;
- nao aceitar base, comando, URL, path ou segredo por ARG;
- nao copiar `.env`, `.git`, `node_modules`, `dist` local, logs, coverage,
  uploads, sessoes, HPROF, PFX ou caches;
- possuir `.dockerignore` especifico;
- usar entrypoint/CMD exec-form;
- declarar porta interna e `STOPSIGNAL SIGTERM`;
- possuir `HEALTHCHECK` com interval, timeout, retries e start-period;
- testar somente `127.0.0.1`, nunca DNS externo;
- nao declarar `VOLUME` nesta fase;
- nao usar Docker socket;
- nao fazer push.

ARGs permitidos:

```text
VCS_REF
IMAGE_VERSION
```

Variaveis publicas de runtime nao devem ser ARG de build. Nenhuma imagem deve
conter valor real de producao.

## 3. Frontend ERP

Arquivos principais:

```text
frontend/Dockerfile
frontend/.dockerignore
frontend/entrypoint.sh
frontend/package.json
frontend/package-lock.json
```

Contrato:

- build via `npm run build`, sem Quasar CLI instalado globalmente;
- usar o binario local disponibilizado pelo projeto;
- `npm run test` deve passar sob Node 24;
- executar lint e registrar separadamente seu resultado; se houver falhas
  preexistentes, nao ocultar nem desabilitar regras — corrigir somente o que
  estiver dentro do escopo; se o lint nao passar, a S09 permanece
  `IN_PROGRESS` e o bloqueio deve ser registrado;
- runtime Nginx nao contem Node/npm;
- preservar `VITE_BASE_API_URL` como nome publico canonico deste componente;
- gerar `runtime-config.js` no startup sem gravar o valor em layer;
- validar/serializar o valor de modo que aspas, quebras de linha ou conteudo
  malformado nao permitam injecao JavaScript;
- nao registrar no log o valor integral configurado;
- injetar o script de runtime de forma idempotente: reiniciar o entrypoint nao
  pode duplicar a tag;
- servir SPA com fallback para `index.html`;
- expor resposta HTTP local deterministica para health;
- health nao depende do backend ERP estar autenticado ou disponivel.

Adicionar teste focado do contrato do entrypoint que cubra, no minimo:

- variavel ausente falha fechado;
- URL publica valida gera configuracao valida;
- valor malformado e rejeitado sem execucao/injecao;
- reinjecao nao duplica a tag;
- o comando final e executado com `exec`.

O teste pode usar shell/Python ja disponivel no ambiente de validacao, mas nao
pode depender de rede, browser ou backend.

## 4. Website frontend

Arquivos principais:

```text
website_front/Dockerfile
website_front/.dockerignore
website_front/entrypoint.sh
website_front/nginx.conf
website_front/package.json
website_front/package-lock.json
website_front/src/**
```

### 4.1 Nomes canonicos

O contrato de runtime deve distinguir:

```text
VITE_ERP_API_URL       -> RuntimeConfig.erpApiUrl
VITE_WEBSITE_API_URL   -> RuntimeConfig.websiteApiUrl
```

Remover do codigo ativo, tipos, comentarios, entrypoint, testes e
documentacao desta imagem os nomes legados:

```text
VITE_VILLA_API_URL
RuntimeConfig.villaApiUrl
```

Nao manter alias silencioso. A ausencia de qualquer variavel obrigatoria deve
falhar fechado, com mensagem que revele apenas o nome ausente.

Todos os consumidores da API do website devem usar `websiteApiUrl`.
Consumidores da API ERP continuam usando `erpApiUrl`. Nao substituir ambos
por uma unica URL.

### 4.2 Alvo interno e tema

O alvo interno canonico da API do website e:

```text
http://website_back:8085
```

O fetch de tema no startup deve:

- tentar o alvo interno acima;
- usar `VITE_WEBSITE_API_URL`, nunca a URL ERP, como fallback externo;
- ter timeout e numero de tentativas limitados;
- continuar com defaults publicos quando o tema estiver indisponivel;
- nao tornar o health do SPA dependente do website backend;
- nao registrar payload do tema nem configuracao sensivel;
- nao instalar `curl`, `jq`, Python ou outro pacote durante o startup.

Ferramentas realmente necessarias ao entrypoint devem estar instaladas no
Dockerfile, ou a implementacao deve remover a necessidade delas.

### 4.3 Serializacao e idempotencia

A geracao de `runtime-config.js` e a injecao de SEO devem:

- serializar dados como JavaScript/JSON valido;
- impedir injecao por aspas, quebras de linha e sequencias de fechamento;
- escapar conteudo de tema antes de inseri-lo no HTML;
- usar arquivo temporario e troca atomica quando praticavel;
- limpar arquivos temporarios;
- nao duplicar scripts ou blocos SEO em reinicializacao;
- terminar com `exec "$@"`;
- manter conteudo estatico somente leitura depois do startup.

### 4.4 Testes

Adicionar `npm run test` deterministico e faze-lo executar os testes existentes
do projeto. Nao aceitar `--passWithNoTests`, teste vazio ou comando que apenas
faz parse.

Adicionar cobertura focada para o contrato runtime, incluindo:

- nomes canonicos e ausencia dos legados;
- variavel obrigatoria ausente;
- serializacao segura;
- alvo interno `website_back:8085`;
- fallback externo pela URL do website;
- fallback de tema sem bloquear startup;
- idempotencia de runtime config/SEO;
- `exec` do comando final.

Os testes nao podem acessar rede real, Firebase, backend, tenant ou browser.
Mockar `curl`/entrada de tema quando necessario.

Executar sob Node 24:

```bash
npm ci
npm run test
npm run build
```

Se `package.json` possuir lint configurado ao fim da implementacao, executa-lo
e registrar o resultado. Nao inventar um gate de lint fora do escopo.

## 5. WhatsApp service

Arquivos principais:

```text
whatsapp_service/Dockerfile
whatsapp_service/.dockerignore
whatsapp_service/package.json
whatsapp_service/package-lock.json
whatsapp_service/index.js
whatsapp_service/README.md
```

Podem ser criados modulos internos e testes para separar bootstrap HTTP,
estado e integracao WhatsApp, desde que a API funcional existente seja
preservada.

### 5.1 Inicializacao e liveness

Criar:

```text
GET /health/live
```

Resposta saudavel:

```json
{"status":"UP"}
```

Regras:

- HTTP 200 significa somente que o processo HTTP/event loop esta vivo;
- nao depende de cliente autenticado, QR disponivel, Chromium conectado ou
  rede externa;
- nao retorna QR, identificador de conta, telefone, sessao, erro interno,
  path ou configuracao;
- `/status` continua representando separadamente `connected` e `hasQr`;
- o servidor HTTP deve poder subir antes ou independentemente da conclusao de
  `client.initialize()`;
- falha/rejeicao da inicializacao WhatsApp deve atualizar estado e ser
  observavel em log sanitizado, sem derrubar a rota de liveness;
- erros fatais do processo nao devem ser mascarados por um
  `uncaughtException` que mantenha estado potencialmente corrompido;
- inicializacoes concorrentes continuam impedidas;
- os contratos existentes de `/start`, `/qr`, `/disconnect`, `/me` e
  `/send-pdf` devem permanecer compativeis.

Estruturar o codigo para permitir teste com cliente WhatsApp falso. Testes nao
podem abrir Chromium real, acessar WhatsApp, emitir QR real, ler uma sessao
existente ou usar rede externa.

### 5.2 Runtime

O runtime deve:

- usar Node 24 LTS;
- instalar Chromium e bibliotecas necessarias de forma reproduzivel;
- registrar a versao efetiva do Chromium;
- configurar explicitamente o executable path;
- usar usuario final nao-root, com UID/GID numericos registrados;
- possuir somente `/data/session` gravavel pelo usuario final;
- manter codigo e manifestos nao gravaveis pelo usuario final;
- criar `/data/session` no build, mas nao declarar volume;
- usar `SESSION_DIR=/data/session` como default;
- nao copiar sessao local;
- expor somente `3001`;
- usar health check em
  `http://127.0.0.1:3001/health/live`, sem depender de curl se uma probe Node
  simples for suficiente;
- iniciar por comando exec-form;
- nao conter PM2, daemonizador, SSH, Docker CLI ou socket;
- nao embutir limites de CPU/memoria/processos ficticios no Dockerfile.

Limites de recursos e persistencia efetiva serao comprovados no Compose. Esta
slice documenta essa fronteira e nao fecha gate de Compose inexistente.

### 5.3 Testes

Adicionar comando real:

```text
npm run test
```

Preferir `node:test` quando suficiente, evitando dependencia nova apenas para
o runner. Cobrir no minimo:

- liveness 200 antes de autenticacao;
- payload exato e sanitizado de liveness;
- `/status` desconectado e conectado;
- falha de inicializacao nao altera liveness para erro;
- bloqueio de inicializacao concorrente;
- endpoints que exigem conexao continuam recusando quando desconectado;
- lifecycle de start/disconnect com fake client;
- nenhum Chromium, WhatsApp ou acesso externo e realizado pela suite.

Executar sob Node 24:

```bash
npm ci
npm run test
npm run start -- --help
```

O ultimo comando e apenas uma sugestao de verificacao sintatica se for
seguro; nao iniciar uma sessao real. Use uma validacao sintatica equivalente
se o entrypoint nao suportar `--help`.

## 6. Health e probes das imagens

Tags locais obrigatorias:

```text
abaronesa-emporio-frontend:s09
abaronesa-emporio-website-front:s09
abaronesa-emporio-whatsapp-service:s09
```

Para cada imagem:

- construir com `docker buildx build --platform linux/amd64 --load`;
- usar somente ARGs neutros;
- inspecionar platform, user, entrypoint, cmd, env, exposed ports, labels e
  health;
- provar que o health local fica `healthy`;
- verificar resposta HTTP esperada por dentro do container;
- provar ausencia de Node/npm nos dois runtimes Nginx;
- provar Node 24 e Chromium no WhatsApp;
- provar permissoes finais e paths gravaveis;
- provar que nenhum container de teste permanece em execucao.

Nos frontends, usar valores estritamente ficticios e locais nas probes, por
exemplo dominios `.invalid`. Nao usar DNS, URLs ou credenciais de producao.

No WhatsApp, a prova de health deve desabilitar ou substituir de forma
explicitamente testavel qualquer inicializacao externa. Uma chave de teste
nao pode alterar o default seguro de producao nem permitir bypass em endpoint
funcional. Documentar o mecanismo escolhido.

Nao imprimir `docker inspect` ou `docker history` integrais. Registrar somente
asserts sanitizados.

## 7. Segredos e contextos

Criar/ajustar os tres `.dockerignore` para excluir no minimo:

- `.git`, `.github` e metadados de IDE;
- `.env` e variantes, preservando exemplos somente se realmente necessarios;
- `node_modules`, `dist`, build, coverage e caches;
- logs, dumps, crash reports e HPROF;
- uploads, sessoes e `.wwebjs_auth`;
- certificados, PFX e chaves;
- documentacao operacional legada desnecessaria ao runtime.

Validar contextos reais sem abrir arquivos sensiveis. Verificar layers,
history, labels, env e filesystem das imagens contra nomes de variaveis
secretas e valores ficticios usados na validacao.

Valores publicos de URL usados apenas em runtime nao sao segredos, mas nao
devem ser congelados na imagem.

## 8. Validador local

Criar:

```text
tools/docker/validate_node_images.py
tools/docker/tests/test_validate_node_images.py
```

Ou estender de forma claramente separada o validador da S08. A interface deve
ser deterministica, fail-closed e nao depender de PyYAML novo.

Validar estaticamente:

- bases exatas e digests;
- Node 24 nos tres builds/runtimes aplicaveis;
- `npm ci` e ausencia de install global;
- stages e ausencia de Node nos runtimes Nginx;
- ARGs permitidos;
- labels, portas, health, entrypoint/CMD e stop signal;
- `.dockerignore`;
- ausencia de instalacao de pacotes nos entrypoints;
- nomes runtime canonicos dos dois frontends;
- ausencia dos nomes legados no website frontend ativo;
- alvo interno `website_back:8085`;
- liveness do WhatsApp separado de `/status`;
- usuario, session path e Chromium do WhatsApp;
- ausencia de `latest`, Docker socket, segredo e valor de producao.

Adicionar mutantes negativos para cada invariavel importante. O validador deve
rejeitar, no minimo:

- base sem digest;
- Node 18 ou alias flutuante;
- `npm install` no lugar de `npm ci`;
- Quasar CLI global;
- Node presente no runtime SPA;
- install de pacote no entrypoint;
- porta/health externo incorreto;
- `VITE_VILLA_API_URL` ou `villaApiUrl`;
- alvo `backend:8085`;
- liveness acoplado a `connected`/QR;
- WhatsApp como root;
- sessao fora de `/data/session`;
- segredo em ARG/ENV/label;
- `latest`;
- Docker socket.

Executar com bytecode desabilitado e remover qualquer cache acidental.

## 9. Catalogo e gates

Apos todas as provas, atualizar juntos:

```text
ops/releases/components.yml
tools/releases/tests/test_catalog.py
docs/infrastructure/deployment/release-control/README.md
```

Fechar exatamente:

```text
FRONTEND_DOCKERFILE_HARDENING
FRONTEND_NODE24_COMPATIBILITY
FRONTEND_HEALTH_CHECK_CONFIRMATION
WEBSITE_FRONT_ENV_NAME
WEBSITE_FRONT_INTERNAL_TARGET
WEBSITE_FRONT_DOCKERFILE_HARDENING
WEBSITE_FRONT_NODE24_COMPATIBILITY
WEBSITE_FRONT_TEST_COMMAND
WEBSITE_FRONT_HEALTH_CHECK_CONFIRMATION
WHATSAPP_NODE18_UNSUPPORTED
WHATSAPP_LIVENESS_CONTRACT
WHATSAPP_DOCKERFILE_HARDENING
WHATSAPP_TEST_COMMAND
```

Entao:

- `frontend`, `website_front` e `whatsapp_service` mudam para `ready`;
- builds/testes/healths ganham status `confirmed` e comandos/paths reais;
- `website_back` continua bloqueado apenas por uploads;
- `gateway` continua bloqueado por seus quatro gates;
- readiness global falha com codigo `3` e exatamente cinco gates:

```text
WEBSITE_BACK_UPLOAD_PERSISTENCE
GATEWAY_CANONICAL_ARTIFACTS
GATEWAY_HEALTH_CHECK
GATEWAY_LOOPBACK_PORT
GATEWAY_TEST_COMMAND
```

Nao remover gate pela intencao. Se qualquer prova falhar, preservar o gate
correspondente, manter o componente `blocked` e relatar o bloqueio. Nessa
situacao, os demais gates independentes podem ser atualizados somente se os
testes do catalogo cobrirem explicitamente o estado parcial.

Executar:

```bash
python3 tools/releases/catalog.py validate
python3 tools/releases/catalog.py validate --require-release-ready
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover \
  -s tools/releases/tests -p 'test_catalog.py' -v
```

## 10. Documentacao

Criar:

```text
docs/infrastructure/deployment/images/NODE_IMAGES.md
```

Documentar:

- bases e politica de atualizacao de digest;
- comandos de CI equivalentes;
- contratos runtime e nomes de variaveis;
- portas e healths;
- Node/Chromium;
- usuarios e permissoes;
- entrypoints e idempotencia;
- como testar sem backend/WhatsApp reais;
- manutencao de lockfiles;
- limites adiados para Compose;
- como repetir build, inspect e probes;
- quando um gate pode ou nao ser fechado.

Atualizar o README do WhatsApp para remover instrucoes que recomendem
`latest`, porta publica, IP de host ou deploy manual incompatível com a
arquitetura aprovada. Preservar orientacao local util, separando claramente
desenvolvimento de producao futura. Nao documentar IP real.

## 11. Validacoes obrigatorias

Registrar CWD, comando exato, codigo de saida, resultado e interpretacao para:

### Frontend ERP

```bash
npm ci
npm run lint
npm run test
npm run build
```

### Website frontend

```bash
npm ci
npm run test
npm run build
```

### WhatsApp

```bash
npm ci
npm run test
```

### Contratos e catalogo

- validador Node;
- suite mutante do validador;
- validador estrutural do catalogo;
- readiness global;
- suite do catalogo.

### Docker

- tres builds `linux/amd64`;
- inspect sanitizado;
- history sanitizado;
- probes de runtime e health;
- verificacoes de usuario, ferramentas e permissoes;
- tamanhos finais.

Falha nao pode ser omitida. Registrar primeiro erro, causa, correcao e
repeticao. Nao transformar warning em sucesso silencioso.

## 12. Fora de escopo

Nao:

- alterar backend ou website backend;
- criar gateway, Compose, Nginx de gateway ou PostgreSQL;
- fechar persistencia de uploads do website backend;
- criar workflow GitHub Actions;
- criar ou alterar `release_control`;
- criar candidato, manifesto, release, tag ou deploy;
- acessar banco externo;
- configurar dominio, DNS, TLS ou segredo real;
- abrir PFX, HPROF, `.env.production`, upload ou sessao WhatsApp real;
- instalar ferramenta no host;
- autenticar em registry;
- acessar GitHub, GHCR ou VPS;
- enviar mensagem WhatsApp;
- fazer push de imagem;
- usar `git add`;
- criar commit, tag Git ou push;
- alterar esta task ou o tracker;
- executar prune ou limpeza Docker ampla;
- remover as imagens `:s08`.

## 13. Acesso externo permitido

Excepcionalmente, somente:

- pull anonimo das bases declaradas no Docker Hub;
- download de pacotes npm publicos exigidos pelos lockfiles;
- download de pacotes publicos da distribuicao durante os builds;
- frontend BuildKit publico necessario a sintaxe Dockerfile.

Nao acessar API de negocio, Firebase, WhatsApp, GitHub, GHCR, DNS operacional,
VPS ou registry autenticado. Registrar hosts observados quando visiveis.

## 14. Estado protegido

Ao final registrar:

```bash
git ls-files --stage
git rev-parse --verify HEAD
git tag --list
git reflog show --all
find .github/workflows -maxdepth 1 -type f \
  \( -name '*.yml' -o -name '*.yaml' \) -print
find tools/docker tools/releases \
  \( -type d -name __pycache__ -o -type f -name '*.pyc' \) -print
docker ps --filter ancestor=abaronesa-emporio-frontend:s09
docker ps --filter ancestor=abaronesa-emporio-website-front:s09
docker ps --filter ancestor=abaronesa-emporio-whatsapp-service:s09
```

Esperado:

- indice real vazio;
- `HEAD` inexistente;
- zero tags/reflog/workflow;
- zero cache Python;
- zero container de validacao em execucao;
- tres imagens locais `:s09`, sem push;
- duas imagens `:s08` preservadas.

## 15. Relatorio obrigatorio

Criar:

```text
docs/infrastructure/deployment/implementation/slices/S09-imagens-node-frontends-contratos-runtime.report.md
```

Incluir:

1. arquivos alterados;
2. bases, tags, digests e plataforma;
3. versoes Node, npm, Nginx e Chromium;
4. contrato comum das imagens;
5. contrato runtime do frontend ERP;
6. renomeacao e contrato runtime do website frontend;
7. liveness e lifecycle do WhatsApp;
8. usuarios, permissoes, packages e paths;
9. testes npm, Python e catalogo, com CWD e codigos;
10. builds e probes Docker;
11. asserts sanitizados de inspect/history;
12. tamanhos das imagens;
13. gates removidos e cinco restantes;
14. falhas intermediarias e correcoes;
15. itens nao determinados;
16. acessos externos;
17. escopo negativo;
18. estado Git, workflows, caches, imagens e containers;
19. bloqueios.

Nao reproduzir segredo, QR, sessao, identificador de conta, valor removido ou
historico Docker integral.

Estado final:

```text
IN_PROGRESS — aguardando revisao do orquestrador
```

Nao declarar `ACCEPTED`.

## 16. Criterios de aceite do orquestrador

A S09 somente podera ser aceita se:

- Node 24 for comprovado nos tres componentes;
- todos os `npm ci`, testes e builds obrigatorios passarem;
- lint do frontend passar sem desabilitar regras para obter sucesso artificial;
- os tres Docker builds `linux/amd64` passarem;
- todas as bases usarem tags exatas com digest;
- os SPAs forem servidos sem Node no runtime;
- entrypoints nao instalarem pacotes e forem seguros/idempotentes;
- o frontend preservar seu contrato runtime e health independente;
- todo codigo ativo do website usar os nomes canonicos;
- `website_back:8085` for o unico alvo interno do website;
- o website possuir teste real e health independente;
- liveness do WhatsApp for independente de autenticacao/QR;
- APIs funcionais existentes do WhatsApp permanecerem compativeis;
- o WhatsApp rodar nao-root, com Chromium e sessao em `/data/session`;
- testes do WhatsApp nao usarem rede, Chromium ou sessao reais;
- usuario, portas, health, labels, stop signal e permissoes forem provados;
- nenhum segredo ou valor real aparecer em imagem/history;
- validador Node e mutantes passarem;
- exatamente os treze gates comprovados forem removidos;
- os tres componentes mudarem para `ready`;
- catalogo e testes passarem;
- readiness global continuar falhando fechado com os cinco gates esperados;
- nenhum componente Java, gateway, Compose, workflow ou release for alterado;
- nao houver publicacao, commit, push ou acesso nao autorizado;
- documentacao e implementacao permanecerem alinhadas.

A proxima slice prevista, apos aceite, sera a S10: gateway canonico e Compose
de producao, incluindo persistencias, rede, health integrado e validacao
efemera sem acesso a VPS.
