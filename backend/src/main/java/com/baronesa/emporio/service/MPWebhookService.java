package com.baronesa.emporio.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.baronesa.emporio.entity.MPPayment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.config.MPConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MPWebhookService {

    private final MPConfig mpConfig;
    private final MPPaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * Processa notificação de webhook do Mercado Pago
     */
    public void processWebhook(String payload, String signature) {
        try {
            log.info("Processando webhook do Mercado Pago");

            // Validar assinatura se configurada
            if (mpConfig.getWebhookSecret() != null && !validateSignature(payload, signature)) {
                log.error("Assinatura do webhook inválida");
                throw new SecurityException("Assinatura do webhook inválida");
            }

            // Parse do payload
            JsonNode webhookData = objectMapper.readTree(payload);

            String action = webhookData.get("action").asText();
            String type = webhookData.get("type").asText();

            log.info("Webhook recebido - Type: {}, Action: {}", type, action);

            // Processar apenas notificações de pagamento
            if ("payment".equals(type)) {
                Long paymentId = webhookData.get("data").get("id").asLong();
                processPaymentNotification(paymentId, action);
            }

        } catch (Exception e) {
            log.error("Erro ao processar webhook", e);
            throw new RuntimeException("Erro ao processar webhook", e);
        }
    }

    /**
     * Processa notificação específica de pagamento
     */
    private void processPaymentNotification(Long paymentId, String action) {
        try {
            log.info("Processando notificação de pagamento ID: {}, Action: {}", paymentId, action);

            // Buscar dados atualizados do pagamento
            var paymentResponse = paymentService.getPayment(paymentId);

            if (paymentResponse.getPayment() != null) {
                MPPayment payment = paymentResponse.getPayment();

                // Processar baseado no status
                switch (payment.getStatus()) {
                    case "approved":
                        handleApprovedPayment(payment);
                        break;
                    case "rejected":
                        handleRejectedPayment(payment);
                        break;
                    case "cancelled":
                        handleCancelledPayment(payment);
                        break;
                    case "refunded":
                        handleRefundedPayment(payment);
                        break;
                    case "pending":
                        handlePendingPayment(payment);
                        break;
                    default:
                        log.info("Status não processado: {} para pagamento ID: {}",
                                payment.getStatus(), paymentId);
                }
            }

        } catch (Exception e) {
            log.error("Erro ao processar notificação de pagamento ID: {}", paymentId, e);
        }
    }

    private void handleApprovedPayment(MPPayment payment) {
        log.info("Pagamento aprovado - ID: {}, External Reference: {}",
                payment.getId(), payment.getExternalReference());

        // TODO: Implementar lógica específica do seu ERP
        // Exemplos:
        // - Atualizar status do pedido
        // - Enviar email de confirmação
        // - Liberar produtos/serviços
        // - Atualizar estoque
    }

    private void handleRejectedPayment(MPPayment payment) {
        log.info("Pagamento rejeitado - ID: {}, External Reference: {}, Motivo: {}",
                payment.getId(), payment.getExternalReference(), payment.getStatusDetail());

        // TODO: Implementar lógica específica do seu ERP
        // Exemplos:
        // - Notificar cliente sobre rejeição
        // - Oferecer métodos alternativos de pagamento
        // - Cancelar reserva de estoque
    }

    private void handleCancelledPayment(MPPayment payment) {
        log.info("Pagamento cancelado - ID: {}, External Reference: {}",
                payment.getId(), payment.getExternalReference());

        // TODO: Implementar lógica específica do seu ERP
    }

    private void handleRefundedPayment(MPPayment payment) {
        log.info("Pagamento estornado - ID: {}, External Reference: {}",
                payment.getId(), payment.getExternalReference());

        // TODO: Implementar lógica específica do seu ERP
    }

    private void handlePendingPayment(MPPayment payment) {
        log.info("Pagamento pendente - ID: {}, External Reference: {}",
                payment.getId(), payment.getExternalReference());

        // TODO: Implementar lógica específica do seu ERP
    }

    /**
     * Valida assinatura do webhook
     */
    private boolean validateSignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    mpConfig.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);

            return expectedSignature.equals(signature);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Erro ao validar assinatura do webhook", e);
            return false;
        }
    }
}