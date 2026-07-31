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
public class ValidadeProdutoLotesDTO {
    private Long produtoId;
    private String produtoNome;
    private Boolean controlaValidade;
    private Integer vidaUtilDias;
    private ValidadeProdutoResumoDTO resumo;
    private List<ValidadeProdutoSkuDTO> skus;
}
