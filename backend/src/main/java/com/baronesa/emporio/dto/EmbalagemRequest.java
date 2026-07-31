package com.baronesa.emporio.dto;

import lombok.Data;

@Data
public class EmbalagemRequest {
    private Long produtoId;
    private String nome;
    private Integer fatorBase;
    private String codigoBarras;
    private Boolean permiteVenda;
    private Boolean principal;
    private Boolean ativo;
}

