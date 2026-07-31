# Vendas — Especificação

## Visão geral

O módulo de Vendas implementa um sistema completo de POS (Point of Sale) com auto-atendimento via PWA (QR code na mesa), atendimento assistido pelo staff (garçom/caixa) e balcão expresso. O ciclo é:

```
[Mesa] → [Sessão] → [Convidado] → [Pedido] → [KDS]
                                              ↓
                        [Conta] ← [Itens entregues]
                           ↓
                     [Pagamento] → [NFC-e / Comprovante]
                           ↓
                   [MovimentoCaixa] → [Baixa Estoque]
```

O frontend de autoatendimento é uma PWA em React (espresso_front/) que consome a API REST do backend. O frontend administrativo usa Vue/Quasar para relatórios, dashboards e gestão.

## Modelo de dados

### Mesa

Tabela `mesa` — ponto de consumo físico.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `slug` | `String` | `unique`, identificador amigável (ex: "mesa-01") |
| `rotulo` | `String` | Nome de exibição (ex: "Mesa 01") |
| `referencia` | `String` | Código auxiliar (ex: "A1") |
| `ativo` | `Boolean` | |
| `criadoEm` | `LocalDateTime` | |
| `atualizadoEm` | `LocalDateTime` | |

### SessaoMesa

Tabela `sessao_mesa` — uma visita/ocupação de mesa.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `mesa` | `@ManyToOne` → `Mesa` | |
| `status` | `StatusSessao` | `OPEN` / `CLOSED` |
| `abertaEm` | `LocalDateTime` | |
| `fechadaEm` | `LocalDateTime` | |
| `observacoes` | `String` | |
| `selfCheckoutLiberado` | `Boolean` | |
| `selfCheckoutResolvidoEm` | `LocalDateTime` | |

### SessaoConvidado

Tabela `sessao_convidado` — um cliente dentro de uma sessão.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `sessaoMesa` | `@ManyToOne` → `SessaoMesa` | |
| `usuario` | `@ManyToOne` → `Usuario` | |
| `guestToken` | `String` | `unique` |
| `nomeExibicao` | `String` | |
| `deviceFingerprint` | `String` | |
| `entrouEm` | `LocalDateTime` | |
| `saiuEm` | `LocalDateTime` | |
| `host` | `Boolean` | Anfitrião da mesa |

### SessaoCobranca

Tabela `sessao_cobranca` — cobranças incidentes (couvert artístico).

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `sessaoMesaId` | `Long` | |
| `sessaoConvidadoId` | `Long` (nullable) | |
| `tipo` | `TipoCobranca` | `COUVERT_ARTISTICO` |
| `valor` | `BigDecimal` | |
| `eventoId` | `Long` (nullable) | |
| `isento` | `Boolean` | |
| `motivoIsencao` | `String` | |
| `status` | `StatusCobranca` | `ATIVA` / `CANCELADA` |

### Pedido

Tabela `pedido` — agrupamento de itens solicitados.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `sessaoMesa` | `@ManyToOne` → `SessaoMesa` | |
| `sessaoConvidado` | `@ManyToOne` → `SessaoConvidado` | |
| `status` | `StatusPedido` | |
| `origem` | `String` | `pwa` / `staff` |
| `criadoEm` | `LocalDateTime` | |
| `aceitoEm` | `LocalDateTime` | |
| `entregueEm` | `LocalDateTime` | |
| `canceladoEm` | `LocalDateTime` | |
| `motivoCancelamento` | `String` | |
| `itens` | `List<ItemPedido>` | `@OneToMany(cascade = ALL)` |

### ItemPedido

Tabela `item_pedido` — linha do pedido.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `pedido` | `@ManyToOne` → `Pedido` | |
| `produto` | `@ManyToOne` → `Produto` | |
| `sku` | `@ManyToOne` → `ProdutoSKU` | nullable |
| `quantidade` | `BigDecimal` | |
| `precoUnitario` | `BigDecimal` | |
| `observacoes` | `String` | |
| `status` | `StatusItem` | |
| `motivoCancelamentoCodigo` | `MotivoCancelamentoItem` | |
| `estacao` | `String` | `kitchen` / `bar` |

