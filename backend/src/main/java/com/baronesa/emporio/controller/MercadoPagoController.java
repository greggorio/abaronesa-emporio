package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.mercadopago.MercadoPagoPaymentRequest;
import com.baronesa.emporio.dto.mercadopago.MercadoPagoPaymentResponse;
import com.baronesa.emporio.service.MercadoPagoService;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/mercadopago")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoController {

    private final MercadoPagoService mercadoPagoService;
    private final ConfigManager configManager;

    @PostMapping("/payments")
    public ResponseEntity<MercadoPagoPaymentResponse> createPayment(
            @Valid @RequestBody MercadoPagoPaymentRequest payload) {
        log.info("Criando pagamento no Mercado Pago");
        return ResponseEntity.ok(mercadoPagoService.createPayment(payload));
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<MercadoPagoPaymentResponse> getPayment(@PathVariable String paymentId) {
        var payment = mercadoPagoService.findPayment(paymentId);
        return payment != null ? ResponseEntity.ok(payment) : ResponseEntity.notFound().build();
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        Map<String, Object> info = Map.of(
                "sandbox", configManager.getBooleanConfig("mercadopago_sandbox", true),
                "webhookConfigured", mercadoPagoService.isWebhookConfigured(),
                "notificationUrl", configManager.getConfig("mercadopago_webhook_url", "")
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/webhooks/history")
    public ResponseEntity<?> listWebhookHistory() {
        return ResponseEntity.ok(mercadoPagoService.getWebhookHistory());
    }
}
