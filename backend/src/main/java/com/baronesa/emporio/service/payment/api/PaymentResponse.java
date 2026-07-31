package com.baronesa.emporio.service.payment.api;

import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class PaymentResponse {
    private PaymentGatewayType gateway;
    private NormalizedPaymentStatus status;
    private String providerPaymentId;
    private String message;
    private String friendlyMessage;

    // Dados opcionais para PIX
    private String pixQrCode;
    private String pixQrCodeBase64;
    private Instant expiresAt;
}
