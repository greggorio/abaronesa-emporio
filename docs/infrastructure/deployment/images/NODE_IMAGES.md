# Imagens Node e Nginx

## Bases e plataforma

As tres imagens usam `linux/amd64` nesta etapa. Builds Node e runtime WhatsApp
usam `node:24.13.0-alpine3.23`, fixado pelo digest OCI
`sha256:cd6fb7efa6490f039f3471a189214d5f548c11df1ff9e5b181aa49e22c14383e`.
Os SPAs usam `nginx:1.29.5-alpine3.23`, fixado por
`sha256:1eff5a5f3fcf8431a0abb7eddf5471fec24e5e1905a2581aeacdb07a4479b92b`.

Para atualizar uma base, selecione uma tag completa, resolva novamente o
digest, execute todos os `npm ci`, lint/test/build, reconstrua as tres imagens
e repita inspect, history, permissoes e probes. Alias flutuante e `latest` nao
sao aceitos.

## Frontend ERP

O build usa `npm ci` e o Quasar local por `npm run build`. O runtime Nginx nao
contem Node/npm. No startup, `VITE_BASE_API_URL` e obrigatoria, validada como
HTTP(S) e serializada por `jq` em:

```javascript
window.RuntimeConfig = { apiBaseUrl: "..." };
```

O entrypoint nao registra o valor, troca arquivos temporarios de forma
atomica, injeta uma unica tag de script, torna config e HTML somente leitura e
termina com `exec`. A porta e `80`; o health independente e `/healthz`.

## Website frontend

As duas configuracoes obrigatorias e distintas sao:

```text
VITE_ERP_API_URL     -> RuntimeConfig.erpApiUrl
VITE_WEBSITE_API_URL -> RuntimeConfig.websiteApiUrl
```

O tema tenta primeiro `http://website_back:8085`, com tres tentativas e timeout
de tres segundos. O fallback usa apenas `VITE_WEBSITE_API_URL`; indisponibilidade
mantem defaults publicos e nao bloqueia o SPA. JSON e serializado com `jq`,
conteudo SEO e escapado para HTML e reinicializacao nao duplica config ou SEO.
O health local e `/healthz`, independente dos backends.

## WhatsApp

O runtime usa Node `24.13.0`, npm `11.6.2` e Chromium da distribuicao Alpine,
com executable path explicito. A imagem executa como `10001:10001`; codigo e
manifestos sao somente leitura e `/data/session` e o unico path persistivel da
aplicacao, com modo `0700`.

`GET /health/live` retorna exatamente `{"status":"UP"}` e mede apenas o
processo HTTP/event loop. `/status` continua separado, com `connected` e
`hasQr`. O servidor sobe antes da inicializacao externa e falhas desta sao
sanitizadas sem derrubar liveness. A suite usa cliente falso e nao abre
Chromium, sessao ou rede externa.

`WHATSAPP_INITIALIZATION_DISABLED=true` serve somente às probes locais da
imagem. O default de producao continua inicializando o cliente, e a chave nao
simula conexao nem libera endpoints funcionais.

## Comandos equivalentes de CI

Em ambiente Node 24:

```bash
cd frontend
npm ci
npm run lint
npm run test
npm run build

cd ../website_front
npm ci
npm run test
npm run build

cd ../whatsapp_service
npm ci
npm run test
node --check index.js
node --check app.js
```

Builds locais:

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

Use inspect sanitizado, verificacao dirigida de history e containers
descartaveis. URLs de probes devem ser ficticias e locais.

## Lockfiles, gates e limites

Lockfiles sao mantidos por `npm ci`, que nao pode reescreve-los. Mudanca
intencional de dependencia exige diff, testes e justificativa separados.
Warnings de dependencias ou auditoria nao devem ser ocultados por
`npm audit fix` automatico.

Um gate somente fecha depois de testes, build da imagem, inspect e health
passarem. Persistencias efetivas, limites de CPU/memoria/processos, redes,
restart policy e health integrado pertencem ao Compose canonico. Estas imagens
`:s09` sao artefatos locais de validacao; nenhuma imagem foi publicada.
