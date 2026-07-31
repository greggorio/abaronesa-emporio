package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoEventoDTO {
    private Long eventoId;
    private String eventoTitulo;
    private LocalDateTime dataEvento;
    private LocalDateTime dataFimEvento;
    private BigDecimal valorCouvertTotal;
    private Integer quantidadePagamentos;
    private List<PagamentoDetalheDTO> pagamentos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagamentoDetalheDTO {
        private Long pagamentoId;
        private LocalDateTime dataPagamento;
        private BigDecimal valorCouvert;
        private BigDecimal valorTotal;
        private String metodoPagamento;
    }
}