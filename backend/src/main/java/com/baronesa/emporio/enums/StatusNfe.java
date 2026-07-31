package com.baronesa.emporio.enums;

public enum StatusNfe {
    NAO_EMITIDA("Não Emitida"),
    PROCESSANDO("Processando"),
    AUTORIZADA("Autorizada"),
    REJEITADA("Rejeitada"),
    CANCELADA("Cancelada");

    private final String descricao;

    StatusNfe(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
