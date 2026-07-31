package com.baronesa.emporio.dto.relatorios;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public record ResumoFormaPagamentoDTO(
        String formaPagamento,
        String metodo,
        String cartaoTipo,
        BigDecimal valor
) {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public ResumoFormaPagamentoDTO(String metodo, String cartaoTipo, BigDecimal valor) {
        this(formatFormaPagamento(metodo, cartaoTipo), metodo, cartaoTipo, valor);
    }

    public String getMetodoFormatado() {
        return metodo != null && !metodo.isBlank() ? capitalize(metodo) : "Outro";
    }

    public String getCartaoTipoFormatado() {
        return cartaoTipo != null && !cartaoTipo.isBlank() ? capitalize(cartaoTipo) : "-";
    }

    public String getValorFormatado() {
        return CURRENCY_FORMAT.format(valor != null ? valor : BigDecimal.ZERO);
    }

    private static String formatFormaPagamento(String metodo, String cartaoTipo) {
        String base;
        if (metodo == null || metodo.isBlank()) {
            base = "Outro";
        } else {
            base = metodo.replaceAll("[^a-zA-Z0-9]", " ").strip();
            if (base.isBlank()) {
                base = "Outro";
            } else {
                base = capitalize(base.toLowerCase(Locale.ROOT));
            }
        }

        if (cartaoTipo != null && !cartaoTipo.isBlank()) {
            base += " (" + capitalize(cartaoTipo.toLowerCase(Locale.ROOT)) + ")";
        }

        return base;
    }

    private static String capitalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1).toLowerCase(Locale.ROOT);
    }
}
