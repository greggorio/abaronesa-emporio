package com.baronesa.emporio.service.payment.mercadopago;

import com.baronesa.emporio.config.MPConfig;
import com.baronesa.emporio.service.payment.api.CardTokenRequest;
import com.baronesa.emporio.service.payment.api.CardTokenResponse;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoTokenService {

    private final MPConfig mpConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    public CardTokenResponse createToken(CardTokenRequest request) {
        try {
            String url = mpConfig.getApiUrl() + "/v1/card_tokens";

            int expMonth = parseIntSafe(request.getCardExpirationMonth(), "expiration_month");
            int expYear = parseIntSafe(request.getCardExpirationYear(), "expiration_year");
            if (expMonth < 1 || expMonth > 12) {
                throw new IllegalArgumentException("expiration_month inválido");
            }
            if (String.valueOf(expYear).length() != 4) {
                throw new IllegalArgumentException("expiration_year inválido");
            }

            Map<String, Object> tokenPayload = Map.of(
                    "card_number", request.getCardNumber(),
                    "cardholder", Map.of(
                            "name", request.getCardholderName(),
                            "identification", Map.of(
                                    "type", request.getIdentificationType(),
                                    "number", request.getIdentificationNumber()
                            )
                    ),
                    "expiration_month", expMonth,
                    "expiration_year", expYear,
                    "security_code", request.getSecurityCode()
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(mpConfig.getAccessToken());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(tokenPayload, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("id")) {
                return CardTokenResponse.builder()
                        .success(true)
                        .gateway(PaymentGatewayType.MERCADOPAGO)
                        .token(String.valueOf(responseBody.get("id")))
                        .cardInfo(Map.of(
                                "firstSixDigits", responseBody.get("first_six_digits"),
                                "lastFourDigits", responseBody.get("last_four_digits")
                        ))
                        .build();
            } else {
                return CardTokenResponse.builder()
                        .success(false)
                        .gateway(PaymentGatewayType.MERCADOPAGO)
                        .error("Token não foi criado")
                        .build();
            }

        } catch (Exception e) {
            log.error("Erro ao criar token via Mercado Pago", e);
            return CardTokenResponse.builder()
                    .success(false)
                    .gateway(PaymentGatewayType.MERCADOPAGO)
                    .error(e.getMessage())
                    .build();
        }
    }

    private int parseIntSafe(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " obrigatório");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " inválido");
        }
    }
}
