# Métricas — Especificação

## Endpoints de métricas (backend principal)

| Endpoint | Parâmetros | Dados retornados |
|----------|-----------|-----------------|
| `GET /api/dashboard/vendas` | — | `totalMesas`, `mesasOcupadas`, `percentualOcupacao`, `totalVendasCentavos`, `ticketMedioCentavos`, `quantidadeVendas` |
| `GET /api/dashboard/pedidos-preparacao` | — | Fila por estação: bar (queued/accepted/preparing/ready) e cozinha (idem), total em espera |
| `GET /api/dashboard/top-produtos` | `periodo` (hoje/7d/30d), `ordenarPor` (valor/quantidade) | Lista de produtos com id, nome, quantidade vendida, valor total |
| `GET /api/dashboard/movimento-caixa` | — | `entradas`, `saidas`, `saldo`, detalhes por tipo (PIX, DINHEIRO, CARTAO_CREDITO, CARTAO_DEBITO, VOUCHER) |
| `GET /api/dashboard/vendas-periodo` | `periodo` (hoje/7d/30d/custom), `dateFrom`, `dateTo` | `faturamento`, `quantidadeVendas`, `ticketMedio`, breakdown por método de pagamento, `faturamentoBase`, `taxaServico` |
| `GET /api/dashboard/financeiro` | — | `recebimentos`, `total_recebido`, `pagamentos`, `total_pago`, `pendencias_recebimento`, `pendencias_pagamento` |
| `GET /api/dashboard/pedidos-local` | `diasRetroativos` (padrão: 30) | Pedidos por local nos últimos N dias |
| `GET /api/dashboard/vendas-historico` | `periodo` (hoje/7d/30d) | Série temporal: lista de `{ data, total, quantidade }` |
| `GET /api/dashboard/pendencias` | `somenteAtivos` (padrão: true) | `total`, `produtosSemPreco`, `produtosSemEstoque`, `geradoEm` |
| `GET /api/admin/dashboard/voucher-consumo/mes-atual` | — | Consumo de voucher de funcionários: `usuarioId`, `nome`, `total`, `voucher`, `excedente` |

## Dashboards especializados

### Gamificação (`DashboardGamificacaoService`)

| Endpoint | Dados |
|----------|-------|
| `GET /api/admin/gamificacao/dashboard` | KPIs: recompensas emitidas, participantes, pontos emitidos/resgatados; rankings: top pontuadores, maiores saldos, maiores resgates |
| `GET /api/admin/gamificacao/clientes/com-pontos` | Lista de clientes com saldo de pontos acima de zero |

### Clientes e engajamento (Espresso Back)

| Endpoint | Parâmetros | Dados |
|----------|-----------|-------|
| `GET /api/clientes-dashboard/ativos` | `range` (dias) | `totalClientes`, `novosPeriodo`, `appAtivos`, `ativos7d`, `tokensOrfaos`, `adocaoPercentual` |
| `GET /api/clientes-dashboard/oportunidades` | `range` (dias) | `novosComApp`, `inativosApp`, `tokensOrfaos` |
| `GET /api/clientes-dashboard/app-ativos` | — | Lista de clientes com app ativo e saldo de pontos |
| `POST /api/clientes-dashboard/enviar-brinde` | `{ userId, mensagem }` | Envia brinde para um cliente específico |
| `GET /api/analytics/clientes/resumo` | `range` (dias) | `totalClientes`, `novosPeriodo`, `rangeDias` |

## Filtros e períodos

- **Períodos predefinidos**: `hoje`, `7d` (últimos 7 dias), `30d` (últimos 30 dias)
- **Período customizado**: `dateFrom` + `dateTo` em formato ISO (YYYY-MM-DD) — disponível em `vendas-periodo`
- **Ordenação de produtos**: `valor` (faturamento) ou `quantidade` (unidades vendidas)
- **Dias retroativos**: parâmetro livre em `pedidos-local` (padrão 30)

## Implementação

Todos os endpoints de métricas são implementados em `DashboardService.java`, com transações ReadOnly e fallback a valores zerados em caso de erro parcial. Os repositórios consultados incluem `ItemPedidoRepository`, `PagamentoRepository`, `MovimentoCaixaRepository`, `ContaReceberRepository`, `ContaPagarRepository`, `ProdutoRepository`, `MesaRepository` e `SessaoMesaRepository`.
