package com.baronesa.emporio.enums;

public enum TipoMidia {
    IMAGEM("Imagem"),
    VIDEO("Vídeo"),
    DOCUMENTO("Documento");

    private final String descricao;

    TipoMidia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}