package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.EventoDashboardDTO;
import com.baronesa.emporio.service.EventoEspressoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard/eventos")
@RequiredArgsConstructor
public class EventoDashboardController {

    private final EventoEspressoService eventoEspressoService;

    @GetMapping
    public ResponseEntity<EventoDashboardDTO> getDadosEventos(@RequestParam(defaultValue = "30d") String periodo) {
        EventoDashboardDTO dados = eventoEspressoService.buscarDadosDashboard(periodo);
        return ResponseEntity.ok(dados);
    }
}