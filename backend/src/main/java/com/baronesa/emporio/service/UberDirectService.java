package com.baronesa.emporio.service;

import com.baronesa.emporio.config.UberProperties;
import com.baronesa.emporio.dto.uber.UberDeliveryRequest;
import com.baronesa.emporio.dto.uber.UberDeliveryResponse;
import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.entity.DeliveryOrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UberDirectService {

    private final UberDirectClient uberDirectClient;
    private final UberProperties uberProperties;

    public UberDeliveryResponse createDelivery(DeliveryOrder order, List<DeliveryOrderItem> items) {
        if (order == null || items == null || items.isEmpty()) return null;
        if (uberProperties.getCustomerId() == null || uberProperties.getCustomerId().isBlank()) {
            log.warn("[Uber Direct] Config ausente; ignorando orderId={}", order.getId());
            return null;
        }
        String phone = normalizePhone(order.getCustomerPhone());
        if (phone == null) {
            log.warn("[Uber Direct] Telefone invalido; ignorando orderId={}", order.getId());
            return null;
        }
        if (order.getDropoffAddress() == null || order.getDropoffAddress().isBlank()) {
            log.warn("[Uber Direct] Endereco vazio; ignorando orderId={}", order.getId());
            return null;
        }

        UberDeliveryRequest request = UberDeliveryRequest.builder()
                .pickupAddress(uberProperties.getPickupAddress())
                .pickupName(uberProperties.getPickupName())
                .pickupPhoneNumber(uberProperties.getPickupPhone())
                .pickupNotes(uberProperties.getPickupNotes())
                .dropoffAddress(order.getDropoffAddress())
                .dropoffName(order.getCustomerName())
                .dropoffPhoneNumber(phone)
                .dropoffNotes(order.getDropoffNotes())
                .externalId("delivery-" + order.getId())
                .deliverableAction("deliverable_action_meet_at_door")
                .manifestItems(items.stream()
                        .map(item -> UberDeliveryRequest.ManifestItem.builder()
                                .name(item.getNome())
                                .quantity(item.getQuantidade())
                                .size("small")
                                .mustBeUpright(false)
                                .build())
                        .toList())
                .build();

        try {
            UberDeliveryResponse response = uberDirectClient.createDelivery(request);
            log.info("[Uber Direct] delivery created orderId={} deliveryId={} status={}",
                    order.getId(), response != null ? response.getId() : null, response != null ? response.getStatus() : null);
            return response;
        } catch (Exception e) {
            log.warn("[Uber Direct] Falha ao criar delivery orderId={}", order.getId(), e);
            return null;
        }
    }

    private String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 10 || digits.length() == 11) {
            return "+55" + digits;
        }
        if (digits.length() >= 12 && raw.startsWith("+")) {
            return raw;
        }
        return null;
    }
}
