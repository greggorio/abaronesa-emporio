# Movimentação — Especificação

## Fluxo de baixa automática por venda

Quando um pedido é finalizado (`PedidoService`):

1. Para cada `ItemPedido`:
   - Identifica o SKU
   - Se `produto.insumo=true`: calcula quantidade em unidade base (`quantidade × embalagem.fatorBase`)
   - Chama `MovimentoEstoqueService.movimentarEstoque()` com `tipo=VENDA`
2. `MovimentoEstoqueService`:
   - Cria `MovimentoEstoque`, atualiza saldo (`estoque.quantidade` para vendáveis ou `estoque_produto.quantidade_base` para insumos)
   - Se `produto.controla_validade=true`: aplica FEFO automaticamente
   - Cria `MovimentoEstoqueLote` para cada lote consumido
3. Cancelamento do pedido → `tipo=ESTORNO_VENDA` (idempotente via `movimentoOrigemId`)

## Fluxo de consumo por produção

Quando produção é registrada (`ProducaoService`):

1. Para cada ingrediente da ficha técnica:
   - Calcula consumo em unidade base
   - Cria `MovimentoEstoque` com `tipo=CONSUMO_PRODUCAO`
   - Reduz `estoque_produto.quantidade_base` do insumo
2. Adiciona quantidade do produto produzido: `tipo=PRODUCAO`

## Fluxo de entrada por recebimento

Quando recebimento é confirmado (`RecebimentoMercadoriaService`):

1. Para cada item do recebimento:
   - Cria `MovimentoEstoque` com `tipo=ENTRADA`
   - Se `produto.controla_validade=true`: cria ou atualiza `EstoqueLote` com lote + data de validade
   - Registra `MovimentoEstoqueLote` para rastreabilidade
2. Cancelamento → `tipo=ESTORNO_ENTRADA`

## FEFO (First Expiry, First Out)

Aplicado automaticamente pelo `MovimentoEstoqueService` em toda saída (`isTipoSaida`) quando o produto controla validade:

1. Busca `EstoqueLote` do SKU ordenados por `data_validade ASC` (mais antigos primeiro)
2. Consome quantidades sequencialmente por lote até satisfazer o total solicitado
3. Cria `MovimentoEstoqueLote` para cada lote consumido (com a quantidade consumida)
4. Se o total de lotes com saldo for insuficiente → registra excedente em `EstoqueLote(SEM_LOTE)` com quantidade negativa (red flag: indica que há saída sem lastro de lote)

Não é possível escolher o lote manualmente — FEFO é automático e obrigatório por saída.

## Sub-ledger de lotes

`EstoqueLote` e `MovimentoEstoqueLote` formam o sub-ledger: rastreiam saldo por lote e registram qual lote foi consumido em cada movimento.

Constantes:
- `DEFAULT_LOTE = "SEM_LOTE"` — lote padrão quando produto não controla lote
- `DEFAULT_DATA_VALIDADE = 1900-01-01` — data sentinela para ausência de data de validade
- Produtos sem `controla_validade=true` nunca criam `EstoqueLote`

## Idempotência de estornos

`MovimentoEstoque.movimentoOrigemId` vincula o estorno ao movimento original. Se o estorno já foi aplicado, a chamada retorna sucesso sem duplicar o reverso — essencial para cancelamentos automáticos em pedidos em massa.

## Endpoints

### MovimentoEstoqueController

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/movimento-estoque` | Criar movimento genérico |
| `POST` | `/api/movimento-estoque/ajuste-rapido` | Ajuste rápido: skuId, quantidade, motivo, adicionar (bool) |
| `GET` | `/api/movimento-estoque/{id}` | Detalhe do movimento |
| `GET` | `/api/movimento-estoque/sku/{skuId}` | Histórico paginado por SKU |
| `GET` | `/api/movimento-estoque/relatorio` | Relatório por período e tipo de movimento |
| `POST` | `/api/movimento-estoque/entrada` | Entrada forçada |

### EstoqueController

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` | `/api/estoque/sku/{skuId}/lotes` | Lista lotes do SKU com quantidade e validade |
| `POST` | `/api/estoque/zerar-estoque` | Zera estoque de todos os produtos |
| `POST` | `/api/estoque/zerar-estoque/{skuId}?motivo=X` | Zera estoque de um SKU específico |

### Admin

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/admin/estoque/movimentos/{id}/estornar` | Estornar movimento (idempotente) |
| `GET` | `/api/admin/itens/{itemPedidoId}/movimentos-estoque` | Movimentos de um item do pedido |

## Gaps

- **Transferência entre depósitos**: tipo `TRANSFERENCIA` existe na enum; não há lógica de negócio implementada para mover saldo entre `local_origem_id` e `local_destino_id`
- **Escolha manual de lote**: FEFO é automático e obrigatório — operador não pode selecionar qual lote consumir
- **Exportação em CSV/Excel**: apenas PDF disponível no relatório de movimentos
