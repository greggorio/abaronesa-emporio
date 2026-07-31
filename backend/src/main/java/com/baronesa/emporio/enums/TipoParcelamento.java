package com.baronesa.emporio.enums;

/**
 * Identifica quem administra o parcelamento em pagamentos com cartão.
 */
public enum TipoParcelamento {
    LOJA("Parcelado pela Loja"),
    ADMINISTRADORA("Parcelado pela Administradora");

    private final String descricao;

    TipoParcelamento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
