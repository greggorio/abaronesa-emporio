package com.baronesa.emporio.service.payment.api;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CardPaymentRequest {
    private BigDecimal amount;
    private String externalReference;
    private String description;
    private String token;
    private Integer installments = 1;
    private String paymentMethodId;
    private String payerEmail;
    private String payerName;
    private String payerTaxId;
    private Map<String, Object> metadata;
}
