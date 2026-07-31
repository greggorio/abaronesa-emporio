# Gamificação

Gamificação é o sub-domínio que governa o saldo de pontos do cliente, os movimentos de crédito e débito, as regras de conversão e arredondamento, a expiração de pontos e as métricas de dashboard. Existe para registrar como o cliente ganha pontos pelo consumo e como os pontos são consumidos por resgates.

Pertenço a [`fidelizacao/`](../README.md).

## Domínio

- [`gamificacao.md`](./gamificacao.md) — modelo de dados, endpoints, regras de pontuação, configurações, dashboard KPIs, rankings, serviços e frontend

## Leitura contextual

O sistema de pontos está implementado no backend bakery com lógica completa de cálculo por consumo (valor do item ÷ taxa de conversão × arredondamento), prevenção de duplicidade por referência, consulta de saldo e extrato, e dashboard administrativo com 7 KPIs e 4 rankings. A configuração (ativa, taxa, arredondamento, expiração) é gerenciada via interface Vue.

A fronteira com `resgates/` é clara: `gamificacao` mantém o saldo e registra os movimentos; `resgates` consulta o saldo e cria movimentos de débito.

## Exploração

- Especificação completa → [`gamificacao.md`](./gamificacao.md)
- Catálogo de recompensas → [`../recompensas/README.md`](../recompensas/README.md)
- Processo de resgate → [`../resgates/README.md`](../resgates/README.md)
