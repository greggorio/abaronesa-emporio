package com.baronesa.emporio.controller.integration;

import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integration/configs")
@RequiredArgsConstructor
public class IntegrationConfigController {

    private final ConfigManager configManager;

    @GetMapping("/uber")
    public ResponseEntity<UberConfigResponse> getUberConfig(
            @RequestHeader(value = "X-ERP-KEY", required = false) String apiKey
    ) {
        String expectedKey = configManager.getConfig("espresso.sync.api-key", "");
        if (expectedKey == null || expectedKey.isBlank() || !expectedKey.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new UberConfigResponse(
                configManager.getConfig("uber_client_id", null),
                configManager.getConfig("uber_client_secret", null),
                configManager.getConfig("uber_customer_id", null),
                configManager.getConfig("uber_scope", "delivery"),
                configManager.getConfig("uber_access_token", null),
                configManager.getConfig("uber_token_url", "https://login.uber.com/oauth/v2/token"),
                configManager.getConfig("uber_api_base_url", "https://api.uber.com"),
                configManager.getConfig("uber_pickup_address", null),
                configManager.getConfig("uber_pickup_name", null),
                configManager.getConfig("uber_pickup_phone", null),
                configManager.getConfig("uber_pickup_notes", null),
                configManager.getConfig("uber_pickup_ready_path", "/v1/customers/%s/deliveries/%s")
        ));
    }

    public record UberConfigResponse(
            String clientId,
            String clientSecret,
            String customerId,
            String scope,
            String accessToken,
            String tokenUrl,
            String apiBaseUrl,
            String pickupAddress,
            String pickupName,
            String pickupPhone,
            String pickupNotes,
            String pickupReadyPath
    ) {}
}
