package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoHarmonizacaoDTO {
    private Long id;
    private Long produtoPrincipalId;
    private Long produtoHarmonizadoId;
    private Long skuHarmonizadoId;
    private String tipo;
    private String descricao;
    private Integer ordem;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private CardapioProdutoDTO produtoHarmonizado; // Details of the harmonized product
}
