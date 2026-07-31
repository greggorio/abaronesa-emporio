package com.baronesa.emporio.enums;

public enum UnidadeMedida {
    UN("Unidade", "UN"),
    L("Litro", "L"),
    ML("Mililitro", "ML"),
    KG("Quilograma", "KG"),
    G("Grama", "G"),
    DOSE("Dose", "DOSE"),
    GARRAFA("Garrafa", "GAR"),
    LATA("Lata", "LATA"),
    CX("Caixa", "CX"),
    PCT("Pacote", "PCT"),
    PORCAO("Porção", "PORÇÃO");

    private final String descricao;
    private final String sigla;

    UnidadeMedida(String descricao, String sigla) {
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