# Promoções

Promoções é o sub-domínio que governa as regras promocionais por produto. Existe para definir descontos percentuais ou valor fixo com dia da semana e janela de horário.

Pertenço a [`produtos/`](../README.md).

## Domínio

- [`promocoes.md`](./promocoes.md) — modelo de dados, regras de precedência (promoção vs desconto de grupo), prevenção de sobreposição, dashboard e decisões de domínio

## Leitura contextual

Promoções competem com desconto de grupo (`clientes/grupos-de-clientes/`). O backend aplica o menor preço. A prevenção de sobreposição impede regras conflitantes no mesmo produto, dia e horário.

## Exploração

- Especificação completa → [`promocoes.md`](./promocoes.md)
- Precedência com desconto de grupo → [`../../clientes/grupos-de-clientes/README.md`](../../clientes/grupos-de-clientes/README.md)
- Cardápio digital → [`../../consumo-digital/README.md`](../../consumo-digital/README.md)
