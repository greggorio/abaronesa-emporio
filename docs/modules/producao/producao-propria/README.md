# Produção Própria

Produção Própria é o sub-domínio que governa o registro da fabricação de itens no estabelecimento. Existe para registrar a produção: consumir insumos da ficha técnica, dar baixa no estoque e entrar o produto acabado.

Pertenço a [`producao/`](../README.md).

## Domínio

- [`producao-propria.md`](./producao-propria.md) — fluxo de produção, validações, tipos de movimento de estoque e decisões de domínio

## Leitura contextual

A produção sempre produz 1 unidade e exige que o produto tenha `producaoPropria=true`, `temFichaTecnica=true` e `controlaEstoque=true`. Os movimentos gerados são do tipo CONSUMO_PRODUCAO (insumos) e PRODUCAO (produto acabado), compartilhando o mesmo `documentoReferencia` para rastreabilidade.

## Exploração

- Especificação completa → [`producao-propria.md`](./producao-propria.md)
- Composição de produtos → [`../ficha-tecnica/README.md`](../ficha-tecnica/README.md)
- Movimentos de estoque → [`../../estoque/README.md`](../../estoque/README.md)
