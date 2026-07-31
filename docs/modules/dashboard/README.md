> **DNA**
>
> - **id**: `dashboard`
> - **type**: `seed+node`
> - **label**: `Dashboard`
> - **ancestors**: `[modules, docs]`
> - **maturity**: `seed`
> - **contract**: `modulo-bakery@1.0`

# Dashboard

Dashboard é a camada transversal de leitura operacional do Bakery. Existe para dar ao gestor uma visão agregada do negócio — vendas do dia, caixa, produção, financeiro, pendências — sem precisar navegar entre módulos. Não cria nem modifica dados: é puro consumidor.

O módulo opera em dois frontends distintos: um painel Vue/Quasar com widgets draggáveis e auto-refresh, e um conjunto de páginas React no espresso_front voltadas ao admin do app.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`dashboard.md`](./dashboard.md) — arquitetura, dois frontends, painéis disponíveis, integrações e decisões
- [`metricas/`](./metricas/README.md) — métricas e KPIs em tempo real: vendas, caixa, produção, financeiro, pendências
- [`relatorios/`](./relatorios/README.md) — geração de relatórios em PDF: vendas, movimento de caixa e vendas por produto

## Maturidade

Estado atual: `seed`. O elemento está ancorado e especificado, mas ainda não foi avaliado contra o contrato `modulo-bakery@1.0`.

Esse contrato — que define os `required_artifacts` mínimos para que um módulo seja considerado `fruit` — ainda não foi formalizado. Sua referência canônica pertencerá a `docs/contracts/`. Até lá, este módulo permanece `seed` por falta de critério, não por falta de conteúdo.

## Leitura contextual

Dashboard é estritamente uma superfície de leitura. Todo dado que exibe pertence a outro módulo — a governança permanece na origem. Essa é a regra mais importante: qualquer endpoint que escreva dados não pertence ao dashboard.

Os dois frontends têm propósitos distintos: o Vue/Quasar é o painel operacional do dia a dia (draggable, auto-refresh a cada 10s, localStorage para layout); o React/Espresso é voltado ao gestor do app (clientes, engajamento, gamificação). Eles não são redundantes — cobrem audiências diferentes.

O módulo ainda não usa WebSocket ou SSE — atualiza por polling. Recharts está instalado no espresso_front mas ainda não é utilizado nos painéis.

## Exploração

- Arquitetura e painéis completos → [`dashboard.md`](./dashboard.md)
- KPIs e métricas operacionais → [`metricas/`](./metricas/README.md)
- Relatórios exportáveis em PDF → [`relatorios/`](./relatorios/README.md)
