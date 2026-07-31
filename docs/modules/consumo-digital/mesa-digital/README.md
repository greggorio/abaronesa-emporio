# Mesa Digital

Mesa Digital é o sub-domínio que governa o consumo presencial compartilhado: múltiplos convidados entram em uma mesa via QR Code, fazem pedidos individuais, acompanham a conta consolidada e pagam de forma autônoma.

Pertenço a [`consumo-digital/`](../README.md).

## Domínio

- [`mesa-digital.md`](./mesa-digital.md) — entidades, fluxo completo, self-checkout e sincronização em tempo real

## Leitura contextual

A conta da mesa é compartilhada mas discriminada por convidado — cada pessoa vê o que consumiu e o total da mesa. O anfitrião (host) tem visão consolidada e pode iniciar o fechamento.

O self-checkout é uma permissão, não um padrão: a flag `selfCheckoutLiberado` em `SessaoMesa` precisa ser habilitada pelo staff. Quando não está liberada, o convidado visualiza a conta mas não pode pagar sem interação do garçom.

A reentrada na sessão é suportada: se o convidado perder o token ou trocar de dispositivo, o sistema verifica o `deviceFingerprint` para resgatar a sessão existente.

## Exploração

- Fluxo e entidades completos → [`mesa-digital.md`](./mesa-digital.md)
- Como o cliente entra → [`../qr-ordering/README.md`](../qr-ordering/README.md)
- Como a cozinha recebe os pedidos → [`../kds/README.md`](../kds/README.md)
- Como o pagamento é processado → [`../pagamentos/README.md`](../pagamentos/README.md)
