package com.baronesa.website.controller.delivery;

import com.baronesa.website.dto.delivery.CreateDeliveryOrderRequest;
import com.baronesa.website.dto.delivery.CreateDeliveryOrderResponse;
import com.baronesa.website.service.delivery.DeliveryOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService orderService;

    @PostMapping
    public ResponseEntity<CreateDeliveryOrderResponse> create(@RequestBody CreateDeliveryOrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }
}
