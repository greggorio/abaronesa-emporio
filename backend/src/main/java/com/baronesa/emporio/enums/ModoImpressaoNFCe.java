package com.baronesa.emporio.enums;

/**
 * Modos de impressão disponíveis para DANFCE
 */
public enum ModoImpressaoNFCe {

    TERMICA("Impressora Térmica", new int[]{58, 80}),
    A4("Impressora A4/Laser", new int[]{210});

    private final String descricao;
    private final int[] largurasSuportadas;

    ModoImpressaoNFCe(String descricao, int[] largurasSuportadas) {
        this.descricao = descricao;
        this.largurasSuportadas = largurasSuportadas;
    }

    public String getDescricao() {
        return descricao;
    }

    public int[] getLargurasSuportadas() {
        return largurasSuportadas;
    }

    public boolean suportaLargura(int largura) {
        for (int l : largurasSuportadas) {
            if (l == largura) return true;
        }
        return false;
    }

    public static ModoImpressaoNFCe fromString(String modo) {
        try {
            return valueOf(modo.toUpperCase());
        } catch (Exception e) {
            return TERMICA; // Default
        }
    }
}
