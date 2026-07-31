package com.baronesa.emporio.enums;

public enum UnidadeBase {
    UNIDADE("Unidade", "UN"),
    MILILITRO("Mililitro", "ML"),
    GRAMA("Grama", "G");

    private final String descricao;
    private final String sigla;

    UnidadeBase(String descricao, String sigla) {
        this.descricao = descricao;
        this.sigla = sigla;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getSigla() {
        return sigla;
    }
}

