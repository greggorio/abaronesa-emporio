package com.baronesa.website.controller.delivery;

import com.baronesa.website.dto.delivery.CreateDeliveryPaymentIntentRequest;
import com.baronesa.website.dto.delivery.CreateDeliveryPaymentIntentResponse;
import com.baronesa.website.dto.delivery.CreateDeliveryQuoteRequest;
import com.baronesa.website.dto.delivery.CreateDeliveryQuoteResponse;
import com.baronesa.website.dto.delivery.DeliveryPaymentWebhookRequest;
import com.baronesa.website.service.delivery.DeliveryPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/payments")
@RequiredArgsConstructor
public class DeliveryPaymentController {

    private final DeliveryPaymentService paymentService;

    @PostMapping("/intent")
    public ResponseEntity<CreateDeliveryPaymentIntentResponse> createIntent(
            @RequestBody CreateDeliveryPaymentIntentRequest request
    ) {
        return ResponseEntity.ok(paymentService.createPaymentIntent(request));
    }

    @PostMapping("/quote")
    public ResponseEntity<CreateDeliveryQuoteResponse> quote(
            @RequestBody CreateDeliveryQuoteRequest request
    ) {
        return ResponseEntity.ok(paymentService.createQuote(request));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody DeliveryPaymentWebhookRequest request) {
        paymentService.handleWebhook(request);
        return ResponseEntity.ok().build();
    }
}
