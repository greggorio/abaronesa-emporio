package com.baronesa.emporio.dto.dashboard;

import java.math.BigDecimal;
import java.util.Map;

public record MovimentoCaixaResumoDTO(
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldo,
        Map<String, BigDecimal> detalhesPorTipo
) {}
