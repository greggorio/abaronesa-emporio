package com.baronesa.emporio.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UberWebhookService {

    private final DeliveryUberStatusService deliveryUberStatusService;

    public void handleDeliveryEvent(JsonNode payload) {
        String kind = payload.path("kind").asText("unknown");
        String status = payload.path("status").asText(null);
        String deliveryId = payload.path("delivery_id").asText(null);
        String externalId = payload.path("data").path("external_id").asText(null);
        if (externalId == null || externalId.isBlank()) {
            externalId = payload.path("external_id").asText(null);
        }

        log.info("Webhook Uber recebido kind={} status={} deliveryId={} externalId={}",
                kind, status, deliveryId, externalId);

        deliveryUberStatusService.updateStatus(deliveryId, status, externalId, payload);
    }
}
