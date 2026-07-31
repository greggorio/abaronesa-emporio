# Mapeamento Oficial de Modulos

Este documento registra a **Etapa 1: Mapear modulos** da pasta `modules`, conforme definido em [GUIA_OPERACIONAL.md](./GUIA_OPERACIONAL.md).

Este trabalho foi executado com duas fontes de verdade:
- **fonte material**: estrutura real do projeto `~/git/bakery`
- **fonte documental**: organizacao atual de `docs/modules`

O principio adotado aqui e simples:

`A estrutura atual de docs nao define sozinha quais sao os modulos reais do sistema.`

Primeiro se identifica a realidade material do produto. Depois se decide como essa realidade deve ser representada editorialmente em `modules`.

## Status da Etapa

| Item | Status |
|------|--------|
| Inventario de dominios reais do sistema | concluido |
| Leitura da organizacao documental atual | concluido |
| Comparacao entre realidade material e realidade documental | concluido |
| Proposta editorial para `modules` | concluido |
| Validacao editorial final | pendente |

## Taxonomia Utilizada

| Tipo | Significado |
|------|-------------|
| **Dominio real** | Recorte funcional com evidencia material no codigo e na operacao |
| **Modulo editorial** | Modulo que deve existir de forma autonoma em `modules` |
| **Submodulo editorial** | Recorte importante dentro de um modulo editorial maior |
| **Capacidade funcional** | Funcionalidade relevante, mas menor que um modulo autonomo |
| **Iniciativa documental** | Escopo, MVP, proposta ou plano de uma frente especifica |
| **Agrupador editorial** | Pasta usada para reunir itens heterogeneos que ainda nao formam um modulo coerente |

## Inventario Real de Dominios do Sistema

### 1. Cadastro e Catalogo de Produtos

**Dominio real**: existe de forma robusta no sistema.

Evidencias materiais:
- entidades: `Produto`, `ProdutoSKU`, `ProdutoMidia`, `ProdutoPromocao`, `ProdutoHarmonizacao`, `ProdutoDisponibilidade`, `Categoria`, `Subcategoria`, `Embalagem`, `FichaTecnica`, `FichaTecnicaItem`
- controllers: `ProdutoController`, `ProdutoSKUController`, `ProdutoPromocaoController`, `ProdutoHarmonizacaoController`, `ProdutoDisponibilidadeController`, `CategoriaController`, `SubcategoriaController`, `EmbalagemController`, `FichaTecnicaController`, `ImportacaoProdutoController`, `CardapioController`
- services: `ProdutoService`, `ProdutoSKUService`, `ProdutoPromocaoService`, `ProdutoHarmonizacaoService`, `ProdutoDisponibilidadeService`, `FichaTecnicaService`, `ImportacaoProdutoService`, `CardapioService`

Subdominios internos identificados:
- produtos
- SKUs
- categorias e subcategorias
- embalagens
- ficha tecnica
- disponibilidade
- promocao
- harmonizacao
- midia / galeria do produto
- cardapio

Situacao em `modules` hoje:
- **subdocumentado**
- aparece de forma fragmentada em `estoque/`, `backoffice/` e `signage/`, mas nao possui modulo editorial proprio

Leitura correta:
- este e um dos maiores dominios reais do sistema
- a ausencia dele como modulo explicito em `modules` revela lacuna de mapeamento documental

### 2. Signage de Produto

**Dominio real**: existe materialmente, mas como recorte especializado do universo de produto/backoffice.

Evidencias materiais:
- entidades: `ProductSignage`, `SignageTemplate`
- controllers: `SignageTemplateController`
- services: `SignageTemplateService`, `ProductSignageAiService`, `ProductSignageJobService`, `SignageRenderService`, `SignageSyncService`, `SignageSyncJobService`, `SignageVideoStorageService`, `SignageVideoCleanupService`, `AiImageHashService`, `AiImageStorageService`, `VideoProcessingService`
- frontend: `frontend/src/components/signage/*`

Situacao em `modules` hoje:
- massa documental forte em `backoffice/signage/`

