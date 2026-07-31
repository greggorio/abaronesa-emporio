package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmbalagemDTO {
    private Long id;
    private Long produtoId;
    private String nome;
    private Integer fatorBase;
    private String codigoBarras;
    private Boolean permiteVenda;
    private Boolean principal;
    private Boolean ativo;
}

