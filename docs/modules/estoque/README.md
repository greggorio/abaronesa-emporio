> **DNA**
>
> - **id**: `estoque`
> - **type**: `seed+node`
> - **label**: `Estoque`
> - **ancestors**: `[modules, docs]`
> - **maturity**: `seed`
> - **contract**: `modulo-bakery@1.0`

# Estoque

Estoque é o módulo que representa, controla e rastreia a quantidade física de tudo que o estabelecimento comercializa, consome na produção ou mantém em armazenamento. Responde a uma pergunta única: "O que temos, quanto temos e quando vence?"

O módulo não é uma tabela — é um sistema de dupla camada. Para produtos vendáveis, o saldo existe por SKU. Para produtos insumo (destilados, ingredientes), o saldo existe em unidade base (ml, g) e os SKUs são apenas formas de embalar esse material. Essa bifurcação determina todas as regras de movimentação e leitura de saldo.

Pertenço a [`modules/`](../README.md).

## Domínio

- [`estoque.md`](./estoque.md) — definição, dois modelos de estoque (vendável e insumo), entidades, integrações e decisões de domínio

## Sub-domínios

- [`movimentacao/`](./movimentacao/README.md) — todos os tipos de movimento, FEFO, sub-ledger de lotes e rastreabilidade
- [`validade/`](./validade/README.md) — alertas, dashboard, tarefas de contagem e tratamento de divergências

## Maturidade

Estado atual: `seed`. O elemento está percebido, ancorado e especificado em [`estoque.md`](./estoque.md), mas ainda não foi avaliado contra o contrato `modulo-bakery@1.0`.

## Leitura contextual

**Coerente:** o módulo tem entidades robustas, service com cobertura ampla de tipos de movimento, FEFO implementado automaticamente e sistema completo de tarefas de validade com detecção e tratamento de divergências entre sub-ledger e saldo agregado.

**Desalinhado:** três funcionalidades planejadas estruturalmente nunca chegaram à implementação — múltiplos depósitos (campos preparados no banco, lógica ausente), reserva dinâmica de estoque (campo `reservado` existe mas nunca é atualizado) e alertas de estoque mínimo (campo `estoque_minimo` existe, sem gatilho). Além disso, a tabela `estoque` para insumos ainda existe e pode ser lida por código legado, criando risco de inconsistência silenciosa.

## Fronteiras

- `estoque` × `suprimentos`: o recebimento de mercadoria pertence a suprimentos; a entrada no estoque é gerada automaticamente por recebimentos confirmados
- `estoque` × `producao`: a ficha técnica e o registro de produção pertencem a producao; o consumo de insumos é acionado por producao via `MovimentoEstoqueService`
- `estoque` × `vendas`: a venda pertence a vendas; a baixa de estoque é um efeito automático do pedido finalizado
- `estoque` × `produtos`: cadastro de produto, SKU e ficha técnica pertencem a produtos; a aba de validade no cadastro do produto é interface hospedada em produtos com domínio em estoque

## Exploração

- Modelos de saldo e entidades → [`estoque.md`](./estoque.md)
- Movimentações, FEFO e lotes → [`movimentacao/README.md`](./movimentacao/README.md)
- Alertas e tarefas de validade → [`validade/README.md`](./validade/README.md)
