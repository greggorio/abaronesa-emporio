# Delivery

Delivery é o sub-domínio que governa pedidos para entrega ao domicílio e retirada na loja. O cliente monta o pedido pelo cardápio digital, paga online e acompanha o status em tempo real — incluindo rastreamento do entregador via integração com Uber Direct.

Pertenço a [`consumo-digital/`](../README.md).

## Domínio

- [`delivery.md`](./delivery.md) — entidades, ciclo de vida do pedido, integração Uber Direct e gateways de pagamento

## Leitura contextual

Delivery suporta dois modos: `DELIVERY` (entrega no endereço) e `RETIRADA` (cliente retira na loja). A distinção afeta o cálculo de taxa de entrega e o fluxo de rastreamento — mas o cardápio, o carrinho e o pagamento são os mesmos.

A integração com Uber Direct é assíncrona: o pedido é criado no Bakery, um quote de entrega é obtido da Uber, e a confirmação de coleta e entrega chega via webhook. O status no sistema segue o estado do entregador.

Os itens de delivery chegam na mesma fila do KDS que os itens de mesa, identificados por `tipo: 'delivery'`. A cozinha processa ambos sem distinção de interface.

## Exploração

- Ciclo completo e integração Uber → [`delivery.md`](./delivery.md)
- Como o KDS recebe os pedidos → [`../kds/README.md`](../kds/README.md)
- Pagamento do delivery → [`../pagamentos/README.md`](../pagamentos/README.md)
- Rastreamento na área do cliente → [`../../clientes/area-do-cliente/README.md`](../../clientes/area-do-cliente/README.md)
