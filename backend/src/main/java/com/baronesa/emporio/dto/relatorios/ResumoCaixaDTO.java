package com.baronesa.emporio.dto.relatorios;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public record ResumoCaixaDTO(
        BigDecimal saldoInicial,
        BigDecimal totalEntradas,
        BigDecimal totalSaidas,
        BigDecimal saldoFinal
) {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public String getSaldoInicialFormatado() {
        return CURRENCY_FORMAT.format(saldoInicial != null ? saldoInicial : BigDecimal.ZERO);
    }

    public String getTotalEntradasFormatado() {
        return CURRENCY_FORMAT.format(totalEntradas != null ? totalEntradas : BigDecimal.ZERO);
    }

    public String getTotalSaidasFormatado() {
        return CURRENCY_FORMAT.format(totalSaidas != null ? totalSaidas : BigDecimal.ZERO);
    }

    public String getSaldoFinalFormatado() {
        return CURRENCY_FORMAT.format(saldoFinal != null ? saldoFinal : BigDecimal.ZERO);
    }
}
