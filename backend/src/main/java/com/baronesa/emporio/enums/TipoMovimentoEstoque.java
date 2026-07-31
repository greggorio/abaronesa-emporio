package com.baronesa.emporio.enums;

public enum TipoMovimentoEstoque {
    INICIAR_ESTOQUE(0, "Estoque Inicial"),
    ENTRADA(1, "Entrada de Mercadoria"),
    VENDA(2, "Venda"),
    AJUSTE(3, "Ajuste de Estoque"),
    INVENTARIO(4, "Inventário"),
    ESTORNO_ENTRADA(6, "Estorno de Entrada"),
    ESTORNO_VENDA(7, "Venda Cancelada"),
    CONSIGNACAO(8, "Consignação"),
    ESTORNO_CONSIGNACAO(9, "Estorno de Consignação"),
    ZERAR_ESTOQUE(10, "Estoque Zerado"),
    CONSUMO_PRODUCAO(11, "Consumo Produção"),
    PRODUCAO(12, "Produção");

    private final int codigo;
    private final String descricao;

    TipoMovimentoEstoque(int codigo, String descricao) {
        this.codigo = codigo;
        this.descricao = descricao;
    }

    public int getCodigo() { return codigo; }
    public String getDescricao() { return descricao; }

    public static TipoMovimentoEstoque fromCodigo(int codigo) {
        for (TipoMovimentoEstoque tipo : values()) {
            if (tipo.codigo == codigo) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Código inválido: " + codigo);
    }
}
