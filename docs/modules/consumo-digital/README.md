# Consumo Digital

Consumo Digital é o domínio que governa toda a jornada digital do cliente: desde o acesso via QR Code até o pagamento sem interação humana. Existe para permitir que o cliente consuma, acompanhe sua conta e pague de forma autônoma — e que a operação da cozinha, do salão e do delivery aconteça de forma coordenada em tempo real.

O módulo opera sobre SSE (Server-Sent Events) para sincronização em tempo real e usa `guestToken` como mecanismo de identidade anônima — o cliente não precisa de cadastro para consumir em mesa.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`consumo-digital.md`](./consumo-digital.md) — arquitetura transversal, entidades-raiz, escopo e integrações
- [`mesa-digital/`](./mesa-digital/README.md) — consumo em mesa compartilhada via QR, conta por convidado e auto-pagamento
- [`qr-ordering/`](./qr-ordering/README.md) — protocolo de acesso à mesa via QR Code e cardápio digital
- [`delivery/`](./delivery/README.md) — pedidos para entrega ou retirada, com rastreamento e integração Uber Direct
- [`pagamentos/`](./pagamentos/README.md) — self-checkout digital com múltiplos gateways, taxa de serviço e couvert artístico
- [`conta-digital/`](./conta-digital/README.md) — acompanhamento em tempo real do consumo, breakdown por convidado
- [`kds/`](./kds/README.md) — fila unificada de cozinha para mesa e delivery, com múltiplas estações
- [`waiter/`](./waiter/README.md) — dashboard operacional para chamados, pagamentos pendentes e fechamento de mesas

## Leitura contextual

`mesa-digital` e `qr-ordering` são faces do mesmo fluxo: QR Ordering é o protocolo de entrada, Mesa Digital é a experiência completa que se segue. A separação é editorial — não há dois sistemas distintos.

`kds` é consumidor de ambos `mesa-digital` e `delivery`: a fila unificada exibe tickets de mesa e de delivery no mesmo display, diferenciados pelo campo `tipo`. Isso é intencional — a cozinha não precisa saber a origem do pedido para prepará-lo.

`pagamentos` não é um canal autônomo — ele encerra a jornada de `mesa-digital` e de `delivery`. A dependência é direcional: pagamentos depende de mesa e delivery; eles não dependem de pagamentos.

## Exploração

- Arquitetura transversal e entidades-raiz → [`consumo-digital.md`](./consumo-digital.md)
- Consumo em mesa → [`mesa-digital/`](./mesa-digital/README.md)
- Entrega e retirada → [`delivery/`](./delivery/README.md)
- Tela da cozinha → [`kds/`](./kds/README.md)
- Pagamento digital → [`pagamentos/`](./pagamentos/README.md)
- Cardápio e produtos → [`produtos/`](../produtos/README.md)
- Identidade do cliente → [`clientes/`](../clientes/README.md)
