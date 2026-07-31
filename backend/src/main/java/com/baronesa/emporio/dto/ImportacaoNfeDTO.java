package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImportacaoNfeDTO(
        String numeroNf,
        String chaveNfe,
        LocalDate dataEmissao,
        FornecedorNfeDTO fornecedor,
        List<ItemNfeDTO> itens,
        BigDecimal valorTotal
) {}