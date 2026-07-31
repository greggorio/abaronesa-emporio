package com.baronesa.emporio.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.baronesa.emporio.dto.mercadopago.MercadoPagoCustomer;
import com.baronesa.emporio.dto.mercadopago.MercadoPagoPaymentRequest;
import com.baronesa.emporio.dto.mercadopago.MercadoPagoPaymentResponse;
import com.baronesa.emporio.util.ConfigManager;
import com.baronesa.emporio.service.payment.PaymentStatusUpdater;
import com.baronesa.emporio.service.payment.mapper.MercadoPagoStatusMapper;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoPagoService {

    private final ConfigManager configManager;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentStatusUpdater paymentStatusUpdater;
    private final MercadoPagoStatusMapper mercadoPagoStatusMapper;
    private final ConcurrentHashMap<String, MercadoPagoPaymentResponse> payments = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> webhookHistory = new CopyOnWriteArrayList<>();
    private static final DateTimeFormatter PIX_EXPIRATION_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'UTC'");

    public MercadoPagoPaymentResponse createPayment(MercadoPagoPaymentRequest request) {
        validateRequest(request);
        Map<String, Object> payload = buildPayload(request);

        try {
            JsonNode response = pagarCartao(payload);
            MercadoPagoPaymentResponse parsed = parsePaymentResponse(response, request);
            payments.put(parsed.getId(), parsed);
            notifyDeliveryIfApproved(parsed, response);
            return parsed;
        } catch (Exception e) {
            throw new RuntimeException("Erro Mercado Pago: " + e.getMessage(), e);
        }
    }

    public MercadoPagoPaymentResponse findPayment(String paymentId) {
        if (StringUtils.hasText(paymentId) && payments.containsKey(paymentId)) {
            return payments.get(paymentId);
        }

        try {
            JsonNode resp = consultarPagamento(paymentId);
            return parsePaymentResponse(resp, null);
        } catch (Exception e) {
            log.warn("Falha ao consultar pagamento {}: {}", paymentId, e.getMessage());
            return null;
        }
    }

    public List<Map<String, Object>> getWebhookHistory() {
        return Collections.unmodifiableList(webhookHistory);
    }

    public boolean isWebhookConfigured() {
        return StringUtils.hasText(configManager.getConfig("mercadopago_webhook_url", "")) ||
                StringUtils.hasText(configManager.getConfig("mercadopago_webhook_secret", ""));
    }

    /**
     * Obtém o access token baseado no ambiente configurado (sandbox/produção)
     */
    private String getAccessToken() {
        boolean isSandbox = configManager.getBooleanConfig("mercadopago_sandbox", true);

        if (isSandbox) {
            // Primeiro tenta buscar o token específico do sandbox
            String sandboxToken = configManager.getConfig("mercadopago_access_token_sandbox", "");
            if (!sandboxToken.isEmpty()) {
                return sandboxToken;
            }
        } else {
            // Para produção, busca o token de produção
            String productionToken = configManager.getConfig("mercadopago_access_token_production", "");
            if (!productionToken.isEmpty()) {
                return productionToken;
            }
        }

        // Fallback para compatibilidade com configuração antiga
        return configManager.getConfig("mercadopago_access_token", "");
    }

    /**
     * Obtém a URL da API baseado no ambiente
     */
    private String getApiUrl() {
        boolean isSandbox = configManager.getBooleanConfig("mercadopago_sandbox", true);
        String baseUrl = isSandbox ?
                configManager.getConfig("mercadopago_sandbox_base_url", "https://api.mercadopago.com") :
                configManager.getConfig("mercadopago_base_url", "https://api.mercadopago.com");

        return baseUrl + "/v1/payments";
    }

    /**
     * Cria headers HTTP padrão para requisições ao Mercado Pago
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAccessToken());

        // Adicionando o header X-Idempotency-Key conforme exigência do Mercado Pago
        headers.add("X-Idempotency-Key", UUID.randomUUID().toString());

        return headers;
    }

    public JsonNode pagarCartao(Map<String, Object> paymentData) throws Exception {
        log.info("Iniciando pagamento com cartão via MercadoPago");

        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentData, headers);

        String apiUrl = getApiUrl();
        log.debug("Chamando API MercadoPago: {}", apiUrl);

        ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Pagamento com cartão processado com sucesso");
            return objectMapper.readTree(response.getBody());
        } else {
            String errorMsg = "MercadoPago erro: " + response.getBody();
            log.error(errorMsg);
            throw new Exception(errorMsg);
        }
    }

    /**
     * Consulta um pagamento pelo ID (método usado pelo VendasOnlineController)
     */
    public JsonNode consultarPagamento(String paymentId) throws Exception {
        return consultarStatusPagamento(paymentId);
    }

    public JsonNode consultarStatusPagamento(String paymentId) throws Exception {
        log.info("Consultando status do pagamento: {}", paymentId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        String url = getApiUrl() + "/" + paymentId;
        log.debug("Consultando URL: {}", url);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Status do pagamento consultado com sucesso");
            return objectMapper.readTree(response.getBody());
        } else {
            String errorMsg = "MercadoPago consulta erro: " + response.getBody();
            log.error(errorMsg);
            throw new Exception(errorMsg);
        }
    }

    public JsonNode criarPix(Map<String, Object> paymentData) throws Exception {
        log.info("Criando pagamento PIX via MercadoPago");

        // Verifica se está configurado corretamente para PIX
        if (!paymentData.containsKey("payment_method_id")) {
            paymentData.put("payment_method_id", "pix");
        }

        // Aplica timeout específico para PIX se configurado
        // MP rejeita formatos invalidos de data; mantemos sem expiracao ate validar formato aceito.

        return pagarCartao(paymentData);
    }

    public JsonNode criarBoleto(Map<String, Object> paymentData) throws Exception {
        log.info("Criando boleto via MercadoPago");

        // Configura payment_method_id para boleto se não estiver definido
        if (!paymentData.containsKey("payment_method_id")) {
            paymentData.put("payment_method_id", "bolbradesco"); // ou outro banco configurado
        }

        return pagarCartao(paymentData);
    }

    /**
     * Método para verificar se as configurações estão válidas
     */
    public boolean isConfigurationValid() {
        String accessToken = getAccessToken();
        boolean isValid = accessToken != null && !accessToken.trim().isEmpty();

        if (!isValid) {
            log.warn("Configuração do MercadoPago inválida - Access Token não encontrado");
        }

        return isValid;
    }

    /**
     * Método para obter informações de configuração (para debug/admin)
     */
    public Map<String, Object> getConfigurationInfo() {
        boolean isSandbox = configManager.getBooleanConfig("mercadopago_sandbox", true);

        return Map.of(
                "sandbox", isSandbox,
                "hasAccessToken", !getAccessToken().isEmpty(),
                "apiUrl", getApiUrl(),
                "pixTimeoutMinutes", configManager.getIntConfig("mercadopago_pix_timeout_minutes", 10),
                "webhookUrl", configManager.getConfig("mercadopago_webhook_url", "")
        );
    }

    private Map<String, Object> buildPayload(MercadoPagoPaymentRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction_amount", request.getAmount());
        payload.put("payment_method_id", request.getPaymentMethodId());
        payload.put("description", StringUtils.hasText(request.getDescription()) ? request.getDescription() : "Pagamento Mercado Pago");
        if (StringUtils.hasText(request.getToken())) {
            payload.put("token", request.getToken());
        }
        if (StringUtils.hasText(request.getExternalReference())) {
            payload.put("external_reference", request.getExternalReference());
        }

        boolean isPix = "pix".equalsIgnoreCase(request.getPaymentMethodId());
        if (!isPix) {
            payload.put("installments", request.getInstallments() != null ? request.getInstallments() : 1);
        }
        // PIX sem date_of_expiration ate ajustarmos formato aceito pelo Mercado Pago.

        MercadoPagoCustomer customer = request.getCustomer();
        Map<String, Object> payer = new HashMap<>();
        payer.put("email", customer.getEmail());
        String fullName = customer.getName() != null ? customer.getName().trim() : "";
        if (!fullName.isEmpty()) {
            String[] nameParts = fullName.split("\\s+");
            payer.put("first_name", nameParts[0]);
            if (nameParts.length > 1) {
                payer.put("last_name", String.join(" ", Arrays.copyOfRange(nameParts, 1, nameParts.length)));
            } else {
                payer.put("last_name", "");
            }
        } else {
            payer.put("first_name", "Cliente");
            payer.put("last_name", "");
        }
        if (StringUtils.hasText(customer.getCpf())) {
            payer.put("identification", Map.of("type", "CPF", "number", customer.getCpf().replaceAll("\\D", "")));
        }
        payload.put("payer", payer);

        String notificationUrl = configManager.getConfig("mercadopago_webhook_url",
                configManager.getConfig("mercadopago_notification_url", ""));
        if (StringUtils.hasText(notificationUrl)) {
            payload.put("notification_url", notificationUrl);
        }

        return payload;
    }

    private void validateRequest(MercadoPagoPaymentRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }

        if (!StringUtils.hasText(request.getPaymentMethodId())) {
            throw new IllegalArgumentException("Método de pagamento é obrigatório");
        }

        MercadoPagoCustomer customer = request.getCustomer();
        if (customer == null || !StringUtils.hasText(customer.getEmail())) {
            throw new IllegalArgumentException("Dados do cliente são obrigatórios");
        }
    }

    private MercadoPagoPaymentResponse parsePaymentResponse(JsonNode node, MercadoPagoPaymentRequest originalRequest) {
        try {
            String id = node.path("id").asText(UUID.randomUUID().toString());
            String status = node.path("status").asText();
            String statusDetail = node.path("status_detail").asText();
            BigDecimal amount = node.path("transaction_amount").decimalValue();
            String paymentMethodId = node.path("payment_method_id").asText();
            String externalReference = node.path("external_reference").asText();
            boolean isPix = "pix".equalsIgnoreCase(paymentMethodId);
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime maxPixExpiration = null;
            if (isPix) {
                int timeoutMinutes = configManager.getIntConfig("mercadopago_pix_timeout_minutes", 10);
                if (timeoutMinutes > 0) {
                    maxPixExpiration = now.plusMinutes(timeoutMinutes);
                }
            }
            String pixQr = node.path("point_of_interaction").path("transaction_data").path("qr_code").asText(null);
            String pixQrBase64 = node.path("point_of_interaction").path("transaction_data").path("qr_code_base64").asText(null);
            OffsetDateTime expiration = null;
            if (node.hasNonNull("date_of_expiration")) {
                expiration = OffsetDateTime.parse(node.get("date_of_expiration").asText());
            }
            if (isPix) {
                // Quando o MP nao retorna expiracao valida, usamos fallback e limitamos ao timeout configurado.
                if (expiration == null || !expiration.isAfter(now.plusMinutes(1))) {
                    expiration = maxPixExpiration;
                } else if (maxPixExpiration != null && expiration.isAfter(maxPixExpiration)) {
                    expiration = maxPixExpiration;
                }
            }

            return MercadoPagoPaymentResponse.builder()
                    .id(id)
                    .status(status)
                    .statusDetail(statusDetail)
                    .amount(amount)
                    .paymentMethodId(paymentMethodId)
                    .externalReference(externalReference)
                    .createdAt(Instant.now())
                    .dateOfExpiration(expiration)
                    .pixQrCode(pixQr)
                    .pixQrCodeBase64(pixQrBase64)
                    .environment(configManager.getBooleanConfig("mercadopago_sandbox", true) ? "sandbox" : "production")
                    .success("approved".equalsIgnoreCase(status))
                    .metadata(Map.of(
                            "raw", node,
                            "request", originalRequest
                    ))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao ler resposta do Mercado Pago", e);
        }
    }

    private void notifyDeliveryIfApproved(MercadoPagoPaymentResponse parsed, JsonNode rawResponse) {
        if (parsed == null || rawResponse == null) return;
        if (!"approved".equalsIgnoreCase(parsed.getStatus())) return;
        try {
            PaymentStatusUpdate update = mercadoPagoStatusMapper.fromProviderPayload(
                    parsed.getId(),
                    parsed.getStatus(),
                    rawResponse.toString()
            );
            paymentStatusUpdater.onPaymentStatusUpdated(update);
        } catch (Exception e) {
            log.warn("Falha ao notificar delivery status imediato pagamentoId={}", parsed.getId(), e);
        }
    }

    private String formatPixExpiration(OffsetDateTime expiration) {
        return expiration.withOffsetSameInstant(ZoneOffset.UTC).format(PIX_EXPIRATION_FORMATTER);
    }
}
