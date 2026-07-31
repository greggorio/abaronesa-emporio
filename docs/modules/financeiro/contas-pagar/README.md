> **DNA**
>
> - **id**: `contas-pagar`
> - **type**: `seed+node`
> - **label**: `Contas a Pagar`
> - **ancestors**: `[financeiro, modules, docs]`
> - **maturity**: `seed`
> - **contract**: `submodulo-bakery@1.0`

# Contas a Pagar

Contas a Pagar é o sub-domínio que gerencia obrigações financeiras com fornecedores: criação, parcelamento, pagamento e cancelamento. Cada pagamento de parcela produz automaticamente uma saída no movimento de caixa na mesma transação.

Pertenço a [`financeiro/`](../README.md).

## Domínio

- [`contas-pagar.md`](./contas-pagar.md) — entidades, ciclo de vida, endpoints e gaps

## Maturidade

Estado atual: `seed`. O contrato `submodulo-bakery@1.0` ainda não foi formalizado. Até sua definição em `docs/contracts/`, este sub-domínio permanece `seed`.

## Leitura contextual

O ciclo é simples: conta criada manualmente → parcelas pagas individualmente no vencimento → cada pagamento registra `MovimentoCaixa(SAIDA)`. Cancelar um pagamento registra `MovimentoCaixa(ESTORNO)` e reabre a parcela.

A gap mais impactante é a ausência de integração com suprimentos: quando um recebimento de mercadoria é confirmado, nenhuma conta a pagar é gerada. O operador cria a obrigação manualmente com o valor e as condições negociadas com o fornecedor.

Conta só pode ser excluída se nenhuma parcela foi paga — proteção que evita desfazer saídas de caixa já registradas sem estorno explícito.

## Exploração

- Entidades, ciclo de vida e endpoints → [`contas-pagar.md`](./contas-pagar.md)
- Como o pagamento impacta o caixa → [`../caixa/README.md`](../caixa/README.md)
- Origem natural (recebimento de mercadoria) → [`../../suprimentos/README.md`](../../suprimentos/README.md)
