package com.baronesa.emporio.service.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PixPaymentRequest {
    @NotNull
    @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    private String externalReference;
    private String description;

    @NotBlank
    @Email
    private String payerEmail;

    @NotBlank
    private String payerName;

    private String payerTaxId;
}
