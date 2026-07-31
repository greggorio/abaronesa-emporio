package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriarLoteProdutoValidadeRequest {
    private Long skuId;
    private String lote;
    private LocalDate dataValidade;
    private BigDecimal quantidade;
    private String observacao;
}
