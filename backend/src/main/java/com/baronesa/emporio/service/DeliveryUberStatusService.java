package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.repository.DeliveryOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryUberStatusService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final SseEventsService eventsService;

    @Transactional
    public void updateStatus(String deliveryId, String status, String externalId, JsonNode payload) {
        log.info("Uber status recebido deliveryId={} status={} externalId={}", deliveryId, status, externalId);
        DeliveryOrder order = resolveOrder(externalId);
        if (order == null) {
            log.warn("Uber status sem pedido associado externalId={}", externalId);
            return;
        }

        if (deliveryId != null && !deliveryId.isBlank()) {
            order.setUberDeliveryId(deliveryId);
        }
        if (status != null && !status.isBlank()) {
            order.setUberStatus(status);
        }

        if (payload != null) {
            JsonNode data = payload.path("data");
            String trackingUrl = data.path("tracking_url").asText(null);
            if (trackingUrl == null || trackingUrl.isBlank()) {
                trackingUrl = payload.path("tracking_url").asText(null);
            }
            if (trackingUrl != null && !trackingUrl.isBlank()) {
                order.setUberTrackingUrl(trackingUrl);
            }

            order.setUberDropoffEta(parseDate(data.path("dropoff_eta").asText(null)));
            order.setUberPickupEta(parseDate(data.path("pickup_eta").asText(null)));

            JsonNode dropoff = data.path("dropoff");
            if (dropoff != null && dropoff.has("address")) {
                String address = dropoff.path("address").asText(null);
                if (address != null && !address.isBlank()) {
                    order.setDropoffAddress(address);
                }
            }
            JsonNode pickup = data.path("pickup");
            if (pickup != null && pickup.has("address")) {
                String address = pickup.path("address").asText(null);
                if (address != null && !address.isBlank()) {
                    order.setUberPickupAddress(address);
                }
            }
        }
        deliveryOrderRepository.save(order);

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("deliveryId", deliveryId);
        eventPayload.put("externalId", externalId);
        eventPayload.put("status", status);
        eventsService.publishKds("kds.delivery_status", eventPayload);
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Falha ao converter data do Uber value={}", value);
            return null;
        }
    }

    private DeliveryOrder resolveOrder(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return null;
        }
        if (externalId.startsWith("delivery-")) {
            try {
                Long id = Long.valueOf(externalId.replace("delivery-", ""));
                Optional<DeliveryOrder> byId = deliveryOrderRepository.findById(id);
                if (byId.isPresent()) {
                    return byId.get();
                }
            } catch (NumberFormatException ignored) {}
        }
        return deliveryOrderRepository.findByExternalReference(externalId).orElse(null);
    }
}
