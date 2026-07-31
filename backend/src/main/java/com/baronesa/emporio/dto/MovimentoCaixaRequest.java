package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;

import java.math.BigDecimal;

public record MovimentoCaixaRequest(
        TipoMovimentoCaixa tipo,
        BigDecimal valor,
        TipoFormaPagamento meioPagamento,
        boolean afetaCaixa,
        TipoOperacao operacao,
        String observacao,
        Long referenciaId,
        String referenciaTipo
) {
}
