# S17 — UI de desenvolvimento para publicação de releases globais

> **Estado:** `ACCEPTED — 29/07/2026`  
> **Tipo:** frontend operacional, integração publisher e usabilidade  
> **Executor previsto:** CLI  
> **Diretório obrigatório:** `/home/gregorio/git/baronesa/emporio`  
> **Dependências:** S01 a S16 `ACCEPTED`  
> **Relatório de saída:** `S17-ui-desenvolvimento-publicacao-releases-globais.report.md`

## Instrução para delegação

Execute integralmente esta slice. Leia, nesta ordem:

1. esta task inteira;
2. a revisão terminal da S16;
3. `release-control/IDENTIDADE_PUBLISHER.md`;
4. `release-control/RUNTIME_PUBLISHER.md`;
5. `release-control/api/publisher.openapi.yml`;
6. `release-control/CANDIDATOS.md`;
7. `release-control/RELEASES.md`;
8. `frontend/src/components/configuracoes/PainelControle.vue`;
9. `frontend/src/stores/userStore.js`;
10. `frontend/src/global.js`;
11. `frontend/src/composables/useApiRequest.js`;
12. `frontend/vitest.config.js`.

O executor implementa as decisões abaixo. Não escolha rota, localização da
tela, armazenamento do token, mecanismo de retry, idempotência, polling,
campos, mensagens de autoridade ou configuração de produção.

Não altere esta task nem o tracker:

```text
docs/infrastructure/deployment/implementation/README.md
```

## 1. Resultado observável

Ao final, no frontend ERP iniciado por `npm run dev`, o usuário de
desenvolvimento encontra no caminho já existente:

```text
Painel de Controle -> Desenvolvimento -> Gerenciamento de Releases
```

A interface:

- não executa Git, commit, push, build, tag ou deploy;
- troca o token ERP por um token publisher curto;
- lista apenas candidatos elegíveis;
- lista releases globais já publicadas;
- permite escolher candidato e incremento MAJOR/MINOR/PATCH;
- exige descrição e changelog;
- cria uma operação idempotente de publicação;
- acompanha o estado reconciliado até `PUBLISHED` ou `FAILED`;
- nunca permite ao usuário selecionar componentes ou dependências;
- não armazena o bearer token publisher fora da memória.

Esta é exclusivamente a UI publisher do ambiente de desenvolvimento. A UI
de produção/deployer permanece futura.

## 2. Fronteira autorizada

### 2.1 Criar

```text
frontend/src/config/releasePublisher.js
frontend/src/config/releasePublisher.spec.js
frontend/src/services/releasePublisherClient.js
frontend/src/services/releasePublisherClient.spec.js
frontend/src/services/releasePublisherAttempt.js
frontend/src/services/releasePublisherAttempt.spec.js
frontend/src/components/configuracoes/ReleasePublisherConfig.vue
frontend/src/components/configuracoes/ReleasePublisherConfig.spec.js
tools/releases/validate_publisher_ui.py
tools/releases/tests/test_publisher_ui_contract.py
docs/infrastructure/deployment/release-control/UI_PUBLISHER.md
docs/infrastructure/deployment/implementation/slices/S17-ui-desenvolvimento-publicacao-releases-globais.report.md
```

### 2.2 Alterar somente

```text
frontend/.env
frontend/src/components/configuracoes/PainelControle.vue
docs/infrastructure/deployment/release-control/README.md
docs/infrastructure/deployment/release-control/IDENTIDADE_PUBLISHER.md
docs/infrastructure/deployment/release-control/RUNTIME_PUBLISHER.md
docs/development/ONBOARDING_MINIMO.md
docs/development/README.md
```

Se um teste do `PainelControle.vue` for indispensável, criar somente:

```text
frontend/src/components/configuracoes/PainelControle.spec.js
```

## 3. Fora de escopo

Não:

- alterar backend ERP, `release_control` Python ou banco;
- alterar OpenAPI, schema, estado ou workflow;
- alterar Dockerfile, entrypoint, Compose, gateway ou configuração runtime
  da imagem frontend;
