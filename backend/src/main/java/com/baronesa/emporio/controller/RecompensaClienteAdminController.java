package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.RecompensaClienteDTO;
import com.baronesa.emporio.service.DashboardGamificacaoService;
import com.baronesa.emporio.service.GamificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/clientes")
@RequiredArgsConstructor
public class RecompensaClienteAdminController {

    private final DashboardGamificacaoService dashboardGamificacaoService;
    private final GamificacaoService gamificacaoService;

    @GetMapping("/{clienteId}/gamificacao/recompensas")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> getRecompensasDisponiveisParaCliente(@PathVariable Long clienteId) {
        List<RecompensaClienteDTO> recompensas = dashboardGamificacaoService.getRecompensasDisponiveisParaCliente(clienteId);
        Integer saldoCliente = gamificacaoService.getSaldoCliente(clienteId);
        
        Map<String, Object> response = Map.of(
            "saldoCliente", saldoCliente,
            "recompensas", recompensas
        );
        
        return ResponseEntity.ok(response);
    }
}