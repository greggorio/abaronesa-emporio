package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaReceberParcelaDTO(
        Long id,
        Integer numeroParcela,
        BigDecimal valor,
        LocalDate dataVencimento,
        LocalDate dataRecebimento,
        BigDecimal valorMulta,
        BigDecimal valorJuros,
        BigDecimal valorDesconto,
        BigDecimal valorRecebido,
        String formaRecebimento,
        boolean recebida,
        boolean vencida,
        long diasAtraso,
        boolean cobrancaEnviada,
        LocalDate dataEnvioCobranca
) {}