package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ContaPagarDTO(
        Long id,
        Long fornecedorId,
        String fornecedorNome,
        Long categoriaDespesaId,
        String categoriaDespesaNome,
        String descricao,
        BigDecimal valorTotal,
        Integer numeroParcelas,
        boolean recorrente,
        LocalDateTime dataCadastro,
        boolean quitada,
        BigDecimal valorPago,
        BigDecimal valorPendente,
        List<ContaPagarParcelaDTO> parcelas
) {}
