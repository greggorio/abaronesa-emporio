# Movimento de Caixa

**Status**: ESTAVEL

## Quem sou

Sub-domínio de Movimento de Caixa — registro cronológico de todas as entradas e saídas financeiras do estabelecimento, incluindo pagamentos de mesa, gorjetas, sangrias, reforços, estornos e movimentos manuais.

## Para que existo

Garantir a rastreabilidade financeira de cada centavo que entra ou sai do caixa, servindo como fonte única da verdade para conciliação, relatórios e fechamento de caixa.

## A quem pertenço

Módulo de **Vendas** — o movimento de caixa é gerado automaticamente a cada pagamento ou estorno, mas também suporta lançamentos manuais.

## Domínio imediato

- Registro automático de pagamentos de mesa (entrada)
- Registro automático de gorjetas
- Registro automático de estornos
- Lançamentos manuais: caixa inicial, sangria, reforço
- Listagem e filtros por tipo, período, responsável
- Tipos: PAGAMENTO_MESA, GORJETA, CAIXA_INICIAL, REFORCO, SANGRIA, CONTAS_PAGAR, CONTAS_RECEBER, ESTORNO, OUTROS

## Coerente / Desalinhado

- **Coerente**: gerado automaticamente por pagamentos e estornos de venda; suporta lançamentos manuais de despesas e receitas
- **Desalinhado**: não faz conciliação automática com extrato bancário; não há fechamento de caixa formal com conferência de valores

## Caminhos de exploração

Leia `movimento-caixa.md` para especificação. Veja também `pagamento/` para entender como os pagamentos geram movimentos de caixa automaticamente.
