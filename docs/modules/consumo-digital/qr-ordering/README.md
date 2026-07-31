# QR Ordering

QR Ordering é o protocolo de acesso à mesa digital: o cliente escaneia o QR Code da mesa, visualiza o cardápio e inicia sua sessão de consumo. É a porta de entrada da jornada de mesa — não um canal de consumo independente.

Pertenço a [`consumo-digital/`](../README.md).

## Domínio

- [`qr-ordering.md`](./qr-ordering.md) — como funciona o acesso, cardápio digital e controle de disponibilidade

## Leitura contextual

QR Ordering não é separado de Mesa Digital — é seu mecanismo de entrada. O QR Code codifica o `slug` da mesa; ao escanear, o cliente é redirecionado para a sessão ativa daquela mesa e cria sua identidade via `guestToken`.

O cardápio digital exposto via QR é filtrado por disponibilidade em tempo real: produtos fora de horário ou marcados como indisponíveis não aparecem. Isso é controlado por `ProdutoDisponibilidade` no módulo `produtos/`.

## Exploração

- Detalhes do acesso e cardápio → [`qr-ordering.md`](./qr-ordering.md)
- O que acontece após entrar → [`../mesa-digital/README.md`](../mesa-digital/README.md)
- Disponibilidade de produtos → [`../../produtos/README.md`](../../produtos/README.md)
