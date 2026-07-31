package com.baronesa.emporio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TipoPromocao {
    PERCENTUAL("Percentual"),
    VALOR("Valor fixo");

    private final String descricao;
}
