package com.baronesa.emporio.dto;

import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SelfCheckoutPaymentResponse {
    private Long pagamentoId;
    private PaymentGatewayType gateway;
    private NormalizedPaymentStatus status;
    private String providerPaymentId;
    private String message;
    private String friendlyMessage;
    private String pixQrCode;
    private String pixQrCodeBase64;
    private String expiresAt; // ISO string
    private long amountCentavos;
}
