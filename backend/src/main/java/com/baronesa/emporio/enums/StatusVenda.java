package com.baronesa.emporio.enums;

/**
 * Situação consolidada da venda utilizada para emissão fiscal.
 */
public enum StatusVenda {
    CONFIRMADA("Confirmada"),
    CANCELADA("Cancelada");

    private final String descricao;

    StatusVenda(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
