package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedSalesResponse {
    private List<SalesRecordDTO> table_data; // Using table_data to match frontend expectation
    private long totalElementos;
    private int totalPaginas;
    private int paginaAtual;
    private int tamanhoPagina;
}
