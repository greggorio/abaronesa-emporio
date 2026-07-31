# Sugestão de Compra — Especificação

## Algoritmo

`SugestaoCompraService` (128 linhas) implementa o motor de sugestão automática.

### Entrada

- `boolean somenteCriticos` — se `true`, retorna apenas itens com criticidade alta

### Saída

- `List<SugestaoCompraItemDTO>` — itens sugeridos para compra

### Lógica

```
gerarSugestoes(somenteCriticos)
  para cada Produto:
    se produto.insumo:
      estoque  = estoque_produto.quantidade_base
      minimo   = estoque_produto.estoque_minimo_base (ou do SKU pai)
      fator    = 1
    senão (vendável):
      para cada SKU do produto:
        estoque  = sku.estoque.quantidade
        minimo   = sku.estoque_minimo
        fator    = sku.fatorBase (se houver embalagem)
    
    se minimo > 0 && estoque < minimo:
      quantidadeSugerida = (minimo - estoque) / fator (se insumo)
                        ou (minimo - estoque) (se vendável)
      custoUnitario = ultimo custo de compra (do recebimento mais recente)
      motivo = "Estoque abaixo do mínimo" / "SKU abaixo do mínimo"
      adiciona à lista

  ordena por: criticidade (menor razão estoque/minimo primeiro), depois nome
  se somenteCriticos: filtra apenas itens onde estoque < minimo * 0.5 (críticos)
  retorna lista
```

### DTO

`SugestaoCompraItemDTO` (record, 22 linhas):

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `produtoId` | `Long` | ID do produto |
| `produtoNome` | `String` | Nome do produto |
| `insumo` | `Boolean` | Se é insumo |
| `skuId` | `Long` | ID do SKU (vendável) |
| `sku` | `String` | Nome do SKU |
| `embalagemId` | `Long` | ID da embalagem (insumo) |
| `embalagemNome` | `String` | Nome da embalagem |
| `fatorBase` | `Integer` | Fator de conversão para base |
| `estoqueAtualBase` | `Integer` | Estoque atual em base |
| `estoqueMinimoBase` | `Integer` | Estoque mínimo em base |
| `estoqueAtualSku` | `Integer` | Estoque atual do SKU |
| `estoqueMinimoSku` | `Integer` | Estoque mínimo do SKU |
| `quantidadeSugerida` | `BigDecimal` | Quantidade a comprar |
| `custoUnitario` | `BigDecimal` | Último custo registrado |
| `motivo` | `String` | "Estoque abaixo do mínimo" |

## Integração com Pedidos de Compra

O endpoint `POST /api/pedidos-compra` aceita uma lista de `SugestaoCompraItemDTO` para criar um novo pedido a partir das sugestões selecionadas. O frontend (`SugestoesTab.vue`) permite:

1. Visualizar todas as sugestões com métricas (total, críticos, valor estimado)
2. Marcar/desmarcar itens individualmente
3. Toggle "Somente críticos" para filtrar
4. Clicar "Criar Pedido" → envia seleção para `POST /api/pedidos-compra`

## Regras

1. Apenas itens com `estoque_minimo > 0` são considerados (produtos sem mínimo configurado são ignorados)
2. Para insumos: o estoque é comparado em `quantidade_base` (já convertido pela embalagem padrão)
3. Para vendáveis: cada SKU é avaliado individualmente contra seu `estoque_minimo`
4. A ordenação por criticidade usa a razão `estoque / minimo` — quanto menor, mais crítico
5. O custo unitário sugerido é o último custo registrado em recebimentos do mesmo produto
