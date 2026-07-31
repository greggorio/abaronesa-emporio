package com.baronesa.emporio.nfe.model;

import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoParcelamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pagamento da venda, compatível com o builder de pagamento da NFC-e.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendaPagamento {

    private Long id;
    private TipoFormaPagamento tipoPagamento;
    private BigDecimal valor;

    // Dados de cartão
    private String bandeiraCartao;
    private String cnpjCredenciadora;
    private String codigoAutorizacao;
    private String nsu;

    private Integer numeroParcelas;
    private TipoParcelamento tipoParcelamento;

    // PIX
    private String chavePix;
    private String codigoTransacaoPix;

    // Vales ou vouchers
    private String numeroVale;

    private LocalDateTime dataPagamento;

    public BigDecimal getValorSeguro() {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
