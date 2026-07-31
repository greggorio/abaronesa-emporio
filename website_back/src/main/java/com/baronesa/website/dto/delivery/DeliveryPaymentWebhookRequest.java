package com.baronesa.website.dto.delivery;

public record DeliveryPaymentWebhookRequest(
        Long paymentId,
        String evento,
        String referenciaProvedor
) {}
