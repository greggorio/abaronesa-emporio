# Conta

**Status**: ESTAVEL

## Quem sou

Sub-domínio de Conta — motor de cálculo que consolida todos os itens consumidos, aplica taxa de serviço e couvert artístico, calcula valores pagos e devidos, e suporta rateio entre convidados.

## Para que existo

Responder à pergunta "quanto cada um deve?" de forma precisa e transparente, seja para a mesa inteira ou por convidado, considerando todos os encargos e pagamentos já realizados.

## A quem pertenço

Módulo de **Vendas** — a conta é o ponto de transição entre o consumo (pedidos) e o recebimento (pagamento).

## Domínio imediato

- Consolidação da conta por mesa (todos os pedidos da sessão)
- Consolidação da conta por convidado (apenas itens do convidado + rateio de couvert)
- Cálculo de subtotal, taxa de serviço (percentual configurável), couvert artístico
- Subtração de valores já pagos (com alocação por convidado)
- Rateio entre convidados (split)

## Coerente / Desalinhado

- **Coerente**: integração com pedidos (itens consumidos), pagamentos (valores pagos e alocados), sessão (couvert por convidado)
- **Desalinhado**: não suporta desconto automático por forma de pagamento (ex: 5% off no PIX); não suporta meia entrada ou cortesia por item individual

## Caminhos de exploração

Leia `conta.md` para detalhes do algoritmo de cálculo. Depois veja `pagamento/` para o fluxo de recebimento.
