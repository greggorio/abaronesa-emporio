package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.delivery.DeliveryCardPaymentRequest;
import com.baronesa.emporio.dto.delivery.DeliveryOrderRequest;
import com.baronesa.emporio.dto.delivery.DeliveryOrderView;
import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.DeliveryOrderService;
import com.baronesa.emporio.repository.DeliveryOrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/orders")
@RequiredArgsConstructor
public class DeliveryOrderController {

    private final DeliveryOrderService deliveryOrderService;
    private final DeliveryOrderRepository deliveryOrderRepository;

    @PostMapping
    public ResponseEntity<DeliveryOrder> create(
            @Valid @RequestBody DeliveryOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "X-User-ID", required = false) Long headerUserId
    ) {
        DeliveryOrder created = deliveryOrderService.createOrder(request, resolveUserId(principal, headerUserId));
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<DeliveryOrder> update(
            @PathVariable Long orderId,
            @Valid @RequestBody DeliveryOrderRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "X-User-ID", required = false) Long headerUserId
    ) {
        DeliveryOrder updated = deliveryOrderService.updateOrder(orderId, request, resolveUserId(principal, headerUserId));
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{orderId}/pay-card")
    public ResponseEntity<DeliveryOrder> payWithCard(
            @PathVariable Long orderId,
            @Valid @RequestBody DeliveryCardPaymentRequest request
    ) {
        DeliveryOrder updated = deliveryOrderService.payWithCard(orderId, request);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<DeliveryOrderView> get(@PathVariable Long orderId) {
        return deliveryOrderService.findWithItems(orderId)
                .map(deliveryOrderService::toView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{orderId}/raw")
    public ResponseEntity<DeliveryOrder> getRaw(@PathVariable Long orderId) {
        return deliveryOrderRepository.findById(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/my/active")
    public ResponseEntity<DeliveryOrderView> getActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "X-User-ID", required = false) Long headerUserId
    ) {
        Long userId = resolveUserId(principal, headerUserId);
        if (userId == null) {
            return ResponseEntity.noContent().build();
        }
        return deliveryOrderService.findActiveForUser(userId)
                .map(deliveryOrderService::toView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private Long resolveUserId(UserPrincipal principal, Long headerUserId) {
        if (principal != null) {
            return principal.getId();
        }
        return headerUserId;
    }
}
