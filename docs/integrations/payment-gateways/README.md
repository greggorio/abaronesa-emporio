# Gateways de Pagamento

> **Status**: `implementado` | **Prioridade**: Alta

## Visao Geral

A integracao `payment-gateways` concentra os provedores externos responsaveis por tokenizacao, criacao de pagamento, webhook de confirmacao e configuracoes por metodo.

Ela nao substitui a documentacao funcional de pagamento. A jornada do cliente continua em:

- [../../modules/consumo-digital/pagamentos/README.md](../../modules/consumo-digital/pagamentos/README.md)
- [../../modules/consumo-digital/self-checkout/README.md](../../modules/consumo-digital/self-checkout/README.md)
- [../../modules/consumo-digital/delivery/README.md](../../modules/consumo-digital/delivery/README.md)

## Papel da Integracao

### Inclui
- credenciais e configuracao por gateway
- criacao de cobrancas e intents
- tokenizacao e chaves publicas
- webhooks de confirmacao
- mapeamento de status do provedor para status interno
- parametros especificos de PIX, cartao e parcelamento

### Nao inclui
- regras de conta da mesa
- jornada de delivery
- experiencia de checkout no frontend
- financeiro corporativo

## Provedores Atuais

| Provedor | Status | Capacidades observadas |
|----------|--------|------------------------|
| **MercadoPago** | `ativo` | Pix, cartao, configuracao de parcelamento, webhook |
| **PagSeguro** | `ativo` | Pix, cartao, chave publica para frontend, webhook, sandbox x producao |

## Estado Atual

| Dimensao | Status | Observacao |
|----------|--------|------------|
| **Especificacao** | `basica` | O README atual delimita o papel da integracao, mas ainda nao ha documentos separados por provedor |
| **Implementacao** | `implementada` | Existem controllers, gateways, token services, mapeadores e configuracoes ativas no backend e no frontend |
| **Operacao** | `ativa` | MercadoPago e PagSeguro ja aparecem no fluxo material de delivery e mesa |

## Evidencia Material Principal

| Evidencia | Papel atual |
|-----------|-------------|
| [PagSeguroWebhookController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/PagSeguroWebhookController.java) | Recebimento de notificacoes do PagSeguro |
| [MPPaymentController.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/controller/MPPaymentController.java) | Endpoints ligados ao MercadoPago |
| [MercadoPagoPaymentGateway.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/payment/gateway/MercadoPagoPaymentGateway.java) | Regra de integracao do MercadoPago |
| [PagSeguroPaymentGateway.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/payment/gateway/PagSeguroPaymentGateway.java) | Regra de integracao do PagSeguro |
| [PaymentSettingsService.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/payment/PaymentSettingsService.java) | Parametros por gateway e parcelamento |
| [PaymentTokenFacadeService.java](/home/gregorio/git/bakery/backend/src/main/java/com/smartdata/bares/service/payment/PaymentTokenFacadeService.java) | Fachada de tokenizacao e acesso aos provedores |
| [MesaPage.tsx](/home/gregorio/git/bakery/espresso_front/src/pages/MesaPage.tsx) | Uso do PagSeguro no fluxo de mesa |
| [useDeliveryCheckout.ts](/home/gregorio/git/bakery/espresso_front/src/hooks/useDeliveryCheckout.ts) | Uso do PagSeguro no checkout de delivery |

## Webhooks e Endpoints Relevantes

| Endpoint | Papel |
|----------|-------|
| `POST /api/webhooks/mercadopago` | Confirmacao de eventos do MercadoPago |
| `POST /api/webhooks/pagseguro` | Confirmacao de eventos do PagSeguro |
| `GET /api/v1/payments/pagseguro/public-key` | Exposicao da chave publica para frontend |
| `POST /api/v1/mercadopago/*` | Operacoes ligadas ao fluxo MercadoPago |

## Fronteiras com Outros Modulos

| Area | Fronteira |
|------|-----------|
| [Pagamentos](/home/gregorio/git/bakery/docs/modules/consumo-digital/pagamentos/README.md) | Regras funcionais de quitacao |
| [Self-Checkout](/home/gregorio/git/bakery/docs/modules/consumo-digital/self-checkout/README.md) | Quitacao da mesa pelo cliente |
| [Delivery](/home/gregorio/git/bakery/docs/modules/consumo-digital/delivery/README.md) | Quitacao do pedido remoto |

## Gaps Prioritarios

- separar documentacao por provedor, em vez de manter tudo num unico README
- consolidar claramente `sandbox x producao`, sobretudo para PagSeguro
- documentar melhor o mapa de status externo x status interno
- manter esta integracao alinhada a [../../api-reference/webhooks/README.md](../../api-reference/webhooks/README.md)

## Proximos Passos

- criar `mercadopago.md` e `pagseguro.md` como documentos especificos da integracao
- adicionar `implementacao-atual.md` se a secao crescer
- substituir referencias aspiracionais a `api-reference` por links reais quando essa secao existir
