package com.baronesa.emporio.dto;

import java.math.BigDecimal;

public record ItemNfeDTO(
        String codigo,
        String descricao,
        String ncm,
        String cfop,
        String unidade,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        Long produtoId, // Se já existe no sistema
        boolean cadastrado
) {}
