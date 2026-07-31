package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record MovimentoCaixaDTO(
        Long id,
        LocalDateTime dataHora,
        TipoMovimentoCaixa tipo,
        BigDecimal valor,
        TipoFormaPagamento meioPagamento,
        boolean afetaCaixa,
        TipoOperacao operacao,
        String observacao,
        Long referenciaId,
        String referenciaTipo,
        Long responsavelId,
        String responsavelNome
) {
}
