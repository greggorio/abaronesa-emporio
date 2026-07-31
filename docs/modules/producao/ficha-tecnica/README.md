# Ficha Técnica

Ficha Técnica é o sub-domínio que governa a composição de produtos acabados por meio de ingredientes. Existe para definir a receita de cada produto: quais insumos, em que quantidade, o custo resultante e o rendimento.

Pertenço a [`producao/`](../README.md).

## Domínio

- [`ficha-tecnica.md`](./ficha-tecnica.md) — modelo de dados, endpoints, regras de cálculo de custo, busca de insumos e decisões de domínio

## Leitura contextual

A ficha técnica é 1:1 com o produto e é a fonte do preço de custo. Ao salvar, o `produto.precoCusto` é atualizado automaticamente. Apenas produtos marcados como `insumo=true` podem ser ingredientes.

Os itens são substituídos em bloco — o frontend envia o conjunto completo a cada salvamento.

## Exploração

- Especificação completa → [`ficha-tecnica.md`](./ficha-tecnica.md)
- Registro de produção → [`../producao-propria/README.md`](../producao-propria/README.md)
- Cadastro de produtos → [`../../produtos/README.md`](../../produtos/README.md)
