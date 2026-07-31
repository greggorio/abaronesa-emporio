# Estoque — Especificação do Domínio

## Definição

Módulo que representa e controla a quantidade física de itens do estabelecimento. Dois modelos de controle coexistem, determinados por flags no produto:

| Modelo | Flag | Tabela | Controle |
|--------|------|--------|----------|
| Vendável | `vendavel=true`, `insumo=false` | `estoque` | Independente por SKU |
| Insumo | `insumo=true` | `estoque_produto` | Centralizado na unidade base (ml, g) |

Para insumos, a tabela `estoque` existe por razões históricas e **deve ser ignorada**. A fonte da verdade é `estoque_produto.quantidade_base`. O estoque disponível por embalagem é calculado como `quantidade_base / fator_base`.

**Exemplo:** Whisky Jack Daniels com 5060 ml em `quantidade_base`, embalagem "Dose 30ml" com `fator_base=30` → 168 doses disponíveis; embalagem "Garrafa 750ml" com `fator_base=750` → 6 garrafas. O mesmo saldo alimenta ambos os SKUs simultaneamente.

## Entidades

### Estoque (vendáveis)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `quantidade` | INTEGER | Saldo do SKU |
| `estoque_minimo` | INTEGER | Limite mínimo (campo sem gatilho implementado) |
| `reservado` | INTEGER | Reservado (nunca atualizado automaticamente) |

### EstoqueProduto (insumos)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `produto_id` | FK unique | Referência ao produto |
| `quantidade_base` | INTEGER | Saldo em unidade base (ml, g, etc.) |
| `reservado_base` | INTEGER | Reservado em base (nunca atualizado) |
| `estoque_minimo_base` | INTEGER | Mínimo em base (sem gatilho) |
| `version` | BIGINT | Controle de concorrência otimista |

### EstoqueLote (sub-ledger de validade)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `produto_sku_id` | FK | SKU específico |
| `lote` | VARCHAR(100) | Código do lote |
| `data_validade` | DATE | Data de vencimento |
| `quantidade` | NUMERIC(14,3) | Saldo do lote |

Lote padrão: `SEM_LOTE` com `data_validade = 1900-01-01` (sentinela para ausência de controle de lote).

### MovimentoEstoque (auditoria)

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `sku_id`, `produto_id` | FK | Produto movimentado |
| `tipo_movimento` | enum | Tipo do movimento |
| `quantidade` | NUMERIC | Quantidade em unidades |
| `quantidadeBase` | INTEGER | Quantidade em unidade base (insumos) |
| `estoque_anterior`, `estoque_atual` | NUMERIC | Saldo antes e depois |
| `documentoReferencia` | VARCHAR(100) | NF, cupom fiscal, etc. |
| `vendaId`, `recebimentoId`, `consignacaoId` | — | Rastreabilidade por origem |
| `itemPedidoId` | FK | Vínculo com item do pedido |
| `movimentoOrigemId` | FK | Para estornos (idempotência) |
| `usuario_id` | FK | Responsável pelo movimento |
| `data_movimento` | TIMESTAMP | — |
| `local_origem_id`, `local_destino_id` | FK | Depósito de origem/destino (futuro) |

### MovimentoEstoqueLote

Vincula um `MovimentoEstoque` a um ou mais `EstoqueLote`, registrando a quantidade consumida de cada lote — resultado do FEFO automático. Ver [`movimentacao/`](./movimentacao/README.md).

## Tipos de movimento

| Código | Tipo | Origem |
|--------|------|--------|
| 0 | `INICIAR_ESTOQUE` | Manual (carga inicial) |
| 1 | `ENTRADA` | Recebimento de mercadoria confirmado |
| 2 | `VENDA` | Pedido finalizado |
| 3 | `AJUSTE` | Ajuste manual ou tratamento de divergência |
| 4 | `INVENTARIO` | Contagem de inventário |
| 6 | `ESTORNO_ENTRADA` | Devolução de entrada |
| 7 | `ESTORNO_VENDA` | Venda cancelada |
| 8 | `CONSIGNACAO` | Produto em consignação |
| 9 | `ESTORNO_CONSIGNACAO` | Devolução de consignação |
| 10 | `ZERAR_ESTOQUE` | Zeramento total |
| 11 | `CONSUMO_PRODUCAO` | Consumo de insumo na produção |
| 12 | `PRODUCAO` | Saída de produto produzido |

## Endpoints

### EstoqueController

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` | `/api/estoque/sku/{skuId}/lotes` | Lista lotes do SKU com quantidade |
| `POST` | `/api/estoque/zerar-estoque` | Zera estoque de todos os produtos |
| `POST` | `/api/estoque/zerar-estoque/{skuId}?motivo=X` | Zera estoque de um SKU |

### MovimentoEstoqueController

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/movimento-estoque` | Criar movimento genérico |
| `POST` | `/api/movimento-estoque/ajuste-rapido` | Ajuste rápido (skuId, quantidade, motivo, adicionar) |
| `GET` | `/api/movimento-estoque/{id}` | Detalhe do movimento |
| `GET` | `/api/movimento-estoque/sku/{skuId}` | Histórico paginado por SKU |
| `GET` | `/api/movimento-estoque/relatorio` | Relatório por período e tipo |

### Admin

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/admin/estoque/movimentos/{id}/estornar` | Estornar movimento (idempotente) |
| `GET` | `/api/admin/itens/{itemPedidoId}/movimentos-estoque` | Movimentos de um item do pedido |

## Integrações

| Módulo | Direção | Mecanismo |
|--------|---------|-----------|
| `suprimentos/` | Entrada | Recebimento confirmado → `tipo=ENTRADA` via `RecebimentoMercadoriaService` |
| `producao/` | Saída | Produção registrada → `tipo=CONSUMO_PRODUCAO` + `tipo=PRODUCAO` via `ProducaoService` |
| `vendas/` | Saída | Pedido finalizado → `tipo=VENDA` via `PedidoService`; cancelamento → `ESTORNO_VENDA` |
| `vendas/` | Saída | Consignação → `tipo=CONSIGNACAO`; devolução → `ESTORNO_CONSIGNACAO` |

## Decisões de domínio

- **Dupla camada coexistente**: `estoque` (saldo agregado por SKU) + `estoque_lote` (sub-ledger por lote/validade). Ambas devem estar em sincronia; divergências são detectadas e resolvidas nas tarefas de validade.
- **Tabela `estoque` para insumos é legacy**: código que lê `estoque.quantidade` para produtos com `insumo=true` está errado. O padrão correto está documentado em `FichaTecnicaService.java`.
- **Estoque negativo permitido**: não há validação de disponibilidade antes de vender. Campo `reservado` existe mas não é gerenciado, tornando o bloqueio de sobrevenda impossível hoje.
- **Idempotência em estornos**: `movimentoOrigemId` evita duplicar reversos — essencial para cancelamentos em massa.
- **Multi-depósito preparado, não ativado**: `local_origem_id` e `local_destino_id` existem em `movimento_estoque` sem lógica associada.

## Gaps

| Funcionalidade | Situação |
|----------------|----------|
| Alertas de estoque mínimo | Campo `estoque_minimo` existe; sem gatilho ou notificação |
| Reserva dinâmica | Campo `reservado` existe; nunca é atualizado automaticamente |
| Multi-depósito | Campos preparados no banco; lógica não implementada |
| Bloqueio de estoque negativo | Configurável por grupo de produto — TODO no código |
| Valoração de inventário | Sem coluna `custo_unitario`; sem cálculo de custo de estoque |
| Exportação de movimentos | Apenas relatório via endpoints; sem CSV/Excel |
