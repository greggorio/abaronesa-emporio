# Movimento de Caixa — Especificação

## Entidade

### MovimentoCaixa

Tabela `movimento_caixa`. Registro individual de entrada ou saída financeira.

| Campo | Tipo | Detalhes |
|-------|------|----------|
| `id` | `Long` | PK |
| `dataHora` | `LocalDateTime` | Momento do movimento |
| `tipo` | `TipoMovimentoCaixa` | (ver enum abaixo) |
| `valor` | `BigDecimal` | |
| `meioPagamento` | `String` | `pix`, `cash`, `card_credito`, `card_debito`, `voucher` |
| `afetaCaixa` | `Boolean` | Se altera o saldo do caixa físico |
| `operacao` | `String` | `ENTRADA` ou `SAIDA` |
| `observacao` | `String` | |
| `referenciaId` | `Long` | ID da entidade de origem (ex: Pagamento.id) |
| `referenciaTipo` | `String` | Tipo da entidade de origem (ex: "Pagamento") |
| `responsavelId` | `Long` | ID do usuário responsável |
| `responsavelNome` | `String` | Nome do responsável |

## Enum

### TipoMovimentoCaixa

| Valor | Operação | Descrição |
|-------|----------|-----------|
| `PAGAMENTO_MESA` | ENTRADA | Pagamento de venda de mesa |
| `GORJETA` | ENTRADA | Gorjeta registrada |
| `CAIXA_INICIAL` | ENTRADA | Valor inicial de abertura de caixa |
| `REFORCO` | ENTRADA | Reforço de caixa |
| `SANGRIA` | SAIDA | Retirada de valor do caixa |
| `CONTAS_PAGAR` | SAIDA | Pagamento de conta |
| `CONTAS_RECEBER` | ENTRADA | Recebimento de conta |
| `ESTORNO` | SAIDA | Estorno de pagamento |
| `OUTROS` | ENTRADA/SAIDA | Movimento manual não classificado |

## Serviço

### MovimentoCaixaService (149 linhas)

| Método | Descrição |
|--------|-----------|
| `registrarPagamentoMesa(Pagamento)` | Cria movimento ENTRADA tipo PAGAMENTO_MESA vinculado ao pagamento |
| `registrarGorjeta(BigDecimal valor, Pagamento pagamento)` | Cria movimento ENTRADA tipo GORJETA |
| `registrarEstorno(Pagamento pagamento)` | Cria movimento SAIDA tipo ESTORNO |
| `registrarCaixaInicial(BigDecimal valor, String responsavel)` | Cria movimento ENTRADA tipo CAIXA_INICIAL |
| `registrarSangria(BigDecimal valor, String observacao, ...)` | Cria movimento SAIDA tipo SANGRIA |
| `registrarReforco(BigDecimal valor, ...)` | Cria movimento ENTRADA tipo REFORCO |
| `registrarMovimentoConta(TipoMovimentoCaixa tipo, ...)` | Genérico para contas a pagar/receber |
| `registrar(MovimentoCaixaRequest request)` | Genérico para movimentos manuais |

## Controller

### MovimentoCaixaController (76 linhas) — `/api/movimento-caixa`

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/movimento-caixa/list` | Lista paginada (via BaseListController) |
| `GET` | `/api/movimento-caixa/form-config` | Config dinâmica |
| `POST` | `/api/movimento-caixa` | Criar manual |
| `POST` | `/api/movimento-caixa/manual` | Registrar movimento manual |

## DTOs

| DTO | Campos |
|-----|--------|
| `MovimentoCaixaDTO` (record, @Builder) | `id, dataHora, tipo, valor, meioPagamento, afetaCaixa, operacao, observacao, referenciaId, referenciaTipo, responsavelId, responsavelNome` |
| `MovimentoCaixaRequest` (record) | `tipo, valor, meioPagamento, afetaCaixa, operacao, observacao, referenciaId, referenciaTipo` |

## Regras de negócio

1. **Movimentos automáticos**: pagamentos de mesa e gorjetas geram movimentos automaticamente (via `MovimentoCaixaService.registrarPagamentoMesa()` e `registrarGorjeta()`) — sem intervenção manual
2. **Estorno**: ao cancelar um pagamento já confirmado, um movimento de estorno é gerado automaticamente para reverter o valor no caixa
3. **Movimentos manuais**: sangria, reforço e caixa inicial são registrados manualmente pelo staff autorizado
4. **Rastreabilidade**: cada movimento mantém `referenciaId` e `referenciaTipo` para rastrear a origem (ex: `Pagamento.id` / `"Pagamento"`)
5. **Auditoria**: todo movimento registra `responsavelId` e `responsavelNome` para auditoria
