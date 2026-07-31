package com.baronesa.website.enums;

public enum GeneroMusical {
    ROCK("Rock"),
    METAL("Metal"),
    ACUSTICO("Acústico"),
    SERTANEJO("Sertanejo"),
    MPB("MPB"),
    BLUES("Blues"),
    JAZZ("Jazz"),
    OUTRO("Outro");

    private final String descricao;

    GeneroMusical(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
