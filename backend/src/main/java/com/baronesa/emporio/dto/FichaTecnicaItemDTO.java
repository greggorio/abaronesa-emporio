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
public class FichaTecnicaItemDTO {
    private Long id;
    private Long insumoSkuId;
    private String insumoSkuCodigo;
    private Long insumoProdutoId;
    private String insumoProdutoNome;
    private String insumoVariacao;
    private Long embalagemId;
    private String embalagemNome;
    private Integer fatorBase;
    private BigDecimal quantidade;
    private BigDecimal custoUnitario;
    private BigDecimal custoTotal;
    private Integer ordem;
    private String observacao;
    private Integer estoqueDisponivel; // Para exibir disponibilidade
}
