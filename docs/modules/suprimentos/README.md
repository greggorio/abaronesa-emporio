# Suprimentos

**Status**: EM_DESENVOLVIMENTO

## Quem sou

Módulo de Suprimentos — responsável pelo ciclo completo de aquisição de insumos e mercadorias: cadastro de fornecedores, geração de pedidos de compra (manuais ou por sugestão automática), recebimento físico com conferência de NF-e e entrada em estoque.

## Para que existo

Garantir que o estoque seja reabastecido com eficiência, rastreabilidade e controle financeiro, desde a identificação da necessidade até a entrada física dos itens com registro de lotes, validade e custos.

## A quem pertenço

Bakery — módulo de suprimentos integra-se diretamente com os módulos de **Estoque** (entrada de mercadorias via `estoque_lote` e `movimento_estoque`), **Produtos** (vinculação de SKUs e insumos) e **Financeiro** (geração de parcelas a pagar no recebimento).

## Domínio imediato

- **fornecedores/** — Cadastro e gestão de fornecedores (CNPJ único, contato, endereço, ativo/inativo)
- **pedidos-compra/** — Pedidos de compra com status RASCUNHO → ENVIADO → PARCIAL → RECEBIDO → CANCELADO; itens com quantidade, custo e controle de recebimento parcial
- **sugestao-compra/** — Geração automática de sugestões de compra baseada em estoque mínimo e consumo
- **recebimento/** — Recebimento de mercadorias com conferência de NF-e, controle de lotes/validade e entrada em estoque

## Coerente / Desalinhado

- **Coerente**: Integração bidirecional com estoque (entrada e estorno), vinculação com produtos/SKUs, geração de parcelas no financeiro
- **Desalinhado**: Sugestão de compra ainda não considera sazonalidade ou lead time; não há integração com CRM para negociar condições com fornecedores

## Caminhos de exploração

Leia `suprimentos.md` para a especificação completa. Navegue pelos sub-domínios na ordem do fluxo: `fornecedores/` → `pedidos-compra/` → `recebimento/`. Para o motor de reabastecimento, veja `sugestao-compra/`.
