# KDS — Especificação

## Definição

Kitchen Display System: fila unificada de preparação que exibe, em tempo real, todos os pedidos pendentes de mesa e delivery, organizados por estação de preparação (KITCHEN ou BAR).

## Estrutura do ticket (KdsTicket)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `itemPedidoId` | number | Identificador do item |
| `pedidoId` | number | Pedido ao qual pertence |
| `estacao` | Enum | `kitchen` ou `bar` |
| `status` | Enum | Estado atual do item (ver ciclo abaixo) |
| `tipo` | Enum | `mesa` ou `delivery` — origem do pedido |
| `item.nome` | string | Nome do produto |
| `item.quantidade` | number | Quantidade |
| `item.observacoes` | string | Instruções especiais do cliente |
| `item.variacao` | string | SKU/variação selecionada |
| `item.necessitaPreparacao` | boolean | Se requer preparo ou é entregue como está |
| `mesa.slug` / `mesa.rotulo` | string | Identificação da mesa |
| `mesa.referencia` | string | Localização física |
| `pedido.criadoEm` | string | Timestamp — usado para calcular tempo de espera |

Para pedidos de delivery, o ticket inclui adicionalmente:
- `delivery.clienteNome`, `delivery.clienteEndereco`
- `delivery.status` (status no Uber Direct)
- `deliveryItemId` para rastreabilidade

## Ciclo de status dos itens

```
QUEUED → ACCEPTED → PREPARING → READY → DELIVERED
                              → CANCELED
```

| Transição | Ação |
|-----------|------|
| QUEUED → ACCEPTED | Cozinha reconhece o pedido |
| ACCEPTED → PREPARING | Inicia a preparação |
| PREPARING → READY | Item pronto para entrega |
| READY → DELIVERED | Waiter confirmou entrega |
| Qualquer → CANCELED | Item cancelado (pelo staff ou cliente) |

Cada transição via `PUT /api/kds/items/{itemId}/status` publica evento SSE imediatamente — o Waiter recebe `item.ready` sem precisar consultar o KDS.

## Estações

| Estação | Descrição |
|---------|-----------|
| KITCHEN | Cozinha quente — pratos, entradas |
| BAR | Bebidas, drinks, sobremesas |

Cada estação filtra sua própria fila. A tela do KDS pode ser configurada para exibir uma estação específica via `StationFilter`.

## Fila e ordenação

`GET /api/kds/queue` retorna todos os tickets com status QUEUED, ACCEPTED, PREPARING ou READY, ordenados por `criadoEm`. O tempo de espera é calculado no frontend a partir do `pedido.criadoEm`.

## Alertas

O KDS emite alertas sonoros quando novos itens chegam na fila. O controle de volume é configurável na interface.

## Escopo

**Inclui:**
- Fila unificada de mesa e delivery
- Duas estações independentes (KITCHEN e BAR)
- Ciclo de status completo com transições auditadas
- Sincronização em tempo real via SSE
- Filtro por estação
- Alertas sonoros de novos pedidos
- Cancelamento de itens

**Não inclui:**
- Priorização por SLA ou urgência (gap identificado)
- Impressão automática de tickets (estrutura existe; não validada)
- Reimpressão de tickets perdidos
