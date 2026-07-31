package com.baronesa.emporio.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AtualizarItemPedidoRequest {
    private BigDecimal quantidade;
    private BigDecimal custoUnitario;
    private Long embalagemId;
    private Long skuId;
}
