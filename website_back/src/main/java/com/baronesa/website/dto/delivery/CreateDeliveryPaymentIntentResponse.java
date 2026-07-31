package com.baronesa.website.dto.delivery;

import com.baronesa.website.enums.delivery.FulfillmentMode;

public record CreateDeliveryPaymentIntentResponse(
        Long paymentId,
        String status,
        Integer amountCents,
        Integer feeCents,
        String currency,
        String quoteId,
        String qrPayload,
        FulfillmentMode serviceMode
) {}
