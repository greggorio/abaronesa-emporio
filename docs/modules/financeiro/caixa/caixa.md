# Caixa — Especificação

## Entidade MovimentoCaixa

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `dataHora` | LocalDateTime | Timestamp do movimento |
| `tipo` | TipoMovimentoCaixa | Tipo (enum abaixo) |
| `valor` | BigDecimal(10,2) | Valor do movimento |
| `meioPagamento` | TipoFormaPagamento | Forma de pagamento (enum abaixo) |
| `afetaCaixa` | Boolean | Se deve compor o saldo computado |
| `operacao` | TipoOperacao | `ENTRADA` ou `SAIDA` |
| `observacao` | TEXT | Campo livre |
| `referenciaId` | Long | ID da conta ou pagamento de origem |
| `referenciaTipo` | String | `PAGAMENTO`, `CONTA_PAGAR`, `CONTA_RECEBER`, `VENDA_CREDIARIO` |
| `responsavel` | FK | Usuário que registrou |

## Tipos de movimento

| Tipo | Operação | Origem |
|------|----------|--------|
| `PAGAMENTO_MESA` | ENTRADA | Pagamento de venda em mesa |
| `GORJETA` | ENTRADA | Gorjeta recebida |
| `CAIXA_INICIAL` | ENTRADA | Abertura de caixa |
| `REFORCO` | ENTRADA | Adição manual de dinheiro |
| `SANGRIA` | SAIDA | Retirada manual de dinheiro |
| `CONTAS_PAGAR` | SAIDA | Pagamento de parcela de conta a pagar |
| `CONTAS_RECEBER` | ENTRADA | Recebimento de parcela de conta a receber |
| `ESTORNO` | SAIDA | Estorno de pagamento ou cancelamento |
| `OUTROS` | ENTRADA/SAIDA | Movimentações diversas |

## Formas de pagamento

| Enum | Código SAT |
|------|-----------|
| `DINHEIRO` | 01 |
| `VOUCHER` | 01 |
| `PIX` | 17 |
| `CARTAO_CREDITO` | 03 |
| `CARTAO_DEBITO` | 04 |
| `TRANSFERENCIA` | 18 |
| `OUTROS` | 99 |

## Operações manuais

### Abertura de caixa
```
tipo: CAIXA_INICIAL
operacao: ENTRADA
meioPagamento: DINHEIRO
afetaCaixa: true
valor: saldo inicial de abertura
```

### Sangria (retirada)
```
tipo: SANGRIA
operacao: SAIDA
afetaCaixa: true
valor: valor retirado
```

### Reforço (adição)
```
tipo: REFORCO
operacao: ENTRADA
afetaCaixa: true
valor: valor adicionado
```

## Cálculo de saldo

Saldo do período = `SUM(ENTRADA where afetaCaixa=true) − SUM(SAIDA where afetaCaixa=true)`.

Queries no `MovimentoCaixaRepository`:
- `findResumoHojeRaw()` — totais de entrada e saída do dia (SQL nativo com CASE)
- `findDetalhesPorTipoRaw()` — subtotais por `meioPagamento` (GROUP BY)
- `calcularSaldoAteData()` — saldo acumulado até uma data específica
- `findMovimentosAfetamCaixaPorPeriodo()` — lista detalhada para o período

## Relatório PDF

`GET /api/relatorios/movimento-caixa/pdf`

Parâmetros: `data` (LocalDate, opcional; padrão: hoje). Data não pode ser futura.

Estrutura do PDF:
1. Header: logo, razão social, CNPJ
2. Período
3. Total entradas / total saídas / saldo
4. Breakdown por forma de pagamento (DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO, VOUCHER)
5. Listagem de movimentos do dia
6. Rodapé: usuário gerador + timestamp

Stack: Thymeleaf (template HTML) → Flying Saucer/iText (PDF).

## Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/movimento-caixa/manual` | Registrar movimento manual |
| `GET` | `/api/movimento-caixa/list` | Listar com paginação |
| `GET` | `/api/movimento-caixa/form-config` | Configuração de formulário dinâmico |
| `GET` | `/api/relatorios/movimento-caixa/pdf` | Gerar relatório PDF do dia |

## Frontend

| Componente | Tipo | Descrição |
|-----------|------|-----------|
| `PainelMovimentoCaixa.vue` | Painel dashboard | Entradas, saídas, saldo e breakdown por forma |
| `PainelFinanceiro.vue` | Painel dashboard | Resumo de recebimentos vs. pagamentos com pendências |
| `MovimentoCaixaDialog.vue` | Diálogo | Registrar movimento manual |
| `RelatorioMovimentoCaixaDialog.vue` | Diálogo | Gerar e baixar relatório PDF |

## Gaps

- **Fechamento formal de caixa**: não existe — "dia" é apenas filtro de data; sem fluxo de abertura/conferência/fechamento com responsável
- **Exportação CSV/Excel**: apenas PDF disponível
- **Conciliação bancária**: sem reconciliação de extrato — `TRANSFERENCIA` e `PIX` são apenas registros manuais
