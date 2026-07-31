# Pedidos de Compra — Especificação

## Entidades

### PedidoCompra (`PedidoCompra.java`, 75 linhas)

Tabela `pedido_compra`.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK, `@GeneratedValue(IDENTITY)` |
| `fornecedor` | `@ManyToOne(LAZY)` → `Fornecedor` | `fornecedor_id`, opcional |
| `usuario` | `@ManyToOne(LAZY)` → `Usuario` | `usuario_id` |
| `status` | `StatusPedidoCompra` | `RASCUNHO, ENVIADO, PARCIAL, RECEBIDO, CANCELADO` |
| `dataPrevista` | `LocalDate` | Data prevista para entrega |
| `observacao` | `String` | `columnDefinition = TEXT` |
| `itens` | `List<PedidoCompraItem>` | `@OneToMany(cascade = ALL, orphanRemoval = true)` |
| `criadoEm` | `LocalDateTime` | `updatable = false` |
| `atualizadoEm` | `LocalDateTime` | |

**Ciclo de vida**: `@PrePersist` define `criadoEm`; `@PreUpdate` define `atualizadoEm`.

**Métodos**:
- `addItem(PedidoCompraItem item)` — adiciona item à coleção e seta a referência bidirecional
- `removeItem(PedidoCompraItem item)` — remove item da coleção

### PedidoCompraItem (`PedidoCompraItem.java`, 59 linhas)

Tabela `pedido_compra_item`.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK, `@GeneratedValue(IDENTITY)` |
| `pedido` | `@ManyToOne(LAZY)` → `PedidoCompra` | `pedido_compra_id`, `nullable = false` |
| `produto` | `@ManyToOne(LAZY)` → `Produto` | `produto_id`, `nullable = false` |
| `sku` | `@ManyToOne(LAZY)` → `ProdutoSKU` | `sku_id`, para vendáveis |
| `embalagem` | `@ManyToOne(LAZY)` → `Embalagem` | `embalagem_id`, para insumos |
| `quantidade` | `BigDecimal(15,3)` | `nullable = false` |
| `quantidadeBase` | `Integer` | Derivado: `quantidade * embalagem.fatorBase` |
| `custoUnitario` | `BigDecimal(15,4)` | |
| `subtotal` | `BigDecimal(15,2)` | |
| `recebidoBase` | `Integer` | Default `0` |
| `status` | `StatusPedidoCompraItem` | `PENDENTE, PARCIAL, RECEBIDO, CANCELADO` |

### StatusPedidoCompra (enum, 10 linhas)

```java
RASCUNHO, ENVIADO, PARCIAL, RECEBIDO, CANCELADO
```

### StatusPedidoCompraItem (enum, 9 linhas)

```java
PENDENTE, PARCIAL, RECEBIDO, CANCELADO
```

## DTOs

| DTO | Tipo | Campos |
|-----|------|--------|
| `PedidoCompraDTO` | class (`@Data @Builder`) | `id, fornecedorId, fornecedorNome, status, dataPrevista, observacao, criadoEm, atualizadoEm, itens (List<PedidoCompraItemDTO>), valorTotal` |
| `PedidoCompraItemDTO` | class (`@Data @Builder`) | `id, produtoId, produtoNome, skuId, skuNome, embalagemId, embalagemNome, quantidade, quantidadeBase, custoUnitario, subtotal, recebidoBase, status` |
| `AtualizarPedidoCompraRequest` | class (`@Data`) | `fornecedorId, dataPrevista, observacao, status` |
| `AdicionarItemPedidoRequest` | class (`@Data`) | `produtoId, skuId, embalagemId, quantidade, custoUnitario` |
| `AtualizarItemPedidoRequest` | class (`@Data`) | `quantidade, custoUnitario, embalagemId, skuId` |
| `SugestaoCompraItemDTO` | record | (ver `sugestao-compra/sugestao-compra.md`) |

## Repositório

`PedidoCompraRepository` — estende `JpaRepository` e `JpaSpecificationExecutor`.

