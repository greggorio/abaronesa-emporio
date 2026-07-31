package com.baronesa.emporio.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EstornarMovimentoEstoqueResponse {
    private boolean sucesso;
    private String mensagem;
    private Long estornoId;
    private Long movimentoOrigemId;
    private boolean jaExistia;
}