package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.payment.PaymentStatusUpdater;
import com.baronesa.emporio.service.payment.mapper.PagSeguroStatusMapper;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/pagseguro")
@RequiredArgsConstructor
@Slf4j
public class PagSeguroWebhookController {

    private final PagSeguroStatusMapper pagSeguroStatusMapper;
    private final PaymentStatusUpdater paymentStatusUpdater;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(@RequestBody String rawBody) {
        log.info("PagSeguro webhook recebido ({} bytes)", rawBody != null ? rawBody.length() : 0);
        PaymentStatusUpdate update = pagSeguroStatusMapper.fromPayload(rawBody);
        paymentStatusUpdater.onPaymentStatusUpdated(update);
        return ResponseEntity.ok().build();
    }
}
