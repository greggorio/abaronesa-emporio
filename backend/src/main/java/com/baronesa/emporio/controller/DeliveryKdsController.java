package com.baronesa.emporio.controller;

import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.entity.DeliveryOrderItem;
import com.baronesa.emporio.enums.DeliveryOrderItemStatus;
import com.baronesa.emporio.enums.DeliveryOrderStatus;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.repository.DeliveryOrderItemRepository;
import com.baronesa.emporio.repository.DeliveryOrderRepository;
import com.baronesa.emporio.service.DeliveryKdsService;
import com.baronesa.emporio.service.UberDirectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/delivery/kds")
@RequiredArgsConstructor
@Slf4j
public class DeliveryKdsController {

    private final DeliveryKdsService kdsService;
    private final DeliveryOrderItemRepository itemRepository;
    private final DeliveryOrderRepository orderRepository;
    private final SseEventsService eventsService;
    private final UberDirectService uberDirectService;

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> queue() {
        Map<String, Object> body = new HashMap<>();
        body.put("tickets", kdsService.getQueue());
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/tickets/{deliveryItemId}")
    @Transactional
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long deliveryItemId,
            @RequestBody DeliveryKdsStatusRequest request
    ) {
        DeliveryOrderItem item = itemRepository.findById(deliveryItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item de delivery não encontrado"));

        if (Boolean.TRUE.equals(item.getKdsArchived())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item arquivado no KDS");
        }

        DeliveryOrderItemStatus status = DeliveryOrderItemStatus.valueOf(request.status().toUpperCase());

        DeliveryOrder order = item.getDeliveryOrder();
        String uberStatus = order != null ? order.getUberStatus() : null;
        boolean uberCanceled = uberStatus != null && (uberStatus.equalsIgnoreCase("canceled") || uberStatus.equalsIgnoreCase("failed"));
        if (uberCanceled && status == DeliveryOrderItemStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Entrega cancelada no parceiro; use arquivar");
        }

        item.setStatus(status);
        item.setUpdatedAt(LocalDateTime.now());
        itemRepository.save(item);

        order = item.getDeliveryOrder();
        if (order != null) {
            order = orderRepository.findById(order.getId()).orElse(order);
            List<DeliveryOrderItem> items = itemRepository.findByDeliveryOrderId(order.getId());
            boolean allDelivered = items.stream().allMatch(i -> i.getStatus() == DeliveryOrderItemStatus.DELIVERED);
            boolean allReady = items.stream().allMatch(i ->
                    i.getStatus() == DeliveryOrderItemStatus.READY
                            || i.getStatus() == DeliveryOrderItemStatus.DELIVERED);
            boolean anyPreparing = items.stream().anyMatch(i ->
                    i.getStatus() == DeliveryOrderItemStatus.PREPARING || i.getStatus() == DeliveryOrderItemStatus.ACCEPTED);
            boolean allCanceled = items.stream().allMatch(i -> i.getStatus() == DeliveryOrderItemStatus.CANCELED);

            if (allDelivered) {
                order.setStatus(DeliveryOrderStatus.DELIVERED);
            } else if (allReady) {
                order.setStatus(DeliveryOrderStatus.READY);
            } else if (anyPreparing) {
                order.setStatus(DeliveryOrderStatus.PREPARING);
            } else if (allCanceled) {
                order.setStatus(DeliveryOrderStatus.CANCELED);
            }
            orderRepository.save(order);

            boolean isPaid = order.getPaidAt() != null
                    || order.getStatus() == DeliveryOrderStatus.PAID
                    || "approved".equalsIgnoreCase(order.getMpStatus());

            if (allReady && isPaid && order.getUberDeliveryId() == null) {
                log.info("Delivery pronto orderId={} items={} -> bakery", order.getId(), items.size());
                var response = uberDirectService.createDelivery(order, items);
                if (response != null && response.getId() != null) {
                    order.setUberDeliveryId(response.getId());
                    order.setUberTrackingUrl(response.getTrackingUrl());
                    order.setUberStatus(response.getStatus());
                    order.setUberDropoffEta(toLocalDateTime(response.getDropoffEta()));
                    order.setUberPickupEta(toLocalDateTime(response.getPickupEta()));
                    order.setStatus(DeliveryOrderStatus.DISPATCHED);
                    orderRepository.save(order);

                    Map<String, Object> deliveryPayload = new HashMap<>();
                    deliveryPayload.put("deliveryId", response.getId());
                    deliveryPayload.put("externalId", order.getExternalReference());
                    deliveryPayload.put("status", response.getStatus() != null ? response.getStatus() : "pending");
                    deliveryPayload.put("deliveryItemId", item.getId());
                    eventsService.publishKds("kds.delivery_status", deliveryPayload);
                }
            }
        }

        Map<String, Object> payload = Map.of(
                "deliveryItemId", item.getId(),
                "status", item.getStatus().name().toLowerCase()
        );
        eventsService.publishKds("kds.delivery_status_changed", payload);

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/tickets/{deliveryItemId}/archive")
    @Transactional
    public ResponseEntity<Void> archive(
            @PathVariable Long deliveryItemId
    ) {
        DeliveryOrderItem item = itemRepository.findById(deliveryItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item de delivery não encontrado"));

        if (Boolean.TRUE.equals(item.getKdsArchived())) {
            return ResponseEntity.ok().build();
        }

        item.setKdsArchived(true);
        item.setArchivedAt(LocalDateTime.now());
        itemRepository.save(item);

        Map<String, Object> payload = Map.of(
                "deliveryItemId", item.getId()
        );
        eventsService.publishKds("kds.delivery_archived", payload);

        return ResponseEntity.ok().build();
    }

    public record DeliveryKdsStatusRequest(String status) {}

    private LocalDateTime toLocalDateTime(OffsetDateTime value) {
        if (value == null) return null;
        try {
            return value.toLocalDateTime();
        } catch (Exception e) {
            log.warn("Erro ao converter data do Uber: {}", e.getMessage());
            return null;
        }
    }
}