- adicionar rota Vue pública/dedicada;
- adicionar dependência npm ou Python;
- implementar deployer, rollback ou atualização de produção;
- permitir escolha de frontend/backend/componente;
- calcular BOM ou dependências no browser;
- executar publicação real, GitHub, GHCR, VPS, DNS ou produção;
- armazenar bearer token em `sessionStorage`, `localStorage`, IndexedDB,
  cookie, URL ou log;
- criar S18;
- executar `git add`, commit, tag ou push.

## 4. Ativação estritamente local

`frontend/.env` deve declarar:

```text
VITE_RELEASE_CONTROL_MODE=publisher
VITE_RELEASE_PUBLISHER_URL=http://127.0.0.1:8090
```

`releasePublisher.js` expõe uma função pura
`resolveReleasePublisherConfig(...)`, testável por injeção.

Contrato:

- em build/dev com `import.meta.env.DEV=true`, os modos aceitos são somente
  `disabled` e `publisher`;
- valor ausente significa `disabled`;
- valor desconhecido, inclusive `deployer`, falha fechado;
- em `disabled`, URL é ignorada e a UI não aparece;
- em `publisher`, URL é obrigatória;
- URL deve ser HTTP, host exatamente `localhost` ou `127.0.0.1`, porta
  explícita, sem userinfo, query ou fragment;
- path deve ser vazio ou `/`;
- retorno normaliza a URL sem slash final;
- em `import.meta.env.PROD=true`, o resultado é sempre `disabled`, mesmo que
  variáveis Vite estejam presentes;
- não ler `window.RuntimeConfig` nesta slice;
- não fornecer fallback hard-coded dentro do JavaScript.

Essa regra impede que o publisher local seja habilitado acidentalmente na
imagem de produção. A futura UI deployer terá configuração runtime própria.

## 5. Localização e autorização visual

Alterar somente o painel já existente:

```text
frontend/src/components/configuracoes/PainelControle.vue
```

Adicionar, dentro da aba `Desenvolvimento`, um card:

```text
Título: Gerenciamento de Releases
Descrição: Publique uma release global a partir de um candidato validado
Ícone: new_releases
Chave interna: release-publisher
Título do conteúdo: Gerenciamento de Releases
```

Regras:

- o card aparece somente quando `isRootUser` já permite a aba e a
  configuração local resolve `publisher`;
- nenhum item é injetado no menu vindo do backend;
- não criar rota adicional;
- o card carrega `ReleasePublisherConfig.vue` pelo mecanismo
  `configComponents` já existente;
- acesso forçado ao componente ainda depende do exchange, que exige
  `ROLE_SYSTEM`; ocultação visual não é autoridade.

## 6. Cliente de identidade e publisher

`releasePublisherClient.js` deve exportar uma factory por instância. Ela
recebe configuração e transportes injetáveis para os testes; em runtime usa
Axios já instalado.

### 6.1 Exchange

Endpoint ERP:

```text
POST <baseApiUrl>/api/release-control/identity/token
Authorization: Bearer <token ERP de sessionStorage>
body ausente
query ausente
```

Regras:

- ler o token ERP somente por callback `getErpToken`;
- não copiar o token ERP para outro storage;
- validar a resposta com conjunto exato:

```json
{
  "accessToken": "<string não vazia>",
  "tokenType": "Bearer",
  "expiresIn": 300,
  "scope": "release:read release:publish"
}
```

- rejeitar campo ausente, extra ou divergente;
- manter `accessToken` apenas em closure da instância;
- possuir no máximo um exchange em voo;
- chamadas concorrentes aguardam a mesma Promise;
- nunca devolver o token ao componente;
- nunca registrar request, response ou token.

### 6.2 Requests publisher

Todo request protegido usa:

```text
Authorization: Bearer <token publisher em memória>
Accept: application/json
```

Para POST JSON:

```text
Content-Type: application/json
```

Se um request publisher retornar `401`:

1. invalidar o token publisher em memória;
2. executar um único novo exchange;
3. repetir o request original exatamente uma vez;
4. um segundo `401` falha; não criar loop.

