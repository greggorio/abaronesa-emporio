# Suprimentos — Especificação

## Visão geral

O módulo de Suprimentos governa o pipeline **identificação de necessidade → pedido → recebimento → entrada em estoque**. Três entidades principais (`Fornecedor`, `PedidoCompra`, `RecebimentoMercadoria`) conectam-se para formar o ciclo de compras, com um motor de sugestão automática (`SugestaoCompraService`) que analisa o estoque atual vs. mínimo e propõe quantidades.

```
[SugestaoCompraService]
        |
        v
[Fornecedor] ---> [PedidoCompra] ---> [RecebimentoMercadoria] ---> [EstoqueLote]
                                                                    [MovimentoEstoque]
```

## Modelo de dados

### Fornecedor

Tabela `fornecedor` — cadastro de pessoas jurídicas fornecedoras.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | Auto incremento |
| `razaoSocial` | `String` | `nullable = false` |
| `nomeFantasia` | `String` | |
| `cnpj` | `String` | `unique = true` |
| `telefone` | `String` | |
| `email` | `String` | |
| `contato` | `String` | Nome da pessoa de contato |
| `endereco` | `String` | |
| `cidade` | `String` | |
| `estado` | `String` | |
| `cep` | `String` | |
| `ativo` | `Boolean` | `nullable = false`, default `true` |

Implementa `LookupSearchable` — `getNomeExibicao()` retorna `razaoSocial`.

### PedidoCompra

Tabela `pedido_compra` — cabeçalho do pedido.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `fornecedor` | `@ManyToOne(LAZY)` → `Fornecedor` | `fornecedor_id`, opcional |
| `usuario` | `@ManyToOne(LAZY)` → `Usuario` | `usuario_id` |
| `status` | `StatusPedidoCompra` (enum) | `RASCUNHO`, `ENVIADO`, `PARCIAL`, `RECEBIDO`, `CANCELADO` |
| `dataPrevista` | `LocalDate` | |
| `observacao` | `String` | `TEXT` |
| `itens` | `List<PedidoCompraItem>` | `@OneToMany(cascade = ALL, orphanRemoval = true)` |
| `criadoEm` | `LocalDateTime` | `updatable = false` |
| `atualizadoEm` | `LocalDateTime` | |

**Lifecycle**: `@PrePersist` / `@PreUpdate` para timestamps.

**Métodos helper**: `addItem()`, `removeItem()` gerenciam a coleção bidirecional.

### PedidoCompraItem

Tabela `pedido_compra_item` — linha do pedido.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `pedido` | `@ManyToOne(LAZY)` → `PedidoCompra` | `pedido_compra_id`, `nullable = false` |
| `produto` | `@ManyToOne(LAZY)` → `Produto` | `produto_id`, `nullable = false` |
| `sku` | `@ManyToOne(LAZY)` → `ProdutoSKU` | `sku_id` (para vendáveis) |
| `embalagem` | `@ManyToOne(LAZY)` → `Embalagem` | `embalagem_id` (para insumos) |
| `quantidade` | `BigDecimal(15,3)` | `nullable = false` |
| `quantidadeBase` | `Integer` | Derivado para insumos (quantidade × fator da embalagem) |
| `custoUnitario` | `BigDecimal(15,4)` | |
| `subtotal` | `BigDecimal(15,2)` | |
| `recebidoBase` | `Integer` | Default `0` |
| `status` | `StatusPedidoCompraItem` | `PENDENTE`, `PARCIAL`, `RECEBIDO`, `CANCELADO` |

### RecebimentoMercadoria

Tabela `recebimento_mercadoria` — cabeçalho do recebimento físico.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `numeroNf` | `String(20)` | `nullable = false` |
| `chaveNfe` | `String(44)` | Chave de acesso da NF-e |
| `fornecedor` | `@ManyToOne(LAZY)` → `Fornecedor` | `fornecedor_id`, `nullable = false` |
| `dataRecebimento` | `LocalDateTime` | `nullable = false` |
| `dataEmissaoNf` | `LocalDate` | |
| `valorTotal` | `BigDecimal(15,2)` | Default `ZERO` |
| `quantidadeItens` | `Integer` | Default `0` |
| `status` | `StatusRecebimento` | `PENDENTE`, `FINALIZADO`, `CANCELADO` |
| `observacao` | `String` | `TEXT` |
| `xmlNfe` | `String` | `TEXT` — XML bruto da NF-e |
| `usuario` | `@ManyToOne(LAZY)` → `Usuario` | |
| `itens` | `List<RecebimentoItem>` | `@OneToMany(cascade = ALL, orphanRemoval = true)` |
| `createdAt` | `LocalDateTime` | `updatable = false` |
| `updatedAt` | `LocalDateTime` | |

