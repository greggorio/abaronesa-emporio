package com.baronesa.emporio.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MPPayment {

    private Long id;

    @JsonProperty("date_created")
    private OffsetDateTime dateCreated;

    @JsonProperty("date_approved")
    private OffsetDateTime dateApproved;

    @JsonProperty("date_last_updated")
    private OffsetDateTime dateLastUpdated;

    @JsonProperty("money_release_date")
    private OffsetDateTime moneyReleaseDate;

    @JsonProperty("operation_type")
    private String operationType;

    @JsonProperty("issuer_id")
    private String issuerId;

    @JsonProperty("payment_method_id")
    private String paymentMethodId;

    @JsonProperty("payment_type_id")
    private String paymentTypeId;

    private String status;

    @JsonProperty("status_detail")
    private String statusDetail;

    @JsonProperty("currency_id")
    private String currencyId;

    private String description;

    @JsonProperty("live_mode")
    private Boolean liveMode;

    @JsonProperty("sponsor_id")
    private Long sponsorId;

    @JsonProperty("authorization_code")
    private String authorizationCode;

    @JsonProperty("money_release_schema")
    private String moneyReleaseSchema;

    @JsonProperty("counter_currency")
    private String counterCurrency;

    @JsonProperty("shipping_amount")
    private BigDecimal shippingAmount;

    @JsonProperty("collector_id")
    private Long collectorId;

    @JsonProperty("external_reference")
    private String externalReference;

    @JsonProperty("processing_mode")
    private String processingMode;

    @JsonProperty("merchant_account_id")
    private String merchantAccountId;

    private MPPayer payer;

    @JsonProperty("additional_info")
    private MPAdditionalInfo additionalInfo;

    @JsonProperty("order")
    private MPOrder order;

    @JsonProperty("transaction_amount")
    private BigDecimal transactionAmount;

    @JsonProperty("transaction_amount_refunded")
    private BigDecimal transactionAmountRefunded;

    @JsonProperty("coupon_amount")
    private BigDecimal couponAmount;

    @JsonProperty("campaign_id")
    private Long campaignId;

    @JsonProperty("coupon_code")
    private String couponCode;

    @JsonProperty("transaction_details")
    private MPTransactionDetails transactionDetails;

    @JsonProperty("fee_details")
    private List<MPFeeDetail> feeDetails;

    @JsonProperty("captured")
    private Boolean captured;

    @JsonProperty("binary_mode")
    private Boolean binaryMode;

    @JsonProperty("call_for_authorize_id")
    private String callForAuthorizeId;

    @JsonProperty("statement_descriptor")
    private String statementDescriptor;

    @JsonProperty("card")
    private MPCard card;

    @JsonProperty("notification_url")
    private String notificationUrl;

    @JsonProperty("refunds")
    private List<MPRefund> refunds;

    private Map<String, Object> metadata;
}