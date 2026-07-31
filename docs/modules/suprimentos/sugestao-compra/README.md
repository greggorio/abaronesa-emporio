# Sugestão de Compra

**Status**: IMPLEMENTADO

## Quem sou

Sub-domínio de Sugestão de Compra — motor automático que analisa o estoque atual contra os parâmetros de estoque mínimo e propõe quantidades a serem compradas.

## Para que existo

Evitar ruptura de estoque identificando proativamente itens com saldo abaixo do mínimo e calculando a quantidade necessária para reabastecimento, separando insumos (base) e vendáveis (SKU).

## A quem pertenço

Módulo de **Suprimentos** — alimenta o sub-domínio de pedidos de compra com dados para criação de novos pedidos.

## Domínio imediato

- Cálculo de déficit por produto (insumo: `estoque_minimo_base - quantidade_base`) e por SKU (vendável: `estoque_minimo - quantidade`)
- Flag "somente críticos" para filtrar itens mais urgentes
- Ordenação por criticidade e nome
- Seleção de itens para criar pedido de compra

## Coerente / Desalinhado

- **Coerente**: integrado com estoque (leitura de saldos e mínimos) e pedidos de compra (criação a partir da seleção)
- **Desalinhado**: não considera lead time do fornecedor, sazonalidade, pedidos já em andamento ou lote econômico de compra

## Caminhos de exploração

Leia `sugestao-compra.md` para detalhes do algoritmo. Depois veja `pedidos-compra/` para entender como as sugestões se transformam em pedidos.
