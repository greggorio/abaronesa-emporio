# Pagamentos

Pagamentos é o sub-domínio que encerra a jornada de consumo digital. O cliente paga de forma autônoma — via Pix ou cartão — sem precisar interagir com o caixa. A operação é suportada por múltiplos gateways, taxa de serviço configurável, couvert artístico e validação manual pelo staff quando necessário.

Pertenço a [`consumo-digital/`](../README.md).

## Domínio

- [`pagamentos.md`](./pagamentos.md) — entidades, gateways, fluxo de self-checkout e configurações

## Leitura contextual

O pagamento não é possível sem que o self-checkout esteja liberado na sessão (`SessaoMesa.selfCheckoutLiberado`). Esse é um controle operacional deliberado — por padrão, o staff valida o pagamento, não o cliente.

Dois gateways operam em paralelo: MercadoPago e PagSeguro. A escolha do gateway é por pedido (`providerGateway`). Webhooks de ambos chegam em endpoints distintos e atualizam o status via `PaymentStatusUpdater`, que depois publica o evento SSE `payment.made`.

Taxa de serviço e couvert artístico são componentes separados do total — o sistema discrimina o que o cliente pagou de cada parte (`valorBase`, `valorTaxaServico`, `valorCouvert`).

## Exploração

- Fluxo completo e gateways → [`pagamentos.md`](./pagamentos.md)
- Configurações de taxa e couvert → [`../consumo-digital.md`](../consumo-digital.md)
- Validação manual de pagamento → [`../waiter/README.md`](../waiter/README.md)
- Registro financeiro do pagamento → [`../../financeiro/README.md`](../../financeiro/README.md)
