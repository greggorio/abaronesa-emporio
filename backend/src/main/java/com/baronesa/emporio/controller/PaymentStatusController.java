package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.payment.PaymentService;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/status")
@RequiredArgsConstructor
public class PaymentStatusController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus(
            @RequestParam String externalReference,
            @RequestParam(required = false) PaymentGatewayType gateway
    ) {
        var paymentOpt = paymentService.findByExternalReference(gateway, externalReference);
        if (paymentOpt.isEmpty()) {
            // Retorna pendente para evitar 404 no polling do front quando ainda não há registro.
            Map<String, Object> pending = new java.util.HashMap<>();
            pending.put("gateway", gateway);
            pending.put("providerPaymentId", null);
            pending.put("normalizedStatus", NormalizedPaymentStatus.PENDING);
            pending.put("providerStatus", "PENDING");
            pending.put("externalReference", externalReference);
            pending.put("amount", null);
            return ResponseEntity.ok(pending);
        }
        var p = paymentOpt.get();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("gateway", p.getGateway());
        body.put("providerPaymentId", p.getProviderPaymentId());
        body.put("normalizedStatus", p.getNormalizedStatus());
        body.put("providerStatus", p.getProviderStatus());
        body.put("externalReference", p.getExternalReference());
        body.put("amount", p.getAmount());
        return ResponseEntity.ok(body);
    }
}
