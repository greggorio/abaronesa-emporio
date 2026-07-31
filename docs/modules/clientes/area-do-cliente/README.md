# Área do Cliente

Área do Cliente é a superfície autenticada onde o cliente visualiza sua relação consolidada com o ecossistema: consumo ativo, contas, pontos, recompensas, notificações e eventos.

É exclusivamente uma superfície de leitura — não governa os dados que exibe. Cada módulo de origem preserva sua própria governança.

Pertenço a [`clientes/`](../README.md).

## Domínio

- [`area-do-cliente.md`](./area-do-cliente.md) — o que é agregado, de onde vem cada dado, e o estado de cada seção

## Leitura contextual

A área do cliente não possui endpoint único — a interface consome múltiplas APIs de módulos distintos em paralelo. Esse modelo de agregação no frontend é intencional: cada módulo expõe seus próprios dados sem precisar conhecer a área do cliente.

O risco estrutural deste sub-domínio é acumular lógica de escrita — qualquer ação que modifique estado deve ser redirecionada para o módulo responsável. A área do cliente orienta e navega; não executa.

## Exploração

- O que é agregado e de onde vem → [`area-do-cliente.md`](./area-do-cliente.md)
- Pontos e recompensas → [`../../fidelizacao/README.md`](../../fidelizacao/README.md)
- Delivery ativo → [`../../consumo-digital/README.md`](../../consumo-digital/README.md)
- Contas abertas → [`../../financeiro/README.md`](../../financeiro/README.md)
