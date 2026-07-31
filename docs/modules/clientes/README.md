# Clientes

Clientes é o domínio que governa a identidade de quem consome no ecossistema Bakery. Existe para responder três perguntas centrais: quem é este cliente, a que segmento comercial ele pertence, e o que ele consome com frequência.

O cliente é modelado como um `Usuario` com `Role.CLIENTE`, complementado por `PerfilCliente` em relação 1:1. O modelo suporta pessoa física e jurídica, origem de cadastro (loja física ou e-commerce) e autenticação via credencial local ou OAuth2.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`clientes.md`](./clientes.md) — modelo de dados, escopo, integrações e decisões de domínio
- [`grupos-de-clientes/`](./grupos-de-clientes/README.md) — classificação comercial e descontos por categoria
- [`area-do-cliente/`](./area-do-cliente/README.md) — superfície autenticada que consolida a relação do cliente com o ecossistema
- [`favoritos/`](./favoritos/README.md) — frequência e afinidade de consumo derivadas do histórico de pedidos

## Leitura contextual

Os sub-domínios maduros são `grupos-de-clientes` e `area-do-cliente` — ambos com implementação completa. `favoritos` tem API funcional, mas a tela dedicada ainda usa dados mock.

A governança dos descontos vive em `grupos-de-clientes`, mas o efeito aparece no cardápio. Isso não é desalinhamento: o grupo é uma classificação do cliente, e o preço resultante é resolvido por `produtos/`. A fronteira está correta.

`clientes` não governa fidelização — pontos, recompensas e resgates pertencem a `fidelizacao/`. A área do cliente exibe dados de fidelização como leitura; essa agregação é intencional e não rompe a fronteira, desde que não acumule lógica de escrita.

## Exploração

- Modelo de dados e entidades → [`clientes.md`](./clientes.md)
- Grupos e descontos comerciais → [`grupos-de-clientes/`](./grupos-de-clientes/README.md)
- Superfície consolidada do cliente → [`area-do-cliente/`](./area-do-cliente/README.md)
- Frequência de consumo → [`favoritos/`](./favoritos/README.md)
- Pontos e recompensas → [`fidelizacao/`](../fidelizacao/README.md)
- Jornada transacional digital → [`consumo-digital/`](../consumo-digital/README.md)
