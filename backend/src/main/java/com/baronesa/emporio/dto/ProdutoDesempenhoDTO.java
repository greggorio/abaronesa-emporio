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
public class ProdutoDesempenhoDTO {
    private Long produtoId;
    private String nome;
    private Long quantidade; // Quantidade total de itens vendidos
    private BigDecimal valor; // Valor total em reais
}
