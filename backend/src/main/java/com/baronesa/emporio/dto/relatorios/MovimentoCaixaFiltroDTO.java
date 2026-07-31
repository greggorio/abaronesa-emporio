package com.baronesa.emporio.dto.relatorios;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record MovimentoCaixaFiltroDTO(
        LocalDate data
) {
    public String getDataFormatada() {
        return data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
