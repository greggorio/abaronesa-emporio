package com.baronesa.emporio.controller;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.baronesa.emporio.config.MPConfig;
import com.baronesa.emporio.dto.MercadoPagoCardRequest;
import com.baronesa.emporio.service.MercadoPagoCardService;
import com.baronesa.emporio.service.payment.mercadopago.MercadoPagoTokenService;
import com.baronesa.emporio.service.payment.api.CardTokenRequest;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoCardController {

    private final MercadoPagoCardService cardService;

    private final MPConfig mpConfig;
    private final MercadoPagoTokenService mercadoPagoTokenService;

    @PostMapping("/process-card")
    public ResponseEntity<Map<String, Object>> processCard(@Valid @RequestBody MercadoPagoCardRequest request) {
        log.info("=== RECEBENDO PAGAMENTO CARTÃO ===");
        log.info("Valor: {}", request.getAmount());
        log.info("Parcelas: {}", request.getInstallments());

        Map<String, Object> result = cardService.processCardPayment(request);

        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/create-token")
    public ResponseEntity<Map<String, Object>> createToken(@Valid @RequestBody CreateTokenRequest request) {
        log.info("=== CRIANDO TOKEN VIA API ===");
        log.info("Cartão: {}", request.getCardNumber().substring(0, 4) + "****");

        try {
            var tokenRequest = new CardTokenRequest();
            tokenRequest.setCardNumber(request.getCardNumber());
            tokenRequest.setCardholderName(request.getCardholderName());
            tokenRequest.setCardExpirationMonth(request.getCardExpirationMonth());
            tokenRequest.setCardExpirationYear(request.getCardExpirationYear());
            tokenRequest.setSecurityCode(request.getSecurityCode());
            tokenRequest.setIdentificationType(request.getIdentificationType());
            tokenRequest.setIdentificationNumber(request.getIdentificationNumber());

            var resp = mercadoPagoTokenService.createToken(tokenRequest);
            if (resp.isSuccess()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("token", resp.getToken());
                result.put("cardInfo", resp.getCardInfo());
                return ResponseEntity.ok(result);
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", resp.getError());
                return ResponseEntity.badRequest().body(error);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao criar token via API: ", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    // DTO para o request
    @Data
    public static class CreateTokenRequest {
        @NotBlank
        private String cardNumber;

        @NotBlank
        private String cardholderName;

        @NotBlank
        private String cardExpirationMonth;

        @NotBlank
        private String cardExpirationYear;

        @NotBlank
        private String securityCode;

        private String identificationType = "CPF";

        @NotBlank
        private String identificationNumber;
    }
}
