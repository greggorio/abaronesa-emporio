# Fidelização

Fidelização é o módulo de pontos, recompensas, resgates e benefícios recorrentes ligados ao cliente. Existe para incentivar o consumo recorrente: em vez de desconto imediato, o cliente acumula pontos e os troca por vantagens.

O módulo opera em três camadas: gamificação (acumulação e saldo de pontos), recompensas (catálogo de vantagens disponíveis) e resgates (troca de pontos por benefícios). O consumo em `vendas/` é a origem dos créditos, e o saldo é exposto ao cliente via `clientes/area-do-cliente/`.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`fidelizacao.md`](./fidelizacao.md) — definição, escopo, regras, integrações e decisões de domínio
- [`gamificacao/`](./gamificacao/README.md) — saldo de pontos, movimentos de crédito e débito, histórico e regras de pontuação
- [`recompensas/`](./recompensas/README.md) — catálogo de recompensas disponíveis: cadastro, tipos, custo em pontos, disponibilidade e validade
- [`resgates/`](./resgates/README.md) — solicitação de resgate, validação de saldo, débito de pontos, registro e entrega da recompensa

## Leitura contextual

O módulo está em desenvolvimento e sua implementação ainda não cobre todos os sub-domínios especificados. O fluxo principal — consumir, pontuar, resgatar — está mapeado conceitualmente, mas a materialização no banco e nas APIs ainda está em construção.

A fronteira com `clientes/` é limpa: `clientes` governa a identidade e o perfil; `fidelizacao` governa o saldo de pontos e os benefícios. A área do cliente (`clientes/area-do-cliente/`) expõe saldo e recompensas como leitura — a governança permanece aqui.

A pontuação nasce do pedido finalizado em `vendas/`, mas `fidelizacao` é quem governa a taxa de conversão, as regras de expiração e o histórico de movimentos.

## Exploração

- Especificação completa do módulo → [`fidelizacao.md`](./fidelizacao.md)
- Pontos e movimentação → [`gamificacao/`](./gamificacao/README.md)
- Catálogo de recompensas → [`recompensas/`](./recompensas/README.md)
- Processo de resgate → [`resgates/`](./resgates/README.md)
- Saldo e histórico na área do cliente → [`clientes/area-do-cliente/`](../clientes/area-do-cliente/README.md)
- Origem dos créditos (pedidos) → [`vendas/`](../vendas/README.md)
