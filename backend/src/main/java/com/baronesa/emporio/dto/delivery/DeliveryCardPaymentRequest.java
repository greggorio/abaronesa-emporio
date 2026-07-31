package com.baronesa.emporio.dto.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

@Data
public class DeliveryCardPaymentRequest {
    @NotBlank
    private String token;

    @NotBlank
    private String paymentMethodId; // visa, mastercard, etc.

    @NotNull
    @Min(1)
    @Max(24)
    private Integer installments = 1;

    private String description;
}
