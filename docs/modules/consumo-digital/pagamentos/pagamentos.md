# Pagamentos — Especificação

## Definição

Sub-domínio que governa o pagamento digital da jornada de consumo — mesa e delivery. Opera com múltiplos gateways (MercadoPago e PagSeguro), suporta Pix e cartão, e discrimina os componentes do total (consumo, taxa de serviço, couvert).

## Entidade Pagamento

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `sessaoMesa` | FK | Sessão de mesa vinculada |
| `sessaoConvidado` | FK (nullable) | Quem está pagando (null = mesa inteira) |
| `pagante` | FK | SessaoConvidado que iniciou o pagamento |
| `metodo` | String | `pix`, `card` ou `cash` |
| `cartaoTipo` | String | `credito` ou `debito` |
| `status` | Enum | PENDING → PAID / FAILED / CANCELED |
| `valor` | BigDecimal | Total do pagamento |
| `valorBase` | BigDecimal | Componente de consumo |
| `valorTaxaServico` | BigDecimal | Componente de taxa de serviço |
| `valorCouvert` | BigDecimal | Componente de couvert artístico |
| `percentualTaxaServico` | BigDecimal | Percentual vigente no momento do pagamento |

## Gateways

| Gateway | Métodos suportados | Webhook |
|---------|--------------------|---------|
| MercadoPago | Pix, cartão crédito, cartão débito | `POST /api/pagamentos/webhook` |
| PagSeguro | Pix, cartão | `POST /api/pagamentos/pagseguro/webhook` |

A seleção do gateway é por pedido. `PaymentFacadeService` abstrai a diferença entre os dois.

## Fluxo de self-checkout (mesa)

1. Convidado acessa conta e decide pagar
2. `POST /api/pagamentos/intent` com escopo (convidado ou mesa inteira) e método
3. Sistema verifica se `selfCheckoutLiberado = true` na sessão — se não, rejeita
4. Gateway cria a cobrança e retorna deeplink (Pix) ou token de cartão
5. Cliente realiza o pagamento
6. Gateway notifica via webhook → `PaymentStatusUpdater` atualiza status para PAID
7. SSE publica `payment.made` → conta é recalculada em todos os dispositivos conectados

## Validação manual pelo staff

Quando o pagamento não é confirmado automaticamente (ex: dinheiro, problemas de gateway), o staff pode resolver via `POST /api/waiter/pagamentos/{pagamentoId}/resolver`. Isso marca o pagamento como PAID sem passar pelo gateway.

## Adicionalidades

### Taxa de serviço
- Percentual configurável via `ConfigManager`
- Apresentada como sugestão — cliente pode ajustar
- Discriminada como `valorTaxaServico` no registro

### Couvert artístico
- Valor fixo por pessoa quando ativo
- Gerenciado por `SessaoCobranca`
- Discriminado como `valorCouvert` no registro

## Escopo

**Inclui:**
- Self-checkout via Pix e cartão (crédito e débito)
- Pagamento manual registrado via staff
- Taxa de serviço configurável
- Couvert artístico
- Múltiplos gateways com webhooks
- Discriminação de componentes do pagamento

**Não inclui:**
- Parcelamento (estrutura existe no modelo; não validada em produção)
- Integração com POS externo
- Processamento offline
