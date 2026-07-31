package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MovimentoEstoqueRequest {
    private Long skuId;
    private Long produtoId;
    private Long embalagemId;
    private Integer tipoMovimento;
    private BigDecimal quantidade;
    private String observacao;
    private Long vendaId;
    private Long recebimentoId;
    private Long consignacaoId;
    private String documentoReferencia;
    private Long itemPedidoId;
}
