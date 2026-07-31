# Pedidos de Compra

**Status**: IMPLEMENTADO

## Quem sou

Sub-domínio de Pedidos de Compra — documentos que formalizam a requisição de compra de insumos e mercadorias junto a um fornecedor.

## Para que existo

Rastrear e controlar todo o pipeline de aquisição: desde a identificação da necessidade (manual ou via sugestão automática) até o recebimento físico, passando por aprovação, envio e acompanhamento parcial.

## A quem pertenço

Módulo de **Suprimentos** — conecta fornecedores (origem) ao recebimento (destino).

## Domínio imediato

- Cabeçalho do pedido (fornecedor, status, data prevista)
- Itens do pedido (produto, SKU/embalagem, quantidade, custo)
- Máquina de estados: RASCUNHO → ENVIADO → PARCIAL → RECEBIDO / CANCELADO
- Criação de pedido a partir de sugestões de compra

## Coerente / Desalinhado

- **Coerente**: integração com sugestão de compra (geração automática), fornecedores (FK), recebimento (atualização de status conforme itens recebidos)
- **Desalinhado**: não há workflow de aprovação multi-nível; não há integração com e-mail para envio automático ao fornecedor

## Caminhos de exploração

Leia `pedidos-compra.md` para a especificação completa. Veja também `sugestao-compra/` para o motor de geração automática e `recebimento/` para a conclusão do ciclo.
