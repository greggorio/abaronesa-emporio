# Webhook MercadoPago

## Endpoint

```http
POST /api/webhooks/mercadopago
```

## Papel

Receber notificacoes do MercadoPago para atualizacao de status de pagamento no backend.

## Evidencia Material

- [MercadoPagoWebhookController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/MercadoPagoWebhookController.java)
- [payment-gateways/README.md](/home/gregorio/git/bakery/docs/integrations/payment-gateways/README.md)

## Headers Relevantes

- `x-signature` (opcional no controller atual)
- `x-request-id` (opcional no controller atual)

## Payload

O controller atual recebe:

```java
@RequestBody Map<String, Object> payload
```

Ou seja, o contrato aceito hoje e um payload JSON generico, sem DTO estrito no boundary HTTP.

## Resposta Atual

### Sucesso

```http
200 OK
{
  "status": "received"
}
```

### Assinatura invalida

```http
401 Unauthorized
{
  "status": "unauthorized"
}
```

### Erro interno durante o recebimento

Mesmo com excecao, o controller atual responde:

```http
200 OK
{
  "status": "error"
}
```

Essa decisao esta documentada no proprio controller para evitar retry agressivo do provedor.

## Fluxo Interno

1. Receber payload e headers
2. Validar assinatura via `MercadoPagoWebhookService`
3. Enfileirar/processar webhook de forma assincrona
4. Retornar rapidamente ao provedor

## Endpoints Auxiliares

### Teste

```http
GET /api/webhooks/mercadopago/test
```

Usado para verificar se o endpoint esta respondendo e se a configuracao minima existe.

### Reprocessamento

```http
POST /api/webhooks/mercadopago/reprocess/{webhookLogId}
```

Exige `X-Admin-Token` no estado atual do controller.

### Logs

```http
GET /api/webhooks/mercadopago/logs
```

Tambem usa `X-Admin-Token` no estado atual.

## Observacoes

- o endpoint esta liberado no `SecurityConfig`
- a validacao real de `X-Admin-Token` ainda aparece como TODO no controller
- a documentacao detalhada do provedor continua em [../../integrations/payment-gateways/README.md](../../integrations/payment-gateways/README.md)
