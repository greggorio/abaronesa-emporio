package com.baronesa.website.controller.delivery;

import com.baronesa.website.dto.delivery.DeliveryQueueResponse;
import com.baronesa.website.enums.delivery.DeliveryItemStatus;
import com.baronesa.website.service.delivery.DeliveryKdsService;
import com.baronesa.website.service.delivery.DeliveryOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/kds")
@RequiredArgsConstructor
public class DeliveryKdsController {

    private final DeliveryKdsService kdsService;
    private final DeliveryOrderService orderService;

    @GetMapping("/queue")
    public ResponseEntity<DeliveryQueueResponse> queue() {
        return ResponseEntity.ok(kdsService.getQueue());
    }

    @PatchMapping("/tickets/{deliveryItemId}")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long deliveryItemId,
            @RequestBody DeliveryKdsStatusRequest request
    ) {
        DeliveryItemStatus status = DeliveryItemStatus.valueOf(request.status());
        orderService.updateItemStatus(deliveryItemId, status);
        return ResponseEntity.ok().build();
    }

    public record DeliveryKdsStatusRequest(String status) {}
}
