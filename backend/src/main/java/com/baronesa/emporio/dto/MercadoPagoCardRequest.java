package com.baronesa.emporio.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Data
public class MercadoPagoCardRequest {
    @NotNull
    private Double amount;

    @NotNull
    private String token; // Token do cartão gerado pelo MP

    @NotNull
    @Min(1)
    @Max(24)
    private Integer installments;

    @NotNull
    private String paymentMethodId; // visa, mastercard, etc.

    private String description;
    private String externalReference;

    @NotNull
    private CustomerData customer;

    @Data
    public static class CustomerData {
        @NotNull
        private String email;
        @NotNull
        private String name;
        private String cpf;
        private String phone;
    }
}