package com.baronesa.emporio.enums;

/**
 * Origem da venda usada pelo módulo fiscal.
 * Mantém os mesmos valores do sistema de referência para compatibilidade.
 */
public enum OrigemVenda {
    LOJA_FISICA("Loja Física"),
    LOJA_ONLINE("Loja Online");

    private final String descricao;

    OrigemVenda(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
