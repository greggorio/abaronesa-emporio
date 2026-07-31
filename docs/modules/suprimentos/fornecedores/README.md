# Fornecedores

**Status**: IMPLEMENTADO

## Quem sou

Sub-domínio de Fornecedores — cadastro de pessoas jurídicas que fornecem insumos e mercadorias para o Bakery.

## Para que existo

Manter um registro centralizado e confiável de fornecedores com dados fiscais (CNPJ), de contato e endereço, servindo como referência para pedidos de compra e recebimento de mercadorias.

## A quem pertenço

Módulo de **Suprimentos** — o fornecedor é a origem do fluxo de compras e recebimento.

## Domínio imediato

- Cadastro (CRUD com validação de CNPJ único)
- Busca textual para lookup (CNPJ, razão social, nome fantasia, cidade)
- Ativação/desativação lógica

## Coerente / Desalinhado

- **Coerente**: integrado com pedidos de compra e recebimento via chave estrangeira
- **Desalinhado**: não há avaliação de desempenho (prazo, qualidade), histórico de preços ou contratos

## Caminhos de exploração

Leia `fornecedores.md` para a especificação detalhada. Depois navegue para `pedidos-compra/` para ver como os fornecedores são usados no ciclo de compras.
