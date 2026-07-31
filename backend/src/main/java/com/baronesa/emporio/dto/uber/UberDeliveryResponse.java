package com.baronesa.emporio.dto.uber;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UberDeliveryResponse {
    private String id;
    @JsonProperty("quote_id")
    private String quoteId;
    private String status;
    private Boolean complete;
    private String kind;
    @JsonProperty("tracking_url")
    private String trackingUrl;
    @JsonProperty("fee")
    private Integer fee;
    private String currency;
    @JsonProperty("external_id")
    private String externalId;
    @JsonProperty("pickup_eta")
    private OffsetDateTime pickupEta;
    @JsonProperty("dropoff_eta")
    private OffsetDateTime dropoffEta;
}
