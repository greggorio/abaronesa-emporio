# WhatsApp Web Service

Servico HTTP interno usado pelo backend para o lifecycle do cliente WhatsApp e
envio de PDFs.

## Endpoints

- `GET /health/live` retorna somente `{"status":"UP"}` e nao depende de
  autenticacao, QR, Chromium conectado ou rede externa.
- `GET /status` retorna separadamente `connected` e `hasQr`.
- `GET /qr`, `POST /start`, `POST /disconnect`, `GET /me` e
  `POST /send-pdf` preservam o contrato funcional existente.

## Desenvolvimento local

Requer Node 24 e Chromium instalado:

```bash
npm ci
npm run test
SESSION_DIR=./session PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium npm start
```

A sessao local e o QR sao dados privados e nao devem ser versionados,
registrados em documentacao ou usados pela suite automatizada.

## Runtime futuro de producao

A imagem usa Node 24, Chromium explicitamente configurado e usuario nao-root.
Somente `/data/session` e o path persistivel da aplicacao. A publicacao de
porta, volume, recursos e restart policy sera definida exclusivamente no
Compose canonico; esta documentacao nao recomenda `latest`, bind de porta
publica, IP de host ou deploy manual.

`WHATSAPP_INITIALIZATION_DISABLED=true` existe apenas para prova local do
health da imagem: impede o bootstrap externo, mantendo todos os endpoints
funcionais no estado desconectado. O default e inicializar normalmente e a
chave nao simula autenticacao nem libera operacoes.
