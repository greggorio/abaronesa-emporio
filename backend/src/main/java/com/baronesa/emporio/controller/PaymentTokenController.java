package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.payment.PaymentTokenFacadeService;
import com.baronesa.emporio.service.payment.api.CardTokenRequest;
import com.baronesa.emporio.service.payment.api.CardTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentTokenController {

    private final PaymentTokenFacadeService paymentTokenFacadeService;

    @PostMapping("/create-token")
    public ResponseEntity<CardTokenResponse> createToken(@Valid @RequestBody CardTokenRequest request) {
        CardTokenResponse response = paymentTokenFacadeService.createToken(request);
        return ResponseEntity.ok(response);
    }
}
