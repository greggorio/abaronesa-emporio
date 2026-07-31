package com.baronesa.emporio.dto.mercadopago;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MercadoPagoPaymentRequest {

    @NotNull(message = "Valor da transação é obrigatório")
    @DecimalMin(value = "0.01", inclusive = true, message = "Valor deve ser maior que zero")
    private BigDecimal amount;

    @NotBlank(message = "Método de pagamento é obrigatório")
    private String paymentMethodId;

    @Positive(message = "Número de parcelas deve ser positivo")
    private Integer installments = 1;

    private String token;
    private String description;
    private String externalReference;

    @Valid
    @NotNull(message = "Dados do cliente são obrigatórios")
    private MercadoPagoCustomer customer;
}
