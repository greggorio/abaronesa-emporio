package com.baronesa.emporio.service.payment.pagseguro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.service.payment.api.CardTokenRequest;
import com.baronesa.emporio.service.payment.api.CardTokenResponse;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagSeguroTokenClient {

    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public CardTokenResponse createCardToken(CardTokenRequest request) {
        try {
            String baseUrl = resolveBaseUrl();
            String url = baseUrl + "/tokens/cards";

            Map<String, Object> payload = buildPayload(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resolveToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            JsonNode node = objectMapper.readTree(response.getBody());
            String tokenId = node.path("id").asText(null);
            String firstDigits = node.path("first_digits").asText(null);
            String lastDigits = node.path("last_digits").asText(null);

            if (!StringUtils.hasText(tokenId)) {
                return CardTokenResponse.builder()
                        .success(false)
                        .gateway(PaymentGatewayType.PAGSEGURO)
                        .error("Token não retornado pelo PagSeguro")
                        .build();
            }

            Map<String, Object> cardInfo = new HashMap<>();
            if (StringUtils.hasText(firstDigits)) {
                cardInfo.put("firstSixDigits", firstDigits);
            }
            if (StringUtils.hasText(lastDigits)) {
                cardInfo.put("lastFourDigits", lastDigits);
            }

            return CardTokenResponse.builder()
                    .success(true)
                    .gateway(PaymentGatewayType.PAGSEGURO)
                    .token(tokenId)
                    .cardInfo(cardInfo.isEmpty() ? null : cardInfo)
                    .build();

        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            log.warn("PagSeguro token error: status={} body={}", e.getStatusCode(), body);
            return CardTokenResponse.builder()
                    .success(false)
                    .gateway(PaymentGatewayType.PAGSEGURO)
                    .error("HTTP " + e.getStatusCode().value() + " - " + body)
                    .build();
        } catch (Exception e) {
            log.error("Erro ao criar token via PagSeguro", e);
            return CardTokenResponse.builder()
                    .success(false)
                    .gateway(PaymentGatewayType.PAGSEGURO)
                    .error(e.getMessage())
                    .build();
        }
    }

    private Map<String, Object> buildPayload(CardTokenRequest request) {
        int expMonth = parseIntSafe(request.getCardExpirationMonth(), "exp_month");
        int expYear = parseIntSafe(request.getCardExpirationYear(), "exp_year");
        if (expMonth < 1 || expMonth > 12) {
            throw new IllegalArgumentException("exp_month inválido");
        }
        if (String.valueOf(expYear).length() != 4) {
            throw new IllegalArgumentException("exp_year inválido");
        }

        if (!StringUtils.hasText(request.getCardNumber())) {
            throw new IllegalArgumentException("card number obrigatório");
        }
        if (!StringUtils.hasText(request.getSecurityCode())) {
            throw new IllegalArgumentException("security_code obrigatório");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("number", request.getCardNumber());
        payload.put("exp_month", expMonth);
        payload.put("exp_year", expYear);
        payload.put("security_code", request.getSecurityCode());
        Map<String, Object> holder = new HashMap<>();
        holder.put("name", request.getCardholderName());
        if (StringUtils.hasText(request.getIdentificationNumber())) {
            String taxId = request.getIdentificationNumber().replaceAll("\\D", "");
            holder.put("tax_id", taxId);
        }
        payload.put("holder", holder);
        return payload;
    }

    private int parseIntSafe(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " obrigatório");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " inválido");
        }
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
}
