package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaPagarParcelaRequest(
        Integer numeroParcela,
        BigDecimal valor,
        LocalDate dataVencimento,
        LocalDate dataPagamento,
        String formaPagamento,
        boolean paga
) {}
