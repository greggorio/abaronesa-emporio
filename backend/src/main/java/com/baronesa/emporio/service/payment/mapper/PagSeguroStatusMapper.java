package com.baronesa.emporio.service.payment.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentMethod;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class PagSeguroStatusMapper {

    private final ObjectMapper objectMapper;
    private final ConfigManager configManager;

    public PaymentStatusUpdate fromPayload(String rawPayload) {
        PaymentStatusUpdate.PaymentStatusUpdateBuilder builder = PaymentStatusUpdate.builder()
                .gateway(PaymentGatewayType.PAGSEGURO)
                .rawPayload(rawPayload);

        if (!StringUtils.hasText(rawPayload)) {
            builder.normalizedStatus(NormalizedPaymentStatus.PENDING);
            return builder.build();
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            boolean allowPayloadStatus = configManager.getBooleanConfig("pagseguro_webhook_allow_payload_status", false);

            JsonNode charge = null;
            JsonNode charges = root.path("charges");
            if (charges.isArray() && charges.size() > 0) {
                charge = charges.get(0);
            } else if (root.hasNonNull("charge")) {
                charge = root.get("charge");
            }

            if (charge != null) {
                builder.method(resolveMethod(charge, root));
                if (charge.hasNonNull("id")) {
                    builder.providerPaymentId(charge.get("id").asText());
                }
                if (charge.hasNonNull("status")) {
                    builder.providerStatus(charge.get("status").asText());
                    builder.normalizedStatus(normalize(charge.get("status").asText()));
                }
                if (charge.hasNonNull("reference_id")) {
                    builder.externalReference(charge.get("reference_id").asText());
                }
                JsonNode amountNode = charge.path("amount");
                if (amountNode.hasNonNull("value")) {
                    BigDecimal amount = BigDecimal.valueOf(amountNode.get("value").asLong()).movePointLeft(2);
                    builder.amount(amount);
                }
                if (charge.hasNonNull("payment_response") && charge.get("payment_response").hasNonNull("message")) {
                    builder.providerStatusDetail(charge.get("payment_response").get("message").asText());
                }
            } else if (allowPayloadStatus) {
                builder.method(resolveMethod(null, root));
                if (root.hasNonNull("id")) {
                    builder.providerPaymentId(root.get("id").asText());
                }
                if (root.hasNonNull("status")) {
                    builder.providerStatus(root.get("status").asText());
                    builder.normalizedStatus(normalize(root.get("status").asText()));
                }
                if (root.hasNonNull("reference_id")) {
                    builder.externalReference(root.get("reference_id").asText());
                }
                if (root.hasNonNull("status_detail")) {
                    builder.providerStatusDetail(root.get("status_detail").asText());
                }
                JsonNode amountNode = root.path("amount");
                if (amountNode.hasNonNull("value")) {
                    BigDecimal amount = BigDecimal.valueOf(amountNode.get("value").asLong()).movePointLeft(2);
                    builder.amount(amount);
                }
            }

            if (!StringUtils.hasText(builder.build().getExternalReference()) && root.hasNonNull("reference_id")) {
                builder.externalReference(root.get("reference_id").asText());
            }

            if (builder.build().getNormalizedStatus() == null) {
                builder.normalizedStatus(normalize(builder.build().getProviderStatus()));
            }

        } catch (Exception e) {
            log.warn("Falha ao interpretar payload PagSeguro", e);
            builder.normalizedStatus(NormalizedPaymentStatus.PENDING);
        }

        if (builder.build().getMethod() == null) {
            builder.method(resolveMethod(null, safeRead(rawPayload)));
        }
        return builder.build();
    }

    private NormalizedPaymentStatus normalize(String providerStatus) {
        if (providerStatus == null) return NormalizedPaymentStatus.PENDING;
        switch (providerStatus.toUpperCase()) {
            case "PAID":
                return NormalizedPaymentStatus.PAID;
            case "DECLINED":
            case "CANCELED":
            case "CANCELLED":
                return NormalizedPaymentStatus.CANCELED;
            case "EXPIRED":
                return NormalizedPaymentStatus.EXPIRED;
            default:
                return NormalizedPaymentStatus.PENDING;
        }
    }

    private PaymentMethod resolveMethod(JsonNode charge, JsonNode root) {
        String type = null;
        if (charge != null) {
            type = textOrNull(charge.path("payment_method"), "type");
        }
        if (!StringUtils.hasText(type) && root != null) {
            type = textOrNull(root.path("payment_method"), "type");
        }
        if (StringUtils.hasText(type) && "PIX".equalsIgnoreCase(type)) {
            return PaymentMethod.PIX;
        }
        if (root != null && root.path("qr_codes").isArray() && root.path("qr_codes").size() > 0) {
            return PaymentMethod.PIX;
        }
        return PaymentMethod.CARD;
    }

    private JsonNode safeRead(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return null;
        }
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }
}
