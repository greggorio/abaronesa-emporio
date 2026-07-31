package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaReceberParcelaRequest(
        Integer id,
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
        boolean cobrancaEnviada,
        LocalDate dataEnvioCobranca
) {}