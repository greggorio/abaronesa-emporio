package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.util.List;

public record ContaReceberRequest(
        Long clienteId,
        Long tipoReceitaId,
        String numeroDocumento,
        String descricao,
        BigDecimal valorTotal,
        Integer numeroParcelas,
        String observacoes,
        boolean recorrente,
        List<ContaReceberParcelaRequest> parcelas
) {}