package com.baronesa.emporio.dto.relatorios;

import java.time.LocalDate;

public record RelatorioVendasFiltroDTO(
        LocalDate dataInicio,
        LocalDate dataFim,
        String produtoNome
) {
    public RelatorioVendasFiltroDTO(LocalDate dataInicio, LocalDate dataFim) {
        this(dataInicio, dataFim, null);
    }
}
