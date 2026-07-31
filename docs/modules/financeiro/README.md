> **DNA**
>
> - **id**: `financeiro`
> - **type**: `seed+node`
> - **label**: `Financeiro`
> - **ancestors**: `[modules, docs]`
> - **maturity**: `seed`
> - **contract**: `modulo-bakery@1.0`

# Financeiro

Financeiro é o módulo que governa as obrigações e os direitos de recebimento do estabelecimento, a execução de caixa no dia a dia e as classificações de despesas e receitas. Responde a uma questão central: "Quanto devemos, quanto temos a receber e qual é o saldo do caixa agora?"

Cada ação financeira — pagar uma parcela, receber um crédito — produz automaticamente um registro em `MovimentoCaixa`, mantendo o caixa sincronizado sem intervenção manual.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`financeiro.md`](./financeiro.md) — entidades, classificações, integrações, decisões de domínio e gaps

## Sub-domínios

- [`contas-pagar/`](./contas-pagar/README.md) — obrigações com fornecedores: criação, parcelamento, pagamento e cancelamento
- [`contas-receber/`](./contas-receber/README.md) — direitos de recebimento: parcelamento, acréscimos/descontos e job de voucher excedente
- [`caixa/`](./caixa/README.md) — movimento de caixa: entradas e saídas manuais, sangria, reforço e relatório PDF

## Maturidade

Estado atual: `seed`. O elemento está percebido, ancorado e especificado em [`financeiro.md`](./financeiro.md), mas ainda não foi avaliado contra o contrato `modulo-bakery@1.0`.

## Leitura contextual

**Coerente:** CRUD completo para contas a pagar e a receber, parcelamento flexível com campos independentes por parcela, reflexo automático em movimento de caixa e painéis financeiros no dashboard.

**Desalinhado:** as duas integrações mais naturais do módulo não estão implementadas — recebimento de mercadoria (suprimentos) não gera conta a pagar automaticamente, e venda a prazo (crediário) não gera conta a receber. O módulo existe operacionalmente, mas depende de registro manual onde o sistema deveria agir sozinho.

## Fronteiras

- `financeiro` × `suprimentos`: pedido e recebimento de mercadoria pertencem a suprimentos; a conta a pagar resultante deveria ser gerada automaticamente — ainda não é
- `financeiro` × `vendas`: venda e pagamento em mesa pertencem a vendas; pagamentos geram `MovimentoCaixa` automaticamente; conta a receber de crediário ainda é criada manualmente
- `financeiro` × `dashboard`: `DashboardService` lê contas e caixa em modo somente leitura; nenhuma escrita cruza para dashboard

## Exploração

- Entidades e classificações → [`financeiro.md`](./financeiro.md)
- Contas a pagar → [`contas-pagar/README.md`](./contas-pagar/README.md)
- Contas a receber → [`contas-receber/README.md`](./contas-receber/README.md)
- Movimento de caixa → [`caixa/README.md`](./caixa/README.md)