**Métodos de guarda**:
- `podeEditar()` → apenas `PENDENTE`
- `podeFinalizar()` → apenas `PENDENTE`, com `itens` não vazia
- `podeCancelar()` → apenas `PENDENTE` ou `FINALIZADO`
- `finalizar()` → altera status para `FINALIZADO`
- `cancelar()` → altera status para `CANCELADO`
- `recalcularTotais()` → soma `quantidadeItens` e `valorTotal` dos itens

### RecebimentoItem

Tabela `recebimento_item` — item recebido.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `recebimento` | `@ManyToOne(LAZY)` → `RecebimentoMercadoria` | `recebimento_id`, `nullable = false`, `@JsonBackReference` |
| `produto` | `@ManyToOne(LAZY)` → `Produto` | `produto_id`, `nullable = false` |
| `sku` | `@ManyToOne(LAZY)` → `ProdutoSKU` | `sku_id` |
| `quantidade` | `BigDecimal(15,3)` | `nullable = false` |
| `custoUnitario` | `BigDecimal(15,4)` | `nullable = false` |
| `valorTotal` | `BigDecimal(15,2)` | `nullable = false`, calculado em `@PrePersist`/`@PreUpdate` |
| `lote` | `String(50)` | |
| `dataValidade` | `LocalDate` | |
| `codigoProdutoFornecedor` | `String(50)` | Código do produto no fornecedor |
| `descricaoNfe` | `String(255)` | Descrição conforme NF-e |
| `ncm` | `String(8)` | |
| `cfop` | `String(4)` | |
| `unidade` | `String(10)` | Unidade da NF-e |

**Métodos**: `isValid()` (quantidade > 0 e custo >= 0), `updateFromNfe()` (preenche descricaoNfe, ncm, cfop, unidade a partir do XML).

### StatusRecebimento (enum)

| Valor | Label | Cor | Ícone |
|-------|-------|-----|-------|
| `PENDENTE` | Pendente | `warning` | `o_hourglass_empty` |
| `FINALIZADO` | Finalizado | `positive` | `o_check_circle` |
| `CANCELADO` | Cancelado | `negative` | `o_cancel` |

## Regras de negócio

### Fornecedor
- CNPJ é único na base — validação em `criar()` e `editar()`
- Exclusão lógica via flag `ativo` (não remove registros)
- Lookup textual busca por CNPJ, razão social, nome fantasia, cidade, email, contato

### Pedido de compra
- **Status**: `RASCUNHO` → `ENVIADO` → `PARCIAL` → `RECEBIDO` (ou `CANCELADO` a qualquer momento)
- Itens só podem ser adicionados/alterados/removidos em status `RASCUNHO`
- Transição direta `RASCUNHO` → `PARCIAL` ou `RASCUNHO` → `RECEBIDO` não é permitida
- Ao finalizar, `status` do pedido pode ir para `PARCIAL` (se nem todos itens recebidos) ou `RECEBIDO` (se todos recebidos)
- `subtotal` do item = `quantidade` × `custoUnitario`
- Para insumos: `quantidadeBase` = `quantidade` × `embalagem.fatorBase`

### Recebimento
- **NF duplicada**: validação por par `(numeroNf, fornecedor)` — não permite duas NF do mesmo fornecedor
- Status flow: `PENDENTE` → `FINALIZADO` (via `finalizar()`) ou `CANCELADO` (via `cancelar()`)
- Ao **finalizar**:
  - Valida lote e validade para produtos com `controlaValidade = true`
  - Cria/atualiza registros em `estoque_lote` (saldo inicial = quantidade recebida)
  - Gera `MovimentoEstoque` do tipo `ENTRADA` e `MovimentoEstoqueLote` para auditoria
  - Entrada em `estoque_produto.quantidade_base` (incrementa)
- Ao **cancelar** (`FINALIZADO`):
  - Reverte movimentos de estoque gerando `MovimentoEstoque` do tipo `ESTORNO_ENTRADA`
  - Decrementa `estoque_lote.saldo` e `estoque_produto.quantidade_base`
  - Se o saldo do lote for insuficiente para estornar, o cancelamento é bloqueado
- Ao **cancelar** (`PENDENTE`): apenas marca como cancelado, sem efeito em estoque

### Sugestão de compra
- Para **insumos**: compara `estoque_produto.quantidade_base` vs `estoque_minimo_base`
- Para **vendáveis**: compara cada SKU's `estoque.quantidade` vs `estoque_minimo`
- Inclui apenas itens onde `minimo > 0` E `estoque < minimo`
- Quantidade sugerida = déficit (`minimo - estoque`)
- Ordenação: críticos primeiro (menor razão estoque/minimo), depois por nome do produto
- Custo unitário sugerido = último custo de compra registrado

## Endpoints da API