Nenhum outro status é repetido automaticamente. Em particular, o POST de
publicação nunca é repetido automaticamente por erro de rede ou status
desconhecido.

### 6.3 Capabilities

Antes de listar ou publicar, exigir:

```text
GET /api/release-control/v1/capabilities
```

Resposta exata:

```json
{
  "mode": "publisher",
  "apiVersion": "v1",
  "capabilities": ["release:read", "release:publish"]
}
```

A ordem das duas capabilities não importa; valores ausentes, extras,
duplicados ou modo divergente falham fechado e bloqueiam toda mutação.

## 7. Operações de leitura

### 7.1 Candidatos

Usar:

```text
GET /api/release-publisher/v1/candidates?eligibility=READY&limit=100
```

Aceitar somente items que satisfaçam:

```text
candidateId: string 12..128
sourceCommit: 40 hex lowercase
eligibility: READY
ciStatus: PASSED
manifestStatus: VALID
createdAt: date-time válido
```

Rejeitar item ou página divergente. Não exibir candidato
`NOT_ELIGIBLE`, `PENDING`, `FAILED` ou `INVALID`.

Quando `nextCursor` não for nulo, mostrar botão `Carregar mais`. O cursor:

- nunca é interpretado;
- tem no máximo 256 caracteres;
- é enviado com `URLSearchParams`;
- páginas são anexadas sem duplicar `candidateId`;
- duplicidade divergente falha fechado.

### 7.2 Releases

Usar:

```text
GET /api/release-publisher/v1/releases?limit=100
```

Validar cada item:

```text
release: SemVer vMAJOR.MINOR.PATCH canônica
sourceCommit: 40 hex lowercase
state: PUBLISHED
publishedAt: date-time válido
```

Carregar releases automaticamente até `nextCursor=null`, usando no máximo
dez páginas. Aplicar as mesmas regras de cursor e deduplicação pela SemVer.
Se uma décima página ainda devolver cursor, falhar fechado e bloquear a
publicação, pois a UI não conhece a release atual completa.

O histórico é ordenado no browser por SemVer numérica decrescente, sem
confiar na ordem da API.

## 8. Formulário e versão estimada

Campos exatos:

```text
Candidato
Tipo de atualização: MAJOR | MINOR | PATCH
Descrição
Changelog
```

Regras:

- candidato vem exclusivamente da lista READY;
- incremento default `PATCH`;
- descrição é trim, 1..500 caracteres;
- changelog é trim, 1..10000 caracteres;
- submit bloqueado durante operação ativa;
- não existe seleção de repositório, componente, dependência, imagem ou tag;
- mostrar release atual pela maior SemVer publicada, ou `Nenhuma`;
- mostrar `Próxima versão estimada`, calculada localmente apenas para
  orientação:

```text
sem release + MAJOR = v1.0.0
sem release + MINOR = v0.1.0
sem release + PATCH = v0.0.1
MAJOR = major+1.0.0
MINOR = major.minor+1.0
PATCH = major.minor.patch+1
```

- rotular explicitamente como estimativa;
- manifesto/workflow continuam autoridades da versão efetiva;
- antes do POST, abrir confirmação contendo candidateId, incremento e versão
  estimada;
- cancelar a confirmação não cria tentativa nem idempotency key.

Payload exato:

```json
{
  "candidateId": "<selecionado>",
  "versionBump": "MAJOR|MINOR|PATCH",
  "description": "<trim>",
  "changelog": "<trim>"
}
```

## 9. Idempotência e recuperação no browser

`releasePublisherAttempt.js` usa somente:

```text
sessionStorage["emporio.releasePublisher.pending.v1"]
```

Nunca usar `localStorage`.

### 9.1 Registro fechado

```json
{
  "schemaVersion": 1,
  "idempotencyKey": "publisher-ui-<uuid-v4>",
  "request": {
    "candidateId": "...",
    "versionBump": "PATCH",
    "description": "...",
    "changelog": "..."
  },
  "operationId": null,
  "createdAt": "<UTC ISO-8601>"
}
```

