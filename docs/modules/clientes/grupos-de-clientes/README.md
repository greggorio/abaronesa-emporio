# Grupos de Clientes

Grupos de Clientes é o sub-domínio que classifica comercialmente o cliente e define os descontos que essa classificação confere sobre o catálogo.

Pertenço a [`clientes/`](../README.md).

## Domínio

- [`grupos-de-clientes.md`](./grupos-de-clientes.md) — entidades, regras de desconto e fluxo de aplicação no cardápio

## Leitura contextual

O efeito dos grupos aparece no cardápio (`produtos/`), mas a governança — criar grupos, vincular clientes, configurar percentuais por categoria — vive aqui. Isso é correto: o grupo é uma propriedade do cliente, não do produto.

A lógica de precedência (desconto de grupo vs. promoção) é resolvida pelo backend: quando ambos coexistem no mesmo item, o menor preço é exposto com `origemDesconto = SOCIO`. A fronteira permanece limpa — `produtos/promocoes` não precisa saber de grupos; `grupos-de-clientes` não precisa saber de promoções.

A operação de salvar descontos funciona por sincronização completa (delete + insert), não por merge parcial. Isso simplifica a lógica mas exige que o cliente envie sempre o conjunto completo de descontos desejados.

## Exploração

- Entidades e regras → [`grupos-de-clientes.md`](./grupos-de-clientes.md)
- Modelo completo do cliente → [`../clientes.md`](../clientes.md)
- Onde o desconto é aplicado → [`../../produtos/README.md`](../../produtos/README.md)
