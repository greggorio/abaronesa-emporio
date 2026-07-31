package com.baronesa.emporio.enums;

/**
 * Enum que define a origem do cadastro do cliente
 * Utilizado para identificar o canal de aquisição
 */
public enum OrigemCadastro {

    LOJA_FISICA("Loja Física"),
    ECOMMERCE("E-commerce"),
    IMPORTACAO("Importação");

    private final String descricao;

    OrigemCadastro(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}