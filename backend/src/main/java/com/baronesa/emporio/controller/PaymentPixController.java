package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.payment.PaymentFacadeService;
import com.baronesa.emporio.service.payment.api.PaymentResponse;
import com.baronesa.emporio.service.payment.api.PixPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentPixController {

    private final PaymentFacadeService paymentFacadeService;

    @PostMapping("/pix")
    public ResponseEntity<PaymentResponse> createPixPayment(@Valid @RequestBody PixPaymentRequest request) {
        try {
            PaymentResponse response = paymentFacadeService.createPixPayment(request);
            return ResponseEntity.ok(response);
        } catch (UnsupportedOperationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
