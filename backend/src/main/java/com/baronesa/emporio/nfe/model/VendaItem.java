package com.baronesa.emporio.nfe.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Item consolidado da venda. Inspirado na entidade do sistema de referência,
 * porém desacoplado do banco de dados para permitir montagem manual.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendaItem {

    private Long id;
    private String codigoProduto;
    private String descricaoProduto;
    private BigDecimal quantidade;
    private BigDecimal valorUnitario;
    private BigDecimal descontoPercentual;
    private BigDecimal descontoValor;
    private BigDecimal valorTotal;

    private String cfop;
    private String ncm;
    private String cst;

    public BigDecimal getValorTotalSeguro() {
        if (valorTotal != null) {
            return valorTotal;
        }

        BigDecimal qtd = quantidade != null ? quantidade : BigDecimal.ZERO;
        BigDecimal unitario = valorUnitario != null ? valorUnitario : BigDecimal.ZERO;
        BigDecimal bruto = qtd.multiply(unitario);

        BigDecimal desconto = BigDecimal.ZERO;
        if (descontoValor != null) {
            desconto = descontoValor;
        } else if (descontoPercentual != null) {
            desconto = bruto.multiply(descontoPercentual)
                    .divide(new BigDecimal("100"));
        }

        return bruto.subtract(desconto);
    }
}
