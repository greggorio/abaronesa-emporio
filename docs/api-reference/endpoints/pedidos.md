# Pedidos

## Base Path

```http
/api/pedidos
```

## Papel

Esses endpoints cobrem pedido em mesa, favoritos do usuario e atualizacao de status de itens.

## Evidencia Material

- [PedidosController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/PedidosController.java)
- [AdminPedidoController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/admin/AdminPedidoController.java)

## Endpoints Publicos e Customer-Facing

### Favoritos do Usuario

```http
GET /api/pedidos/me/favoritos
```

Parametro:

- `limit` opcional, default `5`

Retorna lista de `ProdutoFrequenteDTO`.

### Criar Pedido

```http
POST /api/pedidos
```

Headers relevantes:

- `X-Guest-Token` obrigatorio
- `X-Sessao-Mesa` opcional

Regras observadas no controller:

- a sessao do convidado precisa existir
- a mesa informada precisa corresponder ao convidado, quando enviada
- a sessao nao pode estar encerrada
- a lista de itens e obrigatoria

Resposta:

- `200 OK`
- corpo do tipo `CriarPedidoResponse`

O fluxo atual tambem dispara:

- SSE `order.created`
- notificacao ao host da mesa
- eventos `kds.new_item`

### Consultar Pedido

```http
GET /api/pedidos/{pedidoId}
```

Retorna `CriarPedidoResponse`.

### Atualizar Status de Item

```http
PATCH /api/pedidos/itens/{itemPedidoId}/status
```

Recebe corpo com pelo menos:

```json
{
  "status": "accepted"
}
```

O controller tambem observa:

- `motivoCodigo`
- `motivoDetalhe`

## Endpoints Administrativos Relacionados

### Listar Convidados da Sessao

```http
GET /api/admin/mesas/sessoes/{sessaoMesaId}/convidados
```

### Criar Pedido Staff

```http
POST /api/admin/mesas/sessoes/{sessaoMesaId}/pedidos
```

### Criar Pedido de Balcao

```http
POST /api/admin/mesas/balcao/pedidos
```

Esses endpoints ficam sob `@PreAuthorize("hasAnyRole('ADMIN','SYSTEM','WAITER','CAIXA')")`.

## Observacoes

- os endpoints `customer-facing` de pedido aparecem liberados em `SecurityConfig`
- o dominio funcional do pedido continua em [../../modules/vendas/pedidos/README.md](../../modules/vendas/pedidos/README.md) e [../../modules/consumo-digital/mesa-digital/README.md](../../modules/consumo-digital/mesa-digital/README.md)
