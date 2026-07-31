# Favoritos

Favoritos é o sub-domínio que transforma o histórico de consumo do cliente em uma lista ordenada por frequência — os produtos que ele mais pede, servindo de atalho para recompra.

Pertenço a [`clientes/`](../README.md).

## Domínio

- [`favoritos.md`](./favoritos.md) — como é calculado, o que está implementado e as lacunas atuais

## Leitura contextual

Favoritos não é uma lista explicitamente marcada pelo cliente — é derivada do histórico de `ItemPedido`. A API funciona; o gap está na interface: a tela dedicada ainda exibe dados mock em vez de consumir a API real.

Não existe entidade própria de favorito. O dado é calculado, não persistido — o que é coerente com a natureza da feature, mas limita análises temporais como frequência por período ou sazonalidade.

## Exploração

- Especificação e lacunas → [`favoritos.md`](./favoritos.md)
- Histórico de pedidos (origem do cálculo) → [`../../vendas/README.md`](../../vendas/README.md)
