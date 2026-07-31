# Painel Waiter – mapa de permissões

Visão rápida do que cada role pode acessar no `/waiter` e quais rotinas são chamadas pelo front.

## Guardas de rota
- `/waiter` passa por `ProtectedRoute` exigindo uma das roles `WAITER | CAIXA | ADMIN | SYSTEM` (`src/App.tsx`).
- Dentro da página, a aba “Pagamentos” renderiza para `CAIXA`, `ADMIN` ou `SYSTEM` (checado via auth) (`src/pages/WaiterPage.tsx`).

## WAITER (somente WAITER)
- Abas visíveis: “Chamados” e “Mesas & Pedidos”; não vê “Pagamentos”.
- Listas/alertas:
  - GET `/api/chamados/pendentes`
  - GET `/api/kds/queue`
  - SSE `/api/events/kds` (tickets prontos/alterações)
  - SSE `/api/events/waiter` (eventos `payment.updated`, usados para badges/toasts)
- Ações de atendimento:
  - PATCH `/api/chamados/{id}/atender`
  - PATCH `/api/chamados/{id}/liberar-pagamento` (quando `tipo=conta`)
  - PATCH `/api/kds/tickets/{itemPedidoId}` (marcar item como entregue)
- Gestão de mesas (MesasGrid em modo waiter):
  - GET `/api/admin/mesas/sessoes?status=open`
  - GET `/api/admin/itens/sessoes/{sessaoMesaId}`
  - POST `/api/admin/mesas/sessoes/{id}/fechar` (quando devido=0)
  - Link para `/admin/mesas/{id}/pagamentos`
- Lançamentos no salão:
  - POST `/api/admin/mesas/sessoes/{id}/pedidos` (adicionar item)
  - POST `/api/admin/itens/{itemPedidoId}/cancelar` (cancelar item)
  - POST `/api/admin/mesas/{slug}/assistida` (iniciar sessão assistida)
  - POST `/api/admin/mesas/balcao/pedidos` (venda rápida balcão)
  - Auxiliares: GET `/api/admin/cardapio/v2`, `/api/clientes/options`, `/api/admin/usuarios/options`, `/api/admin/mesas/options`

## WAITER + CAIXA (tem as duas) — ADMIN e SYSTEM também podem tudo
- Tudo do perfil WAITER acima, e a aba “Pagamentos” fica visível.
- Consulta/monitoramento:
  - GET `/api/waiter/pagamentos` (filtro `resolvido`)
  - SSE `payment.updated` em `/api/events/waiter` (atualiza lista/contadores)
- Comprovantes:
  - GET `/api/waiter/pagamentos/{id}/comprovante` (blob para imprimir/enviar)
  - POST `/api/whatsapp/enviar-arquivo` (envio do PDF)
- Fiscal:
  - POST `/api/waiter/pagamentos/{id}/emitir-nfce`
- Encerramento:
  - POST `/api/waiter/pagamentos/{id}/resolver` (param `fecharMesa` opcional)
  - POST `/api/waiter/mesas/{sessaoMesaId}/fechar`
