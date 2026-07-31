# Produção — Especificação do Domínio

## Definição

Módulo responsável pela transformação de insumos em produtos acabados. Conecta o cadastro de produtos com o controle de estoque por meio de fichas técnicas (composição de ingredientes) e do registro de produção própria (consumo de insumos + entrada do produzido).

## Escopo

**Inclui:**
- Ficha técnica de produtos: composição por SKUs de insumo com quantidades, ordem e observações
- Cálculo automático de custo total baseado nos insumos
- Controle de rendimento (porções por lote)
- Registro de produção própria: consumo de insumos e entrada do produto acabado em estoque
- Validação de disponibilidade de insumos em estoque antes de produzir
- Baixa automática de insumos na venda (para produtos com ficha técnica sem produção própria)
- Atualização do preço de custo do produto com base no custo da ficha técnica
- Busca de insumos disponíveis para composição da ficha

**Não inclui:**
- Ordem de produção em larga escala
- Planejamento de produção (MRP)
- Apontamento de mão de obra
- Custo industrial completo (CIF)
- KDS (Kitchen Display System)

## Flags de produto relacionadas

Dois campos booleanos no produto controlam o comportamento da produção e da baixa de insumos:

| Flag | Descrição |
|------|-----------|
| `temFichaTecnica` | Produto possui uma ficha técnica cadastrada (receita) |
| `producaoPropria` | Produto é fabricado internamente (não é apenas montado na venda) |

A combinação determina quando a baixa de insumos ocorre:

| `temFichaTecnica` | `producaoPropria` | Comportamento |
|-------------------|-------------------|---------------|
| `false` | — | Produto simples: sem composição, sem baixa de insumos |
| `true` | `false` | Produto composto: a baixa de insumos ocorre no aceite do pedido (venda) |
| `true` | `true` | Produção própria: a baixa de insumos ocorre no registro de produção |

## Fluxos

### Fluxo de cadastro de ficha técnica

```
1. Usuário acessa a aba "Ficha Técnica" no formulário do produto
2. Busca insumos por nome, SKU, código de barras ou variação
3. Adiciona linhas de ingredientes com:
   ├── SKU do insumo (apenas produtos marcados como insumo)
   ├── Quantidade (decimal com 3 casas)
   ├── Ordem de exibição
   └── Observação opcional
4. Sistema calcula custo unitário de cada item (preçoCusto × quantidade)
5. Sistema calcula custo total (somatório dos itens)
6. Ao salvar:
   ├── Cria/atualiza ficha_tecnica
   ├── Remove itens antigos e insere novos (substituição completa)
   ├── Atualiza produto.precoCusto = custoTotal da ficha
   └── Marca produto.temFichaTecnica = true
```

### Fluxo de produção própria

```
1. Usuário acessa a página de produção
2. Seleciona um produto (deve ter producaoPropria=true e temFichaTecnica=true)
3. Opcionalmente seleciona o SKU (obrigatório se o produto tiver múltiplos SKUs)
4. Sistema exibe a ficha técnica para conferência
5. Usuário confirma a produção
6. Sistema executa (transação):
   ├── Para cada item da ficha técnica:
   │   └── Cria movimento CONSUMO_PRODUCAO (tipo 11) no estoque
   ├── Cria movimento PRODUCAO (tipo 12) para entrada do produto acabado
   └── Retorna os movimentos de estoque gerados
```

### Fluxo de baixa automática na venda (produtos sem produção própria)

```
1. Item do pedido é aceito
2. Sistema verifica: produto.temFichaTecnica = true?
   ├── Se não: encerra
   └── Se sim: verifica produto.producaoPropria = true?
       ├── Se sim: encerra (baixa ocorre na produção)
       └── Se não: continua
3. Busca ficha técnica com itens
4. Para cada ingrediente:
   ├── Calcula quantidade total = ingrediente.quantidade × item.quantidade
   └── Cria movimento CONSUMO_PRODUCAO (tipo 11)
```

## Modelo de dados

### FichaTecnica

Tabela `ficha_tecnica` — vincula um produto à sua composição (receita).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `produto_id` | BIGINT FK UNIQUE | Produto acabado (1:1) |
| `custo_total` | DECIMAL(10,2) | Custo calculado pela soma dos itens |
| `rendimento` | INTEGER | Porções que a receita produz (default 1) |
| `observacoes` | TEXT | Anotações gerais da receita |
| `criado_em` | TIMESTAMP | — |
| `atualizado_em` | TIMESTAMP | — |

FK: `produto_id` → `produto(id)`.

### FichaTecnicaItem

Tabela `ficha_tecnica_item` — linha individual de insumo na composição.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | BIGSERIAL PK | Identificador |
| `ficha_tecnica_id` | BIGINT FK | Ficha técnica (N:1) |
| `insumo_sku_id` | BIGINT FK | SKU do insumo |
| `quantidade` | DECIMAL(10,3) | Quantidade do insumo |
| `ordem` | INTEGER | Ordem de exibição |
| `observacao` | TEXT | Instrução específica para este insumo |

FKs: `ficha_tecnica_id` → `ficha_tecnica(id)`, `insumo_sku_id` → `produto_sku(id)`.

### Tipos de movimento de estoque relacionados

| Código | Tipo | Descrição |
|--------|------|-----------|
| 11 | `CONSUMO_PRODUCAO` | Saída de insumos para produção |
| 12 | `PRODUCAO` | Entrada de produto produzido |

## Endpoints

