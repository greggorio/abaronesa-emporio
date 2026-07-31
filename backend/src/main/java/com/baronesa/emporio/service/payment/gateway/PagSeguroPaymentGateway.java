package com.baronesa.emporio.service.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.service.payment.api.CardPaymentRequest;
import com.baronesa.emporio.service.payment.api.PaymentGatewayResult;
import com.baronesa.emporio.service.payment.api.PaymentResponse;
import com.baronesa.emporio.service.payment.api.PixPaymentRequest;
import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.PaymentFriendlyMessageProvider;
import com.baronesa.emporio.service.payment.model.PaymentMethod;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PagSeguroPaymentGateway implements PaymentGateway {

    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;
    private final PaymentFriendlyMessageProvider friendlyMessageProvider;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public PaymentGatewayType gateway() {
        return PaymentGatewayType.PAGSEGURO;
    }

    @Override
    public PaymentGatewayResult createCardPayment(CardPaymentRequest request) {
        try {
            int amountCents = toCents(request.getAmount());
            String baseUrl = resolveBaseUrl();
            String token = resolveToken();
            HttpHeaders headers = buildHeaders(token);

            // Cria o pedido
            Map<String, Object> orderPayload = buildOrderPayload(request, amountCents);
            ResponseEntity<String> orderResp = restTemplate.exchange(
                    baseUrl + "/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(orderPayload, headers),
                    String.class
            );
            JsonNode orderNode = objectMapper.readTree(orderResp.getBody());
            String orderId = orderNode.path("id").asText();

            // Faz o pagamento do pedido
            Map<String, Object> payPayload = buildPayPayload(request, amountCents);
            ResponseEntity<String> payResp = restTemplate.exchange(
                    baseUrl + "/orders/" + orderId + "/pay",
                    HttpMethod.POST,
                    new HttpEntity<>(payPayload, headers),
                    String.class
            );
            JsonNode payNode = objectMapper.readTree(payResp.getBody());
            JsonNode charge = payNode.path("charges").isArray() && payNode.path("charges").size() > 0
                    ? payNode.path("charges").get(0)
                    : null;

            String providerPaymentId = charge != null ? charge.path("id").asText(orderId) : orderId;
            String providerStatus = charge != null ? charge.path("status").asText() : payNode.path("status").asText();
            NormalizedPaymentStatus normalized = "PAID".equalsIgnoreCase(providerStatus)
                    ? NormalizedPaymentStatus.PAID
                    : NormalizedPaymentStatus.PENDING;

            String providerMessage = charge != null && charge.path("payment_response").has("message")
                    ? charge.path("payment_response").path("message").asText()
                    : null;
            String friendlyMessage = friendlyMessageProvider.resolve(providerMessage, payResp.getBody());
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(normalized)
                    .providerPaymentId(providerPaymentId)
                    .message(providerMessage)
                    .friendlyMessage(friendlyMessage)
                    .build();

            PaymentStatusUpdate update = PaymentStatusUpdate.builder()
                    .gateway(gateway())
                    .providerPaymentId(providerPaymentId)
                    .providerStatus(providerStatus)
                    .providerStatusDetail(charge != null && charge.hasNonNull("payment_response")
                            ? charge.path("payment_response").path("message").asText(null)
                            : null)
                    .normalizedStatus(normalized)
                    .externalReference(request.getExternalReference())
                    .amount(request.getAmount())
                    .rawPayload(payResp.getBody())
                    .method(PaymentMethod.CARD)
                    .build();

            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(update)
                    .build();

        } catch (HttpStatusCodeException e) {
            log.warn("Erro PagSeguro HTTP {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(NormalizedPaymentStatus.FAILED)
                    .providerPaymentId(null)
                    .message("HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString())
                    .friendlyMessage(friendlyMessageProvider.resolve(e.getResponseBodyAsString(), e.getResponseBodyAsString()))
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(null)
                    .build();
        } catch (Exception e) {
            log.error("Erro ao processar pagamento via PagSeguro", e);
            String friendlyMessage = friendlyMessageProvider.resolve(e.getMessage(), null);
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(NormalizedPaymentStatus.FAILED)
                    .providerPaymentId(null)
                    .message(e.getMessage())
                    .friendlyMessage(friendlyMessage)
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(null)
                    .build();
        }
    }

    @Override
    public PaymentGatewayResult createPixPayment(PixPaymentRequest request) {
        try {
            if (!StringUtils.hasText(request.getPayerTaxId())) {
                PaymentResponse response = PaymentResponse.builder()
                        .gateway(gateway())
                        .status(NormalizedPaymentStatus.FAILED)
                        .message("CPF obrigatório para pagamento PIX no PagSeguro.")
                        .friendlyMessage("Informe o CPF para gerar o PIX via PagSeguro.")
                        .build();
                return PaymentGatewayResult.builder()
                        .response(response)
                        .statusUpdate(null)
                        .build();
            }
            int amountCents = toCents(request.getAmount());
            String baseUrl = resolveBaseUrl();
            String token = resolveToken();
            HttpHeaders headers = buildHeaders(token);

            Map<String, Object> orderPayload = buildPixOrderPayload(request, amountCents);
            ResponseEntity<String> orderResp = restTemplate.exchange(
                    baseUrl + "/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(orderPayload, headers),
                    String.class
            );
            JsonNode orderNode = objectMapper.readTree(orderResp.getBody());
            String orderId = orderNode.path("id").asText();
            String providerStatus = orderNode.path("status").asText();
            NormalizedPaymentStatus normalized = "PAID".equalsIgnoreCase(providerStatus)
                    ? NormalizedPaymentStatus.PAID
                    : NormalizedPaymentStatus.PENDING;

            PixData pixData = extractPixData(orderNode, orderNode, null);
            String friendlyMessage = "Pagamento PIX gerado. Utilize o QR Code para pagar.";

            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(normalized)
                    .providerPaymentId(orderId)
                    .message("Cobrança PIX criada com sucesso")
                    .friendlyMessage(friendlyMessage)
                    .pixQrCode(pixData.qrCode)
                    .pixQrCodeBase64(pixData.qrCodeBase64)
                    .expiresAt(pixData.expiresAt)
                    .build();

            PaymentStatusUpdate update = PaymentStatusUpdate.builder()
                    .gateway(gateway())
                    .providerPaymentId(orderId)
                    .providerStatus(providerStatus)
                    .providerStatusDetail(null)
                    .normalizedStatus(normalized)
                    .externalReference(request.getExternalReference())
                    .amount(request.getAmount())
                    .rawPayload(orderResp.getBody())
                    .method(PaymentMethod.PIX)
                    .build();

            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(update)
                    .build();

        } catch (HttpStatusCodeException e) {
            log.warn("Erro PagSeguro HTTP {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(NormalizedPaymentStatus.FAILED)
                    .providerPaymentId(null)
                    .message("HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString())
                    .friendlyMessage(friendlyMessageProvider.resolve(e.getResponseBodyAsString(), e.getResponseBodyAsString()))
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(null)
                    .build();
        } catch (Exception e) {
            log.error("Erro ao processar pagamento PIX via PagSeguro", e);
            String friendlyMessage = friendlyMessageProvider.resolve(e.getMessage(), null);
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(NormalizedPaymentStatus.FAILED)
                    .providerPaymentId(null)
                    .message("Erro ao processar pagamento PIX: " + e.getMessage())
                    .friendlyMessage(friendlyMessage)
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(null)
                    .build();
        }
    }

    private Map<String, Object> buildOrderPayload(CardPaymentRequest request, int amountCents) {
        Map<String, Object> payload = new HashMap<>();
        String reference = StringUtils.hasText(request.getExternalReference())
                ? request.getExternalReference()
                : "ref-" + UUID.randomUUID();
        payload.put("reference_id", reference);

        Map<String, Object> item = new HashMap<>();
        item.put("reference_id", "item-1");
        item.put("name", StringUtils.hasText(request.getDescription()) ? request.getDescription() : "Pedido");
        item.put("quantity", 1);
        item.put("unit_amount", amountCents);
        payload.put("items", java.util.List.of(item));

        // Customer (opcional) — evitar buyer.email igual ao merchant para não gerar erro 400 no PagSeguro
        if (StringUtils.hasText(request.getPayerEmail()) || StringUtils.hasText(request.getPayerName())) {
            Map<String, Object> customer = new HashMap<>();

            if (StringUtils.hasText(request.getPayerName())) {
                customer.put("name", request.getPayerName());
            }
            if (StringUtils.hasText(request.getPayerTaxId())) {
                Map<String, Object> taxId = new HashMap<>();
                taxId.put("tax_id", request.getPayerTaxId());
                customer.put("tax_id", request.getPayerTaxId());
            }

            String payerEmail = StringUtils.hasText(request.getPayerEmail()) ? request.getPayerEmail().trim() : null;
            String merchantEmail = configManager.getConfig("pagseguro_email", "");
            merchantEmail = StringUtils.hasText(merchantEmail) ? merchantEmail.trim() : null;

            boolean canSendEmail = StringUtils.hasText(payerEmail)
                    && (!StringUtils.hasText(merchantEmail) || !payerEmail.equalsIgnoreCase(merchantEmail));

            if (canSendEmail) {
                customer.put("email", payerEmail);
            } else if (StringUtils.hasText(payerEmail)) {
                log.warn("PagSeguro: ignorando payerEmail pois é igual ao pagseguro_email (merchant).");
            }

            payload.put("customer", customer);
        }

        return payload;
    }

    private Map<String, Object> buildPayPayload(CardPaymentRequest request, int amountCents) {
        Map<String, Object> charges = new HashMap<>();
        charges.put("reference_id", StringUtils.hasText(request.getExternalReference())
                ? request.getExternalReference()
                : "charge-" + UUID.randomUUID());
        // Para cartão, o PagSeguro exige o campo currency=BRL no amount.
        charges.put("amount", Map.of("value", amountCents, "currency", "BRL"));

        Map<String, Object> card = new HashMap<>();
        card.put("encrypted", request.getToken());

        Map<String, Object> paymentMethod = new HashMap<>();
        paymentMethod.put("type", "CREDIT_CARD");
        paymentMethod.put("installments", request.getInstallments() != null ? request.getInstallments() : 1);
        paymentMethod.put("capture", true);
        paymentMethod.put("card", card);

        charges.put("payment_method", paymentMethod);

        return Map.of("charges", java.util.List.of(charges));
    }

    private Map<String, Object> buildPixOrderPayload(PixPaymentRequest request, int amountCents) {
        Map<String, Object> payload = new HashMap<>();
        String reference = StringUtils.hasText(request.getExternalReference())
                ? request.getExternalReference()
                : "ref-" + UUID.randomUUID();
        payload.put("reference_id", reference);

        Map<String, Object> item = new HashMap<>();
        item.put("reference_id", "item-1");
        item.put("name", StringUtils.hasText(request.getDescription()) ? request.getDescription() : "Pedido");
        item.put("quantity", 1);
        item.put("unit_amount", amountCents);
        payload.put("items", List.of(item));

        if (StringUtils.hasText(request.getPayerName()) || StringUtils.hasText(request.getPayerEmail())) {
            Map<String, Object> customer = new HashMap<>();
            if (StringUtils.hasText(request.getPayerName())) {
                customer.put("name", request.getPayerName());
            }
            if (StringUtils.hasText(request.getPayerTaxId())) {
                customer.put("tax_id", request.getPayerTaxId().replaceAll("\\D", ""));
            }

            String payerEmail = StringUtils.hasText(request.getPayerEmail()) ? request.getPayerEmail().trim() : null;
            String merchantEmail = configManager.getConfig("pagseguro_email", "");
            merchantEmail = StringUtils.hasText(merchantEmail) ? merchantEmail.trim() : null;

            boolean canSendEmail = StringUtils.hasText(payerEmail)
                    && (!StringUtils.hasText(merchantEmail) || !payerEmail.equalsIgnoreCase(merchantEmail));
            if (canSendEmail) {
                customer.put("email", payerEmail);
            } else if (StringUtils.hasText(payerEmail)) {
                log.warn("PagSeguro: ignorando payerEmail pois é igual ao pagseguro_email (merchant).");
            }
            payload.put("customer", customer);
        }

        String notificationUrl = configManager.getConfig("pagseguro_notification_url", "");
        if (isValidNotificationUrl(notificationUrl)) {
            payload.put("notification_urls", List.of(notificationUrl));
        } else if (StringUtils.hasText(notificationUrl)) {
            log.warn("PagSeguro: ignorando notification_url invalida: {}", notificationUrl);
        }

        Map<String, Object> qrCode = new HashMap<>();
        qrCode.put("amount", Map.of("value", amountCents));
        String expiration = resolvePixExpiration();
        if (StringUtils.hasText(expiration)) {
            qrCode.put("expiration_date", expiration);
        }
        payload.put("qr_codes", List.of(qrCode));

        return payload;
    }

    private String resolvePixExpiration() {
        int timeoutMinutes = configManager.getIntConfig("pagseguro_pix_timeout_minutes", 10);
        if (timeoutMinutes <= 0) {
            timeoutMinutes = 10;
        }
        OffsetDateTime expiration = OffsetDateTime.now().plusMinutes(timeoutMinutes).withNano(0);
        return expiration.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private PixData extractPixData(JsonNode payNode, JsonNode orderNode, JsonNode chargeNode) {
        PixData data = new PixData();
        JsonNode qrNode = firstQrNode(payNode);
        if (qrNode == null) {
            qrNode = firstQrNode(chargeNode);
        }
        if (qrNode == null) {
            qrNode = firstQrNode(orderNode);
        }
        if (qrNode != null) {
            data.qrCode = textOrNull(qrNode, "text");
            if (!StringUtils.hasText(data.qrCode)) {
                data.qrCode = textOrNull(qrNode, "qr_code");
            }
            data.qrCodeBase64 = extractBase64FromLinks(qrNode.path("links"));
            data.expiresAt = parseExpiration(qrNode);
        }

        if (data.expiresAt == null) {
            int timeoutMinutes = configManager.getIntConfig("pagseguro_pix_timeout_minutes", 10);
            if (timeoutMinutes > 0) {
                data.expiresAt = OffsetDateTime.now().plusMinutes(timeoutMinutes).toInstant();
            }
        }

        return data;
    }

    private JsonNode firstQrNode(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return null;
        }
        JsonNode qrCodes = root.path("qr_codes");
        if (qrCodes.isArray() && qrCodes.size() > 0) {
            return qrCodes.get(0);
        }
        JsonNode charges = root.path("charges");
        if (charges.isArray() && charges.size() > 0) {
            JsonNode charge = charges.get(0);
            JsonNode chargeQrCodes = charge.path("qr_codes");
            if (chargeQrCodes.isArray() && chargeQrCodes.size() > 0) {
                return chargeQrCodes.get(0);
            }
            JsonNode paymentMethodQr = charge.path("payment_method").path("pix").path("qr_codes");
            if (paymentMethodQr.isArray() && paymentMethodQr.size() > 0) {
                return paymentMethodQr.get(0);
            }
        }
        return null;
    }

    private Instant parseExpiration(JsonNode qrNode) {
        String expiration = textOrNull(qrNode, "expiration_date");
        if (!StringUtils.hasText(expiration)) {
            expiration = textOrNull(qrNode, "expiration_date_time");
        }
        if (!StringUtils.hasText(expiration)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(expiration).toInstant();
        } catch (Exception ignored) {
            try {
                LocalDateTime local = LocalDateTime.parse(expiration, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return local.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String extractBase64FromLinks(JsonNode links) {
        if (links == null || !links.isArray()) {
            return null;
        }
        for (JsonNode link : links) {
            String rel = textOrNull(link, "rel");
            String href = textOrNull(link, "href");
            if (!StringUtils.hasText(rel) || !StringUtils.hasText(href)) {
                continue;
            }
            if (!rel.toUpperCase().contains("QRCODE")) {
                continue;
            }
            if (href.startsWith("data:image")) {
                int comma = href.indexOf(',');
                return comma > 0 ? href.substring(comma + 1) : null;
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        return node.get(field).asText();
    }

    private static class PixData {
        private String qrCode;
        private String qrCodeBase64;
        private Instant expiresAt;
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String resolveBaseUrl() {
        boolean sandbox = configManager.getBooleanConfig("pagseguro_sandbox", true);
        String sandboxUrl = configManager.getConfig("pagseguro_sandbox_base_url", "https://sandbox.api.pagseguro.com");
        String prodUrl = configManager.getConfig("pagseguro_base_url", "https://api.pagseguro.com");
        String base = sandbox ? sandboxUrl : prodUrl;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String resolveToken() {
        String token = configManager.getConfig("pagseguro_token", "");
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("Token PagSeguro não configurado");
        }
        return token;
    }

    private int toCents(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Valor é obrigatório");
        }
        return amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }

    private boolean isValidNotificationUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String normalized = url.trim();
        if (normalized.contains("{{") || normalized.contains("}}") || normalized.contains("dominio")) {
            return false;
        }
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }
}
