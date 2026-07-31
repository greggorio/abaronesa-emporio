# Produção

Produção é o módulo que governa a transformação de insumos em produtos acabados no estabelecimento. Existe para controlar o que é produzido internamente (drinks, pratos, sobremesas), como é composto (ficha técnica com ingredientes e quantidades) e o registro da produção com baixa automática de insumos e entrada do produto produzido em estoque.

A produção conecta o cadastro de produtos (`produtos/`) com o controle de estoque (`estoque/`) — a ficha técnica define a composição, e o ato de produzir consome insumos e gera o produto acabado.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`producao.md`](./producao.md) — definição, escopo, regras, fluxos, integrações e decisões de domínio
- [`ficha-tecnica/`](./ficha-tecnica/README.md) — composição de produtos por insumos, cálculo de custo e rendimento
- [`producao-propria/`](./producao-propria/README.md) — registro de produção com consumo de insumos e entrada em estoque

## Leitura contextual

O módulo está implementado com dois sub-domínios operacionais. A ficha técnica é uma relação 1:1 com o produto — cada produto pode ter exatamente uma ficha, que lista os insumos necessários com quantidades, ordem e observações. A produção própria consome a ficha técnica para registrar a fabricação: valida estoque de insumos, dá baixa nos ingredientes e entrada do produto acabado.

A flag `producaoPropria` no produto define o comportamento de baixa de insumos na venda: produtos com `producaoPropria=true` têm a baixa feita no momento da produção (não na venda); produtos com `temFichaTecnica=true` + `producaoPropria=false` têm a baixa feita automaticamente no aceite do pedido.

A fronteira com `produtos/` é de composição: a ficha técnica vive neste módulo, mas o cadastro do produto que a referência está em `produtos/`. A fronteira com `estoque/` é de movimento: `CONSUMO_PRODUCAO` (tipo 11) para saída de insumos e `PRODUCAO` (tipo 12) para entrada do produzido.

## Exploração

- Especificação completa do módulo → [`producao.md`](./producao.md)
- Composição e custo de produtos → [`ficha-tecnica/README.md`](./ficha-tecnica/README.md)
- Registro de produção → [`producao-propria/README.md`](./producao-propria/README.md)
- Controle de estoque → [`estoque/`](../estoque/README.md)
- Cadastro de produtos → [`produtos/`](../produtos/README.md)
