package com.baronesa.emporio.enums;

public enum TipoMovimentoCaixa {
    PAGAMENTO_MESA,      // Pagamento de conta de mesa
    GORJETA,             // Gorjetas recebidas
    CAIXA_INICIAL,       // Abertura de caixa
    REFORCO,             // Reforço de caixa (entrada de dinheiro)
    SANGRIA,             // Sangria (retirada de dinheiro)
    CONTAS_PAGAR,        // Pagamento de fornecedores, contas
    CONTAS_RECEBER,      // Recebimento de valores
    ESTORNO,             // Estorno de pagamento
    OUTROS               // Outras movimentações
}
