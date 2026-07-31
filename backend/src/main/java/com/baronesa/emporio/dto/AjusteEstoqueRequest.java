package com.baronesa.emporio.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AjusteEstoqueRequest {
    private Long skuId;
    private Long embalagemId;
    private BigDecimal quantidade;
    private String motivo;
    private boolean adicionar;
}
