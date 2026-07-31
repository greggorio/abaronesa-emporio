package com.baronesa.website.dto.delivery;

import com.baronesa.website.enums.delivery.FulfillmentMode;
import java.util.List;

public record CreateDeliveryOrderRequest(
        String customerName,
        String customerPhone,
        String customerEmail,
        String dropoffAddress,
        String dropoffNotes,
        String externalId,
        FulfillmentMode serviceMode,
        Long paymentId,
        List<DeliveryItemRequest> items
) {
    public record DeliveryItemRequest(
            Long produtoId,
            Long skuId,
            Integer quantidade,
            String observacoes,
            String size
    ) {}
}
