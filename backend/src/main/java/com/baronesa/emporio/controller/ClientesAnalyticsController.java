package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ClientesResumoResponse;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/analytics/clientes")
public class ClientesAnalyticsController {

    private final ClienteRepository clienteRepository;

    @Value("${website.sync.api-key:default-key-for-dev}")
    private String websiteSyncApiKey;

    public ClientesAnalyticsController(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    @GetMapping("/resumo")
    public ResponseEntity<ClientesResumoResponse> getResumo(
            @RequestHeader(value = "X-ERP-KEY", required = false) String apiKey,
            @RequestParam(name = "range", defaultValue = "30") int rangeDias) {

        if (websiteSyncApiKey == null || !websiteSyncApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).build();
        }

        LocalDateTime createdAfter = LocalDateTime.now().minusDays(rangeDias);

        long totalClientes = clienteRepository.countByRole(Usuario.Role.CLIENTE);
        long novosPeriodo = clienteRepository.countByRoleAndCreatedAfter(Usuario.Role.CLIENTE, createdAfter);

        return ResponseEntity.ok(new ClientesResumoResponse(totalClientes, novosPeriodo, rangeDias));
    }
}
