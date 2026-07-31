package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProducaoDTO {
    private Long produtoId;
    private Long skuId;
    private Integer quantidade;
    private MovimentoEstoqueDTO movimentoProduto;
    private List<MovimentoEstoqueDTO> movimentosInsumos;
}
