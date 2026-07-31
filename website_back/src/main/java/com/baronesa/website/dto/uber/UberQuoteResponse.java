package com.baronesa.website.dto.uber;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class UberQuoteResponse {
    private String id;
    @JsonProperty("fee")
    private Integer fee;
    private String currency;
    @JsonProperty("expires_at")
    private OffsetDateTime expiresAt;
}
