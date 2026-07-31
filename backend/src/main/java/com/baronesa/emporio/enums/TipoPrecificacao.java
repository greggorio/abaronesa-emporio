package com.baronesa.emporio.enums;

public enum TipoPrecificacao {
    SIMPLES("Produto Único (sem variações)"),
    UNIFICADA("Múltiplas Variações - Preço Único"),
    INDIVIDUAL("Múltiplas Variações - Preços Individuais");

    private final String descricao;

    TipoPrecificacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}