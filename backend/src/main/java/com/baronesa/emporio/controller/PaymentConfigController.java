package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.payment.PaymentSettingsService;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/config")
@RequiredArgsConstructor
public class PaymentConfigController {

    private final PaymentSettingsService paymentSettingsService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> body = new HashMap<>();
        PaymentGatewayType active = paymentSettingsService.getActiveGateway();
        List<PaymentGatewayType> available = paymentSettingsService.getAvailableGateways();
        body.put("activeGateway", active);
        body.put("availableGateways", available);
        body.put("installments", paymentSettingsService.getInstallmentSettings(active));
        return ResponseEntity.ok(body);
    }
}
