# Recompensas

Recompensas é o sub-domínio que governa o catálogo de vantagens que o cliente pode obter com seus pontos de fidelidade. Existe para cadastrar, classificar por tipo (PRODUTO, DESCONTO_PERCENTUAL, DESCONTO_VALOR, BRINDE_GENERICO), definir custo em pontos, controlar disponibilidade (validade e estoque) e consultar elegibilidade por cliente.

Pertenço a [`fidelizacao/`](../README.md).

## Domínio

- [`recompensas.md`](./recompensas.md) — modelo de dados, tipos, regras de disponibilidade, endpoints, serviços e frontend

## Leitura contextual

O catálogo de recompensas está implementado com CRUD admin, consulta de disponibilidade por cliente com flag `podeResgatar` e cálculo de `faltamPontos`. Quatro tipos de recompensa com campos específicos por tipo. Estoque opcional (null = ilimitado).

A fronteira com `resgates/` é de fluxo: o resgate consulta a recompensa para validar disponibilidade e decrementa o estoque.

## Exploração

- Especificação completa → [`recompensas.md`](./recompensas.md)
- Saldo de pontos → [`../gamificacao/README.md`](../gamificacao/README.md)
- Processo de resgate → [`../resgates/README.md`](../resgates/README.md)
