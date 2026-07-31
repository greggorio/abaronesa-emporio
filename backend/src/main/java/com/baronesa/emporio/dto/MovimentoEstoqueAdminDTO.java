package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MovimentoEstoqueAdminDTO {
    private Long id;
    private Long itemPedidoId;
    private Integer tipoMovimento;
    private BigDecimal quantidade;
    private Long skuId;
    private Long produtoId;
    private String documentoReferencia;
    private String observacao;
    private Long usuarioId;
    private LocalDateTime dataMovimento;
}