Depois do `202`, `operationId` passa ao valor recebido.

Regras:

- gerar chave com `crypto.randomUUID()`;
- formato exato
  `^publisher-ui-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`;
- persistir o registro antes do POST;
- serialização total limitada a 16 KiB;
- leitura valida conjunto exato de propriedades, tipos, limites, UUID,
  timestamp e payload;
- registro inválido é removido e nunca reutilizado;
- se `sessionStorage` estiver indisponível, bloquear publicação;
- não armazenar token, response body, trace, headers ou dados de usuário.

### 9.2 Envio e incerteza

POST:

```text
POST /api/release-publisher/v1/releases
Idempotency-Key: <chave persistida>
```

Em `202`:

- validar `PublicationOperation`;
- persistir `operationId`;
- iniciar acompanhamento.

Em erro de rede ou resposta não validável:

- manter a tentativa;
- não reenviar automaticamente;
- mostrar ação `Retomar envio`;
- a ação reutiliza exatamente request e idempotency key persistidos.

Em `409`, mostrar o código público mapeado e manter a tentativa para decisão
explícita; nunca gerar chave nova silenciosamente. Nos estados retomáveis,
oferecer exatamente:

- `Retomar envio`, reutilizando request e chave;
- `Descartar tentativa`, com confirmação explícita, apenas para remover o
  registro local — isso não cancela operação remota.

Ao recarregar a página:

- tentativa com `operationId` inicia consulta de estado automaticamente;
- tentativa sem `operationId` mostra `Retomar envio`, sem POST automático.

## 10. Acompanhamento da operação

Validar `PublicationOperation` com:

```text
operationId: ^pub_[0-9a-f]{32}$
state: REQUESTED | VALIDATING | PUBLISHING | PUBLISHED | FAILED
candidateId: igual ao request persistido
release: SemVer canônica ou null
workflowRunUrl: https URL ou null
createdAt/updatedAt: date-time válidos
errorCode: string <=100 ou null
```

Polling:

- endpoint
  `/api/release-publisher/v1/operations/{operationId}`;
- intervalos de 3 segundos;
- somente uma consulta em voo;
- somente para `REQUESTED`, `VALIDATING` ou `PUBLISHING`;
- duração máxima contínua de 10 minutos;
- `PUBLISHED` e `FAILED` param imediatamente;
- timeout para o polling, erro de rede ou saída da tela não apagam a
  tentativa;
- ao desmontar o componente, cancelar timer;
- após timeout, oferecer `Atualizar estado`;
- `workflowRunUrl` pode ser exibida como link externo somente se HTTPS;
- nunca usar o link como autoridade de estado.

Em estado terminal:

- remover o registro pendente do `sessionStorage`;
- preservar o resultado na tela até o usuário atualizar ou sair;
- recarregar candidatos e releases;
- não disparar nova publicação automaticamente.

## 11. Estados visuais obrigatórios

A tela deve possuir estados distintos e acessíveis:

```text
configuração local desabilitada
publisher indisponível
sessão ERP expirada
acesso negado
carregando
sem candidatos elegíveis
formulário pronto
confirmação
envio incerto/retomável
REQUESTED
VALIDATING
PUBLISHING
PUBLISHED
FAILED
polling pausado
```

Usar componentes Quasar já instalados. Botões e campos devem possuir labels
ou `aria-label`; loading e disabled não podem depender apenas de cor.

Não mostrar:

- token;
- raw Axios error;
- response body desconhecido;
- stack trace;
- `detail` remoto.
- `PublicationOperation.errorCode` bruto, pois ele pode conter classificação
  operacional interna.

Em `FAILED`, mostrar somente `A publicação falhou. Consulte a operação nos
logs do serviço.`; `errorCode` serve para validação do contrato, não para
renderização.

Mapear os códigos públicos:

