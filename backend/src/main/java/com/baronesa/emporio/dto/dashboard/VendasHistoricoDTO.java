package com.baronesa.emporio.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendasHistoricoDTO(
        LocalDate data,
        BigDecimal valor,
        Long quantidade
) { }
