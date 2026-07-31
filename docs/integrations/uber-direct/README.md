# Uber Direct

> **Status**: `parcial` | **Prioridade**: Alta

## Visao Geral

A integracao `uber-direct` cobre a terceirizacao do fulfillment de pedidos de delivery, incluindo quote, criacao de entrega, webhook de status e rastreio externo.

Ela nao descreve a jornada funcional do pedido. Essa parte continua em [../../modules/consumo-digital/delivery/README.md](../../modules/consumo-digital/delivery/README.md).

## Papel da Integracao

### Inclui
- autenticacao com Uber Direct
- quote de entrega
- criacao da entrega externa
- recebimento de webhooks
- atualizacao de status logistico
- suporte a tracking e ETA

### Nao inclui
- catalogo de delivery
- checkout do cliente
- pagamento do pedido
- KDS como dominio funcional

## Estado Atual

| Dimensao | Status | Observacao |
|----------|--------|------------|
| **Especificacao** | `basica` | A integracao ainda nao tem documento-fonte completo, mas ja possui fluxo minimo de webhook sandbox e contexto historico suficiente para consolidacao |
| **Implementacao** | `parcial` | Ha endpoint de webhook, parsing de eventos e fluxo de teste documentado |
| **Operacao** | `sandbox documentado` | Ainda falta consolidar producao, persistencia e autenticacao do webhook |

## Documentos da Integracao

| Documento | Papel | Status |
|-----------|-------|--------|
| [README.md](./README.md) | Porta editorial da integracao | Ativo |
| [uber_webhooks_sandbox.md](./uber_webhooks_sandbox.md) | Fluxo de teste e recebimento de webhooks | Ativo |
| [../_legacy/uber_delivery_integration.md](../_legacy/uber_delivery_integration.md) | Registro historico do ciclo inicial de delivery | Referencia historica |

## Relacao com Outros Modulos

| Area | Fronteira |
|------|-----------|
| [Delivery](/home/gregorio/git/bakery/docs/modules/consumo-digital/delivery/README.md) | Jornada funcional do pedido remoto |
| [Pagamentos](/home/gregorio/git/bakery/docs/modules/consumo-digital/pagamentos/README.md) | Quitacao do pedido antes da expedicao |
| [KDS](/home/gregorio/git/bakery/docs/modules/consumo-digital/kds/README.md) | Publicacao operacional dos itens |

## Gaps Prioritarios

- consolidar credenciais, ambiente e pre-requisitos da Uber Direct
- registrar claramente `sandbox x producao`
- documentar o mapeamento `delivery_id x pedido interno`
- consolidar assinatura/autenticacao do webhook

## Proximos Passos

- criar `implementacao-atual.md` se a integracao continuar crescendo
- mover do legado o que ainda for materialmente valido
- manter esta pasta alinhada a [../../api-reference/webhooks/uber-direct.md](../../api-reference/webhooks/uber-direct.md)
