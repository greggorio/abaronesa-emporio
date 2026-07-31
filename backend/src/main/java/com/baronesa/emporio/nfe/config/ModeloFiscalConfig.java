package com.baronesa.emporio.nfe.config;

/**
 * Modelos fiscais suportados pelo emissor.
 * 55 = NFe (modelo completo, gera DANFE A4)
 * 65 = NFCe (cupom fiscal eletrônico, gera DANFCE 80mm)
 */
public enum ModeloFiscalConfig {
    NFE(55, "NFe"),
    NFCE(65, "NFCe");

    private final int codigo;
    private final String sigla;

    ModeloFiscalConfig(int codigo, String sigla) {
        this.codigo = codigo;
        this.sigla = sigla;
    }

    public int getCodigo() {
        return codigo;
    }

    public String getSigla() {
        return sigla;
    }

    public static ModeloFiscalConfig porCodigo(int codigo) {
        for (ModeloFiscalConfig modelo : values()) {
            if (modelo.codigo == codigo) {
                return modelo;
            }
        }
        throw new IllegalArgumentException("Modelo fiscal não suportado: " + codigo);
    }
}
