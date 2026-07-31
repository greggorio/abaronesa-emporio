package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardapioSkuDTO {
    private Long id;
    private String variacao;
    private BigDecimal preco;
    private BigDecimal precoPromocional;
    private String origemDesconto;
    private Boolean principal;
    private Boolean ativo;
}
