> **DNA**
>
> - **id**: `contas-receber`
> - **type**: `seed+node`
> - **label**: `Contas a Receber`
> - **ancestors**: `[financeiro, modules, docs]`
> - **maturity**: `seed`
> - **contract**: `submodulo-bakery@1.0`

# Contas a Receber

Contas a Receber é o sub-domínio que gerencia direitos de recebimento: criação, parcelamento, recebimento com aplicação de juros/multa/desconto e marcação de cobrança enviada. Cada recebimento de parcela produz automaticamente uma entrada no movimento de caixa.

Pertenço a [`financeiro/`](../README.md).

## Domínio

- [`contas-receber.md`](./contas-receber.md) — entidades, ciclo de vida, cálculo de valor líquido, job de voucher excedente e endpoints

## Maturidade

Estado atual: `seed`. O contrato `submodulo-bakery@1.0` ainda não foi formalizado. Até sua definição em `docs/contracts/`, este sub-domínio permanece `seed`.

## Leitura contextual

A estrutura de recebimento é mais rica que a de pagamento: `ContaReceberParcela` tem campos separados para `valorMulta`, `valorJuros`, `valorDesconto` e `valorAcrescimo`, calculados no ato do recebimento. O valor efetivamente recebido (`valorRecebido`) pode diferir do valor original da parcela.

O `VoucherExcedenteJobService` é o único produtor automático de contas a receber no sistema — executa mensalmente e cria uma `ContaReceber` para cada funcionário que consumiu além do seu limite de voucher. Todo o restante é criação manual.

A integração com crediário existe como método em `ContaReceberService.receberParcelaCredito()`, mas não é acionada por nenhum listener de venda — o ponto de disparo não foi implementado.

## Exploração

- Entidades, ciclo de vida e endpoints → [`contas-receber.md`](./contas-receber.md)
- Como o recebimento impacta o caixa → [`../caixa/README.md`](../caixa/README.md)
- Job de voucher excedente → [`../../clientes/README.md`](../../clientes/README.md)
