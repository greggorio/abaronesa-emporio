# Clientes — Contexto

## Modelo de dados

### Usuario

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | Long | Identificador |
| `nome` | String | Nome completo |
| `email` | String | Único no sistema |
| `telefone` | String | Contato |
| `roles` | Set\<Role\> | CLIENTE (pode acumular outros papéis) |
| `ativo` | Boolean | Soft delete |
| `emailVerificado` | Boolean | Campos existem; fluxo não operacionalizado |
| `provider` | Enum | LOCAL ou GOOGLE |
| `providerId` | String | ID do provedor OAuth2 |
| `clienteOnline` | Boolean | Tracking de presença em tempo real |
| `criadoEm` | Timestamp | — |
| `ultimoLogin` | Timestamp | — |

### PerfilCliente

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `tipoPessoa` | Enum | PF ou PJ |
| `cpf` / `cnpj` | String | Documento fiscal |
| `inscricaoEstadual` | String | Para PJ |
| `dataNascimento` | Date | Para PF |
| Endereço completo | String | logradouro, numero, bairro, complemento, cidade, estado, cep |
| `codigoMunicipioIbge` | String | Para emissão fiscal |
| `origemCadastro` | Enum | LOJA_FISICA ou E_COMMERCE |
| `mensalista` | Boolean | Flag para assinatura (estrutura existe; feature não desenvolvida) |
| `grupoClienteId` | FK | Grupo comercial associado |

O método `isPerfilCompleto()` valida campos obrigatórios por tipo: PF requer CPF; PJ requer CNPJ.

### GrupoCliente e GrupoClienteDesconto

- `GrupoCliente`: `id` (Long), `descricao` (String, UNIQUE) — ex: "SÓCIO", "E-COMMERCE"
- `GrupoClienteDesconto`: FK para grupo, categoria, subcategoria (nullable), `descontoPercentual` (Decimal 5,2), `ativo` (Boolean)
- Unicidade sem subcategoria: `(grupoClienteId, categoriaId)`; com subcategoria: `(grupoClienteId, categoriaId, subcategoriaId)`

## Decisões de domínio

- **Grupo padrão E-COMMERCE**: clientes criados via e-commerce recebem automaticamente o grupo "E-COMMERCE"; hardcoded, não configurável
- **Resgate admin-only**: resgates de recompensa operam via admin, sem endpoint direto para o cliente
- **Favoritos derivados**: não há marcação explícita; calculados por frequência de consumo (somatório de `ItemPedido` por produto), itens cancelados excluídos
- **guestToken**: suporte a consumo anônimo via token de sessão; `grupoClienteId` resolvido a partir do token
- **OAuth2 preparado**: campos `provider` e `providerId` existem; login via Google não operacionalizado
- **ClienteRef desacoplado**: `espresso_back` mantém cópia de referência sincronizada via ERP (`POST /api/clientes-ref/sync`), sem sincronização reversa
- **Descontos por sincronização**: `PUT /api/grupos-clientes/{id}/descontos` opera por delete + insert; o chamador deve enviar o conjunto completo
- **Precedência de preço**: quando desconto de grupo e promoção coexistem, o backend expõe o menor preço com `origemDesconto = SOCIO` quando o desconto de grupo vence

## Fluxo de aplicação de desconto no cardápio

1. Backend resolve `grupoClienteId` a partir do `guestToken` da sessão autenticada
2. Carrega os descontos ativos do grupo
3. Para cada item do cardápio, calcula preço com desconto de grupo
4. Compara com promoção vigente do produto
5. Expõe o menor preço com a origem correspondente (`SOCIO` ou `PROMOCAO`)

## Área do Cliente

Superfície autenticada de **somente leitura** — não governa os dados que exibe. Não possui endpoint único; a interface consome múltiplas APIs de módulos distintos em paralelo. O risco estrutural é acumular lógica de escrita: qualquer ação que modifique estado deve ser redirecionada ao módulo responsável.

### Dados agregados

| Seção | Origem | Endpoint |
|-------|--------|----------|
| Contas abertas | `financeiro/` | `GET /api/contas-receber/me` |
| Favoritos | `vendas/pedidos` | `GET /api/pedidos/me/favoritos` |
| Mesa ativa | `vendas/mesas` | `GET /api/mesas/me/ativa` |
| Saldo/extrato de pontos | `fidelizacao/` | `GET /api/clientes/me/gamificacao` |
| Recompensas | `fidelizacao/` | `GET /api/rewards/my` |
| Notificações não lidas | transversal | `GET /api/notifications/my/unread-count` |
| Delivery ativo | `consumo-digital/` | `GET /api/delivery/orders/my/active` |
| Próximos eventos | `eventos/` | via eventoService |

### Páginas dedicadas implementadas

- `/areacliente/gamificacao` — saldo + últimos 50 movimentos
- `/areacliente/recompensas` — lista com status (disponível, resgatada, expirada)
- `/areacliente/notificacoes` — paginadas, mark as read individual e global
- `/areacliente/eventos` — próximos eventos do estabelecimento
- `/areacliente/financeiro` — contas abertas e quitadas
- Delivery tracking — via deeplink, status e ETA

### Notificações

Entregues via `UserNotification` (espresso_back) com `title`, `body`, `imageUrl`, `source` (REWARD, BIRTHDAY, MANUAL, etc.), `deeplink` e `readAt`. Ações: marcar como lida, marcar todas como lidas.

## Favoritos

- Calculados por agregação de `ItemPedido` pelo `usuarioId`: agrupa por produto, soma quantidade, registra `ultimoPedidoEm`, ordena por quantidade decrescente
- API `GET /api/pedidos/me/favoritos` funcional; exibição na área do cliente funcional; página dedicada ainda usa dados mock
- Lacunas: sem histórico temporal, sem marcação explícita, página dedicada não integrada à API real

## Estado de maturidade

| Sub-domínio | Estado |
|-------------|--------|
| `grupos-de-clientes` | Implementação completa |
| `area-do-cliente` | Implementação completa |
| `favoritos` | API funcional; interface parcial (dados mock na página dedicada) |
