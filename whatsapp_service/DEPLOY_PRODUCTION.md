# Deploy em Produção — WhatsApp + Backend/Frontend

Este guia explica, passo a passo, como colocar em produção o envio de comprovantes por WhatsApp (microserviço whatsapp-web.js) integrado ao backend (roupas_back) e à UI (smartdata_erp), sem alterar a sua stack existente.

## Visão Geral
- Microserviço independente (Node + whatsapp-web.js) exposto em `:3001`.
- Backend consome o microserviço via `APP_WHATSAPP_SERVICE_URL`.
- UI (Painel → Comunicações → WhatsApp) gerencia a sessão (QR/Conectar/Desconectar) e permite teste de envio.

## Pré‑requisitos
- Acesso ao servidor (SSH) com Docker instalado.
- Diretórios:
  - App: `/opt/sistemas/contente_erp` (docker‑compose do ERP)
  - Microserviço: `/opt/sistemas/whatsapp_service`
  - Sessão persistente do WhatsApp: `/opt/sistemas/wa-session`

## 1) Microserviço WhatsApp (whatsapp-web.js)

Crie os arquivos no servidor (ou copie a pasta `whatsapp_service/` deste repositório):

- `package.json`
- `index.js`
- `Dockerfile`

Exemplo de estrutura esperada em `/opt/sistemas/whatsapp_service`:

```
/opt/sistemas/whatsapp_service
├─ package.json
├─ index.js
└─ Dockerfile
```

Build + run do container (sem tocar no seu docker-compose existente):

```
cd /opt/sistemas/whatsapp_service

docker build -t whatsapp-service:latest .

docker rm -f whatsapp-service || true

docker run -d --name whatsapp-service \
  -p 3001:3001 \
  -e PORT=3001 \
  -e SESSION_DIR=/data/session \
  -e BASE_COUNTRY_CODE=55 \
  -v /opt/sistemas/wa-session:/data \
  whatsapp-service:latest
```

Verifique se o serviço está de pé:

```
curl -s http://127.0.0.1:3001/status
# Esperado: {"connected":false,"hasQr":true} (se ainda não conectou)

curl -s http://127.0.0.1:3001/qr | head -c 120
# Esperado: JSON com png base64 quando hasQr=true
```

Notas:
- O serviço roda em modo headless utilizando Chromium (instalado via Dockerfile).
- A sessão (login) é persistida no volume `/opt/sistemas/wa-session`.

## 2) Backend — Variáveis de Ambiente

No `.env` do ERP em produção (`/opt/sistemas/contente_erp/.env`) adicione/ajuste:

```
APP_WHATSAPP_ENABLED=true
APP_WHATSAPP_SERVICE_URL=http://SEU_IP_PUBLICO:3001
```

Importante:
- Se o backend roda em container, **não use** `127.0.0.1` na URL — use o IP do host (ex.: `http://31.97.251.16:3001`).

Reinicie somente o backend:

```
cd /opt/sistemas/contente_erp

docker compose up -d backend
```

## 3) UI (Painel) — Gestão do WhatsApp

Na UI do ERP:
- Acesse: Painel → Comunicações → WhatsApp.
- Status: badge “Conectado/Desconectado”.
- Conectar (QR): abre o QR no modal. Ao escanear, o painel troca para “Conectado”.
- Desconectar: encerra a sessão (útil para trocar de conta).
- Teste de envio: informe um telefone e envie um PDF de teste.

## 4) Flags (opcional) — Exibir/Salvar no Painel

Para refletir as configs do WhatsApp no Painel (sem alterar o `.env`), existem duas chaves em `Flags`:
- `whatsapp_enabled` (ex.: `true`)
- `whatsapp_service_url` (ex.: `http://31.97.251.16:3001`)

Criação via API (sem token):

```
# Habilitar WhatsApp
curl -X PUT 'https://SEU_DOMINIO/api/configs/config/whatsapp_enabled' \
  -H 'Content-Type: application/json' \
  --data '{"valor":"true"}'

# URL do serviço
curl -X PUT 'https://SEU_DOMINIO/api/configs/config/whatsapp_service_url' \
  -H 'Content-Type: application/json' \
  --data '{"valor":"http://SEU_IP_PUBLICO:3001"}'
```

Leitura via API (autenticado):

```
# Obtenha um token via login (Accept: application/json)
TOKEN=$(curl -s -X POST 'https://SEU_DOMINIO/api/auth/login' \
  -H 'Content-Type: application/json' -H 'Accept: application/json' \
  --data '{"email":"USUARIO","password":"SENHA"}' | jq -r .accessToken)

# Leia os valores
curl -H "Authorization: Bearer $TOKEN" \
  'https://SEU_DOMINIO/api/configs/config/whatsapp_enabled'

curl -H "Authorization: Bearer $TOKEN" \
  'https://SEU_DOMINIO/api/configs/config/whatsapp_service_url'
```

Observação:
- Se ao criar as flags ocorrer erro de PK (duplicate key), a sequence de `flags` pode estar desatualizada. Corrija com:

```
# Dentro do container do Postgres
psql -U <usuario> -d <db> -c \
  "SELECT setval('flags_id_seq', (SELECT COALESCE(MAX(id),1) FROM flags)+1, false);"
```

## 5) Validação de ponta a ponta

1) Microserviço:
   - `GET /status` → `connected`/`hasQr` ok
   - `GET /qr` → base64 PNG quando há QR
2) Backend:
   - `GET /api/whatsapp/status` → reflete o estado do serviço
   - `POST /api/whatsapp/start` → inicia sessão e gera QR
   - `GET /api/whatsapp/qr` → retorna QR (quando disponível)
   - `POST /api/whatsapp/disconnect` → encerra sessão
   - `GET /api/whatsapp/me` → retorna conta conectada (wid/pushname)
3) UI (Painel):
   - Conectar (QR) → escanear; painel muda para “Conectado” e exibe conta
   - Teste de envio → mensagem com PDF chega ao número informado

## 6) Operação & Notas

- Logout no app oficial do WhatsApp:
  - O microserviço foi ajustado para **não cair** nesse cenário. Ele recria o client automaticamente e volta a exibir QR.
- “QR indisponível” na primeira tentativa:
  - A UI faz polling leve de `/qr` e `/status`. Assim que o QR é emitido, o modal exibe; e ao conectar, o modal fecha.
- Atualizações do microserviço:
  - Para atualizar: substitua `index.js` e recrie o container com `docker build + docker run` conforme acima.

## 7) Solução de Problemas

- Backend retorna 401 em `/api/configs` ou `/api/configs/config/...`:
  - Endpoints de leitura exigem token JWT (faça login e use `Authorization: Bearer ...`).
  - Only `PUT /api/configs/config/{chave}` é público para permitir atualizar flags.

- Backend retorna 404 em `/api/configs/config/whatsapp_enabled`:
  - Significa que a flag ainda **não foi criada**. Crie via `PUT` como mostrado acima.

- Backend não consegue falar com o serviço (status/qr vazios):
  - Verifique `APP_WHATSAPP_SERVICE_URL` no `.env`. Se backend roda em container, **use o IP do host**, não `127.0.0.1`.

- Microserviço cai ao fazer logout no app:
  - A versão atual trata esse caso recriando o client e mantendo o processo ativo.

---

Com isso, o envio de comprovantes PDF via WhatsApp fica disponível em produção, com gestão da sessão pelo painel e sem alterar o docker‑compose do ERP.

