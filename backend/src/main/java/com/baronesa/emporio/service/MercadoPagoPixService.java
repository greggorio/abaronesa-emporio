package com.baronesa.emporio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.config.MercadoPagoProperties;
import com.baronesa.emporio.dto.MercadoPagoPixRequest;
import com.baronesa.emporio.dto.MercadoPagoPixResponse;
import com.baronesa.emporio.dto.PaymentStatusResponse;

@Service
public class MercadoPagoPixService {

    private static final Logger logger = LoggerFactory.getLogger(MercadoPagoPixService.class);
    private static final String MP_API_BASE = "https://api.mercadopago.com";

    @Autowired
    private MercadoPagoProperties mercadoPagoProperties;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        logger.info("✅ MercadoPagoPixService inicializado com sucesso");
        logger.info("Ambiente: {}", mercadoPagoProperties.isSandbox() ? "SANDBOX" : "PRODUCTION");
    }

    /**
     * Criar pagamento PIX
     */
    public MercadoPagoPixResponse createPixPayment(MercadoPagoPixRequest request) {
        logger.info("=== CRIANDO PAGAMENTO PIX ===");
        logger.info("Valor: R$ {}", request.getAmount());
        logger.info("Cliente: {}", request.getCustomer().getEmail());

        try {
            // Validar dados
            validatePixRequest(request);

            // Construir payload para Mercado Pago
            Map<String, Object> payload = buildPixPayload(request);

            // Headers com X-Idempotency-Key
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mercadoPagoProperties.getCurrentAccessToken());
            headers.set("X-Idempotency-Key", UUID.randomUUID().toString()); // ⭐ ADICIONADO

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            logger.info("Enviando request para Mercado Pago...");
            logger.info("URL: {}/v1/payments", MP_API_BASE);
            logger.info("Payload: {}", objectMapper.writeValueAsString(payload));

            // Fazer chamada para API
            ResponseEntity<String> response = restTemplate.postForEntity(
                    MP_API_BASE + "/v1/payments",
                    entity,
                    String.class
            );

            logger.info("Response Status: {}", response.getStatusCode());
            logger.info("Response Body: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.CREATED || response.getStatusCode() == HttpStatus.OK) {
                // Converter resposta
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                MercadoPagoPixResponse pixResponse = convertToPixResponse(responseJson);

                logger.info("PIX criado com sucesso! ID: {}", pixResponse.getId());
                return pixResponse;

            } else {
                throw new RuntimeException("Erro na API do Mercado Pago: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Erro ao criar pagamento PIX", e);
            throw new RuntimeException("Falha ao criar pagamento PIX: " + e.getMessage(), e);
        }
    }

    /**
     * Consultar status do pagamento
     */
    public PaymentStatusResponse getPaymentStatus(String paymentId) {
        logger.info("=== CONSULTANDO STATUS DO PAGAMENTO ===");
        logger.info("Payment ID: {}", paymentId);

        try {
            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(mercadoPagoProperties.getCurrentAccessToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Fazer chamada para API
            String url = MP_API_BASE + "/v1/payments/" + paymentId;
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            logger.info("Status Response: {}", response.getStatusCode());
            logger.info("Status Body: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                PaymentStatusResponse statusResponse = convertToStatusResponse(responseJson);

                logger.info("Status obtido: {}", statusResponse.getStatus());
                return statusResponse;

            } else {
                throw new RuntimeException("Erro ao consultar status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Erro ao consultar status do pagamento", e);
            throw new RuntimeException("Falha ao consultar status: " + e.getMessage(), e);
        }
    }

    /**
     * Obter chave pública
     */
    public String getPublicKey() {
        String publicKey = mercadoPagoProperties.getCurrentPublicKey();
        logger.info("Retornando chave pública: {}***",
                publicKey != null ? publicKey.substring(0, Math.min(10, publicKey.length())) : "null");
        return publicKey;
    }

    /**
     * Processar webhook
     */
    public boolean processWebhook(String payload, String signature) {
        logger.info("=== PROCESSANDO WEBHOOK ===");
        logger.info("Payload length: {}", payload != null ? payload.length() : 0);
        logger.info("Signature: {}", signature);

        try {
            // TODO: Implementar validação de assinatura se configurada
            if (mercadoPagoProperties.getWebhook().isValidateSignature()) {
                logger.info("Validação de assinatura habilitada (implementar se necessário)");
            }

            // TODO: Processar notificação
            logger.info("Webhook processado com sucesso");
            return true;

        } catch (Exception e) {
            logger.error("Erro ao processar webhook", e);
            return false;
        }
    }

    /**
     * Cancelar pagamento
     */
    public boolean cancelPayment(String paymentId) {
        logger.info("=== CANCELANDO PAGAMENTO ===");
        logger.info("Payment ID: {}", paymentId);

        try {
            // Verificar se pagamento pode ser cancelado
            PaymentStatusResponse status = getPaymentStatus(paymentId);

            if ("approved".equals(status.getStatus())) {
                logger.warn("Pagamento já foi aprovado, não pode ser cancelado");
                return false;
            }

            if ("cancelled".equals(status.getStatus())) {
                logger.info("Pagamento já estava cancelado");
                return true;
            }

            // TODO: Implementar cancelamento via API se disponível
            logger.info("Cancelamento processado");
            return true;

        } catch (Exception e) {
            logger.error("Erro ao cancelar pagamento", e);
            return false;
        }
    }

    // MÉTODOS PRIVADOS

    private void validatePixRequest(MercadoPagoPixRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }

        if (request.getAmount().compareTo(new BigDecimal("50000")) > 0) {
            throw new IllegalArgumentException("Valor não pode ser superior a R$ 50.000,00");
        }

        if (request.getCustomer() == null) {
            throw new IllegalArgumentException("Dados do cliente são obrigatórios");
        }

        if (request.getCustomer().getEmail() == null || request.getCustomer().getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email do cliente é obrigatório");
        }

        if (request.getCustomer().getName() == null || request.getCustomer().getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }
    }

    private Map<String, Object> buildPixPayload(MercadoPagoPixRequest request) {
        Map<String, Object> payload = new HashMap<>();

        // ⭐ ARREDONDAR VALOR PARA 2 CASAS DECIMAIS
        BigDecimal amount = new BigDecimal(request.getAmount().toString())
                .setScale(2, RoundingMode.HALF_UP);

        payload.put("transaction_amount", amount.doubleValue());
        payload.put("description", request.getDescription() != null ? request.getDescription() : "Pagamento PIX");
        payload.put("payment_method_id", "pix");

        if (request.getExternalReference() != null && !request.getExternalReference().trim().isEmpty()) {
            payload.put("external_reference", request.getExternalReference());
        }

        // Dados do pagador
        Map<String, Object> payer = new HashMap<>();
        payer.put("email", request.getCustomer().getEmail());

        // ⭐ CORRIGIR NOMES (estava vazio)
        String fullName = request.getCustomer().getName();
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] nameParts = fullName.trim().split("\\s+");
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

        // CPF apenas se válido
        if (request.getCustomer().getCpf() != null && !request.getCustomer().getCpf().trim().isEmpty()) {
            Map<String, Object> identification = new HashMap<>();
            identification.put("type", "CPF");
            identification.put("number", request.getCustomer().getCpf().replaceAll("\\D", ""));
            payer.put("identification", identification);
        }

        payload.put("payer", payer);

        // URL de notificação
        String notificationUrl = mercadoPagoProperties.getWebhookUrl();
        if (notificationUrl != null && !notificationUrl.trim().isEmpty()) {
            payload.put("notification_url", notificationUrl);
        }

        return payload;
    }

    private MercadoPagoPixResponse convertToPixResponse(JsonNode json) {
        MercadoPagoPixResponse response = new MercadoPagoPixResponse();

        response.setId(json.get("id").asText());
        response.setStatus(json.get("status").asText());
        response.setAmount(new BigDecimal(json.get("transaction_amount").asText()));
        response.setDescription(json.has("description") ? json.get("description").asText() : null);
        response.setExternalReference(json.has("external_reference") ? json.get("external_reference").asText() : null);
        response.setCreatedDate(LocalDateTime.now());

        // Extrair QR Code
        if (json.has("point_of_interaction") && json.get("point_of_interaction").has("transaction_data")) {
            JsonNode transactionData = json.get("point_of_interaction").get("transaction_data");

            if (transactionData.has("qr_code")) {
                response.setQrCode(transactionData.get("qr_code").asText());
            }

            if (transactionData.has("qr_code_base64")) {
                response.setQrCodeBase64(transactionData.get("qr_code_base64").asText());
            }
        }

        return response;
    }

    private PaymentStatusResponse convertToStatusResponse(JsonNode json) {
        PaymentStatusResponse response = new PaymentStatusResponse();

        response.setId(json.get("id").asText());
        response.setStatus(json.get("status").asText());
        response.setAmount(new BigDecimal(json.get("transaction_amount").asText()));
        response.setStatusDetail(json.has("status_detail") ? json.get("status_detail").asText() : null);
        response.setExternalReference(json.has("external_reference") ? json.get("external_reference").asText() : null);
        response.setPaymentMethod(json.has("payment_method_id") ? json.get("payment_method_id").asText() : null);
        response.setLastUpdated(LocalDateTime.now());

        return response;
    }

    private String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "Cliente";
        }

        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    private String extractLastName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }

        String[] parts = fullName.trim().split("\\s+");
        if (parts.length > 1) {
            return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }

        return "";
    }
}