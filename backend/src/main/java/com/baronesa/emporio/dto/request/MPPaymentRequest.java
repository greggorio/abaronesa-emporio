package com.baronesa.emporio.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.baronesa.emporio.entity.MPAdditionalInfo;
import com.baronesa.emporio.entity.MPPayer;
import com.baronesa.emporio.entity.MPPointOfInteraction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPPaymentRequest {

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    private String token;

    private String description;

    private Integer installments;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("issuer_id")
    private String issuerId;

    private MPPayer payer;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("notification_url")
    private String notificationUrl;

    @JsonProperty("additional_info")
    private MPAdditionalInfo additionalInfo;

    @JsonProperty("binary_mode")
    private Boolean binaryMode;

    @JsonProperty("capture")
    private Boolean capture;

    @JsonProperty("coupon_amount")
    private BigDecimal couponAmount;

    @JsonProperty("campaign_id")
    private Long campaignId;

    @JsonProperty("coupon_code")
    private String couponCode;

    @JsonProperty("differential_pricing_id")
    private Long differentialPricingId;

    @JsonProperty("application_fee")
    private BigDecimal applicationFee;

    @JsonProperty("merchant_account_id")
    private String merchantAccountId;

    @JsonProperty("processing_mode")
    private String processingMode;

    @JsonProperty("point_of_interaction")
    private MPPointOfInteraction pointOfInteraction;

    @JsonProperty("date_of_expiration")
    private LocalDateTime dateOfExpiration;

    @JsonProperty("statement_descriptor")
    private String statementDescriptor;

    private Map<String, Object> metadata;
}