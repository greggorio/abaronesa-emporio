package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.payment.PaymentFacadeService;
import com.baronesa.emporio.service.payment.api.CardPaymentRequest;
import com.baronesa.emporio.service.payment.api.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentCardController {

    private final PaymentFacadeService paymentFacadeService;

    @PostMapping("/card")
    public ResponseEntity<PaymentResponse> createCardPayment(@Valid @RequestBody CardPaymentRequest request) {
        PaymentResponse response = paymentFacadeService.createCardPayment(request);
        return ResponseEntity.ok(response);
    }
}