### Fornecedores (`/api/fornecedores`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/fornecedores/list` | Lista paginada (via `BaseListController`) |
| `GET` | `/api/fornecedores/form-config` | Config dinâmica de formulário |
| `GET` | `/api/fornecedores/{id}` | Busca por ID |
| `POST` | `/api/fornecedores` | Criar |
| `PUT` | `/api/fornecedores/{id}` | Atualizar |
| `DELETE` | `/api/fornecedores/{id}` | Excluir (204) |
| `GET` | `/api/fornecedores/options` | Lista de opções (id, nome, cnpj) |
| `GET` | `/api/fornecedores/optionsfornecedor` | Idem (duplicata) |
| `GET` | `/api/fornecedores/ativos` | Lista fornecedores ativos |
| `GET` | `/api/fornecedores/search` | Busca textual para lookup |
| `GET` | `/api/fornecedores/lookup/search` | Lookup com searchForLookup |

### Pedidos de Compra (`/api/pedidos-compra`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/pedidos-compra/sugestoes` | Sugestões de compra — query param `somenteCriticos` (default true) |
| `POST` | `/api/pedidos-compra` | Criar pedido com itens |
| `GET` | `/api/pedidos-compra` | Listar com filtros: `status`, `fornecedorId`, `de`, `ate`, `search`, `page`, `size` |
| `GET` | `/api/pedidos-compra/{id}` | Buscar por ID (com itens) |
| `PATCH` | `/api/pedidos-compra/{id}` | Atualizar cabeçalho |
| `POST` | `/api/pedidos-compra/{id}/itens` | Adicionar item (só RASCUNHO) |
| `PATCH` | `/api/pedidos-compra/{id}/itens/{itemId}` | Atualizar item (só RASCUNHO) |
| `DELETE` | `/api/pedidos-compra/{id}/itens/{itemId}` | Remover item (só RASCUNHO) |

### Recebimentos (`/api/recebimentos`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/recebimentos/list` | Lista paginada |
| `GET` | `/api/recebimentos/form-config` | Config dinâmica de formulário |
| `GET` | `/api/recebimentos/{id}` | Buscar por ID |
| `POST` | `/api/recebimentos` | Criar |
| `PUT` | `/api/recebimentos/{id}` | Atualizar |
| `DELETE` | `/api/recebimentos/{id}` | Excluir (só PENDENTE) |
| `GET` | `/api/recebimentos/verificar-nfe` | Verificar NF duplicada — params `numeroNf`, `cnpj` |
| `POST` | `/api/recebimentos/{id}/finalizar` | Finalizar e gerar entrada em estoque |
| `POST` | `/api/recebimentos/{id}/cancelar` | Cancelar e estornar estoque |
| `POST` | `/api/recebimentos/parse-nfe` | Importar XML NF-e (multipart) |
| `GET` | `/api/recebimentos/options` | Opções para dropdown |
| `GET` | `/api/recebimentos/produtos/search` | Busca de produtos (stub) |
| `GET` | `/api/recebimentos/fornecedores/search` | Busca de fornecedores (stub) |

## Frontend

### Rotas

| Path | Componente | Descrição |
|------|-----------|-----------|
| `/pedidos-compra` | `PedidosCompraPage.vue` | Sugestões + lista de pedidos com CRUD completo |

### Componentes principais

| Componente | Arquivo | Linhas | Função |
|-----------|---------|--------|--------|
| SugestoesTab | `components/pedidos-compra/SugestoesTab.vue` | 545 | Cards métricos + tabela de sugestões por produto/SKU; toggle "Somente críticos"; seleção para criar pedido |
| PedidosTab | `components/pedidos-compra/PedidosTab.vue` | 503 | Tabela de pedidos com cards de resumo (total, rascunhos, enviados, valor), filtros e ações |
| PedidoFilters | `components/pedidos-compra/PedidoFilters.vue` | ~200 | Filtros por status, fornecedor, período e texto |
| PedidoDetalhesDialog | `components/pedidos-compra/PedidoDetalhesDialog.vue` | 1024 | Dialog maximizado com info + itens + modo edição |
| AdicionarItemDialog | `components/pedidos-compra/AdicionarItemDialog.vue` | ~320 | Lookup de produto, SKU, embalagem |
| RecebimentoForm | `components/forms/RecebimentoForm.vue` | 820 | Dialog "Entrada de Mercadoria" com NF, fornecedor, itens, importação NF-e |
| RecebimentoItensTab | `components/forms/RecebimentoItensTab.vue` | 302 | Tabela de itens com lookup de produto/SKU, lote e validade |
| RecebimentosTab | `components/forms/RecebimentosTab.vue` | 716 | Geração de parcelas financeiras |
| ImportNFeDialog | `components/forms/ImportNFeDialog.vue` | (existente) | Dialog para upload e parse de XML NF-e |

### Menu (backend `MenuController`)

As opções aparecem sob a seção "Produtos" para usuários com permissão `produtos`:
- **Fornecedores** → rota `fornecedores`, ícone `o_local_shipping`
- **Pedidos de Compra** → rota `pedidos-compra`, ícone `o_shopping_cart`
- **Recebimento de Mercadoria** → rota `recebimentos`, ícone `o_input`