```text
BAD_REQUEST -> Solicitação inválida.
UNAUTHORIZED -> Sessão expirada. Entre novamente.
FORBIDDEN -> Você não possui permissão para publicar releases.
NOT_FOUND -> Candidato ou operação não encontrado.
IDEMPOTENCY_CONFLICT -> A tentativa salva não corresponde à solicitação.
VERSION_RESERVATION_CONFLICT -> Outra publicação reservou essa versão.
UNPROCESSABLE -> Revise os dados informados.
RATE_LIMITED -> Muitas solicitações. Aguarde e tente novamente.
INTERNAL_ERROR -> O serviço encontrou um erro interno.
SERVICE_UNAVAILABLE -> O serviço de releases está indisponível.
```

Para `ProblemDetails` válido, pode mostrar `traceId` apenas como
`Código de suporte: <traceId>`. Código desconhecido usa mensagem genérica.

## 12. Testes causais mínimos

### 12.1 Configuração

Cobrir:

- dev disabled;
- dev publisher canônico;
- modo desconhecido/deployer;
- URL ausente;
- HTTPS, host não loopback, sem porta, userinfo, path, query e fragment;
- normalização do slash;
- produção sempre disabled, ainda que variáveis publisher existam;
- ausência de `window.RuntimeConfig`.

### 12.2 Cliente

Cobrir:

- exchange exato e validação fechada da resposta;
- token apenas em closure e ausente nos storages;
- single-flight de exchange;
- capabilities exatas e mutantes;
- 401 -> um exchange e uma repetição;
- segundo 401 termina;
- POST nunca repetido por rede/500/409;
- candidatos e releases válidos;
- item, cursor e duplicidade mutantes;
- paginação preserva cursor opaco;
- releases percorrem no máximo dez páginas e bloqueiam cursor excedente;
- payload e `Idempotency-Key` exatos;
- validação integral de `PublicationOperation`;
- ProblemDetails mapeado sem `detail` bruto.

### 12.3 Tentativa persistida

Cobrir:

- UUID v4 e schema exato;
- persistência anterior ao envio;
- restore com e sem operationId;
- chave/request reutilizados;
- propriedades extras;
- cada tipo/limite inválido;
- payload acima de 16 KiB;
- storage indisponível;
- remoção em terminal;
- nenhuma chave chamada `token` e nenhum `localStorage`.

### 12.4 Componente e painel

Com Vue Test Utils/Vitest já instalados, cobrir:

- card visível somente para root + publisher local;
- card ausente em disabled;
- nenhum router novo;
- candidato READY e release atual;
- validações do formulário;
- confirmação cancela sem tentativa;
- submit persiste antes do POST;
- rede incerta oferece retomada sem reenvio automático;
- descarte exige confirmação e não simula cancelamento remoto;
- restore com operationId consulta sem publicar;
- estados REQUESTED/VALIDATING/PUBLISHING;
- polling 3 s sem overlap;
- terminal para polling, limpa tentativa e atualiza listas;
- unmount limpa timer;
- timeout preserva recuperação;
- mensagens públicas não exibem raw detail/token.
- estado FAILED não exibe `errorCode` bruto.

Não realizar rede real nos testes. Use transportes, storage, clock e timers
injetados/fakes.

## 13. Validador estrutural

`validate_publisher_ui.py` deve falhar fechado e verificar:

- ativação local e produção disabled;
- card/chave/componente exatos;
- ausência de rota Vue nova;
- endpoints e methods exatos;
- exchange e token somente em memória;
- capabilities antes de mutação;
- lista `READY`;
- histórico integral limitado a dez páginas;
- campos e payload exatos;
- nenhuma seleção de componente;
- sessionStorage/key/schema exatos;
- ausência de localStorage;
- POST sem retry automático;
- polling, estados terminais e cleanup;
- mensagens públicas sem `errorCode` interno;
- documentação.

`test_publisher_ui_contract.py` deve executar mutantes reais em cópias
temporárias para cada grupo. Busca textual sem mutante causal não basta.

## 14. Documentação obrigatória

Criar `UI_PUBLISHER.md` com:

