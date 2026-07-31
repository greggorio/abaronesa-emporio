# Disponibilidade

Disponibilidade é o sub-domínio que controla quando e onde cada produto pode ser vendido. Existe para definir regras por dia e horário em dois níveis (produto e subcategoria), com comportamento específico por canal (mesa digital, delivery, presencial).

Pertenço a [`produtos/`](../README.md).

## Domínio

- [`disponibilidade.md`](./disponibilidade.md) — modelo de dados (ProdutoDisponibilidade, SubcategoriaDisponibilidade), comportamento por canal, endpoints e decisões de domínio

## Leitura contextual

Disponibilidade é definida por regras positivas — sem regras, o produto está sempre disponível. Mesa digital bloqueia compra se indisponível; delivery adiciona filtro de estoque; presencial é irrestrito.

## Exploração

- Especificação completa → [`disponibilidade.md`](./disponibilidade.md)
- Cardápio digital → [`../../consumo-digital/README.md`](../../consumo-digital/README.md)
- Controle de estoque para delivery → [`../../estoque/README.md`](../../estoque/README.md)
