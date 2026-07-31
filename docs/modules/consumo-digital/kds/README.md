# KDS — Kitchen Display System

KDS é o sub-domínio que governa a fila de preparação da cozinha. Exibe em tempo real todos os pedidos aguardando preparação — tanto de mesa quanto de delivery — organizados por estação (cozinha ou bar), com controle de status e sincronização instantânea.

Pertenço a [`consumo-digital/`](../README.md).

## Domínio

- [`kds.md`](./kds.md) — estrutura do ticket, fluxo de status, estações e integração com delivery

## Leitura contextual

O KDS não distingue a origem do pedido na interface — um ticket de mesa e um ticket de delivery passam pelo mesmo fluxo de preparação. A distinção (`tipo: 'mesa'` ou `tipo: 'delivery'`) existe nos dados e é exibida no card, mas não muda o comportamento da cozinha.

A fila tem duas estações independentes: `KITCHEN` (cozinha quente) e `BAR` (bebidas). Cada estação filtra apenas seus próprios itens — um barman não vê os tickets da cozinha e vice-versa.

Toda mudança de status é publicada via SSE imediatamente, garantindo que a equipe de salão (Waiter) saiba quando um item está pronto para ser entregue sem polling.

## Exploração

- Estrutura do ticket e fluxo completo → [`kds.md`](./kds.md)
- Como os pedidos chegam → [`../mesa-digital/README.md`](../mesa-digital/README.md) e [`../delivery/README.md`](../delivery/README.md)
- Quem entrega os itens prontos → [`../waiter/README.md`](../waiter/README.md)
