package com.baronesa.emporio.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.enums.DeliveryOrderStatus;
import com.baronesa.emporio.repository.DeliveryOrderRepository;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryOrderPaymentSync {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentSyncResult sync(PaymentGatewayType gateway, String providerPaymentId, String status, String rawPayload) {
        Optional<DeliveryOrder> orderOpt = findOrder(gateway, providerPaymentId, rawPayload);
        if (orderOpt.isEmpty()) {
            log.info("Pagamento {} não vinculado a delivery_order", providerPaymentId);
            return PaymentSyncResult.empty();
        }

        DeliveryOrder order = orderOpt.get();
        DeliveryOrderStatus previousStatus = order.getStatus();

        order.setProviderGateway(gateway);
        order.setProviderPaymentId(providerPaymentId);
        if (gateway == PaymentGatewayType.MERCADOPAGO) {
            order.setMpPaymentId(providerPaymentId);
        }
        order.setMpStatus(status);
        order.setMpRawResponse(rawPayload);

        updateFromPayload(order, rawPayload);

        boolean becamePaid = applyStatus(order, status, previousStatus);

        deliveryOrderRepository.save(order);

        return new PaymentSyncResult(order, becamePaid);
    }

    private boolean applyStatus(DeliveryOrder order, String status, DeliveryOrderStatus previousStatus) {
        DeliveryOrderStatus target = null;
        LocalDateTime now = LocalDateTime.now();

        // Mercado Pago: approved | PagSeguro: PAID
        if ("approved".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)) {
            target = DeliveryOrderStatus.PAID;
            if (previousStatus != DeliveryOrderStatus.PAID) {
                order.setPaidAt(now);
            }
        // Mercado Pago: rejected/cancelled | PagSeguro: DECLINED/CANCELED/CANCELLED
        } else if ("rejected".equalsIgnoreCase(status)
                || "cancelled".equalsIgnoreCase(status)
                || "DECLINED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status)) {
            target = DeliveryOrderStatus.CANCELED;
            order.setCanceledAt(now);
            order.setCanceledReason("Pagamento rejeitado/cancelado");
        } else if ("expired".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
            target = DeliveryOrderStatus.EXPIRED;
            order.setCanceledAt(now);
            order.setCanceledReason("Pagamento expirado");
        }

        if (target != null) {
            order.setStatus(target);
        }

        return previousStatus != DeliveryOrderStatus.PAID && target == DeliveryOrderStatus.PAID;
    }

    private void updateFromPayload(DeliveryOrder order, String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) return;
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            if (node.hasNonNull("status_detail")) {
                order.setMpStatusDetail(node.get("status_detail").asText());
            }
            if (node.hasNonNull("payment_method_id")) {
                order.setMpPaymentMethod(node.get("payment_method_id").asText());
            }
            if (node.hasNonNull("external_reference")) {
                order.setExternalReference(node.get("external_reference").asText());
            }
            JsonNode poi = node.path("point_of_interaction").path("transaction_data");
            if (poi.hasNonNull("qr_code")) {
                order.setMpQrCode(poi.get("qr_code").asText());
            }
            if (poi.hasNonNull("qr_code_base64")) {
                order.setMpQrCodeBase64(poi.get("qr_code_base64").asText());
            }
        } catch (Exception e) {
            log.warn("Não foi possível interpretar payload de pagamento", e);
        }
    }

    private Optional<DeliveryOrder> findOrder(PaymentGatewayType gateway, String providerPaymentId, String rawPayload) {
        if (gateway != null && StringUtils.hasText(providerPaymentId)) {
            Optional<DeliveryOrder> byProvider = deliveryOrderRepository.findByProviderGatewayAndProviderPaymentId(gateway, providerPaymentId);
            if (byProvider.isPresent()) {
                return byProvider;
            }
        }
        if (gateway == PaymentGatewayType.MERCADOPAGO && StringUtils.hasText(providerPaymentId)) {
            Optional<DeliveryOrder> byPayment = deliveryOrderRepository.findByMpPaymentId(providerPaymentId);
            if (byPayment.isPresent()) {
                return byPayment;
            }
        }
        String externalRef = extractExternalReference(rawPayload);
        if (StringUtils.hasText(externalRef)) {
            return deliveryOrderRepository.findByExternalReference(externalRef);
        }
        return Optional.empty();
    }

    private String extractExternalReference(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) return null;
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            // Mercado Pago usa external_reference; PagSeguro usa reference_id (pedido ou charge)
            if (node.hasNonNull("external_reference")) {
                return node.get("external_reference").asText();
            }
            if (node.hasNonNull("reference_id")) {
                return node.get("reference_id").asText();
            }
            JsonNode charges = node.path("charges");
            if (charges.isArray() && charges.size() > 0) {
                JsonNode charge = charges.get(0);
                if (charge.hasNonNull("reference_id")) {
                    return charge.get("reference_id").asText();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
