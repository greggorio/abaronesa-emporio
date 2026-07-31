# Dashboard — Especificação do Domínio

## Definição

Camada transversal de leitura operacional. Consolida métricas, painéis e relatórios de múltiplos módulos para acompanhamento diário do gestor. Não origina nem modifica dados — opera exclusivamente com transações ReadOnly.

## Escopo

**Inclui:**
- Métricas de vendas, caixa, produção, financeiro e pendências em tempo real
- Painéis draggáveis com auto-refresh (Vue/Quasar)
- Páginas de administração e engajamento (React/Espresso)
- Relatórios em PDF: vendas, movimento de caixa, vendas por produto
- Dashboard de gamificação, promoções, eventos e voucher de funcionários
- Dashboard de clientes e engajamento com app (Espresso)

**Não inclui:**
- Escrita ou mutação de dados
- Regras de negócio de Vendas, Financeiro, Estoque ou Produção
- Exportação em CSV/Excel (gap identificado — apenas PDF disponível)
- Relatórios agendados

## Dois frontends

### Vue/Quasar — painel operacional

`DashboardPage.vue` organiza widgets draggáveis com estado persistido em `localStorage`. Auto-refresh a cada 10 segundos nos painéis críticos. Layout responsivo (col-lg/md/sm/xs).

| Painel | Dados |
|--------|-------|
| `PainelMesaTop` | Mesas totais, ocupadas, % ocupação |
| `PainelTopMesas` | Top 10 mesas por consumo |
| `PainelVendasPeriodo` | Faturamento, ticket médio, breakdown por método de pagamento |
| `PainelMovimentoCaixa` | Entradas, saídas, saldo e detalhe por tipo (PIX, cartão, dinheiro, voucher) |
| `PainelProdutos` | Top produtos por valor ou quantidade, por período |
| `PainelFinanceiro` | Recebimentos, pagamentos e pendências |
| `PainelPedidosLocal` | Pedidos por local nos últimos N dias |
| `PainelPendencias` | Produtos sem preço ou sem estoque |
| `PainelVoucher` | Consumo de voucher de funcionários no mês atual |
| `PainelProdutosPromocao` | Produtos em promoção, desconto médio e impacto no faturamento |
| `PainelEventos` | Couvert e faturamento de eventos |
| `PainelVendasHistorico` | Série histórica de vendas por dia (tabela/gráfico) |
| `PainelValidade` | Controle de validade de produtos — filtrável |

**Métricas rápidas no topo** (sempre visíveis): pedidos no bar, pedidos na cozinha, pedidos prontos, mesas (ocupadas/total), faturamento do dia, ticket médio, % ocupação.

### React/Espresso — administração e engajamento

| Página | Rota | Dados |
|--------|------|-------|
| `AdminDashboard` | `/admin` | KPIs: eventos ativos, clientes, cancelamentos |
| `ClientesDashboard` | `/admin/clientes` | Ativos, oportunidades, engajamento com app, envio de brinde |
| `MesasDashboard` | `/admin/mesas` | Grid visual de mesas com status integrado ao KDS |
| `SalesReportsPage` | `/admin/relatorios/vendas` | KPIs + tabela paginada de vendas com comprovante PDF |
| `OrdersReportsPage` | `/admin/relatorios/pedidos` | Fila do KDS + KPIs de produção em tempo real |

## Integrações

| Módulo | Dados consumidos |
|--------|-----------------|
| `vendas/` | Pedidos, itens, pagamentos (totais, por período, por método) |
| `financeiro/` | Contas a receber/pagar, movimento de caixa |
| `estoque/` | Produtos sem estoque ou sem preço |
| `producao/` | Pedidos em preparação (bar e cozinha) |
| `eventos/` | Couvert e faturamento de eventos |
| `fidelizacao/` | Saldos de pontos, recompensas emitidas/resgatadas |
| `clientes/` | Total de clientes, novos no período, adoção do app |

## Decisões de domínio

- **ReadOnly estrito**: todas as queries do `DashboardService` usam transações somente-leitura; nenhum endpoint do módulo realiza escrita
- **Agregação em tempo real**: dados calculados a cada requisição, sem cache distribuído ou views materializadas — simplifica a operação mas não escala infinitamente
- **Auto-refresh por polling**: Vue/Quasar atualiza a cada 10s; sem SSE ou WebSocket no dashboard (gap que pode ser corrigido sem mudar a arquitetura dos módulos-fonte)
- **Dois backends sincronizados**: ERP (backend/) e Espresso (espresso_back/) expõem dados independentes; sincronização via header `X-ERP-KEY` — o dashboard Espresso consome ambos
- **Layout por localStorage**: posição dos painéis Vue persiste no navegador, não no servidor — cada usuário tem seu próprio arranjo sem custo de infraestrutura
