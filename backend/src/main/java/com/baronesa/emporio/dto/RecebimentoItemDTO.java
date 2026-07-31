package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecebimentoItemDTO(
        Long id,
        Long produtoId,
        Long skuId,
        Long embalagemId,
        String produtoCodigo,
        String produtoDescricao,
        BigDecimal quantidade,
        BigDecimal custoUnitario,
        BigDecimal valorTotal,
        String lote,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate dataValidade,
        String codigoProdutoFornecedor,
        String descricaoNfe
) {}
