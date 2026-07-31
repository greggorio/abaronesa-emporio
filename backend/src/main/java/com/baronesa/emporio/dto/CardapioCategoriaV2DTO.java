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
public class CardapioCategoriaV2DTO {
    private Long id;
    private String nome;
    private String icone;
    private String cover;
    private Integer ordem;
    private List<CardapioProdutoV2DTO> produtos;
}