### Pagamento

Tabela `pagamento` — transação de pagamento.

| Campo | Tipo | Restrições |
|-------|------|------------|
| `id` | `Long` (PK, identity) | |
| `sessaoMesa` | `@ManyToOne` → `SessaoMesa` | |
| `sessaoConvidado` | `@ManyToOne` → `SessaoConvidado` | beneficiário (nullable = mesa inteira) |
| `pagante` | `@ManyToOne` → `SessaoConvidado` | quem pagou |
| `metodo` | `String` | `pix` / `card` / `cash` / `voucher` |
| `cartaoTipo` | `String` | `credito` / `debito` |
| `status` | `StatusPagamento` | `PENDING` / `PAID` / `FAILED` / `CANCELED` |
| `valor` | `BigDecimal` | |
| `valorBase` | `BigDecimal` | |
| `valorTaxaServico` | `BigDecimal` | |
| `valorCouvert` | `BigDecimal` | |
| `percentualTaxaServico` | `BigDecimal` | |
| `incluiTaxaServico` | `Boolean` | |
| `qrPayload` | `String` | |
| `providerRef` | `String` | |
| `selfCheckoutOrigem` | `Boolean` | |
| `selfCheckoutResolvido` | `Boolean` | |
| `pagoEm` | `LocalDateTime` | |

### PagamentoAlocacao

Tabela `pagamento_alocacao` — distribuição de um pagamento entre convidados.

| Campo | Tipo |
|-------|------|
| `id` | `Long` (PK) |
| `pagamento` | `@ManyToOne` → `Pagamento` |
| `sessaoConvidado` | `@ManyToOne` → `SessaoConvidado` |
| `valor` | `BigDecimal` |

### MovimentoCaixa

Tabela `movimento_caixa` — registro contábil de entrada/saída.

| Campo | Tipo |
|-------|------|
| `id` | `Long` (PK) |
| `dataHora` | `LocalDateTime` |
| `tipo` | `TipoMovimentoCaixa` |
| `valor` | `BigDecimal` |
| `meioPagamento` | `String` |
| `afetaCaixa` | `Boolean` |
| `operacao` | `String` (`ENTRADA` / `SAIDA`) |
| `observacao` | `String` |
| `referenciaId` | `Long` |
| `referenciaTipo` | `String` |
| `responsavelId` | `Long` |
| `responsavelNome` | `String` |

## Enums

| Enum | Valores |
|------|---------|
| `StatusVenda` | `CONFIRMADA`, `CANCELADA` |
| `StatusPedido` | `PENDING`, `ACCEPTED`, `PREPARING`, `READY`, `DELIVERED`, `CANCELED` |
| `StatusItem` | `QUEUED`, `ACCEPTED`, `PREPARING`, `READY`, `DELIVERED`, `CANCELED` |
| `StatusPagamento` | `PENDING`, `PAID`, `FAILED`, `CANCELED` |
| `StatusSessao` | `OPEN`, `CLOSED` |
| `StatusCobranca` | `ATIVA`, `CANCELADA` |
| `TipoCobranca` | `COUVERT_ARTISTICO` |
| `TipoFormaPagamento` | `DINHEIRO`, `VOUCHER`, `PIX`, `CARTAO_CREDITO`, `CARTAO_DEBITO`, `TRANSFERENCIA`, `OUTROS` |
| `OrigemVenda` | `LOJA_FISICA`, `LOJA_ONLINE` |
| `MotivoCancelamentoItem` | `FALTA_INSUMO`, `EQUIPE_INDISPONIVEL`, `ERRO_PEDIDO`, `CLIENTE_DESISTIU`, `OUTRO` |
| `TipoMovimentoCaixa` | `PAGAMENTO_MESA`, `GORJETA`, `CAIXA_INICIAL`, `REFORCO`, `SANGRIA`, `CONTAS_PAGAR`, `CONTAS_RECEBER`, `ESTORNO`, `OUTROS` |

