# Eventos

Eventos é o domínio que governa a agenda de shows, happy hours e festas temáticas do estabelecimento. Existe para responder três perguntas operacionais: qual evento está agendado, quando e onde acontece, e qual a taxa de entrada.

O domínio canônico vive no `espresso_back` — entidade `Evento` com `EventoStatus` e `GeneroMusical` como enums, soft delete via `ativo=false`, validação de sobreposição de horário. O `backend` bakery consome via REST client (`EventoEspressoService`) para alimentar o dashboard de faturamento, a notificação pré-evento e do dia, e o cálculo de couvert artístico debitado em `Pagamento`.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`eventos.md`](./eventos.md) — definição, escopo, modelo de dados, endpoints, regras, integrações, distribuição entre backends e decisões de domínio

## Leitura contextual

O CRUD básico funciona — cadastro, listagem pública, paginação admin e validação de sobreposição de horário estão implementados no `espresso_back`. Os refinamentos de regras de negócio (consistência entre `status` e datas, comportamento de `CANCELADO` etc.) ainda estão em evolução, e é por isso que o `eventos.md` classifica o módulo como `EM_DESENVOLVIMENTO`. Esta é a leitura conservadora e deve prevalecer sobre o status `ESTÁVEL` que já constou aqui.

A natureza do módulo é distribuída: o `espresso_back` é a fonte da verdade (entidade, enums, endpoints) e o `backend` bakery é um consumidor REST — sem réplica de dados. Dashboard, notificações e couvert consultam o `espresso_back` em tempo de leitura; o couvert registrado em `Pagamento` é materializado no instante do pagamento, com a janela temporal do evento vigente naquele momento.

A fronteira com `quiz/` é de uso: o quiz acontece dentro de um evento, mas não governa a agenda nem a operação do evento em si. Da mesma forma, a área do cliente em `clientes/` lista os próximos eventos como leitura — quem governa a fonte é este módulo. O couvert artístico, por sua vez, pertence ao fluxo de pagamento digital em `consumo-digital/pagamentos/`: este módulo provê o `preco` e o flag `gratuito`; a cobrança e a materialização em `Pagamento.valorCouvert` são de lá.

## Exploração

- Especificação completa do domínio → [`eventos.md`](./eventos.md)
- Próximos eventos para o cliente → [`clientes/area-do-cliente/`](../clientes/area-do-cliente/README.md)
- Painel de faturamento por evento → [`dashboard/`](../dashboard/README.md)
- Engajamento ao vivo dentro do evento → [`quiz/`](../quiz/README.md)
- Couvert artístico no pagamento digital → [`consumo-digital/pagamentos/`](../consumo-digital/pagamentos/README.md)
