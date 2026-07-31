package com.baronesa.emporio.dto.importacao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaDetectada {
    private String nome;
    private Boolean existe;
    private Long categoriaId;
    private Integer contagem;
}