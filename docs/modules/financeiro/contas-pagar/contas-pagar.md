# Contas a Pagar — Especificação

## Entidades

### ContaPagar

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `fornecedor` | FK | Fornecedor obrigatório |
| `categoriaDespesa` | FK | Classificação obrigatória |
| `descricao` | String | Descrição da obrigação |
| `valorTotal` | BigDecimal(10,2) | Valor total |
| `numeroParcelas` | Integer | Quantidade de parcelas |
| `recorrente` | Boolean | Flag para contas recorrentes |
| `dataCadastro` | LocalDateTime | Preenchido automaticamente |
| `parcelas` | OneToMany | Parcelas em cascata |

Métodos derivados (não persistidos): `isQuitada()`, `getValorPago()`, `getValorPendente()`, `isVencida()`.

### ContaPagarParcela

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `contaPagar` | FK | Conta pai |
| `numeroParcela` | Integer | Sequencial (1, 2, 3…) |
| `valor` | BigDecimal(10,2) | Valor da parcela |
| `dataVencimento` | LocalDate | Obrigatória |
| `dataPagamento` | LocalDate | Preenchida após pagamento |
| `formaPagamento` | String(50) | PIX, BOLETO, CARTAO, DINHEIRO, TRANSFERENCIA |
| `paga` | Boolean | Flag de pagamento |

Método derivado: `isVencida()` — `dataVencimento < hoje AND paga = false`.

## Ciclo de vida

```
CRIAR → [PENDENTE] → pagar parcela → [EM ABERTO ou QUITADA]
                   ↓
            ultrapassar vencimento sem pagar → [VENCIDA]
            cancelar pagamento → [EM ABERTO]
```

| Estado | Condição |
|--------|----------|
| Pendente | Nenhuma parcela paga |
| Em aberto | Parcelas aguardando pagamento |
| Vencida | Tem parcela com `dataVencimento < hoje AND paga=false` |
| Quitada | Todas as parcelas pagas |

## Fluxo de pagamento de parcela

1. Validar que parcela existe e não está paga
2. Marcar `paga=true`, preencher `dataPagamento` e `formaPagamento`
3. Registrar `MovimentoCaixa(tipo=CONTAS_PAGAR, operacao=SAIDA, referenciaId=conta.id)`
4. Salvar em `@Transactional`

## Fluxo de cancelamento de pagamento

1. Validar que parcela está paga
2. Registrar `MovimentoCaixa(tipo=ESTORNO, operacao=SAIDA)` para reverter
3. Marcar `paga=false`, limpar `dataPagamento` e `formaPagamento`

## Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/contas-pagar` | Criar conta com parcelas |
| `GET` | `/api/contas-pagar/{id}` | Buscar por ID |
| `PUT` | `/api/contas-pagar/{id}` | Editar conta e parcelas |
| `DELETE` | `/api/contas-pagar/{id}` | Deletar (apenas se sem parcelas pagas) |
| `POST` | `/api/contas-pagar/parcela/{parcelaId}/pagar` | Pagar parcela |
| `POST` | `/api/contas-pagar/parcela/{parcelaId}/cancelar-pagamento` | Cancelar pagamento |
| `GET` | `/api/contas-pagar/list` | Listar com paginação |
| `GET` | `/api/contas-pagar/form-config` | Configuração de formulário dinâmico |

**Parâmetros de pagamento**: `dataPagamento` (LocalDate), `formaPagamento` (String).

## Gaps

- **Geração automática por recebimento**: recebimento de mercadoria confirmado em suprimentos não cria `ContaPagar` — criação sempre manual
- **Parcelamento automático**: sem cálculo automático de parcelas (30/60/90 dias) — operador define cada parcela individualmente
- **Juros por atraso**: sem configuração de taxa de juros automática — inserção manual no cancelamento e recriar se necessário
- **Alertas de vencimento**: sem notificação automática quando parcela está próxima do vencimento