## Endpoints da API

### Mesas (`/api/mesas`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/mesas/list` | Lista paginada |
| `GET` | `/api/mesas/form-config` | Config dinâmica |
| `GET` | `/api/mesas/{id}` | Busca por ID |
| `POST` | `/api/mesas` | Criar |
| `PUT` | `/api/mesas/{id}` | Atualizar |
| `DELETE` | `/api/mesas/{id}` | Excluir |
| `PATCH` | `/api/mesas/{mesaSlug}/referencia` | Atualizar referência |
| `GET` | `/api/mesas/options` | Dropdown |
| `GET` | `/api/mesas/me/ativa` | Sessão ativa do usuário |
| `GET` | `/api/mesas/{mesaSlug}/sessao` | Verificar sessão ativa |
| `POST` | `/api/mesas/{mesaSlug}/convidados` | Criar convidado |
| `POST` | `/api/mesas/sessoes/{sessaoMesaId}/fechar` | Fechar sessão |

### Pedidos (`/api/pedidos`, `/api/admin/mesas/...`)

| Método | Path | Descrição |
|--------|------|-----------|
| `POST` | `/api/pedidos` | Criar pedido (PWA self-service) |
| `GET` | `/api/pedidos/{pedidoId}` | Buscar pedido |
| `PATCH` | `/api/pedidos/itens/{itemPedidoId}/status` | Atualizar status do item (KDS) |
| `GET` | `/api/pedidos/me/favoritos` | Produtos favoritos do usuário |
| `GET` | `/api/admin/mesas/sessoes/{sessaoMesaId}/convidados` | Listar convidados (staff) |
| `POST` | `/api/admin/mesas/sessoes/{sessaoMesaId}/pedidos` | Criar pedido (staff) |
| `POST` | `/api/admin/mesas/balcao/pedidos` | Pedido balcão expresso |
| `GET` | `/api/admin/itens/sessoes/{sessaoMesaId}` | Listar itens por sessão |
| `POST` | `/api/admin/itens/{itemPedidoId}/cancelar` | Cancelar item |
| `GET` | `/api/admin/cancelamentos/hoje` | KPIs de cancelamentos hoje |
| `GET` | `/api/admin/cancelamentos` | Listar cancelamentos |

### Conta (`/api/conta`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/conta?sessaoMesaId=` | Conta da mesa (consolidada) |
| `GET` | `/api/conta?sessaoConvidadoId=` | Conta do convidado (individual) |

### Pagamentos (`/api/pagamentos`)

| Método | Path | Descrição |
|--------|------|-----------|
| `POST` | `/api/pagamentos/intent` | Criar intenção de pagamento (PIX/cartão) |
| `POST` | `/api/pagamentos/webhook` | Webhook de confirmação do gateway |
| `POST` | `/api/waiter/pagamentos` | Listar pagamentos self-checkout |
| `POST` | `/api/waiter/mesas/{sessaoMesaId}/fechar` | Fechar sessão (staff) |
| `POST` | `/api/waiter/pagamentos/{pagamentoId}/resolver` | Resolver/arquivar pagamento |
| `POST` | `/api/waiter/pagamentos/{pagamentoId}/emitir-nfce` | Emitir NFC-e |
| `GET` | `/api/waiter/pagamentos/{pagamentoId}/comprovante` | PDF comprovante |

### Vendas (`/api/vendas`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/vendas/list` | Lista paginada de vendas realizadas |
| `GET` | `/api/vendas/form-config` | Config dinâmica |
| `GET` | `/api/vendas/{id}` | Detalhe da venda |
| `GET` | `/api/vendas/report-table` | Tabela de vendas para relatório |

