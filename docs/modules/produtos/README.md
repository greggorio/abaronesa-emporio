# Produtos

Produtos é o módulo central que representa, classifica e parametriza tudo que o estabelecimento oferece — itens para venda, insumos para produção, variações por SKU, composição técnica, promoções, disponibilidade por canal, exposição em painéis digitais e harmonização entre produtos.

É o módulo raiz que alimenta praticamente todos os outros módulos do sistema: `estoque/`, `producao/`, `vendas/`, `consumo-digital/`, `suprimentos/` e `clientes/`.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`produtos.md`](./produtos.md) — definição, modelo de dados, flags, categorias, endpoints, serviços e decisões de domínio
- [`skus/`](./skus/README.md) — variações de produto (SKUs) e embalagens
- [`promocoes/`](./promocoes/README.md) — regras promocionais por produto com dia da semana e horário
- [`disponibilidade/`](./disponibilidade/README.md) — disponibilidade por canal e faixa de horário
- [`signage/`](./signage/README.md) — exposição em painéis digitais com IA e renderização de vídeo
- [`harmonizacao/`](./harmonizacao/README.md) — sugestão de combinações entre produtos

Ficha técnica (composição de produtos por insumos) pertence a [`producao/ficha-tecnica/`](../producao/ficha-tecnica/README.md).

## Leitura contextual

O módulo é estável e maduro. O cadastro de produtos é a entidade mais referenciada do sistema — praticamente todos os módulos dependem dele. As flags booleanas no produto (`vendavel`, `insumo`, `controlaEstoque`, `temFichaTecnica`, `producaoPropria`, `exibirNoCardapio`, etc.) determinam o comportamento do produto em cada subsistema.

A governança de promoções está aqui (em `promocoes/`), mas o desconto de grupo (sócio) pertence a `clientes/grupos-de-clientes/`. A precedência é resolvida no backend: quando ambos coexistem, o menor preço é exposto com `origemDesconto` correspondente.

## Exploração

- Especificação completa do módulo → [`produtos.md`](./produtos.md)
- Variações e embalagens → [`skus/`](./skus/README.md)
- Regras promocionais → [`promocoes/`](./promocoes/README.md)
- Disponibilidade por canal → [`disponibilidade/`](./disponibilidade/README.md)
- Painéis digitais com IA → [`signage/`](./signage/README.md)
- Combinações entre produtos → [`harmonizacao/`](./harmonizacao/README.md)
- Ficha técnica e composição → [`producao/ficha-tecnica/`](../producao/ficha-tecnica/README.md)
