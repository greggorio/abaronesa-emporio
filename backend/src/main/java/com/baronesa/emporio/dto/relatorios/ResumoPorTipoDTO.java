package com.baronesa.emporio.dto.relatorios;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public record ResumoPorTipoDTO(
        String descricao,
        Integer quantidade,
        BigDecimal valor
) {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public String getValorFormatado() {
        return CURRENCY_FORMAT.format(valor != null ? valor : BigDecimal.ZERO);
    }
}
