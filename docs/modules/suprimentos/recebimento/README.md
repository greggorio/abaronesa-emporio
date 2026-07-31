# Recebimento de Mercadorias

**Status**: IMPLEMENTADO

## Quem sou

Sub-domínio de Recebimento de Mercadorias — ponto de entrada física de insumos e mercadorias no estoque, com conferência fiscal, controle de lotes/validade e geração de obrigações financeiras.

## Para que existo

Garantir que tudo que entra no estoque seja registrado com procedência, custo, lote e validade, evitando duplicidade de NF-e e assegurando rastreabilidade completa.

## A quem pertenço

Módulo de **Suprimentos** — é a etapa final do fluxo de compras, conectando pedidos ao estoque físico.

## Domínio imediato

- Registro de recebimento com NF-e (número, chave, XML)
- Validação de NF duplicada por par (numeroNf, fornecedor)
- Itens com lote, validade, custo, NCM, CFOP
- Finalização → entrada em `estoque_lote` e `movimento_estoque`
- Cancelamento → estorno de estoque
- Importação de XML NF-e com preenchimento automático
- Geração de parcelas a pagar no financeiro

## Coerente / Desalinhado

- **Coerente**: integração com estoque (lotes, movimentos), pedidos de compra (atualização de status dos itens), financeiro (parcelas)
- **Desalinhado**: não há conferência por diferença de peso/quantidade contra o pedido original; o estorno manual de item individual não é suportado (apenas cancelamento total)

## Caminhos de exploração

Leia `recebimento.md` para a especificação detalhada com diagrama de fluxo. Para entender a origem, veja `pedidos-compra/` e `sugestao-compra/`.
