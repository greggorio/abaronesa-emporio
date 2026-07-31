# Uber Direct (sandbox) – Webhooks e fluxo de teste

## Endpoints criados (backend)
- `POST /api/uber/webhooks/deliveries` — recebe webhooks `deliveries.*` e responde `202` com `{received:true}`.
- `GET /api/uber/webhooks/deliveries/{deliveryId}/events` — lista os últimos eventos recebidos para um `delivery_id` (buffer em memória, até 50 por entrega).

Arquivos relevantes:
- `backend/src/main/java/com/smartdata/bares/controller/UberWebhookController.java`
- `backend/src/main/java/com/smartdata/bares/service/UberWebhookService.java`

## Parsing e logging
- Leitura direta do payload:
  - `kind` → tipo de evento (`event.delivery_status`, `event.courier_update`, `event.refund_request`).
  - `status` → estado da entrega quando `kind=event.delivery_status`.
  - `delivery_id` → identificador canônico.
- Log em nível INFO: `Webhook Uber recebido kind={} status={} deliveryId={} payload={}`.
- `handleEvent` já roteia pelos três tipos e deixa TODOs para persistir status/localização/refund.

## Configuração de webhook (Uber dashboard)
- URL registrada: `https://15511eea0e89.ngrok-free.app/api/uber/webhooks/deliveries` (trocar pela URL ngrok atual).
- Eventos selecionados: `deliveries.*` (no dashboard aparecem como Delivery status, Courier update, Refund request).

## Como testar manualmente
1) Backend rodando em 8080 (`./mvnw spring-boot:run` dentro de `backend`).
2) Expor com ngrok: `ngrok http 8080` e copiar a URL HTTPS.
3) Atualizar a URL do webhook no dashboard Uber com o host ngrok atual.
4) Criar entrega de teste (sandbox):
   ```bash
   curl -X POST "https://api.uber.com/v1/customers/{CUSTOMER_ID}/deliveries" \
     -H "Authorization: Bearer {ACCESS_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{
       "pickup_address": "Rua Alice Rodrigues Moreira 75, Sorocaba, SP",
       "pickup_name": "SmartData Test Pickup",
       "pickup_phone_number": "+5515996328201",
       "dropoff_address": "Rua Moacyr Figueira 75, Sorocaba, SP",
       "dropoff_name": "Adelino Freitas",
       "dropoff_phone_number": "+5515996139031",
       "manifest_items": [{ "name": "Pedido #544336", "quantity": 1, "size": "small" }],
       "external_id": "teste-003"
     }'
   ```
5) Ver eventos recebidos:
   ```bash
   curl -s "http://localhost:8080/api/uber/webhooks/deliveries/{delivery_id}/events"
   ```

## Evidência de recebimento (sandbox)
Exemplo retornado pelo GET após criar `teste-003`:
```json
{
  "id": "evt_OdbdmkKpRRmiLsaojGlvmQ",
  "kind": "event.delivery_status",
  "status": "pending",
  "delivery_id": "del_RQSImeN6Sd-eCAriQGMnUg",
  "data": {
    "status": "pending",
    "pickup": { "address": "...", "name": "SmartData Test Pickup" },
    "dropoff": { "address": "Rua Moacyr Figueira 75, Sorocaba, SP" },
    "manifest_items": [{ "name": "Pedido #544336", "quantity": 1, "size": "small" }]
  }
}
```

## Próximos passos sugeridos
- Persistir estado de entregas (tabela local) e mapear `delivery_id` ↔ `external_id`.
- Processar `event.delivery_status` atualizando status interno e notificar via SSE.
- Armazenar/usar localização/ETA em `event.courier_update`.
- Registrar e tratar `event.refund_request`.
- Adicionar autenticação/assinatura de webhook se disponível no Uber Direct (ver docs).***
