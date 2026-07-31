package com.baronesa.emporio.dto.relatorios;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record MovimentoCaixaDetalheDTO(
        LocalDateTime dataHora,
        String tipo,
        String descricao,
        String formaPagamento,
        String responsavel,
        BigDecimal entrada,
        BigDecimal saida,
        BigDecimal saldo
) {
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter HORA_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public String getHoraFormatada() {
        return dataHora.format(HORA_FORMATTER);
    }

    public boolean isEntrada() {
        return entrada != null && entrada.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isSaida() {
        return saida != null && saida.compareTo(BigDecimal.ZERO) > 0;
    }

    public String getEntradaFormatada() {
        if (entrada == null || entrada.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return CURRENCY_FORMAT.format(entrada);
    }

    public String getSaidaFormatada() {
        if (saida == null || saida.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return CURRENCY_FORMAT.format(saida);
    }

    public String getSaldoFormatado() {
        return CURRENCY_FORMAT.format(saldo != null ? saldo : BigDecimal.ZERO);
    }
}
