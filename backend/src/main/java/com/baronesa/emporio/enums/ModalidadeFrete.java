package com.baronesa.emporio.enums;

/**
 * Modalidade de frete associada à nota (espelhada do sistema de referência).
 */
public enum ModalidadeFrete {
    EMITENTE("0"),
    DESTINATARIO("1"),
    TERCEIROS("2"),
    PROPRIO_REMETENTE("3"),
    PROPRIO_DESTINATARIO("4"),
    SEM_FRETE("9");

    private final String codigoNFe;

    ModalidadeFrete(String codigoNFe) {
        this.codigoNFe = codigoNFe;
    }

    public String getCodigoNFe() {
        return codigoNFe;
    }

    public static ModalidadeFrete fromLegacy(String valor) {
        if (valor == null) {
            return SEM_FRETE;
        }
        String normalizado = valor.trim().toUpperCase();
        return switch (normalizado) {
            case "0", "EMITENTE", "REMETENTE", "CIF" -> EMITENTE;
            case "1", "DESTINATARIO", "DESTINATÁRIO", "FOB" -> DESTINATARIO;
            case "2", "TERCEIROS" -> TERCEIROS;
            case "3", "PROPRIO_REMETENTE", "PROPRIA_REMETENTE" -> PROPRIO_REMETENTE;
            case "4", "PROPRIO_DESTINATARIO", "PROPRIA_DESTINATARIO" -> PROPRIO_DESTINATARIO;
            default -> SEM_FRETE;
        };
    }
}
