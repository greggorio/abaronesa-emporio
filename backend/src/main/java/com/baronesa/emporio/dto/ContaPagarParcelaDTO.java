package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaPagarParcelaDTO(
        Long id,
        Integer numeroParcela,
        BigDecimal valor,
        LocalDate dataVencimento,
        LocalDate dataPagamento,
        String formaPagamento,
        boolean paga,
        boolean vencida
) {}
