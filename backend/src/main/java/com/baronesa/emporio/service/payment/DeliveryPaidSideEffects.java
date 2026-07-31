package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.repository.DeliveryOrderItemRepository;
import com.baronesa.emporio.repository.DeliveryOrderRepository;
import com.baronesa.emporio.service.DeliveryKdsService;
import com.baronesa.emporio.events.SseEventsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryPaidSideEffects {

    private final DeliveryOrderItemRepository deliveryOrderItemRepository;
    private final DeliveryKdsService deliveryKdsService;
    private final SseEventsService eventsService;
    private final DeliveryOrderRepository deliveryOrderRepository;

    @Transactional
    public void handlePaid(DeliveryOrder order) {
        if (order == null) return;
        if (order.getKdsPublishedAt() != null) return;

        var items = deliveryOrderItemRepository.findByDeliveryOrderId(order.getId());
        if (items == null || items.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        items.forEach(item -> {
            try {
                var payload = deliveryKdsService.toKdsTicket(item);
                eventsService.publishKds("kds.new_item", payload);
            } catch (Exception e) {
                log.warn("Falha ao publicar item delivery no KDS itemId={}", item.getId(), e);
            }
        });

        order.setKdsPublishedAt(now);
        deliveryOrderRepository.save(order);
    }
}
