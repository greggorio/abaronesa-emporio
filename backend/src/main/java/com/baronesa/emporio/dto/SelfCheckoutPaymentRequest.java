package com.baronesa.emporio.dto;

import lombok.Data;

@Data
public class SelfCheckoutPaymentRequest {
    /**
     * "convidado" | "mesa"
     */
    private String escopo;
    private Long sessaoMesaId;
    private Long sessaoConvidadoId;

    /**
     * "pix" | "card"
     */
    private String metodo;

    private String payerName;
    private String payerEmail;
    private String payerTaxId;

    // Campos para cartão
    private String cardToken;
    private Integer installments;
    private String paymentMethodId;
}
