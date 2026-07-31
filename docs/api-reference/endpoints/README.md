# Endpoints

Documentacao de endpoints REST expostos pelo backend e já descritos de forma mais detalhada nesta secao.

## Cobertura Atual

| Documento | Papel | Status |
|-----------|-------|--------|
| [auth.md](./auth.md) | Endpoints de autenticacao e sessao do usuario | `ativo` |
| [admin-consumo-comprovante.md](./admin-consumo-comprovante.md) | Endpoint administrativo de comprovante de consumo | `ativo` |
| [cardapio-publico.md](./cardapio-publico.md) | Catalogo publico consumido por site, mesa e delivery | `ativo` |
| [conta-e-mesa.md](./conta-e-mesa.md) | Sessao de mesa, convidados e consulta de conta | `ativo` |
| [delivery-orders.md](./delivery-orders.md) | Criacao, atualizacao e consulta de pedidos de delivery | `ativo` |
| [pagamentos-publicos.md](./pagamentos-publicos.md) | Intent, webhook e polling de pagamento | `ativo` |
| [pedidos.md](./pedidos.md) | Pedido em mesa, favoritos e atualizacao de itens | `ativo` |

## Estrategia da Pasta

Esta pasta sera preenchida gradualmente, priorizando:

- endpoints com consumidores externos claros
- endpoints administrativos com contrato estavel
- grupos de endpoints que sirvam como referencia para integracoes ou frontends

Nao e objetivo documentar toda a superficie REST de uma vez sem curadoria.