Leitura correta:
- signage nao e documento solto
- tampouco e modulo isolado de negocio no mesmo nivel de `estoque` ou `financeiro`
- hoje a melhor leitura e **submodulo editorial forte**, ligado a produto/backoffice

### 3. Suprimentos e Operacao Interna

**Dominio real**: existe de forma clara e relevante.

Evidencias materiais:
- entidades: `PedidoCompra`, `PedidoCompraItem`, `RecebimentoMercadoria`, `RecebimentoItem`, `Fornecedor`
- controllers: `PedidoCompraController`, `RecebimentoMercadoriaController`, `FornecedorController`
- services: `PedidoCompraService`, `SugestaoCompraService`, `RecebimentoMercadoriaService`, `FornecedorService`
- frontend: `PedidosCompraPage.vue`

Subdominios internos identificados:
- pedidos de compra
- recebimento de mercadoria
- fornecedores

Situacao em `modules` hoje:
- `pedidos de compra` aparece dentro de `backoffice/`
- `recebimento de mercadoria` nao aparece como modulo ou submodulo claro
- `fornecedores` tambem nao aparece como recorte documental proprio

Leitura correta:
- a documentacao atual subrepresenta esse dominio
- ha forte indicio de que `pedidos de compra` e `recebimento de mercadoria` merecem tratamento editorial explicito e conectado

### 4. Estoque

**Dominio real**: existe fortemente no sistema.

Evidencias materiais:
- entidades: `Estoque`, `EstoqueProduto`, `MovimentoEstoque`, `EstoqueLote`, `MovimentoEstoqueLote`, `TarefaValidade`, `TarefaValidadeItem`, `TarefaValidadeDivergencia`
- controllers: `EstoqueController`, `MovimentoEstoqueController`, `ValidadeController`
- services: `MovimentoEstoqueService`, `ValidadeService`
- frontend: `ValidadeDashboardPage.vue`, componentes `validade/*`

Subdominios internos identificados:
- estoque agregado
- movimentacao de estoque
- validade
- sub-ledger de lotes
- tarefas de validade

Situacao em `modules` hoje:
- pasta `estoque/` existe
- documentacao atual e fortemente concentrada no subdominio `validade`

Leitura correta:
- `estoque` e um **modulo editorial obrigatorio**
- `validade` e um **submodulo editorial dominante** dentro dele

### 5. Producao

**Dominio real**: existe materialmente.

Evidencias materiais:
- controller: `ProducaoController`
- service: `ProducaoService`
- frontend: `ProducaoPage.vue`
- relacoes de negocio com ficha tecnica e consumo de estoque

Situacao em `modules` hoje:
- nao possui pasta nem representacao documental propria

Leitura correta:
- a etapa anterior estava errada em simplesmente ignorar `producao`
- ele existe como dominio real e precisa ao menos entrar no inventario

### 6. Vendas, Pedidos e Conta

**Dominio real**: existe de forma ampla e transversal.

Evidencias materiais:
- entidades: `Pedido`, `ItemPedido`, `Conta`, `Pagamento`, `PagamentoAlocacao`, `SessaoCobranca`
- controllers: `PedidosController`, `VendasController`, `ContaController`, `PagamentosController`, `MesaController`, `SessaoMesaController`
- services: `PedidoService`, `ContaService`, `ConsumoReceiptService`, `SessaoMesaService`

Subdominios internos identificados:
- pedidos
- conta e fechamento
- vendas
- alocacao de pagamentos
- sessao de mesa / cobranca

Situacao em `modules` hoje:
- o dominio foi posteriormente consolidado em `vendas/`
- o agrupamento antigo em `consumo-digital-e-fidelizacao` foi descontinuado

Leitura correta:
- esse dominio nao pode desaparecer do inventario so porque a pasta documental ainda nao o organiza bem

### 7. Financeiro

**Dominio real**: existe claramente.

