package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EstornarMovimentoEstoqueRequest {
    private String observacao;
    private String motivo;
    private Long usuarioId;
}