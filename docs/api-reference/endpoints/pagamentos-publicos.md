# Pagamentos Publicos

## Base Paths

```http
/api/pagamentos
/api/payments/status
```

## Papel

Estes endpoints suportam o fluxo publico de quitacao em mesa e o acompanhamento de status de pagamento.

## Evidencia Material

- [PagamentosController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/PagamentosController.java)
- [PaymentStatusController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/PaymentStatusController.java)

## Endpoints Principais

### Criar Intent de Pagamento

```http
POST /api/pagamentos/intent
```

Cria intent de pagamento para self-checkout.

Headers relevantes:

- `X-Guest-Token` obrigatorio no estado atual do controller

O controller valida, entre outros pontos:

- `escopo` (`convidado|mesa`)
- `metodo` (`pix|card`)
- coerencia entre convidado, mesa e permissao de pagamento

Resposta:

- `200 OK`
- corpo do tipo `SelfCheckoutPaymentResponse`

### Webhook Interno de Pagamento

```http
POST /api/pagamentos/webhook
```

Usado no fluxo atual para marcar pagamento como concluido a partir de evento `payment.paid`.

Resposta tipica:

- `200 OK`

### Consultar Status de Pagamento

```http
GET /api/payments/status
```

Parametros:

- `externalReference` obrigatorio
- `gateway` opcional

Comportamento relevante:

- se nao encontrar pagamento, responde `200 OK` com status normalizado `PENDING`
- evita `404` para nao quebrar polling do frontend

## Observacoes

- esses endpoints aparecem liberados no `SecurityConfig`
- o dominio funcional da quitacao continua em [../../modules/consumo-digital/pagamentos/README.md](../../modules/consumo-digital/pagamentos/README.md)
- o fluxo de mesa relacionado continua em [../../modules/consumo-digital/self-checkout/README.md](../../modules/consumo-digital/self-checkout/README.md)
