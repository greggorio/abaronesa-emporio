package com.baronesa.emporio.dto.relatorios;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public record ResumoVendasDTO(
        BigDecimal totalPago,
        BigDecimal totalBase,
        BigDecimal totalTaxaServico,
        BigDecimal valorCouvert,
        Long pagamentosComCouvert,
        Long numeroVendas,
        BigDecimal ticketMedio
) {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public String getTotalPagoFormatado() {
        return formatCurrency(totalPago);
    }

    public String getTotalBaseFormatado() {
        return formatCurrency(totalBase);
    }

    public String getTotalTaxaServicoFormatada() {
        return formatCurrency(totalTaxaServico);
    }

    public String getValorCouvertFormatado() {
        return formatCurrency(valorCouvert);
    }

    public String getTicketMedioFormatado() {
        return formatCurrency(ticketMedio);
    }

    public String getPagamentosComCouvertFormatado() {
        return pagamentosComCouvert != null ? String.valueOf(pagamentosComCouvert) : "0";
    }

    public String getNumeroVendasFormatado() {
        return numeroVendas != null ? String.valueOf(numeroVendas) : "0";
    }

    private String formatCurrency(BigDecimal value) {
        return CURRENCY_FORMAT.format(value != null ? value : BigDecimal.ZERO);
    }
}