| Método | Descrição |
|--------|-----------|
| `findByStatus(StatusPedidoCompra)` | Pedidos por status |
| `findByIdWithItens(Long id)` | JPQL: `LEFT JOIN FETCH itens` |

`PedidoCompraItemRepository` — estende `JpaRepository`.

| Método | Descrição |
|--------|-----------|
| `findByPedidoId(Long)` | Itens de um pedido |

## Serviço

`PedidoCompraService` (314 linhas):

| Método | Descrição |
|--------|-----------|
| `listarPedidos(status, fornecedorId, de, ate, search, page, size)` | Lista paginada com Specification dinâmica |
| `buscarPorId(Long)` | DTO com itens via `findByIdWithItens` |
| `criar(List<SugestaoCompraItemDTO> itens)` | Cria pedido RASCUNHO a partir de sugestões |
| `atualizarPedido(Long, AtualizarPedidoCompraRequest)` | Atualiza cabeçalho com validação de transição de status |
| `adicionarItem(Long, AdicionarItemPedidoRequest)` | Adiciona item (só RASCUNHO) |
| `atualizarItem(Long, Long, AtualizarItemPedidoRequest)` | Atualiza item (só RASCUNHO) |
| `removerItem(Long, Long)` | Remove item (só RASCUNHO) |

**Regras de transição de status** (validadas em `validarTransicaoStatus`):
- `CANCELADO` → nenhuma alteração permitida
- `RECEBIDO` → nenhuma alteração permitida
- `RASCUNHO` → não pode ir diretamente para `PARCIAL` ou `RECEBIDO`
- Demais transições livres

**Cálculos**:
- `calcularQuantidadeBase(item)` = `item.quantidade * item.embalagem.fatorBase` (para insumos)
- `calcularSubtotal(item)` = `item.quantidade * item.custoUnitario`

## Controller

`PedidoCompraController` (163 linhas) — `@RequestMapping("/api/pedidos-compra")`.

| Método | Path | Descrição | Restrições |
|--------|------|-----------|------------|
| `GET` | `/api/pedidos-compra/sugestoes` | Lista sugestões | Query: `somenteCriticos` (default true) |
| `POST` | `/api/pedidos-compra` | Criar pedido | Body com lista de itens |
| `GET` | `/api/pedidos-compra` | Listar | Filtros: status, fornecedorId, de, ate, search, page, size |
| `GET` | `/api/pedidos-compra/{id}` | Buscar por ID | Com itens |
| `PATCH` | `/api/pedidos-compra/{id}` | Atualizar | Status, fornecedor, data, obs |
| `POST` | `/api/pedidos-compra/{id}/itens` | Adicionar item | Só RASCUNHO |
| `PATCH` | `/api/pedidos-compra/{id}/itens/{itemId}` | Atualizar item | Só RASCUNHO |
| `DELETE` | `/api/pedidos-compra/{id}/itens/{itemId}` | Remover item | Só RASCUNHO (204) |

## Frontend

### Rota

`/pedidos-compra` → `PedidosCompraPage.vue` (307 linhas)

### Componentes

| Componente | Linhas | Descrição |
|-----------|--------|-----------|
| `SugestoesTab.vue` | 545 | Cards: total, selecionados, críticos, valor estimado. Tabela: tipo, produto, SKU/embalagem, estoque atual/mínimo, qtd pedido, custo, subtotal. Toggle "Somente críticos". Seleção para criar pedido |
| `PedidosTab.vue` | 503 | Cards: total, rascunhos, enviados, valor total. Tabela com status, fornecedor, datas, itens, valor, ações |
| `PedidoFilters.vue` | ~200 | Filtros: status (select), fornecedor (lookup), período (de/ate datepicker), texto |
| `PedidoDetalhesDialog.vue` | 1024 | Dialog maximizado: info do pedido + tabela de itens + modo edição com lookup de fornecedor |
| `AdicionarItemDialog.vue` | ~320 | Lookup de produto, select de SKU (vendáveis) ou embalagem (insumos), quantidade, custo |
