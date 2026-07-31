package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVendasDTO {
    private Long totalMesas;
    private Long mesasOcupadas;
    private Double percentualOcupacao;
    private Long totalVendasCentavos;
    private Long ticketMedioCentavos;
    private Long quantidadeVendas;
}
