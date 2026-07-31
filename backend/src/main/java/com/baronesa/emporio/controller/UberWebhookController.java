package com.baronesa.emporio.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.baronesa.emporio.service.UberWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/uber/webhooks")
@RequiredArgsConstructor
public class UberWebhookController {

    private final UberWebhookService webhookService;

    @PostMapping("/deliveries")
    public ResponseEntity<Void> handleDeliveryEvent(@RequestBody JsonNode payload) {
        webhookService.handleDeliveryEvent(payload);
        return ResponseEntity.accepted().build();
    }
}
