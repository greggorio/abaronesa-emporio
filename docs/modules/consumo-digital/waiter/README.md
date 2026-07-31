# Waiter

Waiter é o sub-domínio que governa o suporte operacional do salão: chamados dos clientes, entrega de itens prontos, validação de pagamentos e fechamento de mesas. É a interface do staff para resolver o que os canais digitais produzem.

Pertenço a [`consumo-digital/`](../README.md).

## Domínio

- [`waiter.md`](./waiter.md) — chamados, dashboard operacional, entidades e funcionalidades

## Leitura contextual

Waiter não é um canal de consumo — é a camada de operação que sustenta os outros canais. Ele existe para os casos em que a automação não é suficiente: um chamado de garçom, um pagamento que precisa de validação manual, uma mesa que precisa ser fechada pelo staff.

Os chamados chegam em tempo real via SSE — o staff não precisa recarregar a tela. A tradução automática de observações (via `LanguageDetectionService`) é relevante em estabelecimentos com clientela internacional.

A geração de NFCe e exportação de recibo em PDF são funcionalidades de fechamento que vivem no Waiter, mesmo que o dado fiscal pertença ao `financeiro/`.

## Exploração

- Chamados e dashboard completo → [`waiter.md`](./waiter.md)
- Itens prontos para entregar → [`../kds/README.md`](../kds/README.md)
- Pagamentos a validar → [`../pagamentos/README.md`](../pagamentos/README.md)
- Conta da mesa antes do fechamento → [`../conta-digital/README.md`](../conta-digital/README.md)
