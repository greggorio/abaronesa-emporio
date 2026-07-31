# Pagamento

**Status**: ESTAVEL

## Quem sou

Sub-domínio de Pagamento — processa o recebimento das vendas, seja por auto-atendimento (cliente paga via PIX/cartão pelo celular) ou assistido (staff registra pagamento no PDV). Integra gateways, emite NFC-e, gera comprovante e aloca valores entre convidados.

## Para que existo

Converter a conta em receita de forma segura, com suporte a múltiplos métodos de pagamento (PIX, cartão crédito/débito, dinheiro, voucher), split entre convidados, esteira de confirmação via webhook e registro no movimento de caixa.

## A quem pertenço

Módulo de **Vendas** — o pagamento é o desfecho do ciclo de vendas, conectando conta (origem) ao movimento de caixa e financeiro (destino).

## Domínio imediato

- Intenção de pagamento (PIX com QR code, cartão com token)
- Self-checkout: cliente inicia e paga pelo próprio celular
- Pagamento assistido: staff registra pagamento no PDV
- Split: um pagamento pode ser alocado entre múltiplos convidados
- Múltiplos métodos na mesma conta (ex: parte no PIX, parte no dinheiro)
- Webhook de confirmação do gateway
- Resolução de pagamentos self-checkout pelo garçom (liberação)
- Emissão de NFC-e e comprovante não-fiscal PDF
- Registro automático em movimento de caixa
- Fechamento de sessão ao concluir

## Coerente / Desalinhado

- **Coerente**: integração com conta (valores devidos), mesa (fechamento de sessão), caixa (movimento), fiscal (NFC-e). Gateways: Mercado Pago e PagSeguro
- **Desalinhado**: não suporta pagamento recorrente ou assinatura; não há integração com TEF (captura de cartão na maquininha)

## Caminhos de exploração

Leia `pagamento.md` para especificação completa dos fluxos de pagamento. Veja também `movimento-caixa/` para o registro contábil das transações.
