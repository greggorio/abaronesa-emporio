# Resgates

Resgates é o sub-domínio que governa a troca de pontos por recompensas. Existe para processar a solicitação de resgate (admin-only): validar recompensa (ativa, dentro da validade, com estoque), verificar saldo do cliente, debitar pontos, decrementar estoque e registrar a transação.

Pertenço a [`fidelizacao/`](../README.md).

## Domínio

- [`resgates.md`](./resgates.md) — fluxo de resgate, validações, regras, DTOs, endpoints, serviços e frontend

## Leitura contextual

O resgate é admin-only e não possui endpoint de autoatendimento para o cliente. A transação é registrada como `MovimentoPontos` com tipo `RESGATE` e origem `RECOMPENSA` — não há tabela própria. O saldo após o resgate é congelado no momento do débito.

Depende de `gamificacao/` para saldo e de `recompensas/` para o catálogo.

## Exploração

- Especificação completa → [`resgates.md`](./resgates.md)
- Saldo de pontos → [`../gamificacao/README.md`](../gamificacao/README.md)
- Catálogo de recompensas → [`../recompensas/README.md`](../recompensas/README.md)
