package com.baronesa.emporio.enums;

public enum TipoProduto {
    // Bebidas
    CERVEJA("Cerveja"),
    CHOPP("Chopp"),
    DRINK("Drink"),
    DOSE("Dose"),
    VINHO("Vinho"),
    REFRIGERANTE("Refrigerante"),
    SUCO("Suco"),
    AGUA("Água"),
    CAFE("Café"),

    // Comidas
    PRATO("Prato"),
    PETISCO("Petisco"),
    PORCAO("Porção"),
    LANCHE("Lanche"),
    SOBREMESA("Sobremesa"),

    // Outros
    COMBO("Combo"),
    CIGARRO("Cigarro"),
    OUTRO("Outro");

    private final String descricao;

    TipoProduto(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}