- finalidade e exclusões;
- pré-requisitos locais;
- caminho de navegação;
- campos e regras;
- diferença entre candidato e release;
- significado de MAJOR/MINOR/PATCH;
- versão estimada versus versão efetiva;
- estados da publicação;
- recuperação após rede/reload;
- mensagens de erro;
- segurança dos dois tokens;
- roteiro de uso;
- troubleshooting;
- limites e próximos passos.

Atualizar:

- README release control com link e estado real;
- identidade publisher marcando o cliente/UI como implementado;
- runtime publisher com o consumidor frontend;
- onboarding com as duas variáveis Vite e sequência local;
- índice de desenvolvimento.

Não afirmar publicação remota executada, credenciais reais configuradas,
deployer implementado ou produção preparada.

## 15. Matriz obrigatória

Executar no CWD indicado e persistir comando, exit, resultado e interpretação:

```bash
cd frontend
npm run lint
npm test
npm run build
```

```bash
PYTHONDONTWRITEBYTECODE=1 \
python3 tools/releases/validate_publisher_ui.py

PYTHONDONTWRITEBYTECODE=1 \
python3 -m unittest tools/releases/tests/test_publisher_ui_contract.py -v

PYTHONDONTWRITEBYTECODE=1 \
python3 -m unittest discover \
  -s tools/releases/tests \
  -p 'test_*.py'

PYTHONDONTWRITEBYTECODE=1 \
python3 tools/releases/release_control_contract.py validate

git diff --check
git diff --cached --name-only
git rev-parse --verify HEAD
git tag --list
git reflog
```

Não repetir Maven, pytest do publisher ou actionlint: suas superfícies estão
fora da fronteira S17.

## 16. Higiene e estado protegido

Antes da execução, inventariar sem alterar:

```text
frontend/node_modules
frontend/dist
caches do frontend
```

Ao final:

- não remover dependência/cache preexistente;
- remover somente `dist` ou cache criado pela própria execução, se não
  existia antes;
- nenhum token, request ou chave idempotente real em relatório/fixture;
- nenhum cache Python criado;
- índice Git vazio;
- HEAD inexistente;
- tags/reflog inexistentes;
- exatamente três workflows;
- nenhum commit, push, publicação, acesso externo ou S18.

## 17. Relatório obrigatório

Criar o relatório previsto com:

- resumo e estado;
- arquivos alterados;
- ativação local;
- fluxo de exchange e armazenamento em memória;
- contrato do cliente;
- formulário e estados visuais;
- idempotência, recovery e polling;
- lista nominal dos testes causais;
- comandos/exits/resultados/interpretação;
- falhas intermediárias e correções;
- inventário e limpeza;
- evidência de zero rede/publicação real;
- estado Git/workflows/caches;
- divergências e itens não determinados.

Para cada comando:

```text
CWD
comando exato
exit code
resultado
interpretação
artefatos/resíduos
```

Estado final obrigatório:

```text
IN_PROGRESS — aguardando revisão do orquestrador
```

Não declarar `ACCEPTED` e não criar S18.

## 18. Critérios de aceite do orquestrador

A S17 só será aceita se:

- UI estiver disponível apenas em desenvolvimento/publisher;
- exchange preceder qualquer API publisher;
- token publisher existir apenas em memória;
- capabilities falhar fechado;
- somente candidato READY puder ser enviado;
- nenhum componente/dependência for selecionável;
- idempotency key sobreviver à incerteza/reload;
- POST nunca for repetido automaticamente por erro incerto;
- polling for bounded, single-flight e recuperável;
- payload, responses e erros forem validados;
- testes, lint, build e validadores passarem;
- documentação de uso coincidir com a interface;
- fronteira e estado protegido forem preservados.

## 19. Condições de bloqueio

Pare e documente, sem improvisar, se:

- o OpenAPI não permitir implementar algum estado prescrito;
- Axios não permitir separar exchange e publisher sem interceptor global;
- Vitest não conseguir controlar timers/storage sem nova dependência;
- o painel exigir nova rota para renderizar o componente;
- for necessário alterar backend, publisher, workflow, Docker ou produção;
- algum teste tentar acessar rede não loopback.
