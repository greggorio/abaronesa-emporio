package com.baronesa.emporio.dto.relatorios;

import com.baronesa.emporio.entity.Pagamento;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record PagamentoDetalheDTO(
        Long id,
        String metodo,
        String cartaoTipo,
        String status,
        LocalDateTime pagoEm,
        BigDecimal valor,
        BigDecimal valorBase,
        BigDecimal valorTaxaServico,
        BigDecimal valorCouvert,
        String paganteNome,
        String referencia
) {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static PagamentoDetalheDTO from(Pagamento pagamento) {
        String paganteNome = pagamento.getPagante() != null ? pagamento.getPagante().getNomeExibicao() : null;
        String referencia = pagamento.getProviderRef();
        return new PagamentoDetalheDTO(
                pagamento.getId(),
                pagamento.getMetodo(),
                pagamento.getCartaoTipo(),
                pagamento.getStatus() != null ? pagamento.getStatus().name() : null,
                pagamento.getPagoEm(),
                pagamento.getValor(),
                pagamento.getValorBase(),
                pagamento.getValorTaxaServico(),
                pagamento.getValorCouvert(),
                paganteNome,
                referencia
        );
    }

    public String getMetodoFormatado() {
        return capitalize(metodo);
    }

    public String getCartaoTipoFormatado() {
        return cartaoTipo != null && !cartaoTipo.isBlank() ? capitalize(cartaoTipo) : "-";
    }

    public String getStatusFormatado() {
        return status != null ? status : "-";
    }

    public String getPagoEmFormatado() {
        if (pagoEm == null) {
            return "-";
        }
        return pagoEm.format(DATE_TIME_FORMATTER);
    }

    public String getValorFormatado() {
        return formatCurrency(valor);
    }

    public String getValorBaseFormatado() {
        return formatCurrency(valorBase);
    }

    public String getValorTaxaServicoFormatado() {
        return formatCurrency(valorTaxaServico);
    }

    public String getValorCouvertFormatado() {
        return formatCurrency(valorCouvert);
    }

    public String getPaganteNomeFormatado() {
        return paganteNome != null && !paganteNome.isBlank() ? paganteNome : "-";
    }

    public String getReferenciaFormatada() {
        return referencia != null && !referencia.isBlank() ? referencia : "-";
    }

    private String formatCurrency(BigDecimal value) {
        return CURRENCY_FORMAT.format(value != null ? value : BigDecimal.ZERO);
    }

    private String capitalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1).toLowerCase(Locale.ROOT);
    }
}
