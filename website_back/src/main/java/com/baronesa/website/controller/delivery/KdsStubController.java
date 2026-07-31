package com.baronesa.website.controller.delivery;

import com.baronesa.website.dto.delivery.DeliveryQueueResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/api/kds")
public class KdsStubController {

    @GetMapping("/queue")
    public ResponseEntity<DeliveryQueueResponse> queue() {
        return ResponseEntity.ok(new DeliveryQueueResponse(Collections.emptyList()));
    }

    @PatchMapping("/tickets/{itemPedidoId}")
    public ResponseEntity<Void> updateStatus(@PathVariable Long itemPedidoId, @RequestBody(required = false) String body) {
        return ResponseEntity.ok().build();
    }
}
