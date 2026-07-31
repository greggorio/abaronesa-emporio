package com.baronesa.emporio.service.payment.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentMethod;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MercadoPagoStatusMapper implements PaymentStatusMapper {

    private final ObjectMapper objectMapper;

    @Override
    public PaymentGatewayType gateway() {
        return PaymentGatewayType.MERCADOPAGO;
    }

    @Override
    public PaymentStatusUpdate fromProviderPayload(String providerPaymentId, String providerStatus, String rawPayload) {
        PaymentStatusUpdate.PaymentStatusUpdateBuilder builder = PaymentStatusUpdate.builder()
                .gateway(gateway())
                .providerPaymentId(providerPaymentId)
                .providerStatus(providerStatus)
                .rawPayload(rawPayload)
                .normalizedStatus(normalizeStatus(providerStatus))
                .method(PaymentMethod.CARD);

        if (rawPayload != null && !rawPayload.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(rawPayload);
                if (root.hasNonNull("external_reference")) {
                    builder.externalReference(root.get("external_reference").asText());
                }
                if (root.hasNonNull("status_detail")) {
                    builder.providerStatusDetail(root.get("status_detail").asText());
                }
                PaymentMethod method = resolveMethod(root);
                builder.method(method);

                JsonNode poi = root.path("point_of_interaction").path("transaction_data");
                if (poi.hasNonNull("qr_code")) {
                    builder.pixQrCode(poi.get("qr_code").asText());
                }
                if (poi.hasNonNull("qr_code_base64")) {
                    builder.pixQrCodeBase64(poi.get("qr_code_base64").asText());
                }
            } catch (Exception ignored) {
                // Keep defaults if parsing fails.
            }
        }

        return builder.build();
    }

    private NormalizedPaymentStatus normalizeStatus(String providerStatus) {
        if (providerStatus == null) {
            return NormalizedPaymentStatus.PENDING;
        }
        switch (providerStatus.toLowerCase()) {
            case "approved":
                return NormalizedPaymentStatus.PAID;
            case "rejected":
            case "cancelled":
                return NormalizedPaymentStatus.CANCELED;
            case "expired":
                return NormalizedPaymentStatus.EXPIRED;
            case "pending":
            case "in_process":
                return NormalizedPaymentStatus.PENDING;
            default:
                return NormalizedPaymentStatus.PENDING;
        }
    }

    private PaymentMethod resolveMethod(JsonNode root) {
        if (root == null) {
            return PaymentMethod.CARD;
        }
        String paymentMethodId = textOrNull(root, "payment_method_id");
        String paymentTypeId = textOrNull(root, "payment_type_id");
        if ("pix".equalsIgnoreCase(paymentMethodId) || "pix".equalsIgnoreCase(paymentTypeId)) {
            return PaymentMethod.PIX;
        }
        return PaymentMethod.CARD;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }
}
