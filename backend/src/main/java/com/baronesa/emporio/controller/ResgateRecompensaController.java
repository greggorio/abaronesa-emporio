package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ResgateRecompensaRequest;
import com.baronesa.emporio.dto.ResgateRecompensaResponse;
import com.baronesa.emporio.service.ResgateRecompensaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/gamificacao")
@RequiredArgsConstructor
public class ResgateRecompensaController {

    private final ResgateRecompensaService resgateRecompensaService;

    @PostMapping("/resgates")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<ResgateRecompensaResponse> resgatarRecompensa(@Valid @RequestBody ResgateRecompensaRequest request) {
        ResgateRecompensaResponse response = resgateRecompensaService.resgatarRecompensa(request);
        return ResponseEntity.ok(response);
    }
}