### Ficha Técnica

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `GET` | `/api/ficha-tecnica/produto/{produtoId}` | — | Buscar ficha técnica de um produto (ou vazia se não existir) |
| `POST` | `/api/ficha-tecnica` | — | Salvar/criar ficha técnica (substituição completa de itens) |
| `GET` | `/api/ficha-tecnica/produto/{produtoId}/custo` | — | Calcular custo total da ficha |
| `GET` | `/api/ficha-tecnica/buscar-insumos?search=` | — | Buscar insumos disponíveis para composição |

### Produção

| Método | Path | Auth | Resumo |
|--------|------|------|--------|
| `POST` | `/api/producao` | — | Registrar produção |

Request:
```json
{
  "produtoId": 1,
  "skuId": null,
  "observacao": "Produção do dia"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "produtoId": 1,
    "skuId": 5,
    "quantidade": 1,
    "movimentoProduto": { ... },
    "movimentosInsumos": [ ... ]
  },
  "message": "Produção registrada com sucesso"
}
```

## Regras de validação

### Ficha técnica
- Um produto pode ter no **máximo uma** ficha técnica (1:1)
- Itens são substituídos em bloco — não há merge parcial
- Apenas produtos marcados como `insumo=true` são elegíveis como ingredientes
- O custo total é a soma do `precoCusto × quantidade` de cada item
- Ao salvar, `produto.precoCusto` é atualizado com `ficha.custoTotal`

### Produção
- Produto deve ter `producaoPropria = true`
- Produto deve ter `temFichaTecnica = true`
- Produto deve ter `controlaEstoque = true`
- Ficha técnica deve ter pelo menos um item
- Se o produto tem múltiplos SKUs, o SKU a produzir é obrigatório
- Se o produto tem apenas um SKU, ele é resolvido automaticamente

## Serviços

### FichaTecnicaService

| Método | Descrição |
|--------|-----------|
| `buscarPorProduto(Long)` | Retorna a ficha do produto ou DTO vazio se não existir |
| `salvar(FichaTecnicaRequest)` | Cria ou atualiza ficha, substitui itens, recalcula custo, atualiza preço de custo do produto |
| `calcularCusto(Long)` | Calcula e retorna o custo total de uma ficha |
| `buscarInsumosDisponiveis(String)` | Busca SKUs de insumos ativos por nome, SKU, código de barras ou variação (limit 50) |

### ProducaoService

| Método | Descrição |
|--------|-----------|
| `produzir(ProducaoRequest)` | Valida produto, resolve SKU, consome insumos via MovimentoEstoqueService, entra produto produzido |

### PedidoService.processarBaixaInsumos

| Método | Descrição |
|--------|-----------|
| `processarBaixaInsumos(ItemPedido)` | Disparado no aceite do item: baixa insumos automaticamente se produto tem ficha técnica e não é produção própria |

## Integrações

| Módulo | Natureza |
|--------|----------|
| `produtos/` | Leitura: dados do produto, SKUs, flags `temFichaTecnica` e `producaoPropria` |
| `produtos/` | Escrita: atualiza `produto.precoCusto` e `produto.temFichaTecnica` ao salvar ficha |
| `estoque/` | Escrita: movimentos `CONSUMO_PRODUCAO` (tipo 11) e `PRODUCAO` (tipo 12) |
| `vendas/` | Leitura: `PedidoService.processarBaixaInsumos` consome ficha técnica para baixa automática |

## Frontend

### Ficha Técnica

| Componente | Descrição |
|-----------|-----------|
| `ProdutoFichaTecnicaTab.vue` | Aba no formulário do produto: tabela de ingredientes com busca de insumo, quantidade, custo unitário/total, estoque disponível |

### Produção

| Componente | Rota | Descrição |
|-----------|------|-----------|
| `ProducaoPage.vue` | `/producao` | Seleção de produto, exibição da ficha técnica, botão de produzir, exibição dos movimentos gerados |

## Decisões de domínio

- **Ficha técnica como 1:1 com produto** — cada produto tem no máximo uma receita. Não há versões ou revisões de ficha.
- **Substituição completa de itens** — ao salvar, itens antigos são removidos e novos inseridos. Não há merge ou edição incremental.
- **Preço de custo derivado da ficha** — o `produto.precoCusto` é sobrescrito pelo custo total da ficha técnica ao salvar. Isso garante que o custo do produto acabado reflita a composição atual.
- **Apenas insumos como ingredientes** — a busca de insumos filtra produtos com `insumo=true`. Produtos acabados não podem ser ingredientes de outros produtos.
- **Dois regimes de baixa** — produtos com `producaoPropria=true` têm baixa na produção; produtos com `temFichaTecnica=true` + `producaoPropria=false` têm baixa na venda. Essa bifurcação evita dupla contagem de estoque.
- **Produção sempre de 1 unidade** — o registro de produção produz exatamente 1 unidade por chamada. O `rendimento` na ficha técnica é informacional (não valida quantidade produzida vs. rendimento).
- **Sem controle de lote na produção** — a produção não gera lote próprio. Os movimentos de entrada usam o lote padrão do SKU.
- **i18n** — mensagens de sucesso da produção são internacionalizadas (pt, en, es, fr).

## Status de implementação

**IMPLEMENTADO**. Cadastro de ficha técnica, registro de produção com consumo de insumos e entrada em estoque, e baixa automática de insumos na venda estão operacionais. Gaps conhecidos: produção em escala (múltiplas unidades por vez), planejamento (MRP) e apontamento de mão de obra não estão no escopo atual.
