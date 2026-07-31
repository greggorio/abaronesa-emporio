package com.baronesa.emporio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecebimentoListDTO(
        Long id,
        String numeroNf,
        String fornecedor,
        LocalDateTime dataRecebimento,
        BigDecimal valorTotal,
        String status,
        String statusColor
) {}
