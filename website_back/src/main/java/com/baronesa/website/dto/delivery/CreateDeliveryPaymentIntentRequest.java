package com.baronesa.website.dto.delivery;

import com.baronesa.website.enums.delivery.FulfillmentMode;
import java.util.List;

public record CreateDeliveryPaymentIntentRequest(
        String customerName,
        String customerPhone,
        String customerEmail,
        String dropoffAddress,
        String dropoffNotes,
        String externalId,
        FulfillmentMode serviceMode,
        List<CreateDeliveryOrderRequest.DeliveryItemRequest> items
) {}
