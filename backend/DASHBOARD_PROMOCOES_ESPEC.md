# Dashboard de Promocoes - Escopo de Desenvolvimento

Este documento descreve o que deve ser implementado para o endpoint do dashboard de promocoes, alinhado ao escopo fornecido.

## Objetivo

Disponibilizar um endpoint que retorne os dados do painel de promocoes com base em registros ativos na tabela `produto_promocao` (campo `ativo = true`), contando produtos unicos (distinct por produto).

## Formato de retorno (exemplo)

```
{
  "produtosEmPromocao": 12,
  "descontoMedio": -18,
  "impactoVendas": 1240.00,
  "vendasPromocao": 62,
  "vendasNormais": 38,
  "produtosEmPromocaoLista": [
    {
      "nome": "Pera Premium Vodka 1L",
      "total": 110.00,
      "precoOriginal": 58.00,
      "precoComDesconto": 52.00,
      "desconto": -10,
      "vendas": 4,
      "progressWidth": 30
    }
  ]
}
```

## Regras principais

1. Produtos em promocao = produtos com ao menos um registro em `produto_promocao` com `ativo = true`.
2. Contagem deve ser `DISTINCT produto_id` (um produto conta apenas uma vez, mesmo com varias promocoes ativas).
3. A lista `produtosEmPromocaoLista` deve trazer uma linha por produto (agrupar por produto).
4. Nao filtrar por horario/dia. Considerar apenas `produto_promocao.ativo = true`.

## Fontes de dados

- `produto_promocao` (campo `ativo`) para identificar produtos com promocao ativa (no sentido de registro ativo).
- `produto` para nome e preco base do produto.
- `produto_sku` para preco base quando o SKU for usado no pedido.
- `item_pedido` + `pedido` para vendas e totalizacao.
- `pagamento` para considerar apenas vendas efetivadas (status PAID), como ja feito em outras consultas de dashboard.

## Detalhamento dos campos

### produtosEmPromocao

- `COUNT(DISTINCT produto_id)` em `produto_promocao` com `ativo = true`.

### descontoMedio

- Media do desconto percentual associado aos produtos em promocao (distinct por produto).
- Se houver mais de uma promocao ativa para o mesmo produto, aplicar regra de desempate.
- Resultado deve ser retornado negativo para fins de UI (ex.: 18% => -18).

Regra de desempate sugerida (confirmar):
- Escolher a promocao com maior desconto efetivo:
  - Tipo PERCENTUAL: `percentual_desconto`.
  - Tipo VALOR: converter para percentual com base no preco base (produto ou SKU principal).

### impactoVendas

- Soma do impacto financeiro das vendas com desconto.
- Regra sugerida:
  - Considerar itens de pedidos pagos (pagamento PAID).
  - Impacto = (preco_original - preco_unitario) * quantidade.
- Observacao: `item_pedido` nao registra o preco original historico. O calculo acima usa preco atual do produto/SKU como base e pode ficar impreciso se o preco mudou.

### vendasPromocao e vendasNormais

- Deve retornar o percentual das vendas (0-100) entre promocionais e normais no periodo (ex.: 62/38).
- Definicao sugerida para identificar venda promocional:
  - `item_pedido.preco_unitario < preco_base_atual` do produto/SKU.
  - Opcional: vincular apenas aos produtos que possuem promocao ativa em `produto_promocao`.
- Periodo: usar o mesmo filtro do dashboard de vendas (ex.: hoje, ou ultimos 7 dias). Definir e documentar.

### produtosEmPromocaoLista

Para cada produto com promocao ativa:
- `nome`: `produto.nome`.
- `total`: soma de `preco_unitario * quantidade` dos itens vendidos (pagos).
- `precoOriginal`: preco base atual (`produto.preco_venda` ou `produto_sku.preco_venda` se houver SKU principal).
- `precoComDesconto`: media ponderada dos precos de venda (`preco_unitario`) para o produto.
- `desconto`: percentual negativo calculado a partir de `precoOriginal` e `precoComDesconto`.
- `vendas`: quantidade total vendida (soma de `quantidade`).
- `progressWidth`: percentual de participacao do produto no total vendido dentro da lista (0-100).

## Componentes a implementar

1. DTO de resposta (ex.: `DashboardPromocoesDTO`) com os campos exigidos.
2. DTO de item (ex.: `DashboardPromocaoProdutoDTO`) para `produtosEmPromocaoLista`.
3. Repository queries:
   - Buscar `produto_id` distintos em `produto_promocao` com `ativo = true`.
   - Trazer dados de vendas por produto (total, quantidade, preco_unitario).
   - Filtrar vendas por pagamentos efetivados (PAID), conforme queries existentes.
4. Service:
   - Agregar os dados e calcular os campos acima.
   - Aplicar regra de desempate das promocoes quando houver mais de uma ativa por produto.
5. Controller:
   - Expor o endpoint (ex.: `/api/dashboard/promocoes`).

## Observacoes e riscos

- Sem historico de preco base no momento da venda, os calculos de desconto/impacto sao estimativas.
- Se precisarmos de precisao historica, sera necessario gravar preco base e origem do desconto no `item_pedido` no momento da venda.
- Confirmar periodo de analise (hoje, 7 dias, 30 dias).

## Confirmacoes pendentes (para o qwen)

1. Periodo padrao do dashboard (hoje, 7 dias, 30 dias).
2. Regra de desempate para multiplas promocoes ativas por produto.
3. Definicao exata de vendas promocionais (apenas por preco menor, ou apenas produtos com promocao ativa).
