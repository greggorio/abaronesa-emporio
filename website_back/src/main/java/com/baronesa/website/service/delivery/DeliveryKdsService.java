package com.baronesa.website.service.delivery;

import com.baronesa.website.dto.delivery.DeliveryQueueResponse;
import com.baronesa.website.entity.delivery.DeliveryOrder;
import com.baronesa.website.entity.delivery.DeliveryOrderItem;
import com.baronesa.website.enums.delivery.DeliveryItemStatus;
import com.baronesa.website.enums.delivery.FulfillmentMode;
import com.baronesa.website.repository.delivery.DeliveryOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryKdsService {

    private final DeliveryOrderItemRepository itemRepository;

    @Transactional(readOnly = true)
    public DeliveryQueueResponse getQueue() {
        List<DeliveryOrderItem> items = itemRepository.findByStatusInAndKdsVisibleTrue(List.of(
                DeliveryItemStatus.queued,
                DeliveryItemStatus.accepted,
                DeliveryItemStatus.preparing,
                DeliveryItemStatus.ready,
                DeliveryItemStatus.canceled
        ));

        List<DeliveryQueueResponse.KdsTicketDto> tickets = items.stream()
                .map(this::toTicket)
                .toList();

        return new DeliveryQueueResponse(tickets);
    }

    private DeliveryQueueResponse.KdsTicketDto toTicket(DeliveryOrderItem item) {
        DeliveryOrder order = item.getDeliveryOrder();
        Instant updatedAt = item.getUpdatedAt() == null ? Instant.now() : item.getUpdatedAt();

        return new DeliveryQueueResponse.KdsTicketDto(
                item.getId(),
                order.getId(),
                item.getEstacao(),
                item.getStatus().name(),
                updatedAt,
                "delivery",
                item.getId(),
                new DeliveryQueueResponse.DeliveryInfo(
                        order.getUberDeliveryId(),
                        order.getExternalId(),
                        order.getCustomerName(),
                        order.getDropoffAddress(),
                        order.getUberDeliveryId() == null ? null : order.getStatus().name()
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
    }
}
