package com.baronesa.website.dto.delivery;

import com.baronesa.website.enums.delivery.FulfillmentMode;
import java.time.OffsetDateTime;

public record CreateDeliveryQuoteResponse(
        String quoteId,
        Integer feeCents,
        String currency,
        OffsetDateTime expiresAt,
        FulfillmentMode serviceMode
) {}
