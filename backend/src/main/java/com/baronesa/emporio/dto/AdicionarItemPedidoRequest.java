package com.baronesa.emporio.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdicionarItemPedidoRequest {
    private Long produtoId;
    private Long skuId;
    private Long embalagemId;
    private BigDecimal quantidade;
    private BigDecimal custoUnitario;
}
