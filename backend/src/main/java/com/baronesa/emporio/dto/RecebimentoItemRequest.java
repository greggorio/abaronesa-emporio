package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecebimentoItemRequest(
        Long produtoId,
        Long skuId,
        Long embalagemId,
        BigDecimal quantidade,
        BigDecimal custoUnitario,
        String lote,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dataValidade
) {}
