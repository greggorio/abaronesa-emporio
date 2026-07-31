# Financeiro — Especificação do Domínio

## Entidades centrais

| Entidade | Descrição |
|----------|-----------|
| `ContaPagar` | Obrigação com fornecedor |
| `ContaPagarParcela` | Parcela da obrigação |
| `ContaReceber` | Direito de recebimento |
| `ContaReceberParcela` | Parcela do recebimento |
| `MovimentoCaixa` | Registro de entrada/saída de caixa |
| `CategoriaDespesa` | Classificação de despesas (ex.: Aluguel, Folha) |
| `TipoReceita` | Classificação de receitas (ex.: Geral, Mensalista) |

## Classificações

### CategoriaDespesa

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `nome` | String | Nome da categoria |
| `ativo` | Boolean | Ativo/inativo (padrão: true) |

Obrigatória em toda `ContaPagar`. Gerenciada via CRUD admin — sem seed padrão.

### TipoReceita

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | PK | — |
| `nome` | String | Nome do tipo |

Seed padrão: `Geral` (id=1) e `Mensalista` (id=2). O tipo `Excedente de voucher` é criado dinamicamente pelo `VoucherExcedenteJobService` quando necessário — não faz parte do seed fixo.

Obrigatório em toda `ContaReceber`.

## Integrações

| Módulo | Direção | Mecanismo | Implementado |
|--------|---------|-----------|:---:|
| `suprimentos/` | Entrada | Recebimento confirmado deveria gerar `ContaPagar` | ❌ |
| `vendas/` | Entrada | Venda a prazo deveria gerar `ContaReceber` | ❌ |
| `vendas/` | Saída | Pagamento de mesa gera `MovimentoCaixa` automaticamente | ✅ |
| `clientes/` | Referência | `ContaReceber.cliente` referencia `Usuario` | ✅ |
| `dashboard/` | Saída | `DashboardService` lê contas e caixa em modo ReadOnly | ✅ |

## Decisões de domínio

- **Reflexo automático em caixa**: pagar uma parcela gera `MovimentoCaixa(tipo=CONTAS_PAGAR, operacao=SAIDA)` na mesma transação; receber gera `MovimentoCaixa(tipo=CONTAS_RECEBER, operacao=ENTRADA)`. Consistência garantida sem etapa manual.
- **Parcelamento explícito**: parcelas são entidades separadas com campos independentes (valor, vencimento, forma de pagamento). Não são calculadas automaticamente — o operador define cada parcela.
- **Acréscimos e descontos no ato**: juros, multa, desconto e acréscimo genérico são aplicados no momento do recebimento, não pré-configurados. O valor líquido é calculado: `valorLíquido = valorBruto + valorAcrescimo + valorJuros + valorMulta − valorDesconto`.
- **Exclusão protegida**: conta só pode ser deletada se nenhuma parcela foi paga/recebida. Deletar remove parcelas em cascata.
- **`afetaCaixa`**: flag em `MovimentoCaixa` permite registrar movimentos informativos que não entram no saldo computado.
- **Rastreabilidade**: `MovimentoCaixa.responsavel` é sempre preenchido via `SecurityUtils.getUsuarioAtual()`.

## Gaps

| Funcionalidade | Situação |
|----------------|----------|
| `ContaPagar` automática por recebimento | Não implementada — criação manual pelo operador |
| `ContaReceber` automática por crediário | Não implementada — criação manual pelo operador |
| Conciliação bancária | Fora do escopo — sem reconciliação de extrato bancário |
| Fluxo de caixa projetado | Fora do escopo — apenas dados históricos disponíveis |
| DRE / contabilidade geral | Fora do escopo |
| Juros automáticos por atraso | Sem configuração de taxa — operador insere valor manualmente |
| Renegociação de parcelas | Sem fluxo dedicado — requer deletar e recriar |
| Alertas de vencimento | Sem notificação push ou e-mail automático |
| Exportação CSV/Excel | Apenas relatório PDF de movimento de caixa disponível |