Evidencias materiais:
- entidades: `ContaPagar`, `ContaPagarParcela`, `ContaReceber`, `ContaReceberParcela`, `MovimentoCaixa`, `TipoReceita`
- controllers: `ContaPagarController`, `ContaReceberController`, `MovimentoCaixaController`, `RelatorioMovimentoCaixaController`, `RelatorioVendasController`, `TipoReceitaController`
- services: `ContaPagarService`, `ContaReceberService`, `MovimentoCaixaService`, `RelatorioMovimentoCaixaService`, `RelatorioVendasService`, `TipoReceitaService`

Capacidades identificadas:
- contas a pagar
- contas a receber
- movimento de caixa
- relatorios
- tipos de receita
- cobranca de excedente de voucher

Situacao em `modules` hoje:
- a pasta `financeiro/` existe
- mas hoje documenta apenas `voucher excedente`

Leitura correta:
- `financeiro` e um **modulo editorial obrigatorio**
- sua cobertura documental atual e muito insuficiente diante do dominio real

### 8. Clientes, Perfis e Relacionamento

**Dominio real**: existe de forma clara.

Evidencias materiais:
- entidades: `Cliente`, `PerfilCliente`, `PerfilFuncionario`, `GrupoCliente`, `GrupoClienteDesconto`
- controllers: `ClienteController`, `GrupoClienteController`, `GrupoClienteDescontoController`, `ClientesAnalyticsController`
- services: `ClienteService`, `GrupoClienteService`, `GrupoClienteDescontoService`, `UserProfileService`

Capacidades identificadas:
- cadastro de clientes
- perfis
- segmentacao
- analytics de clientes
- beneficios funcionais como voucher

Situacao em `modules` hoje:
- praticamente ausente como modulo editorial explicito

Leitura correta:
- o dominio real de clientes e relacionamento esta submapeado em `modules`

### 9. Fidelizacao, Gamificacao e Recompensas

**Dominio real**: existe de forma clara e transversal.

Evidencias materiais:
- entidades: `MovimentoPontos`, `Recompensa`, `TipoRecompensa`, `GamificacaoEventoTipo`
- controllers: `ClienteGamificacaoController`, `DashboardGamificacaoController`, `RecompensaClienteController`, `RecompensaClienteAdminController`, `ResgateRecompensaController`
- services: `GamificacaoService`, `GamificacaoConsultaService`, `DashboardGamificacaoService`, `RecompensaService`, `RecompensaClienteService`, `ResgateRecompensaService`
- digital: `RewardController`, `GamificacaoAdmin.tsx`, `GamificacaoExtrato.tsx`, `RecompensasInbox.tsx`

Leitura correta:
- fidelizacao nao deve desaparecer dentro do nome "gamificacao"
- o dominio real inclui pontos, recompensas, resgates e relacao com cliente

Situacao em `modules` hoje:
- representado por `gamificacao/`
- mas o nome editorial talvez nao cubra toda a abrangencia do dominio

### 10. Eventos

**Dominio real**: existe de forma transversal.

Evidencias materiais:
- entidades: `Evento` no ecossistema digital
- controllers: `EventsController`, `EventoDashboardController`, `EventoController`
- services: `EventoEspressoService`, `EventNotificationService`
- digital: `EventosCliente.tsx`, `EventosAdmin.tsx`

Situacao em `modules` hoje:
- representado por `eventos/`

Leitura correta:
- aqui ha coerencia razoavel entre dominio real e pasta documental

### 11. Quiz

**Dominio real**: existe de forma transversal.

Evidencias materiais:
- entidades: `Question`, `QuizSession`, `Player`
- controllers: `QuestionController`, `QuestionImportController`, `QuizSessionController`, `QuizWebSocketController`
- services: `QuizGenerationService`, `QuizGameService`, `QuizSessionService`, `QuizNotificationService`
- digital: `QuizPlayer.tsx`, `QuizAdmin.tsx`, `QuizManagement.tsx`

Situacao em `modules` hoje:
- representado por `quiz/`

Leitura correta:
- ha aderencia razoavel entre realidade material e organizacao documental

### 12. Temas e White-Label

**Dominio real**: existe de forma robusta.