### Movimento Caixa (`/api/movimento-caixa`)

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/movimento-caixa/list` | Lista paginada |
| `GET` | `/api/movimento-caixa/form-config` | Config dinâmica |
| `POST` | `/api/movimento-caixa` | Criar manual |
| `POST` | `/api/movimento-caixa/manual` | Registrar movimento manual |

### Relatórios

| Método | Path | Descrição |
|--------|------|-----------|
| `GET` | `/api/relatorios/vendas/pdf` | PDF relatório de vendas |

## Frontend

### PWA Autoatendimento (React — `espresso_front/`)

| Componente | Arquivo | Linhas | Função |
|-----------|---------|--------|--------|
| MesaPage | `pages/MesaPage.tsx` | 1358 | **Tela principal do cliente**: entrada via QR code, navegação por categorias, adição ao carrinho, fechamento de pedido, visualização da conta, pagamento self-service (PIX/cartão), SSE para eventos em tempo real. Suporte pt-BR/en-US/es-ES |
| WaiterPage | `pages/WaiterPage.tsx` | 1169 | **Painel do garçom**: abas Chamados, Pagamentos, Mesas. Ações: atender chamado, liberar pagamento, entregar itens prontos, emitir NFC-e, gerar comprovante PDF, enviar WhatsApp |
| MenuPage | `pages/MenuPage.tsx` | 32 | Cardápio delivery |
| MesaPaymentSuccessPage | `pages/MesaPaymentSuccessPage.tsx` | 34 | Confirmação pós-pagamento |
| MesasGrid | `components/admin/MesasGrid.tsx` | 879 | Grid de mesas (staff): ocupação, pessoas, totais devidos. Ações: novo pedido, pagamento, mover mesa, cancelar itens |
| PagamentoDialog | `components/admin/PagamentoDialog.tsx` | 485 | Dialog de pagamento (staff): split por convidado ou mesa, múltiplos métodos |
| FastSaleDrawer | `components/admin/FastSaleDrawer.tsx` | 644 | Drawer de balcão expresso: catálogo buscável, carrinho, finalização |
| ProductCard | `components/mesa/ProductCard.tsx` | | Cardápio — card de produto |
| CartItem | `components/mesa/CartItem.tsx` | | Item no carrinho |
| MesaPedidosList | `components/mesa/MesaPedidosList.tsx` | | Lista de pedidos da mesa |
| ProductDetailsDialog | `components/mesa/ProductDetailsDialog.tsx` | | Detalhes do produto (SKU, variações) |
| UserMenuSheet | `components/mesa/UserMenuSheet.tsx` | | Menu do usuário |
| ObservationEditor | `components/mesa/ObservationEditor.tsx` | | Editor de observações do item |

### Backoffice (Vue/Quasar)

| Componente | Arquivo | Função |
|-----------|---------|--------|
| DashboardPage | `pages/DashboardPage.vue` | Dashboard geral |
| RelatorioVendasDialog | `components/dialogs/RelatorioVendasDialog.vue` | Relatório de vendas |
| RelatorioVendasProdutosDialog | `components/dialogs/RelatorioVendasProdutosDialog.vue` | Relatório por produto |
| PainelVendasPeriodo | `components/paineis/PainelVendasPeriodo.vue` | Vendas por período |
| PainelVendasHistorico | `components/paineis/PainelVendasHistorico.vue` | Histórico de vendas |
| PainelFinanceiro | `components/paineis/PainelFinanceiro.vue` | Painel financeiro |
| PainelMovimentoCaixa | `components/paineis/PainelMovimentoCaixa.vue` | Movimento de caixa |
| PainelPedidosLocal | `components/paineis/PainelPedidosLocal.vue` | Pedidos por local (bar/cozinha) |

## Serviços principais

### ContaService (366 linhas)

**Responsabilidade**: calcular contas (por mesa ou por convidado).

- `contaMesa(sessaoMesaId)` → `ContaMesaResponse`: soma itens de todos pedidos da sessão, aplica taxa de serviço (percentual configurável), calcula couvert, subtrai pagamentos já realizados (com alocação por convidado). Retorno: total, taxaServico, descontos, pago, devido, breakdown por pessoa
- `contaConvidado(sessaoConvidadoId)` → `ContaConvidadoResponse`: similar, mas apenas itens do convidado + rateio de cobranças da mesa

### SessaoMesaService (264 linhas)

**Responsabilidade**: ciclo de vida da sessão.

- `obterOuAbrirSessaoMesa(mesaSlug, usuario)` — abre sessão se não existir ativa
- `criarConvidado(sessaoMesa, request)` — gera guestToken único, cria convidado
- `fecharSessao(sessaoMesaId)` — marca como CLOSED, seta fechadaEm
- `gerarGuestTokenUnico()` — UUID aleatório
- `iniciarSessaoAssistida(mesaSlug, usuario)` — staff abre mesa
- `adicionarConvidadoExtra(sessaoMesaId, request)` — staff adiciona convidado
- `moverSessaoMesa(sessaoMesaId, novaMesaSlug)` — transfere sessão entre mesas

### PedidoService (242 linhas)

**Responsabilidade**: processamento de pedidos e baixa de estoque.

- `atualizarStatusItem(itemPedidoId, novoStatus, motivoCodigo, motivoDetalhe)` — núcleo do KDS:
  - `ACCEPTED`: dá baixa no estoque (processarBaixaInsumos via ficha técnica + processarBaixaSku)
  - `CANCELED`: reverte baixa (estorna estoque)
  - `DELIVERED`: apenas marca como entregue
- `processarBaixaInsumos(ItemPedido item)` — percorre FichaTecnica do produto, decrementa `estoque_produto.quantidade_base` de cada insumo
- `processarBaixaSku(ItemPedido item)` — decrementa `sku.estoque.quantidade`

### MovimentoCaixaService (149 linhas)

**Responsabilidade**: registro de movimentações financeiras.

- `registrarPagamentoMesa(pagamento)` — registra entrada referente a pagamento
- `registrarGorjeta(valor, pagamento)` — registra gorjeta como movimento separado
- `registrarEstorno(pagamento)` — estorna movimento
- `registrarCaixaInicial(valor, responsavel)` — abertura de caixa
- `registrarSangria / Reforco` — movimentos manuais de caixa

### DashboardService (580 linhas)

**Responsabilidade**: agregar métricas para dashboards (ver `dashboard/`).

### RelatorioVendasService (246 linhas)

**Responsabilidade**: gerar PDF de relatório de vendas via Thymeleaf + Flying Saucer.

## Regras de negócio

1. **Sessão**: cada mesa pode ter apenas uma sessão `OPEN` por vez. Ao fechar (por pagamento ou staff), nova sessão pode ser iniciada
2. **Guest token**: único por convidado, usado como identidade no PWA. Vinculado a `deviceFingerprint` para segurança
3. **Baixa de estoque**: ocorre no momento em que o item é aceito (`ACCEPTED`), não no momento do pedido. Itens cancelados após aceitos estornam o estoque automaticamente
4. **Cancelamento de item**: só permitido se não houver pagamento vinculado ao item. Exige motivo codificado (`MotivoCancelamentoItem`)
5. **Taxa de serviço**: percentual configurável aplicado sobre o subtotal. Pode ser incluída ou não pelo pagante. Registrada separadamente no `Pagamento.valorTaxaServico`
6. **Couvert artístico**: cobrança automática por convidado (isenta para menores ou eventos específicos). Controlada por `SessaoCobranca`
7. **Pagamento self-checkout**: cliente inicia via PIX ou cartão. Pagamentos PIX têm QR code exibido na tela. Pagamentos cartão redirecionam ao gateway. Garçom resolve (confirma) pagamentos self-checkout antes de liberar a mesa
8. **Split de pagamento**: um pagamento pode ser alocado entre múltiplos convidados (`PagamentoAlocacao`), permitindo rateio exato
9. **NFC-e**: emitida pelo staff no momento da resolução do pagamento. Comprovante não-fiscal em PDF disponível para download/WhatsApp
10. **Eventos SSE**: o backend emite eventos em tempo real (order.created, kds.status_changed, payment.updated, table.closed, guest.joined, waiter.call) consumidos pelo frontend React
