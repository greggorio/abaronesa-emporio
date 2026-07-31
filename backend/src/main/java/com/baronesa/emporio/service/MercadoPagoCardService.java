package com.baronesa.emporio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.baronesa.emporio.config.MPConfig;
import com.baronesa.emporio.config.MercadoPagoProperties;
import com.baronesa.emporio.dto.MercadoPagoCardRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class MercadoPagoCardService {

    private final MercadoPagoProperties mercadoPagoProperties;
    private final RestTemplate restTemplate;
    private final MPConfig mpConfig;

    public Map<String, Object> processCardPayment(MercadoPagoCardRequest request) {
        try {
            log.info("=== PROCESSANDO PAGAMENTO CARTÃO ===");
            log.info("Valor: {}", request.getAmount());
            log.info("Parcelas: {}", request.getInstallments());
            log.info("Token: {}", request.getToken());

            // Construir payload para MP
            Map<String, Object> payload = buildCardPayload(request);
            log.info("Payload: {}", payload);

            // Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mpConfig.getAccessToken());
            headers.set("X-Idempotency-Key", UUID.randomUUID().toString());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

            // Chamar API do MP
            String mpUrl = "https://api.mercadopago.com/v1/payments";
            ResponseEntity<Map> response = restTemplate.exchange(
                    mpUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            log.info("Resposta MP: {}", response.getBody());

            // Processar resposta
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> mpData = response.getBody();

            result.put("success", true);
            result.put("id", mpData.get("id"));
            result.put("status", mpData.get("status"));
            result.put("status_detail", mpData.get("status_detail"));
            result.put("amount", mpData.get("transaction_amount"));
            result.put("installments", mpData.get("installments"));
            result.put("payment_method", mpData.get("payment_method_id"));
            result.put("external_reference", mpData.get("external_reference"));

            // Dados do cartão (sem informações sensíveis)
            if (mpData.containsKey("card")) {
                Map<String, Object> cardInfo = (Map<String, Object>) mpData.get("card");
                result.put("card_last_digits", cardInfo.get("last_four_digits"));
                result.put("card_brand", cardInfo.get("first_six_digits"));
            }

            return result;

        } catch (Exception e) {
            log.error("Erro ao processar pagamento com cartão", e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("mensagem", "Erro ao processar pagamento: " + e.getMessage());
            return error;
        }
    }

    private Map<String, Object> buildCardPayload(MercadoPagoCardRequest request) {
        Map<String, Object> payload = new HashMap<>();

        // Valor arredondado
        BigDecimal amount = new BigDecimal(request.getAmount().toString())
                .setScale(2, RoundingMode.HALF_UP);

        payload.put("transaction_amount", amount.doubleValue());
        payload.put("token", request.getToken());
        payload.put("installments", request.getInstallments());
        payload.put("payment_method_id", request.getPaymentMethodId());
        payload.put("description", request.getDescription() != null ? request.getDescription() : "Pagamento cartão");

        if (request.getExternalReference() != null && !request.getExternalReference().trim().isEmpty()) {
            payload.put("external_reference", request.getExternalReference());
        }

        // Dados do pagador
        Map<String, Object> payer = new HashMap<>();
        payer.put("email", request.getCustomer().getEmail());

        // Nome
        String fullName = request.getCustomer().getName();
        if (fullName != null && !fullName.trim().isEmpty()) {
            String[] nameParts = fullName.trim().split("\\s+");
            payer.put("first_name", nameParts[0]);
            if (nameParts.length > 1) {
                payer.put("last_name", String.join(" ", Arrays.copyOfRange(nameParts, 1, nameParts.length)));
            } else {
                payer.put("last_name", "");
            }
        }

        // CPF
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
            payload.put("notification_url", notificationUrl + "/webhook-card");
        }

        return payload;
    }
}