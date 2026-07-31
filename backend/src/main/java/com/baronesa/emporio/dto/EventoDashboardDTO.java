package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoDashboardDTO {
    
    private BigDecimal totalCouverAtual;
    private BigDecimal totalFaturamento30d;
    private BigDecimal mediaFaturamento;
    private List<EventoResumoDTO> eventosLista;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventoResumoDTO {
        private String nome;
        private BigDecimal total;
        private Integer progressWidth;
    }
}