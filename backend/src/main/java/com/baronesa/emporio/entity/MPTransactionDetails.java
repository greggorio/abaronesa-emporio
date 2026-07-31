package com.baronesa.emporio.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPTransactionDetails {
    @JsonProperty("net_received_amount")
    private BigDecimal netReceivedAmount;

    @JsonProperty("total_paid_amount")
    private BigDecimal totalPaidAmount;

    @JsonProperty("overpaid_amount")
    private BigDecimal overpaidAmount;

    @JsonProperty("external_resource_url")
    private String externalResourceUrl;

    @JsonProperty("installment_amount")
    private BigDecimal installmentAmount;

    @JsonProperty("financial_institution")
    private String financialInstitution;

    @JsonProperty("payment_method_reference_id")
    private String paymentMethodReferenceId;
}