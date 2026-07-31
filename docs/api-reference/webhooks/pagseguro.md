# Webhook PagSeguro

## Endpoint

```http
POST /api/webhooks/pagseguro
```

## Papel

Receber notificacoes do PagSeguro e traduzi-las para o modelo interno de atualizacao de status de pagamento.

## Evidencia Material

- [PagSeguroWebhookController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/PagSeguroWebhookController.java)
- [payment-gateways/README.md](/home/gregorio/git/bakery/docs/integrations/payment-gateways/README.md)

## Payload

O controller atual recebe o corpo bruto:

```java
@RequestBody String rawBody
```

Esse corpo e entregue a `PagSeguroStatusMapper`, que converte o payload externo em `PaymentStatusUpdate`.

## Resposta Atual

### Sucesso

```http
200 OK
```

O controller atual nao retorna corpo.

## Fluxo Interno

1. Receber corpo bruto do webhook
2. Registrar log do recebimento
3. Converter o payload externo via `PagSeguroStatusMapper`
4. Encaminhar o resultado para `PaymentStatusUpdater`

## Observacoes

- o endpoint esta liberado no `SecurityConfig`
- o contrato HTTP e propositalmente simples; a inteligencia de parsing esta fora do controller
- o mapeamento detalhado de status ainda deve ser aprofundado na documentacao da integracao
