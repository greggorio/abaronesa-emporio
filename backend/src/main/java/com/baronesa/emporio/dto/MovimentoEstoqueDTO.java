package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MovimentoEstoqueDTO {
    private Long id;
    private LocalDateTime dataMovimento;
    private String produtoNome;
    private String skuCodigo;
    private String skuDescricao;
    private String tipoMovimento;
    private BigDecimal quantidade;
    private BigDecimal estoqueAnterior;
    private BigDecimal estoqueAtual;
    private String observacao;
    private String documentoReferencia;
    private Long itemPedidoId;
}
