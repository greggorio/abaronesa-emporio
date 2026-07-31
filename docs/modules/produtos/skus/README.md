# SKUs

SKUs é o sub-domínio que governa as variações de um produto. Existe para permitir que o mesmo produto opere em diferentes apresentações, tamanhos, embalagens e preços.

Pertenço a [`produtos/`](../README.md).

## Domínio

- [`skus.md`](./skus.md) — modelo de dados (ProdutoSKU, Embalagem), endpoints, comportamento por modelo de produto, serviços e frontend

## Leitura contextual

SKU depende do produto — não existe SKU sem produto pai. O comportamento de estoque do SKU varia conforme o modelo: vendáveis têm estoque próprio; insumos derivam o saldo do estoque centralizado pelo fator da embalagem.

## Exploração

- Especificação completa → [`skus.md`](./skus.md)
- Cadastro de produtos → [`../produtos.md`](../produtos.md)
- Controle de estoque → [`../../estoque/README.md`](../../estoque/README.md)
