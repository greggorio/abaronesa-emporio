package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.util.List;

public record ContaPagarRequest(
        Long fornecedorId,
        Long categoriaDespesaId,
        String descricao,
        BigDecimal valorTotal,
        Integer numeroParcelas,
        boolean recorrente,
        List<ContaPagarParcelaRequest> parcelas
) {}
