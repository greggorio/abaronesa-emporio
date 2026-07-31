package com.baronesa.website.dto.erp;

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
