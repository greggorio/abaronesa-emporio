package com.baronesa.website.controller;

import com.baronesa.website.dto.ClienteRefResponse;
import com.baronesa.website.dto.ClienteRefSyncRequest;
import com.baronesa.website.entity.ClienteRef;
import com.baronesa.website.security.CustomUserPrincipal;
import com.baronesa.website.service.ClienteRefService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clientes-ref")
@RequiredArgsConstructor
public class ClienteRefController {

    private final ClienteRefService clienteRefService;

    @Value("${website.sync.api-key:#{null}}")
    private String websiteSyncApiKey;

    @PutMapping("/{id}")
    public ResponseEntity<ClienteRef> upsertClienteRef(@PathVariable Long id, @RequestBody UpsertClienteRefRequest request) {
        try {
            // Verificar se o usuário está autenticado e tem role ADMIN/SYSTEM/FUNCIONARIO
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                if (!principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_SYSTEM") && !principal.hasRole("ROLE_FUNCIONARIO")) {
                    return ResponseEntity.status(403).build();
                }
            } else {
                return ResponseEntity.status(401).build();
            }

            // Validar campos obrigatórios
            if (request.getNome() == null || request.getNome().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            ClienteRef clienteRef = clienteRefService.upsert(id, request.getNome(), request.getEmail());
            return ResponseEntity.ok(clienteRef);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<ClienteRef> syncClienteRef(@RequestHeader(value = "X-ERP-KEY", required = false) String apiKey, @RequestBody ClienteRefSyncRequest request) {
        try {
            // Validar API key
            if (websiteSyncApiKey == null || !websiteSyncApiKey.equals(apiKey)) {
                return ResponseEntity.status(401).build();
            }

            ClienteRef clienteRef = clienteRefService.syncFromErp(request);
            return ResponseEntity.ok(clienteRef);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClienteRefResponse>> searchClientesRef(@RequestParam String q) {
        try {
            // Verificar se o usuário está autenticado e tem role ADMIN/SYSTEM/FUNCIONARIO
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                if (!principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_SYSTEM") && !principal.hasRole("ROLE_FUNCIONARIO")) {
                    return ResponseEntity.status(403).build();
                }
            } else {
                return ResponseEntity.status(401).build();
            }

            List<ClienteRef> clientes = clienteRefService.search(q);
            List<ClienteRefResponse> responses = clientes.stream()
                .map(cliente -> new ClienteRefResponse(cliente.getId(), cliente.getNome(), cliente.getEmail(), cliente.getTelefone()))
                .collect(Collectors.toList());

            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public static class UpsertClienteRefRequest {
        private String nome;
        private String email;

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}