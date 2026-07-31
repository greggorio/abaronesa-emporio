package com.baronesa.emporio.controller;

import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/pagseguro")
@RequiredArgsConstructor
public class PaymentPagSeguroConfigController {

    private final ConfigManager configManager;

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        String key = configManager.getConfig("pagseguro_public_key", "");
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagseguro_public_key não configurada");
        }
        return ResponseEntity.ok(Map.of("publicKey", key));
    }
}
