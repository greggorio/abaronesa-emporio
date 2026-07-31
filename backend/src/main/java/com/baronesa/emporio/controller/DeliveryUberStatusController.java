package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.DeliveryUberStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/uber")
@RequiredArgsConstructor
@Slf4j
public class DeliveryUberStatusController {

    private final DeliveryUberStatusService deliveryUberStatusService;

    @PostMapping("/status")
    public ResponseEntity<Void> updateStatus(@RequestBody DeliveryUberStatusRequest request) {
        deliveryUberStatusService.updateStatus(request.deliveryId(), request.status(), request.externalId(), null);
        return ResponseEntity.ok().build();
    }

    public record DeliveryUberStatusRequest(String deliveryId, String status, String externalId) {}
}
