# Contas a Receber — Especificação

## Entidades

### ContaReceber

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `cliente` | FK | `Usuario` com role elegível |
| `tipoReceita` | FK | Classificação obrigatória |
| `numeroDocumento` | String | Número de fatura ou referência (único) |
| `descricao` | String | Descrição do direito |
| `valorTotal` | BigDecimal(10,2) | Valor total |
| `numeroParcelas` | Integer | Quantidade de parcelas |
| `observacoes` | TEXT | Campo livre |
| `recorrente` | Boolean | Flag para contas recorrentes |
| `dataCadastro` | LocalDateTime | Preenchido automaticamente |
| `parcelas` | OneToMany | Parcelas em cascata |

Roles aceitas em `cliente`: `CLIENTE`, `ADMIN`, `FUNCIONARIO`, `WAITER`, `KDS`, `CAIXA`.

Métodos derivados: `isQuitada()`, `getValorRecebido()`, `getValorPendente()`, `isVencida()`.

### ContaReceberParcela

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `contaReceber` | FK | Conta pai |
| `numeroParcela` | Integer | Sequencial (1, 2, 3…) |
| `valorBruto` | BigDecimal(10,2) | Valor original da parcela |
| `valor` | BigDecimal(10,2) | Valor padrão |
| `dataVencimento` | LocalDate | Obrigatória |
| `dataRecebimento` | LocalDate | Preenchida após recebimento |
| `valorMulta` | BigDecimal(10,2) | Acréscimo por atraso (padrão: 0) |
| `valorJuros` | BigDecimal(10,2) | Juros por atraso (padrão: 0) |
| `valorDesconto` | BigDecimal(10,2) | Desconto (padrão: 0) |
| `valorAcrescimo` | BigDecimal(10,2) | Acréscimo genérico (padrão: 0) |
| `valorRecebido` | BigDecimal(10,2) | Valor efetivamente recebido |
| `formaRecebimento` | String(50) | PIX, BOLETO, CARTAO, DINHEIRO, TRANSFERENCIA |
| `usuarioRecebimento` | FK | Quem registrou o recebimento |
| `recebida` | Boolean | Flag de recebimento |
| `cobrancaEnviada` | Boolean | Se cobrança foi enviada |
| `dataEnvioCobranca` | LocalDate | Data da cobrança enviada |
| `valorLiquidoArmazenado` | BigDecimal(10,2) | Calculado e persistido no `@PrePersist` |

**Cálculo do valor líquido:**
```
valorLíquido = valorBruto + valorAcrescimo + valorJuros + valorMulta − valorDesconto
```

Método derivado: `getDiasAtraso()` — dias entre `dataVencimento` e hoje se vencida.

## Ciclo de vida

```
CRIAR → [PENDENTE] → receber parcela → [EM ABERTO ou QUITADA]
                   ↓
            ultrapassar vencimento sem receber → [VENCIDA]
            marcar cobrança → [COM COBRANÇA ENVIADA]
```

| Estado | Condição |
|--------|----------|
| Pendente | Nenhuma parcela recebida |
| Em aberto | Parcelas aguardando recebimento |
| Vencida | Tem parcela com `dataVencimento < hoje AND recebida=false` |
| Quitada | Todas as parcelas recebidas |
| Com cobrança enviada | `cobrancaEnviada=true` em pelo menos uma parcela |

## Fluxo de recebimento de parcela

1. Validar que parcela existe e não está recebida
2. Marcar `recebida=true`, preencher `dataRecebimento`, `formaRecebimento`, `valorRecebido`
3. Aplicar acréscimos/descontos se informados (multa, juros, desconto)
4. Registrar `MovimentoCaixa(tipo=CONTAS_RECEBER, operacao=ENTRADA, valor=valorRecebido, referenciaId=conta.id)`
5. Salvar em `@Transactional`

## Job de voucher excedente

`VoucherExcedenteJobService` executa mensalmente e cria automaticamente `ContaReceber` para funcionários que consumiram além do limite de voucher:

1. Busca consumo de voucher do mês anterior por funcionário
2. Calcula excedente: `consumo total − voucher disponível`
3. Se excedente > 0, cria `ContaReceber` com:
   - `tipoReceita` = `Excedente de voucher` (criado dinamicamente se não existir)
   - `numeroDocumento` = `VOUCHER-EXC-{usuarioId}-{yyyyMM}` (único — evita duplicatas)
   - Parcela única com vencimento = próximo dia útil do mês atual
4. Validação de idempotência por `numeroDocumento` antes de criar

## Endpoints de gestão do cliente

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` | `/api/contas-receber/me` | Contas do cliente logado: abertas, pagas, total |
| `GET` | `/api/contas-receber/getrecebimentoshoje` | Recebimentos do dia |

## Endpoints de admin

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/contas-receber` | Criar conta com parcelas |
| `GET` | `/api/contas-receber/{id}` | Buscar por ID |
| `PUT` | `/api/contas-receber/{id}` | Editar conta e parcelas |
| `DELETE` | `/api/contas-receber/{id}` | Deletar (apenas se sem parcelas recebidas) |
| `POST` | `/api/contas-receber/parcela/{parcelaId}/receber` | Receber parcela |
| `POST` | `/api/contas-receber/parcela/{parcelaId}/marcar-cobranca` | Marcar cobrança enviada |
| `GET` | `/api/contas-receber/list` | Listar com paginação |
| `GET` | `/api/contas-receber/form-config` | Configuração de formulário dinâmico |

**Parâmetros de recebimento**: `dataRecebimento` (LocalDate), `formaRecebimento` (String), `valorRecebido` (BigDecimal).

## Gaps

- **Geração automática por crediário**: método `receberParcelaCredito()` existe em `ContaReceberService` mas não é acionado por nenhum evento de venda — ponto de disparo não implementado
- **Juros automáticos por atraso**: sem configuração de taxa — operador insere `valorJuros` e `valorMulta` manualmente no recebimento
- **Renegociação de parcelas**: sem fluxo dedicado — requer deletar e recriar a conta
- **Alertas de cobrança**: sem envio automático de cobrança ao cliente; `cobrancaEnviada` é apenas um flag manual
