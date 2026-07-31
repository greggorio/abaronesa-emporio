package com.baronesa.emporio.dto.mercadopago;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
public class MercadoPagoPaymentResponse {
    private String id;
    private String status;
    private String statusDetail;
    private BigDecimal amount;
    private String paymentMethodId;
    private String externalReference;
    private String environment;
    private Instant createdAt;
    private OffsetDateTime dateOfExpiration;
    private String pixQrCode;
    private String pixQrCodeBase64;
    private Map<String, Object> metadata;
    private boolean success;
}
