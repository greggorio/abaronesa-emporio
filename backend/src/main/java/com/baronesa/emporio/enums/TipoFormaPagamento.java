package com.baronesa.emporio.enums;

public enum TipoFormaPagamento {
    DINHEIRO("Dinheiro", "01"),
    VOUCHER("Voucher", "01"),
    PIX("Pix", "17"),
    CARTAO_CREDITO("Cartão de Crédito", "03"),
    CARTAO_DEBITO("Cartão de Débito", "04"),
    TRANSFERENCIA("Transferência", "18"),
    OUTROS("Outros", "99");

    private final String descricao;
    private final String codigo;

    TipoFormaPagamento(String descricao, String codigo) {
        this.descricao = descricao;
        this.codigo = codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCodigo() {
        return codigo;
    }

    public static String codigoDe(TipoFormaPagamento tipo) {
        return tipo != null ? tipo.codigo : OUTROS.codigo;
    }

    public static TipoFormaPagamento fromCodigo(String codigo) {
        if (codigo == null) return OUTROS;
        for (TipoFormaPagamento t : values()) {
            if (t.codigo.equals(codigo)) return t;
        }
        return OUTROS;
    }
}
