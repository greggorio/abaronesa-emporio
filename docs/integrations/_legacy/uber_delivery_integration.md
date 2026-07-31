# Uber Direct - Integracao Delivery

## Objetivo
Consolidar o fluxo de delivery com Uber Direct no backend e fornecer endpoints para o front testar o ciclo completo.

## Variaveis de ambiente
Defina no backend (espresso_back) conforme suas credenciais:

- UBER_CLIENT_ID
- UBER_CLIENT_SECRET
- UBER_CUSTOMER_ID
- UBER_SCOPE (default: delivery)
- UBER_ACCESS_TOKEN (opcional, para pular login)
- UBER_PICKUP_ADDRESS
- UBER_PICKUP_NAME
- UBER_PICKUP_PHONE
- UBER_PICKUP_NOTES

## Endpoints principais

### Criar intent de pagamento (stub)
POST /api/delivery/payments/intent

Body:
{
  "customerName": "Nome",
  "customerPhone": "+5511...",
  "customerEmail": "email@dominio.com",
  "dropoffAddress": "Rua Exemplo 123, Sorocaba, SP",
  "dropoffNotes": "",
  "externalId": "opcional",
  "items": [
    {
      "produtoId": 1,
      "skuId": 2,
      "quantidade": 1,
      "observacoes": "",
      "size": "small"
    }
  ]
}

Response:
{
  "paymentId": 1,
  "status": "pending",
  "amountCents": 1500,
  "feeCents": 300,
  "currency": "brl",
  "quoteId": "dqt_...",
  "qrPayload": "PAYMENT:..."
}

### Quote (taxa antes do pagamento)
POST /api/delivery/payments/quote

Body:
{
  "customerName": "Nome",
  "customerPhone": "+5511...",
  "customerEmail": "email@dominio.com",
  "dropoffAddress": "Rua Exemplo 123, Sorocaba, SP",
  "dropoffNotes": "",
  "externalId": "opcional",
  "items": [
    {
      "produtoId": 1,
      "skuId": 2,
      "quantidade": 1,
      "observacoes": "",
      "size": "small"
    }
  ]
}

Response:
{
  "quoteId": "dqt_...",
  "feeCents": 300,
  "currency": "brl",
  "expiresAt": "2025-01-01T10:10:10Z"
}

### Simular pagamento
POST /api/delivery/payments/webhook

Body:
{
  "paymentId": 1,
  "evento": "payment.paid",
  "referenciaProvedor": "manual-test"
}

### Criar delivery (Uber)
POST /api/delivery/orders

Body:
{
  "customerName": "Nome",
  "customerPhone": "+5511...",
  "customerEmail": "email@dominio.com",
  "dropoffAddress": "Rua Exemplo 123, Sorocaba, SP",
  "dropoffNotes": "",
  "externalId": "opcional",
  "paymentId": 1,
  "items": [
    {
      "produtoId": 1,
      "skuId": 2,
      "quantidade": 1,
      "observacoes": "",
      "size": "small"
    }
  ]
}

Response:
{
  "orderId": 10,
  "deliveryId": "del_...",
  "externalId": "delivery-10",
  "status": "pending",
  "trackingUrl": "https://..."
}

### Webhook Uber
POST /api/uber/webhooks/deliveries

- O payload eh salvo em delivery_order_event.
- O campo payload.kind define o tipo do evento.
- Se status mudar, o delivery e atualizado e os itens do KDS recebem SSE.

## KDS (delivery)

### Fila delivery
GET /api/delivery/kds/queue

Response:
{
  "tickets": [
    {
      "itemPedidoId": 1,
      "pedidoId": 10,
      "estacao": "kitchen",
      "status": "queued",
      "atualizadoEm": "2025-01-01T10:00:00Z",
      "tipo": "delivery",
      "deliveryItemId": 1,
      "delivery": {
        "deliveryId": "del_...",
        "externalId": "delivery-10",
        "customerName": "Nome",
        "dropoffAddress": "Rua ..."
      },
      "item": {
        "nome": "Produto",
        "quantidade": 1,
        "observacoes": "",
        "necessitaPreparacao": true
      },
      "mesa": {
        "slug": "delivery",
        "rotulo": "DELIVERY",
        "referencia": "Uber"
      },
      "pedido": {
        "criadoEm": "2025-01-01T09:59:00Z"
      }
    }
  ]
}

### Atualizar status do item
PATCH /api/delivery/kds/tickets/{deliveryItemId}

Body:
{
  "status": "preparing"
}

Quando o status do item for "ready", o backend aciona automaticamente o endpoint da Uber
para marcar "pickup ready" (retirada liberada) e grava pickup_ready_at no delivery.

### SSE
GET /api/events/kds

Eventos relevantes:
- connected
- kds.new_item
- kds.delivery_status_changed
- kds.delivery_status

## Observacoes
- O cardapio eh buscado do ERP via /api/public/cardapio/v2 usando erp.api.url.
- O pagamento esta stubado apenas para destravar o fluxo (ajuste futuro).
