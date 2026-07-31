# Pagamento — Especificação

## Entidades

### Pagamento (`Pagamento.java`, 84 linhas)

Tabela `pagamento`. Representa uma transação de pagamento.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK |
| `sessaoMesa` | `@ManyToOne` → `SessaoMesa` | Sessão sendo paga |
| `sessaoConvidado` | `@ManyToOne` → `SessaoConvidado` | Beneficiário (nullable = mesa inteira) |
| `pagante` | `@ManyToOne` → `SessaoConvidado` | Quem efetuou o pagamento |
| `metodo` | `String` | `pix`, `card`, `cash`, `voucher` |
| `cartaoTipo` | `String` | `credito`, `debito` |
| `status` | `StatusPagamento` | `PENDING`, `PAID`, `FAILED`, `CANCELED` |
| `valor` | `BigDecimal` | Valor total pago |
| `valorBase` | `BigDecimal` | Valor sem taxa de serviço |
| `valorTaxaServico` | `BigDecimal` | Taxa de serviço |
| `valorCouvert` | `BigDecimal` | Couvert incluso |
| `percentualTaxaServico` | `BigDecimal` | Percentual aplicado |
| `incluiTaxaServico` | `Boolean` | Se o pagamento incluiu taxa |
| `qrPayload` | `String` | Payload do QR code PIX |
| `providerRef` | `String` | Referência do gateway (Mercado Pago / PagSeguro) |
| `selfCheckoutOrigem` | `Boolean` | Se veio do auto-atendimento |
| `selfCheckoutResolvido` | `Boolean` | Se já foi resolvido pelo staff |
| `pagoEm` | `LocalDateTime` | Data/hora do pagamento |

### PagamentoAlocacao (`PagamentoAlocacao.java`, 32 linhas)

Tabela `pagamento_alocacao`. Distribui um pagamento entre convidados (split).

| Campo | Tipo |
|-------|------|
| `id` | `Long` (PK) |
| `pagamento` | `@ManyToOne` → `Pagamento` |
| `sessaoConvidado` | `@ManyToOne` → `SessaoConvidado` |
| `valor` | `BigDecimal` |

## DTOs

| DTO | Descrição |
|-----|-----------|
| `SelfCheckoutPaymentRequest` | `escopo` (convidado/mesa), `sessaoMesaId`, `sessaoConvidadoId`, `metodo` (pix/card), `payerName`, `payerEmail`, `payerTaxId`, `cardToken`, `installments`, `paymentMethodId` |
| `SelfCheckoutPaymentResponse` | `pagamentoId`, `gateway`, `status`, `providerPaymentId`, `message`, `pixQrCode`, `pixQrCodeBase64`, `expiresAt`, `amountCentavos` |
| `RegistrarPagamentoRequest` | `sessaoConvidadoId`, `paganteId`, `valorCentavos`, `metodo`, `cartaoTipo`, `alocacoes`, `valorTaxaServicoCentavos`, `incluiTaxaServico`, `valorCouvertCentavos` |
| `RegistrarPagamentosMultiplosRequest` | Lista de `RegistrarPagamentoRequest` |
| `PagamentoWebhookRequest` | `provedor`, `evento`, `referenciaProvedor`, `pagamentoId`, `valorCentavos` |
| `SalesRecordDTO` | `id`, `pagoEm`, `mesaSlug`, `mesaRotulo`, `beneficiario`, `pagante`, `metodo`, `valor`, `valorBase`, `valorTaxaServico`, `providerRef` |

## Controllers

### PagamentosController (386 linhas) — `/api/pagamentos`

| Método | Path | Descrição |
|--------|------|-----------|
| `POST` | `/api/pagamentos/intent` | Criar intenção de pagamento (PIX ou cartão). Para PIX: cria Pagamento PENDING, chama gateway, retorna QR code. Para cartão: chaga gateway com token, processa imediatamente |
| `POST` | `/api/pagamentos/webhook` | Webhook do gateway (Mercado Pago / PagSeguro). Confirma pagamento, atualiza status para PAID, gera MovimentoCaixa (PAGAMENTO_MESA), fecha sessão se tudo pago |

