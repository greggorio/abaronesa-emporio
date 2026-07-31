package com.baronesa.website.service.delivery;

import com.baronesa.website.dto.delivery.CreateDeliveryOrderRequest;
import com.baronesa.website.dto.delivery.CreateDeliveryPaymentIntentRequest;
import com.baronesa.website.dto.delivery.CreateDeliveryOrderResponse;
import com.baronesa.website.dto.delivery.DeliveryQueueResponse;
import com.baronesa.website.dto.uber.UberDeliveryRequest;
import com.baronesa.website.dto.uber.UberDeliveryResponse;
import com.baronesa.website.entity.delivery.DeliveryOrder;
import com.baronesa.website.entity.delivery.DeliveryOrderItem;
import com.baronesa.website.entity.delivery.DeliveryPayment;
import com.baronesa.website.enums.delivery.DeliveryItemStatus;
import com.baronesa.website.enums.delivery.DeliveryPaymentStatus;
import com.baronesa.website.enums.delivery.DeliveryStatus;
import com.baronesa.website.enums.delivery.FulfillmentMode;
import com.baronesa.website.repository.delivery.DeliveryOrderItemRepository;
import com.baronesa.website.repository.delivery.DeliveryOrderRepository;
import com.baronesa.website.repository.delivery.DeliveryPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository orderRepository;
    private final DeliveryOrderItemRepository itemRepository;
    private final DeliveryPaymentRepository paymentRepository;
    private final DeliveryOrderCalculator orderCalculator;
    private final UberDirectClient uberDirectClient;
    private final UberConfigService uberConfigService;
    private final KdsEventService kdsEventService;

    @Transactional
    public CreateDeliveryOrderResponse createOrder(CreateDeliveryOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Itens do delivery não informados");
        }
        if (request.paymentId() == null) {
            throw new IllegalArgumentException("Pagamento é obrigatório para delivery");
        }

        var payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado"));

        FulfillmentMode serviceMode = FulfillmentMode.from(payment.getFulfillmentMode());

        if (payment.getStatus() != DeliveryPaymentStatus.paid) {
            throw new IllegalStateException("Pagamento não confirmado");
        }

        String externalId = request.externalId();
        if (externalId == null || externalId.isBlank()) {
            externalId = "delivery-payment-" + request.paymentId();
        }

        DeliveryOrder order = orderRepository.findByExternalId(externalId).orElse(null);
        if (order != null && order.getUberDeliveryId() != null) {
            return new CreateDeliveryOrderResponse(
                    order.getId(),
                    order.getUberDeliveryId(),
                    order.getExternalId(),
                    order.getStatus().name(),
                    order.getUberTrackingUrl(),
                    order.getUberFeeCents(),
                    order.getUberCurrency(),
                    order.getFulfillmentMode()
            );
        }

        var calculation = orderCalculator.calculate(request.items());

        if (order == null) {
            order = new DeliveryOrder();
            order.setFulfillmentMode(serviceMode);
            order.setCustomerName(request.customerName());
            order.setCustomerPhone(request.customerPhone());
            order.setCustomerEmail(request.customerEmail());
            order.setDropoffAddress(request.dropoffAddress());
            order.setDropoffNotes(request.dropoffNotes());
            order.setExternalId(externalId);
            order.setPayment(payment);

            for (var calcItem : calculation.items()) {
                DeliveryOrderItem item = new DeliveryOrderItem();
                item.setDeliveryOrder(order);
                item.setProdutoId(calcItem.produtoId());
                item.setSkuId(calcItem.skuId());
                item.setNome(calcItem.nome());
                item.setQuantidade(calcItem.quantidade());
                item.setObservacoes(calcItem.observacoes());
                item.setSize(calcItem.size());
                item.setStatus(DeliveryItemStatus.queued);
                item.setKdsVisible(true);
                item.setEstacao("kitchen");
                order.getItems().add(item);
            }

            orderRepository.save(order);
        } else {
            order.setCustomerName(request.customerName());
            order.setCustomerPhone(request.customerPhone());
            order.setCustomerEmail(request.customerEmail());
            order.setDropoffAddress(request.dropoffAddress());
            order.setDropoffNotes(request.dropoffNotes());
            order.setFulfillmentMode(serviceMode);
            order.setPayment(payment);
            orderRepository.save(order);
        }

        DeliveryOrder saved = orderRepository.save(order);

        publishNewDeliveryItems(saved);

        return new CreateDeliveryOrderResponse(
                saved.getId(),
                saved.getUberDeliveryId(),
                saved.getExternalId(),
                saved.getStatus().name(),
                saved.getUberTrackingUrl(),
                saved.getUberFeeCents(),
                saved.getUberCurrency(),
                saved.getFulfillmentMode()
        );
    }

    @Transactional
    public DeliveryOrder createDraft(CreateDeliveryPaymentIntentRequest request, DeliveryPayment payment) {
        String externalId = request.externalId();
        if (externalId == null || externalId.isBlank()) {
            externalId = "delivery-payment-" + payment.getId();
        }

        DeliveryOrder order = orderRepository.findByExternalId(externalId).orElse(null);
        if (order == null) {
            order = new DeliveryOrder();
        }

        order.setCustomerName(request.customerName());
        order.setCustomerPhone(request.customerPhone());
        order.setCustomerEmail(request.customerEmail());
        order.setDropoffAddress(request.dropoffAddress());
        order.setDropoffNotes(request.dropoffNotes());
        order.setExternalId(externalId);
        order.setFulfillmentMode(payment.getFulfillmentMode());
        order.setPayment(payment);

        if (order.getItems().isEmpty()) {
            var calculation = orderCalculator.calculate(request.items());
            for (var calcItem : calculation.items()) {
                DeliveryOrderItem item = new DeliveryOrderItem();
                item.setDeliveryOrder(order);
                item.setProdutoId(calcItem.produtoId());
                item.setSkuId(calcItem.skuId());
                item.setNome(calcItem.nome());
                item.setQuantidade(calcItem.quantidade());
                item.setObservacoes(calcItem.observacoes());
                item.setSize(calcItem.size());
                item.setStatus(DeliveryItemStatus.queued);
                item.setKdsVisible(false);
                item.setEstacao("kitchen");
                order.getItems().add(item);
            }
        }

        return orderRepository.save(order);
    }

    @Transactional
    public void publishOrderToKds(DeliveryOrder order) {
        for (DeliveryOrderItem item : order.getItems()) {
            item.setKdsVisible(true);
            itemRepository.save(item);
        }
        publishNewDeliveryItems(order);
    }

    @Transactional
    public void updateItemStatus(Long itemId, DeliveryItemStatus status) {
        DeliveryOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item de delivery não encontrado"));

        item.setStatus(status);
        itemRepository.save(item);

        kdsEventService.send("kds.delivery_status_changed", new DeliveryStatusPayload(item.getId(), status.name()));

        if (status == DeliveryItemStatus.ready) {
            DeliveryOrder order = item.getDeliveryOrder();
            if (order != null && order.getUberDeliveryId() == null && order.getFulfillmentMode() == FulfillmentMode.DELIVERY) {
                boolean allReady = order.getItems().stream()
                        .allMatch(i -> i.getStatus() == DeliveryItemStatus.ready || i.getStatus() == DeliveryItemStatus.delivered);
                if (allReady) {
                    try {
                        createUberDeliveryForOrder(order);
                    } catch (Exception ex) {
                        log.warn("Falha ao criar delivery Uber no pronto orderId={}", order.getId(), ex);
                    }
                }
            }
        }
    }

    @Transactional
    public void updateOrderStatusByDeliveryId(String deliveryId, DeliveryStatus status) {
        DeliveryOrder order = orderRepository.findByUberDeliveryId(deliveryId)
                .orElse(null);
        if (order == null) {
            log.warn("Delivery não encontrado para deliveryId={}", deliveryId);
            return;
        }

        order.setStatus(status);
        orderRepository.save(order);

        if (status == DeliveryStatus.delivered || status == DeliveryStatus.canceled || status == DeliveryStatus.failed) {
            DeliveryItemStatus itemStatus = status == DeliveryStatus.delivered
                    ? DeliveryItemStatus.delivered
                    : DeliveryItemStatus.canceled;
            for (DeliveryOrderItem item : order.getItems()) {
                item.setStatus(itemStatus);
                itemRepository.save(item);
                kdsEventService.send("kds.delivery_status_changed", new DeliveryStatusPayload(item.getId(), itemStatus.name()));
            }
        }

        kdsEventService.send("kds.delivery_status", new DeliveryDeliveryStatusPayload(deliveryId, status.name()));
    }

    private void publishNewDeliveryItems(DeliveryOrder order) {
        for (DeliveryOrderItem item : order.getItems()) {
            if (!Boolean.TRUE.equals(item.getKdsVisible())) {
                continue;
            }
            String deliveryStatus = order.getUberDeliveryId() == null ? null : order.getStatus().name();
            DeliveryQueueResponse.KdsTicketDto ticket = new DeliveryQueueResponse.KdsTicketDto(
                    item.getId(),
                    order.getId(),
                    item.getEstacao(),
                    item.getStatus().name(),
                    item.getUpdatedAt() == null ? Instant.now() : item.getUpdatedAt(),
                    "delivery",
                    item.getId(),
                    new DeliveryQueueResponse.DeliveryInfo(
                            order.getUberDeliveryId(),
                            order.getExternalId(),
                            order.getCustomerName(),
                            order.getDropoffAddress(),
                            deliveryStatus
                    ),
                    new DeliveryQueueResponse.ItemInfo(
                            item.getNome(),
                            item.getQuantidade(),
                            item.getObservacoes(),
                            true,
                            item.getSkuId(),
                            null
                    ),
                    new DeliveryQueueResponse.MesaInfo(
                            "delivery",
                            order.getFulfillmentMode().name(),
                            order.getFulfillmentMode() == FulfillmentMode.PICKUP ? "Retirada no balcão" : "Uber",
                            order.getFulfillmentMode().name()
                    ),
                    new DeliveryQueueResponse.PedidoInfo(order.getCreatedAt())
            );

            kdsEventService.send("kds.new_item", ticket);
        }
    }

    private void createUberDeliveryForOrder(DeliveryOrder order) {
        var config = uberConfigService.getConfig();
        UberDeliveryRequest uberRequest = UberDeliveryRequest.builder()
                .pickupAddress(config.pickupAddress())
                .pickupName(config.pickupName())
                .pickupPhoneNumber(config.pickupPhone())
                .pickupNotes(config.pickupNotes())
                .dropoffAddress(order.getDropoffAddress())
                .dropoffName(order.getCustomerName())
                .dropoffPhoneNumber(order.getCustomerPhone())
                .dropoffNotes(order.getDropoffNotes())
                .externalId(order.getExternalId())
                .deliverableAction("deliverable_action_meet_at_door")
                .manifestItems(order.getItems().stream()
                        .map(item -> UberDeliveryRequest.ManifestItem.builder()
                                .name(item.getNome())
                                .quantity(item.getQuantidade())
                                .size(item.getSize() == null ? "small" : item.getSize())
                                .mustBeUpright(false)
                                .build())
                        .toList())
                .build();

        UberDeliveryResponse uberResponse = uberDirectClient.createDelivery(uberRequest);
        if (uberResponse == null || uberResponse.getId() == null) {
            throw new IllegalStateException("Falha ao criar delivery Uber");
        }

        order.setUberDeliveryId(uberResponse.getId());
        order.setUberQuoteId(uberResponse.getQuoteId());
        order.setUberTrackingUrl(uberResponse.getTrackingUrl());
        order.setUberFeeCents(uberResponse.getFee());
        order.setUberCurrency(uberResponse.getCurrency());
        if (uberResponse.getStatus() != null) {
            order.setStatus(mapDeliveryStatus(uberResponse.getStatus()));
        }

        DeliveryOrder saved = orderRepository.save(order);
        publishDeliveryCreated(saved);
    }

    private void publishDeliveryCreated(DeliveryOrder order) {
        for (DeliveryOrderItem item : order.getItems()) {
            kdsEventService.send(
                    "kds.delivery_status",
                    new DeliveryCreatedPayload(
                            item.getId(),
                            order.getUberDeliveryId(),
                            order.getExternalId(),
                            order.getStatus().name()
                    )
            );
        }
    }

    private DeliveryStatus mapDeliveryStatus(String status) {
        try {
            return DeliveryStatus.valueOf(status.toLowerCase());
        } catch (IllegalArgumentException ex) {
            return DeliveryStatus.pending;
        }
    }

    private record DeliveryStatusPayload(Long deliveryItemId, String status) {}

    private record DeliveryCreatedPayload(Long deliveryItemId, String deliveryId, String externalId, String status) {}

    private record DeliveryDeliveryStatusPayload(String deliveryId, String status) {}
}
