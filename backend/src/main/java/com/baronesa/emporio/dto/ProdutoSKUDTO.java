package com.baronesa.emporio.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoSKUDTO {
    private Long id;
    private Long produtoId;
    private Long embalagemId;
    private String sku;
    private String variacao;
    private String codigoBarras;
    private BigDecimal precoCusto;
    private BigDecimal precoVenda;
    private BigDecimal estoqueAtual;
    private Integer estoqueMinimo; // somente vendáveis (insumo=false)
    private Boolean ativo;
    private Boolean principal;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}
