package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ContaConvidadoResponse;
import com.baronesa.emporio.dto.ContaMesaResponse;
import com.baronesa.emporio.service.ContaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conta")
@RequiredArgsConstructor
public class ContaController {

    private final ContaService contaService;

    @GetMapping
    public ResponseEntity<?> obterConta(@RequestParam(required = false) Long sessaoMesaId,
                                        @RequestParam(required = false) Long sessaoConvidadoId) {
        if (sessaoMesaId != null) {
            ContaMesaResponse resp = contaService.contaMesa(sessaoMesaId);
            return ResponseEntity.ok(resp);
        }
        if (sessaoConvidadoId != null) {
            ContaConvidadoResponse resp = contaService.contaConvidado(sessaoConvidadoId);
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.badRequest().body(
                java.util.Map.of("error", java.util.Map.of(
                        "code", "bad_request",
                        "message", "Informe sessaoMesaId ou sessaoConvidadoId"
                ))
        );
    }
}

