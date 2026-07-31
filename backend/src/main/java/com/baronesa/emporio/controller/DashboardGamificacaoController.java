package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ClienteOptionDTO;
import com.baronesa.emporio.dto.DashboardGamificacaoResponse;
import com.baronesa.emporio.service.DashboardGamificacaoService;
import com.baronesa.emporio.service.GamificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/gamificacao")
@RequiredArgsConstructor
public class DashboardGamificacaoController {

    private final DashboardGamificacaoService dashboardGamificacaoService;
    private final GamificacaoService gamificacaoService;
    @Value("${website.sync.api-key:default-key-for-dev}")
    private String websiteSyncApiKey;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<DashboardGamificacaoResponse> getDashboard() {
        DashboardGamificacaoResponse response = dashboardGamificacaoService.getDashboardAdmin();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/saldos")
    public ResponseEntity<Map<Long, Integer>> getSaldos(
            @RequestHeader(value = "X-ERP-KEY", required = false) String apiKey,
            @RequestParam("ids") List<Long> ids) {

        if (!isApiKeyValid(apiKey) && !hasRoleAdminOrSystem()) {
            return ResponseEntity.status(401).build();
        }

        Map<Long, Integer> saldos = gamificacaoService.getSaldosClientes(ids);
        return ResponseEntity.ok(saldos);
    }

    @GetMapping("/clientes/com-pontos")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<List<ClienteOptionDTO>> getClientesComPontos() {
        List<ClienteOptionDTO> clientes = dashboardGamificacaoService.getClientesComPontos();
        return ResponseEntity.ok(clientes);
    }

    private boolean isApiKeyValid(String apiKey) {
        return websiteSyncApiKey != null && websiteSyncApiKey.equals(apiKey);
    }

    private boolean hasRoleAdminOrSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String r = ga.getAuthority();
            if ("ROLE_ADMIN".equals(r) || "ROLE_SYSTEM".equals(r)) return true;
        }
        return false;
    }
}
