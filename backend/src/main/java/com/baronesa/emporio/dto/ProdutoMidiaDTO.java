package com.baronesa.emporio.dto;

import com.baronesa.emporio.enums.TipoMidia;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoMidiaDTO {
    private Long id;
    private Long produtoId;
    private TipoMidia tipo;
    private String url;
    private String titulo;
    private String descricao;
    private Integer ordem;
    private Boolean principal;
    private Boolean ativo;
    private LocalDateTime criadoEm;
}