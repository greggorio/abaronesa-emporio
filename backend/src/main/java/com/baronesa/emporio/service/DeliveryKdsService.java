package com.baronesa.emporio.service;

import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.entity.DeliveryOrderItem;
import com.baronesa.emporio.enums.DeliveryOrderItemStatus;
import com.baronesa.emporio.repository.DeliveryOrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeliveryKdsService {

    private final DeliveryOrderItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getQueue() {
        List<DeliveryOrderItem> items = itemRepository.findByStatusInAndKdsArchivedFalse(List.of(
                DeliveryOrderItemStatus.QUEUED,
                DeliveryOrderItemStatus.ACCEPTED,
                DeliveryOrderItemStatus.PREPARING,
                DeliveryOrderItemStatus.READY
        ));

        return items.stream()
                .map(this::toKdsTicket)
                .toList();
    }

    public Map<String, Object> toKdsTicket(DeliveryOrderItem item) {
        DeliveryOrder order = item.getDeliveryOrder();

        Map<String, Object> itemInfo = new HashMap<>();
        itemInfo.put("nome", item.getNome());
        itemInfo.put("quantidade", item.getQuantidade());
        itemInfo.put("observacoes", item.getObservacoes());
        itemInfo.put("necessitaPreparacao", true);
        itemInfo.put("skuId", item.getSkuId());
        itemInfo.put("variacao", item.getVariacao());

        Map<String, Object> deliveryInfo = new HashMap<>();
        deliveryInfo.put("deliveryId", order != null ? order.getUberDeliveryId() : null);
        deliveryInfo.put("externalId", order != null ? order.getExternalReference() : null);
        deliveryInfo.put("customerName", order != null ? order.getCustomerName() : null);
        deliveryInfo.put("dropoffAddress", order != null ? order.getDropoffAddress() : null);
        String deliveryStatus = null;
        if (order != null && order.getUberDeliveryId() != null) {
            if (order.getUberStatus() != null && !order.getUberStatus().isBlank()) {
                deliveryStatus = order.getUberStatus();
            } else if (order.getStatus() != null) {
                deliveryStatus = order.getStatus().name().toLowerCase();
            }
        }
        deliveryInfo.put("status", deliveryStatus);

        Map<String, Object> mesaInfo = new HashMap<>();
        mesaInfo.put("slug", "delivery");
        mesaInfo.put("rotulo", "DELIVERY");
        mesaInfo.put("referencia", order != null && order.getUberDeliveryId() != null
                ? order.getUberDeliveryId()
                : order != null ? order.getExternalReference() : null);

        Map<String, Object> pedidoInfo = new HashMap<>();
        LocalDateTime createdAt = order != null ? order.getCreatedAt() : null;
        pedidoInfo.put("criadoEm", createdAt != null ? createdAt.toString() : null);

        Map<String, Object> ticket = new HashMap<>();
        ticket.put("itemPedidoId", item.getId());
        ticket.put("pedidoId", order != null ? order.getId() : null);
        ticket.put("estacao", item.getEstacao() != null ? item.getEstacao() : "kitchen");
        ticket.put("status", item.getStatus() != null ? item.getStatus().name().toLowerCase() : "queued");
        ticket.put("atualizadoEm", item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : LocalDateTime.now().toString());
        ticket.put("tipo", "delivery");
        ticket.put("deliveryItemId", item.getId());
        ticket.put("delivery", deliveryInfo);
        ticket.put("item", itemInfo);
        ticket.put("mesa", mesaInfo);
        ticket.put("pedido", pedidoInfo);

        return ticket;
    }
}
