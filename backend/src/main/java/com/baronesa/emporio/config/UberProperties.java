package com.baronesa.emporio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "uber")
public class UberProperties {
    private String clientId;
    private String clientSecret;
    private String customerId;
    private String scope = "delivery";
    private String tokenUrl = "https://login.uber.com/oauth/v2/token";
    private String apiBaseUrl = "https://api.uber.com";
    private String accessToken;
    private String pickupReadyPath = "/v1/customers/%s/deliveries/%s/pickup/ready";

    private String pickupAddress;
    private String pickupName;
    private String pickupPhone;
    private String pickupNotes;
}
