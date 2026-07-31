package com.baronesa.emporio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPCreatePaymentDTO {

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    private String description;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    private String token;

    private Integer installments;

    private MPPayerDTO payer;

    private Boolean capture;

    @JsonProperty("binary_mode")
    private Boolean binaryMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MPPayerDTO {
        private String email;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("last_name")
        private String lastName;

        private MPIdentificationDTO identification;

        @JsonProperty("entity_type")
        private String entityType;

        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MPIdentificationDTO {
        private String type;
        private String number;
    }
}