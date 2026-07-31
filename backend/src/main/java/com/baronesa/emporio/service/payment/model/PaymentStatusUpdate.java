package com.baronesa.emporio.service.payment.model;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusUpdate {

    private PaymentGatewayType gateway;
    private String providerPaymentId;
    private String externalReference;
    private NormalizedPaymentStatus normalizedStatus;
    private String providerStatus;
    private String providerStatusDetail;
    private PaymentMethod method;
    private String rawPayload;

    private BigDecimal amount;
    private Instant paidAt;
    private Instant canceledAt;
    private Instant expiredAt;
    private String pixQrCode;
    private String pixQrCodeBase64;
    private String providerEventId;
    private String webhookHash;
}
