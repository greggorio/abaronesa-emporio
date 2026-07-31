# Área do Cliente — Especificação

## Definição

Superfície autenticada que consolida, em uma única interface, a relação do cliente com o ecossistema. É somente leitura e não possui endpoint único — a interface consome múltiplas APIs de módulos distintos em paralelo.

## Dados agregados

| Seção | Origem | Endpoint |
|-------|--------|----------|
| Contas abertas | `financeiro/` | `GET /api/contas-receber/me` |
| Favoritos (scroll horizontal) | `vendas/pedidos` | `GET /api/pedidos/me/favoritos` |
| Mesa ativa | `vendas/mesas` | `GET /api/mesas/me/ativa` |
| Saldo e extrato de pontos | `fidelizacao/` | `GET /api/clientes/me/gamificacao` |
| Recompensas disponíveis | `fidelizacao/` | `GET /api/rewards/my` |
| Notificações não lidas | transversal | `GET /api/notifications/my/unread-count` |
| Delivery ativo | `consumo-digital/` | `GET /api/delivery/orders/my/active` |
| Próximos eventos | `eventos/` | via eventoService |

## Páginas dedicadas

| Página | Rota | Estado |
|--------|------|--------|
| Extrato de pontos | `/areacliente/gamificacao` | Implementado — saldo + últimos 50 movimentos |
| Recompensas | `/areacliente/recompensas` | Implementado — lista com status (disponível, resgatada, expirada) |
| Notificações | `/areacliente/notificacoes` | Implementado — paginadas, mark as read individual e global |
| Eventos | `/areacliente/eventos` | Implementado — próximos eventos do estabelecimento |
| Delivery tracking | via deeplink | Implementado — status e ETA do delivery ativo |
| Financeiro | `/areacliente/financeiro` | Implementado — contas abertas e quitadas |

## Notificações

Notificações são entregues ao cliente via `UserNotification` (espresso_back). Cada notificação possui `title`, `body`, `imageUrl`, `source` (REWARD, BIRTHDAY, MANUAL, etc.), `deeplink` para navegação e `readAt` para controle de leitura.

Ações disponíveis: marcar uma notificação como lida, marcar todas como lidas.
