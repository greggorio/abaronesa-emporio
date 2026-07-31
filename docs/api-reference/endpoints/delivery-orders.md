# Delivery Orders

## Base Path

```http
/api/delivery/orders
```

## Papel

Esses endpoints cobrem criacao, atualizacao, pagamento por cartao e consulta de pedidos de delivery.

## Evidencia Material

- [DeliveryOrderController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/DeliveryOrderController.java)

## Endpoints Principais

### Criar Pedido

```http
POST /api/delivery/orders
```

Recebe `DeliveryOrderRequest` e cria um pedido.

Contexto de usuario:

- `@AuthenticationPrincipal UserPrincipal`
- `X-User-ID` opcional

### Atualizar Pedido

```http
PUT /api/delivery/orders/{orderId}
```

Permite atualizar pedido existente enquanto o fluxo ainda estiver em estado editavel.

### Pagar com Cartao

```http
POST /api/delivery/orders/{orderId}/pay-card
```

Recebe `DeliveryCardPaymentRequest` e executa quitacao do pedido por cartao.

### Consultar Pedido

```http
GET /api/delivery/orders/{orderId}
```

Retorna `DeliveryOrderView`.

Se nao encontrar:

- `404 Not Found`

### Consultar Pedido Bruto

```http
GET /api/delivery/orders/{orderId}/raw
```

Retorna a entidade `DeliveryOrder` sem a projecao de view.

### Pedido Ativo do Usuario

```http
GET /api/delivery/orders/my/active
```

Usa `UserPrincipal` ou `X-User-ID`.

Se nao houver usuario resolvido ou pedido ativo:

- `204 No Content`

## Observacoes

- o fluxo funcional do delivery continua em [../../modules/consumo-digital/delivery/README.md](../../modules/consumo-digital/delivery/README.md)
- a integracao logistica com Uber Direct esta em [../../integrations/uber-direct/README.md](../../integrations/uber-direct/README.md)
