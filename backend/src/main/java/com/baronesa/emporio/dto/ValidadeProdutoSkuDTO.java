package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidadeProdutoSkuDTO {
    private Long skuId;
    private String skuCodigo;
    private String skuDescricao;
    private BigDecimal estoqueAgregado;
    private BigDecimal somaLotes;
    private Boolean possuiDivergencia;
    private List<ValidadeProdutoLoteDTO> lotes;
}
