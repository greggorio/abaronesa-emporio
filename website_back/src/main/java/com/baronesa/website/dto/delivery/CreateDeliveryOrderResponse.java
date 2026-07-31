package com.baronesa.website.dto.delivery;

import com.baronesa.website.enums.delivery.FulfillmentMode;

public record CreateDeliveryOrderResponse(
        Long orderId,
        String deliveryId,
        String externalId,
        String status,
        String trackingUrl,
        Integer feeCents,
        String currency,
        FulfillmentMode serviceMode
) {}
