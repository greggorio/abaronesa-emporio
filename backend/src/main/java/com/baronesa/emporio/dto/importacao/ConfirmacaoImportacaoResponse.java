package com.baronesa.emporio.dto.importacao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmacaoImportacaoResponse {
    private Integer total;
    private Integer processados;
    private Integer criadas;
    private Integer ignoradasDuplicadas; // duplicados já existentes
    private Integer erros;
    private List<CategoriaDetectada> categoriasCriadas;
    private List<AmostraErro> amostrasErro;
}