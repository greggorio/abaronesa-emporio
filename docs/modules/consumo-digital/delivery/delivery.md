# Delivery — Especificação

## Definição

Sub-domínio responsável por pedidos de consumo fora do estabelecimento: entrega ao domicílio (`DELIVERY`) e retirada na loja (`RETIRADA`). Inclui cardápio filtrado por canal, carrinho, checkout, pagamento digital, integração com logística externa e rastreamento em tempo real.

## Entidades

### DeliveryOrder

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `tipo` | Enum | DELIVERY ou RETIRADA |
| `status` | Enum | Ciclo de vida completo (ver abaixo) |
| `customerName`, `customerPhone`, `customerEmail`, `customerCpf` | String | Dados do cliente |
| `dropoffAddress` | String | Endereço de entrega |
| `dropoffNotes` | String | Observações de entrega (portão, apartamento, etc.) |
| `deliveryFeeCents` | Integer | Taxa de entrega em centavos |
| `itemsTotalCents` | Integer | Subtotal dos itens |
| `totalCents` | Integer | Total (itens + taxa) |
| `currency` | String | "BRL" |
| `externalId` | String | ID do pedido na Uber Direct |
| `providerGateway` | Enum | MERCADOPAGO ou PAGSEGURO |
| `clienteId` | Long | Referência ao cliente identificado |

### DeliveryOrderItem

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `produtoId` / `skuId` | Long | Produto e variação |
| `nome`, `variacao` | String | Nome e descrição da variação |
| `quantidade` | Integer | — |
| `precoUnitCents` | Integer | Preço unitário em centavos |
| `observacoes` | String | Instruções especiais |
| `status` | Enum | QUEUED → ACCEPTED → PREPARING → READY → DISPATCHED → DELIVERED / CANCELED |
| `estacao` | Enum | KITCHEN ou BAR |

## Ciclo de vida do pedido

```
PENDING_PAYMENT → PAID → PREPARING → READY → DISPATCHED → DELIVERED
                                                         → CANCELED
                                                         → EXPIRED
```

| Status | Significado |
|--------|-------------|
| PENDING_PAYMENT | Pedido criado; aguardando confirmação de pagamento |
| PAID | Pagamento confirmado; pedido liberado para preparação |
| PREPARING | Cozinha está preparando |
| READY | Pronto para retirada pelo entregador |
| DISPATCHED | Entregador saiu com o pedido |
| DELIVERED | Entregue ao cliente |
| CANCELED | Cancelado |
| EXPIRED | Pedido expirou sem pagamento |

## Integração Uber Direct

O fluxo com Uber Direct é assíncrono:

1. Pedido criado no Bakery com status `PENDING_PAYMENT`
2. Após pagamento confirmado: `UberDirectService` solicita quote de entrega
3. Uber retorna estimativa de custo e ETA
4. Pedido aceito → `externalId` da Uber armazenado
5. Entregador coletado → webhook atualiza status para `DISPATCHED`
6. Entrega confirmada → webhook atualiza para `DELIVERED`
7. `trackingUrl` exposta ao cliente para acompanhamento

A autenticação com Uber usa OAuth2. O cancelamento após despacho não está sincronizado com a Uber — limitação conhecida.

## Cardápio de delivery

O cardápio de delivery é filtrado por:
- Disponibilidade por canal (produtos habilitados para delivery)
- Horário de funcionamento do delivery
- Filtros do cliente (preço, categoria)

O proxy `CardapioProxyController` no espresso_back sincroniza o cardápio com o ERP principal via `ErpCardapioClient`.

## Escopo

**Inclui:**
- Pedidos de entrega e retirada
- Cardápio filtrado por canal e horário
- Cálculo automático de taxa de entrega
- Integração com Uber Direct (quote, criação, webhooks)
- Múltiplos gateways de pagamento (MercadoPago, PagSeguro)
- Rastreamento via trackingUrl
- Itens com estação de preparação (KITCHEN ou BAR)

**Não inclui:**
- Cancelamento pós-despacho sincronizado com Uber (gap conhecido)
- Integração com múltiplos provedores de logística (apenas Uber Direct)
- Histórico de entregas por cliente
