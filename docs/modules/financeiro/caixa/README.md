> **DNA**
>
> - **id**: `caixa`
> - **type**: `seed+node`
> - **label**: `Caixa`
> - **ancestors**: `[financeiro, modules, docs]`
> - **maturity**: `seed`
> - **contract**: `submodulo-bakery@1.0`

# Caixa

Caixa é o sub-domínio que registra e consolida todas as movimentações financeiras do estabelecimento: entradas e saídas automáticas (geradas por contas e pagamentos de venda), operações manuais (abertura, sangria, reforço) e relatório PDF do movimento do dia.

Pertenço a [`financeiro/`](../README.md).

## Domínio

- [`caixa.md`](./caixa.md) — entidade `MovimentoCaixa`, tipos de movimento, operações, consultas de saldo e relatório

## Maturidade

Estado atual: `seed`. O contrato `submodulo-bakery@1.0` ainda não foi formalizado. Até sua definição em `docs/contracts/`, este sub-domínio permanece `seed`.

## Leitura contextual

O movimento de caixa é produzido por três origens distintas: pagamentos de mesa (via vendas), pagamentos/recebimentos de contas (via financeiro) e operações manuais (via este sub-domínio). O flag `afetaCaixa` permite registrar movimentos informativos que não entram no saldo — útil para rastrear formas de pagamento sem comprometer o saldo em dinheiro.

O relatório PDF (`GET /api/relatorios/movimento-caixa/pdf`) consolida o dia com breakdown por forma de pagamento (DINHEIRO, PIX, CARTAO_CREDITO, CARTAO_DEBITO, VOUCHER). Não há fechamento formal de caixa — o conceito de "dia" é apenas um filtro de data.

## Exploração

- Entidade, tipos e saldo → [`caixa.md`](./caixa.md)
- O que gera entradas automáticas → [`../contas-receber/README.md`](../contas-receber/README.md)
- O que gera saídas automáticas → [`../contas-pagar/README.md`](../contas-pagar/README.md)