### WaiterPaymentsController (353 linhas) — `/api/waiter/pagamentos` (`@PreAuthorize` WAITER/CAIXA/ADMIN)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/waiter/pagamentos` | Listar pagamentos self-checkout pendentes de resolução |
| `POST` | `/api/waiter/mesas/{sessaoMesaId}/fechar` | Fechar sessão (staff) |
| `POST` | `/api/waiter/pagamentos/{pagamentoId}/resolver` | Resolver/arquivar pagamento self-checkout |
| `POST` | `/api/waiter/pagamentos/{pagamentoId}/emitir-nfce` | Emitir NFC-e |
| `GET` | `/api/waiter/pagamentos/{pagamentoId}/comprovante` | Gerar comprovante não-fiscal PDF |

### VendasController (254 linhas) — `/api/vendas`

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/vendas/list` | Lista paginada (via PagamentoListService) |
| `GET` | `/api/vendas/form-config` | Config dinâmica |
| `GET` | `/api/vendas/{id}` | Detalhe da venda |
| `GET` | `/api/vendas/report-table` | Tabela para relatório |

## Fluxo de pagamento

### Self-checkout (PIX)
1. Cliente clica "Pagar" na `MesaPage.tsx` → `POST /api/pagamentos/intent {metodo: "pix"}`
2. Backend cria `Pagamento` com status `PENDING`, consulta gateway PIX
3. Retorna `pixQrCode` (texto copia-cola) e `pixQrCodeBase64` (imagem)
4. Cliente paga pelo app do banco
5. Gateway notifica → `POST /api/pagamentos/webhook` → status vira `PAID`
6. Backend gera `MovimentoCaixa` (PAGAMENTO_MESA)
7. Evento SSE `payment.updated` é emitido
8. Se tudo pago, sessão é automaticamente fechada (`SessaoMesa.CLOSED`)
9. Garçom vê no `WaiterPage.tsx` → resolve, emite NFC-e, fecha

### Self-checkout (Cartão)
1. Cliente clica "Pagar" → `POST /api/pagamentos/intent {metodo: "card", cardToken, installments, paymentMethodId}`
2. Backend processa imediatamente via gateway com token do cartão
3. Se sucesso → `PAID`, mesmo fluxo do PIX
4. Se falha → `FAILED`, cliente tenta novamente

### Pagamento assistido (staff)
1. Garçom abre `PagamentoDialog.tsx` na `MesasGrid.tsx`
2. Visualiza conta, pode fazer split por convidado
3. Registra pagamento: `POST /api/waiter/pagamentos` (via outra rota ou diretamente no fluxo)
4. Suporta múltiplos métodos na mesma conta (ex: R$50 no dinheiro, R$70 no cartão)
5. Após tudo pago, emite NFC-e, fecha sessão

## Resolução de pagamento (garçom)

No `WaiterPage.tsx`:
1. Aba "Pagamentos" lista pagamentos self-checkout com status `PAID` e `selfCheckoutResolvido = false`
2. Garçom confere, clica em "Resolver" → `POST /api/waiter/pagamentos/{id}/resolver`
3. Opcionalmente emite NFC-e → `POST /api/waiter/pagamentos/{id}/emitir-nfce`
4. Gera comprovante PDF → `GET /api/waiter/pagamentos/{id}/comprovante`
5. Envia via WhatsApp se necessário

## Regras de negócio

1. **Pagamento PIX expira**: QR code tem validade (campo `expiresAt`). Após expirado, cliente precisa gerar nova intenção
2. **Múltiplos pagamentos**: uma conta pode ser paga em múltiplas transações (split por método ou por convidado). A sessão só fecha quando `devidoCentavos = 0`
3. **Alocação**: pagamentos podem ser alocados a convidados específicos (`PagamentoAlocacao`). Pagamentos sem alocação são rateados proporcionalmente entre todos os convidados
4. **NFC-e**: emitida pelo garçom no momento da resolução. Se a venda for cancelada após emissão, é necessário fazer o cancelamento da NFC-e na SEFAZ (não automatizado)
5. **Self-checkout resolvido**: o pagamento só é considerado completamente processado após o garçom resolver. Até lá, fica como "pendente de conferência" na tela do garçom
