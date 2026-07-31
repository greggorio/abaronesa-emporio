# Conta Digital

Conta Digital é o sub-domínio que governa o acompanhamento em tempo real do consumo do cliente durante a sessão de mesa: o que foi pedido, quanto está devendo, o que foi pago e o saldo restante — por convidado e consolidado.

Pertenço a [`consumo-digital/`](../README.md).

## Domínio

- [`conta-digital.md`](./conta-digital.md) — estrutura da conta, cálculo, eventos SSE e configurações

## Leitura contextual

A conta digital não é um módulo de cobrança — é uma superfície de visibilidade. O cálculo acontece no servidor a cada consulta, considerando todos os itens não-cancelados da sessão e os pagamentos já confirmados.

A taxa de serviço e o couvert artístico são componentes opcionais configuráveis que aparecem discriminados na conta — o cliente vê exatamente o que está pagando por cada componente.

A sincronização é por SSE: toda mudança relevante (item adicionado, item cancelado, pagamento confirmado) dispara um evento que recalcula e atualiza a conta em todos os dispositivos conectados à sessão.

## Exploração

- Estrutura e cálculo → [`conta-digital.md`](./conta-digital.md)
- Como os itens chegam na conta → [`../mesa-digital/README.md`](../mesa-digital/README.md)
- Como o pagamento liquida a conta → [`../pagamentos/README.md`](../pagamentos/README.md)
