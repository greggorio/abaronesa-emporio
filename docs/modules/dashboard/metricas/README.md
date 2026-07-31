> **DNA**
>
> - **id**: `metricas`
> - **type**: `seed+node`
> - **label**: `Métricas`
> - **ancestors**: `[dashboard, modules, docs]`
> - **maturity**: `seed`
> - **contract**: `submodulo-bakery@1.0`

# Métricas

Métricas é o sub-domínio que expõe os indicadores operacionais do negócio em tempo real: vendas do dia, movimento de caixa, pedidos em produção, top produtos, histórico de vendas e pendências de catálogo.

Pertenço a [`dashboard/`](../README.md).

## Domínio

- [`metricas.md`](./metricas.md) — todos os endpoints, parâmetros, dados retornados e filtros suportados

## Maturidade

Estado atual: `seed`. O contrato `submodulo-bakery@1.0` ainda não foi formalizado. Até sua definição em `docs/contracts/`, este sub-domínio permanece `seed`.

## Leitura contextual

As métricas são calculadas em tempo real a cada requisição — sem cache. Isso garante dados frescos mas implica que picos de carga batem direto nos repositórios. Para leituras operacionais de uso diário, o custo é aceitável; para alta concorrência, o padrão precisaria ser revisado.

Os dashboards especializados (gamificação, promoções, voucher) são menos óbvios na estrutura da pasta mas existem e estão implementados — `DashboardGamificacaoService`, `DashboardPromocoesService` e o endpoint de consumo de voucher têm controllers próprios.

## Exploração

- Endpoints e dados completos → [`metricas.md`](./metricas.md)
- Como os dados são exibidos → [`../dashboard.md`](../dashboard.md)
- Fonte dos dados de vendas → [`../../vendas/README.md`](../../vendas/README.md)
- Fonte dos dados financeiros → [`../../financeiro/README.md`](../../financeiro/README.md)
