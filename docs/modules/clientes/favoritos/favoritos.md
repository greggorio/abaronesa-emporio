# Favoritos — Especificação

## Definição

Frequência e afinidade de consumo derivadas do histórico de pedidos do cliente. Não há marcação explícita — favoritos são calculados a partir do somatório de `ItemPedido` agrupados por produto, ordenados por quantidade decrescente. Itens com `status = CANCELED` são excluídos do cálculo.

## Como é calculado

A query agrega `ItemPedido` pelo `usuarioId` da sessão:
- Agrupa por produto (id, nome, imagem)
- Soma a quantidade total consumida de cada produto
- Registra o `ultimoPedidoEm` (data do pedido mais recente com aquele produto)
- Ordena por quantidade decrescente
- Retorna os N primeiros (padrão: 5, configurável via `?limit=N`)

## Estado atual

| Camada | Estado |
|--------|--------|
| API `GET /api/pedidos/me/favoritos` | Funcional |
| Exibição na área do cliente (scroll horizontal) | Funcional — consome API real |
| Página dedicada de favoritos | Parcial — exibe dados mock; não integrada à API |

## Lacunas

- Sem histórico temporal: não há frequência por período (semanal, mensal)
- Sem marcação explícita: cliente não pode "favoritar" um produto manualmente
- Página dedicada ainda mockada — integração com a API real pendente