Evidencias materiais:
- entidades: `Theme`, `ThemeAssignment`, `ThemeAndroidAsset`
- controllers: `ThemeController`, `ThemeAssetController`, `ThemeAndroidAssetController`, `EspressoThemeController`
- services: `ThemeService`, `EspressoThemeClient`
- digital: `TemasPage.tsx`, componentes admin de temas

Situacao em `modules` hoje:
- representado por `temas/`

Leitura correta:
- ha boa aderencia entre realidade material e pasta documental

### 13. Consumo Digital, Mesa, Delivery, KDS e Waiter

**Dominio real**: existe fortemente no sistema.

Evidencias materiais:
- entidades: `SessaoMesa`, `SessaoConvidado`, `Mesa`, `DeliveryOrder`, `delivery/DeliveryOrder`, `delivery/DeliveryPayment`
- controllers: `MesaController`, `SessaoMesaController`, `DeliveryOrderController`, `DeliveryKdsController`, `delivery/DeliveryOrderController`, `delivery/DeliveryPaymentController`, `KdsController`, `WaiterPaymentsController`
- services: `MesaService`, `SessaoMesaService`, `DeliveryOrderService`, `DeliveryKdsService`, `UberDirectService`
- digital: `MesaPage.tsx`, `DeliveryMenuPage.tsx`, `DeliveryTrackingPage.tsx`, `KdsPage.tsx`, `WaiterPage.tsx`

Subdominios internos identificados:
- mesa digital
- qr ordering
- delivery
- KDS
- waiter
- pagamento digital da mesa

Situacao em `modules` hoje:
- hoje esta consolidado em `consumo-digital/`, com submodulos proprios para mesa, `qr-ordering`, delivery, KDS, waiter, pagamentos e conta digital

Leitura correta:
- este e um dominio real muito maior do que o antigo MVP documental de QR
- o agrupamento antigo foi superado, mas segue relevante como contexto historico da migracao

## Diagnostico da Estrutura Atual de `modules`

O erro principal da estrutura atual e misturar niveis diferentes:
- alguns diretorios representam **modulos reais**
- outros representam **areas editoriais agregadoras**
- outros escondem **subdominios relevantes** dentro de um nome generico

## Enquadramento Editorial Recomendado

### Modulos editoriais que deveriam existir com autonomia

- `produtos`
- `estoque`
- `producao`
- `suprimentos`
- `financeiro`
- `clientes`
- `fidelizacao`
- `eventos`
- `quiz`
- `temas`
- `consumo-digital`

### Submodulos editoriais fortes

- `produtos/signage`
- `produtos/ficha-tecnica`
- `produtos/promocoes`
- `produtos/skus`
- `suprimentos/pedidos-de-compra`
- `suprimentos/recebimento-de-mercadoria`
- `estoque/validade`
- `consumo-digital/mesa-digital`
- `consumo-digital/delivery`
- `consumo-digital/kds`
- `consumo-digital/waiter`

### Pastas historicas que devem ser lidas apenas como areas editoriais transitorias

- `backoffice/`
- `dashboard/`
- `consumo-digital-e-fidelizacao/`

### Pasta atual que deve ser tratada apenas como agrupador

- `funcionalidades/`

## Implicacoes para as Proximas Etapas

Seguindo o guia, a **Etapa 2: Consolidar a especificacao** nao deve mais partir das pastas atuais como se elas fossem o mapa final do produto.

Ela deve partir deste inventario de dominios reais e decidir, conscientemente:
- quais dominios entrarao agora em `modules`
- quais subdominios precisam virar submodulo documental
- quais itens devem permanecer apenas como capacidade interna

## Proximo Passo Segundo o Guia

Se este documento for validado, o proximo passo oficial e:

`Etapa 2: Consolidar a especificacao`

Mas agora com a seguinte prioridade de saneamento:

1. definir a taxonomia editorial alvo de `modules`
2. escolher os primeiros modulos editoriais a consolidar
3. reclassificar o conteudo existente nas pastas agregadoras

## Observacao de Governanca

Este documento deve ser revisado sempre que:
- um dominio real novo for identificado no sistema
- um subdominio passar a exigir autonomia editorial
- a estrutura de `modules` for reorganizada para refletir melhor o produto
