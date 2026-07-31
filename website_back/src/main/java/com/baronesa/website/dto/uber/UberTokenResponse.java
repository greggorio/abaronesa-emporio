package com.baronesa.website.dto.uber;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UberTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("token_type")
    private String tokenType;
    @JsonProperty("expires_in")
    private Long expiresIn;
    private String scope;
}
