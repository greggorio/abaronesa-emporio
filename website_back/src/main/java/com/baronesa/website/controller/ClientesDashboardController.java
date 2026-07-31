package com.baronesa.website.controller;

import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.AtivosResponse;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.ClienteAppAtivoResponse;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.EnviarBrindeRequest;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.EnviarBrindeResponse;
import com.baronesa.website.dto.dashboard.ClientesDashboardResponses.OportunidadesResponse;
import com.baronesa.website.service.ClientesDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes-dashboard")
@RequiredArgsConstructor
public class ClientesDashboardController {

    private final ClientesDashboardService service;

    @GetMapping("/ativos")
    public ResponseEntity<AtivosResponse> getAtivos(@RequestParam(name = "range", defaultValue = "30") int rangeDias) {
        return ResponseEntity.ok(service.getAtivos(rangeDias));
    }

    @GetMapping("/oportunidades")
    public ResponseEntity<OportunidadesResponse> getOportunidades(@RequestParam(name = "range", defaultValue = "30") int rangeDias) {
        return ResponseEntity.ok(service.getOportunidades(rangeDias));
    }

    @GetMapping("/app-ativos")
    public ResponseEntity<List<ClienteAppAtivoResponse>> getAppAtivos() {
        return ResponseEntity.ok(service.getAppAtivos());
    }

    @PostMapping("/enviar-brinde")
    public ResponseEntity<EnviarBrindeResponse> enviarBrinde(@RequestBody EnviarBrindeRequest request) {
        return ResponseEntity.ok(service.enviarBrinde(request));
    }
}
