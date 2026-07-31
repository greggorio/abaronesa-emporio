package com.baronesa.emporio.dto.importacao;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmostraErro {
    private Integer linha;
    private String mensagem;
}