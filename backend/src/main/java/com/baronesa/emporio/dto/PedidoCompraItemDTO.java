package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.StatusPedidoCompraItem;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PedidoCompraItemDTO {
    private Long id;
    private Long produtoId;
    private String produtoNome;
    private Long skuId;
    private String skuNome;
    private Long embalagemId;
    private String embalagemNome;
    private BigDecimal quantidade;
    private Integer quantidadeBase;
    private BigDecimal custoUnitario;
    private BigDecimal subtotal;
    private Integer recebidoBase;
    private StatusPedidoCompraItem status;
}
