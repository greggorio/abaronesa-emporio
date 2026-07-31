package com.baronesa.emporio.enums;

/**
 * Enum que representa os tipos de documentos fiscais eletrônicos
 * suportados pelo sistema.
 */
public enum TipoDocumentoFiscal {

    NFE(55, "NF-e", "Nota Fiscal Eletrônica", "enviNFe", "NFe"),
    NFCE(65, "NFC-e", "Nota Fiscal de Consumidor Eletrônica", "enviNFe", "NFe");

    private final int modelo;
    private final String sigla;
    private final String descricao;
    private final String tagEnvio;
    private final String tagDocumento;

    TipoDocumentoFiscal(int modelo, String sigla, String descricao,
                        String tagEnvio, String tagDocumento) {
        this.modelo = modelo;
        this.sigla = sigla;
        this.descricao = descricao;
        this.tagEnvio = tagEnvio;
        this.tagDocumento = tagDocumento;
    }

    public int getModelo() {
        return modelo;
    }

    public String getModeloString() {
        return String.valueOf(modelo);
    }

    public String getSigla() {
        return sigla;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getTagEnvio() {
        return tagEnvio;
    }

    public String getTagDocumento() {
        return tagDocumento;
    }

    public boolean isNFe() {
        return this == NFE;
    }

    public boolean isNFCe() {
        return this == NFCE;
    }

    /**
     * Obtém o tipo de documento fiscal a partir do código do modelo
     * @param modelo código do modelo (55 ou 65)
     * @return TipoDocumentoFiscal correspondente
     * @throws IllegalArgumentException se o modelo não for válido
     */
    public static TipoDocumentoFiscal fromModelo(int modelo) {
        for (TipoDocumentoFiscal tipo : values()) {
            if (tipo.modelo == modelo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Modelo de documento fiscal inválido: " + modelo);
    }

    /**
     * Obtém o tipo de documento fiscal a partir da string do modelo
     * @param modelo string do modelo ("55" ou "65")
     * @return TipoDocumentoFiscal correspondente
     */
    public static TipoDocumentoFiscal fromModeloString(String modelo) {
        try {
            return fromModelo(Integer.parseInt(modelo));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Modelo de documento fiscal inválido: " + modelo);
        }
    }
}
