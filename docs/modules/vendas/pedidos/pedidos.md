# Pedidos — Especificação

## Entidades

### Pedido (`Pedido.java`, 60 linhas)

Tabela `pedido`. Agrupamento de itens solicitados por um convidado ou pela mesa.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK |
| `sessaoMesa` | `@ManyToOne` → `SessaoMesa` | Sessão de consumo |
| `sessaoConvidado` | `@ManyToOne` → `SessaoConvidado` | Convidado que pediu (nullable = pedido da mesa) |
| `status` | `StatusPedido` | `PENDING, ACCEPTED, PREPARING, READY, DELIVERED, CANCELED` |
| `origem` | `String` | `pwa` (self-service) ou `staff` (garçom) |
| `criadoEm` | `LocalDateTime` | |
| `aceitoEm` | `LocalDateTime` | |
| `entregueEm` | `LocalDateTime` | |
| `canceladoEm` | `LocalDateTime` | |
| `motivoCancelamento` | `String` | |
| `itens` | `List<ItemPedido>` | `@OneToMany(cascade = ALL)` |

### ItemPedido (`ItemPedido.java`, 64 linhas)

Tabela `item_pedido`. Linha individual do pedido.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK |
| `pedido` | `@ManyToOne` → `Pedido` | |
| `produto` | `@ManyToOne` → `Produto` | |
| `sku` | `@ManyToOne` → `ProdutoSKU` | Variação (nullable) |
| `quantidade` | `BigDecimal` | |
| `precoUnitario` | `BigDecimal` | |
| `observacoes` | `String` | Ex: "sem cebola", "ponto mal passado" |
| `status` | `StatusItem` | `QUEUED, ACCEPTED, PREPARING, READY, DELIVERED, CANCELED` |
| `motivoCancelamentoCodigo` | `MotivoCancelamentoItem` | (ver abaixo) |
| `estacao` | `String` | `kitchen` ou `bar` — define para onde o item vai no KDS |

## Enums

### StatusPedido / StatusItem

```
QUEUED → ACCEPTED → PREPARING → READY → DELIVERED
  ↓         ↓          ↓          ↓
CANCELED  CANCELED   CANCELED   CANCELED
```

- **QUEUED**: aguardando aceite da cozinha/bar
- **ACCEPTED**: cozinha/bar aceitou — momento da baixa de estoque
- **PREPARING**: em preparo
- **READY**: pronto para entrega
- **DELIVERED**: entregue ao cliente
- **CANCELED**: cancelado (a qualquer momento do fluxo)

### MotivoCancelamentoItem

| Valor | Descrição |
|-------|-----------|
| `FALTA_INSUMO` | Sem insumo para preparar |
| `EQUIPE_INDISPONIVEL` | Sem profissional para preparar |
| `ERRO_PEDIDO` | Pedido errado |
| `CLIENTE_DESISTIU` | Cliente desistiu |
| `OUTRO` | Outro motivo |

## Serviço

### PedidoService (242 linhas)

**Método principal**: `atualizarStatusItem(itemPedidoId, novoStatus, motivoCodigo, motivoDetalhe)`

Regras por transição:
- `ACCEPTED`: executa `processarBaixaInsumos(item)` + `processarBaixaSku(item)`
- `CANCELED`: executa estorno de insumos e SKU (reversão da baixa)
- `DELIVERED`: apenas marca como entregue
- Demais transições: apenas atualizam o status

**Baixa de insumos** (`processarBaixaInsumos`):
1. Para cada `ItemPedido`, busca o `Produto`
2. Busca a `FichaTecnica` do produto (lista de insumos com quantidade)
3. Para cada insumo da ficha: decrementa `estoque_produto_setor.quantidade_base` pelo `fator_base * item.quantidade`
4. Para SKU: decrementa `sku.estoque.quantidade` por `item.quantidade`

**Estorno de cancelamento**:
- Reverte a operação: incrementa `estoque_produto_setor.quantidade_base` e `sku.estoque.quantidade`

## Controllers

### PedidosController (297 linhas) — `/api/pedidos`

| Método | Path | Descrição | Auth |
|--------|------|-----------|------|
| `POST` | `/api/pedidos` | Criar pedido (PWA) | Convidado (guestToken) |
| `GET` | `/api/pedidos/{pedidoId}` | Buscar pedido | Convidado |
| `PATCH` | `/api/pedidos/itens/{itemPedidoId}/status` | Atualizar status do item | KDS/Staff |
| `GET` | `/api/pedidos/me/favoritos` | Produtos favoritos do usuário | Usuário |

### AdminPedidoController (335 linhas) — `/api/admin/mesas/...` (`@PreAuthorize ADMIN/SYSTEM/WAITER/CAIXA`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/admin/mesas/sessoes/{sessaoMesaId}/convidados` | Listar convidados da sessão |
| `POST` | `/api/admin/mesas/sessoes/{sessaoMesaId}/pedidos` | Criar pedido (staff) |
| `POST` | `/api/admin/mesas/balcao/pedidos` | Balcão expresso |

### AdminItemController (87 linhas)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/admin/itens/sessoes/{sessaoMesaId}` | Listar itens por sessão |
| `POST` | `/api/admin/itens/{itemPedidoId}/cancelar` | Cancelar item (com verificação de pagamento) |

### AdminCancelamentoController (86 linhas)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/admin/cancelamentos/hoje` | KPIs de cancelamento do dia |
| `GET` | `/api/admin/cancelamentos` | Listar cancelamentos por período |

## Fluxo de criação de pedido

### Self-service (PWA)
1. Cliente navega pelo cardápio em `MesaPage.tsx`
2. Adiciona itens ao carrinho (com SKU, observações, quantidade)
3. Confirma → `POST /api/pedidos` com `itens[]` + `guestToken` no header
4. Backend cria `Pedido` com `origem = "pwa"` e status `PENDING`
5. Backend cria `ItemPedido` para cada item com status `QUEUED`
6. Evento SSE `order.created` é emitido para o KDS

### Staff (garçom)
1. Garçom abre `MesasGrid.tsx` ou `FastSaleDrawer.tsx`
2. Seleciona mesa/sessão e produtos
3. `POST /api/admin/mesas/sessoes/{id}/pedidos` com `origem = "staff"`
4. Mesmo fluxo de criação de itens

### KDS (atualização de status)
1. Cozinha/bar vê novos itens no KDS (status `QUEUED`)
2. Aceita → `PATCH /api/pedidos/itens/{id}/status` → `ACCEPTED` → baixa estoque
3. Prepara → `PREPARING`
4. Finaliza → `READY`
5. Garçom entrega → `DELIVERED`
6. Cada transição emite evento SSE `kds.status_changed`

## Regras de negócio

1. **Baixa de estoque no ACCEPTED, não no pedido**: evita baixar estoque de pedidos que a cozinha não consegue atender. Se o pedido for cancelado após aceito, o estoque é estornado automaticamente
2. **Cancelamento bloqueado se pago**: `AdminItemController.cancelar()` verifica se já existe pagamento vinculado à sessão para o item. Se sim, não permite cancelamento
3. **Estação do item**: definida pelo campo `estacao` do produto (`kitchen` ou `bar`), direciona o item para a tela correta no KDS
4. **Motivo de cancelamento obrigatório**: todo cancelamento exige um código do enum `MotivoCancelamentoItem`
