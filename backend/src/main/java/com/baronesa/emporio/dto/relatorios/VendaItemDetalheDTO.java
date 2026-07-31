package com.baronesa.emporio.dto.relatorios;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendaItemDetalheDTO {
    private LocalDateTime dataHora;
    private Long cupomId;
    private String produtoNome;
    private Long quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal valorTotal;
}
