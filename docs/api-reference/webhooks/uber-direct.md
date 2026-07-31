# Webhook Uber Direct

## Endpoint

```http
POST /api/uber/webhooks/deliveries
```

## Papel

Receber eventos de entrega do Uber Direct para atualizar o estado logistico interno do delivery.

## Evidencia Material

- [UberWebhookController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/UberWebhookController.java)
- [uber_webhooks_sandbox.md](/home/gregorio/git/bakery/docs/integrations/uber-direct/uber_webhooks_sandbox.md)

## Payload

O controller atual recebe:

```java
@RequestBody JsonNode payload
```

Segundo a documentacao de sandbox ja existente, o processamento observa principalmente:

- `kind`
- `status`
- `delivery_id`

Eventos mencionados no fluxo atual:

- `event.delivery_status`
- `event.courier_update`
- `event.refund_request`

## Resposta Atual

### Sucesso

```http
202 Accepted
```

O controller nao retorna corpo.

## Fluxo Interno

1. Receber payload do webhook
2. Encaminhar para `UberWebhookService`
3. Processar o evento conforme tipo e status

## Endpoints Relacionados

O material de sandbox documenta tambem:

```http
GET /api/uber/webhooks/deliveries/{deliveryId}/events
```

Esse endpoint auxilia em teste e inspecao dos ultimos eventos recebidos para uma entrega.

## Observacoes

- o endpoint esta liberado no `SecurityConfig`
- a documentacao mais operacional de teste continua em [../../integrations/uber-direct/uber_webhooks_sandbox.md](../../integrations/uber-direct/uber_webhooks_sandbox.md)
- ainda faltam consolidacao de autenticacao/assinatura, persistencia e mapeamento mais formal do contrato
