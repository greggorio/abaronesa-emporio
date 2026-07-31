# Pedidos

**Status**: ESTAVEL

## Quem sou

Sub-domínio de Pedidos — criação, processamento e acompanhamento dos itens consumidos. Conecta o cliente (via PWA ou staff) à cozinha/bar via KDS e ao estoque via baixa automática.

## Para que existo

Registrar o que cada cliente consome, em que quantidade, com que observações, e orquestrar o fluxo desde o pedido até a entrega, com rastreamento de status em tempo real e baixa de insumos no estoque.

## A quem pertenço

Módulo de **Vendas** — o pedido é o elo entre a mesa/sessão (origem) e a conta (destino financeiro).

## Domínio imediato

- Criação de pedidos (self-service via PWA ou staff)
- Itens com produto, SKU, quantidade, preço, observações, estação (cozinha/bar)
- Máquina de estados: QUEUED → ACCEPTED → PREPARING → READY → DELIVERED → CANCELED
- Cancelamento de item com motivo codificado
- Integração com KDS via SSE (eventos em tempo real)
- Baixa automática de estoque (insumos via ficha técnica + SKU) no ACCEPTED
- Estorno de estoque no CANCELED

## Coerente / Desalinhado

- **Coerente**: integração bidirecional com KDS (tempo real), estoque (baixa e estorno), conta (consolidação de valores)
- **Desalinhado**: não há impressão de comanda na cozinha (apenas KDS); não há pré-pedido (cliente agenda pedido para horário futuro)

## Caminhos de exploração

Leia `pedidos.md` para especificação completa. Veja também `conta/` para entender como os itens se consolidam financeiramente e `pagamento/` para o desfecho.
