package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.print.PrintAgentPairingRequest;
import com.baronesa.emporio.dto.print.PrintAgentTestJobRequest;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.PrintAgentPairingService;
import com.baronesa.emporio.print.PrintWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/print-agent")
@RequiredArgsConstructor
public class PrintAgentController {

    private final PrintAgentPairingService pairingService;
    private final UsuarioRepository usuarioRepository;
    private final PrintWebSocketHandler printWebSocketHandler;

    @PostMapping("/pair")
    public ResponseEntity<Map<String, Object>> pair(
            @Valid @RequestBody PrintAgentPairingRequest request,
            HttpServletRequest httpRequest
    ) {
        String token = extractBearerToken(httpRequest);
        Usuario usuario = getAuthenticatedUsuario();
        String storeName = resolveStoreName(usuario);

        Map<String, Object> response = pairingService.registerPending(
                request.getPairingCode(),
                token,
                storeName
        );

        String message = response.getOrDefault("message", "Código registrado").toString();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "payload", response
        ));
    }

    @PostMapping("/pair/claim")
    public ResponseEntity<Map<String, Object>> claimPairing(
            @Valid @RequestBody PrintAgentPairingRequest request
    ) {
        try {
            Map<String, Object> response = pairingService.claim(request.getPairingCode());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = pairingService.getStatus();
        return ResponseEntity.ok(status);
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetPairing() {
        pairingService.resetPairing();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Pareamento reiniciado com sucesso"
        ));
    }

    @PostMapping("/test-job")
    public ResponseEntity<Map<String, Object>> testJob(
            @Valid @RequestBody PrintAgentTestJobRequest request
    ) {
        if (!printWebSocketHandler.hasAvailableAgent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", "agent_not_connected"
            ));
        }

        String route = request.getRoute().toUpperCase();
        String text = request.getText();
        if (!StringUtils.hasText(text)) {
            text = "Teste de impressão para " + route;
        }

        String jobId = "erp-test-" + UUID.randomUUID().toString().replace("-", "");

        try {
            printWebSocketHandler.sendTestPrint(route, text, jobId);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "success", false,
                    "message", "agent_not_connected"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "job_id", jobId
        ));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de autenticação ausente");
        }

        String token = header.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }

        return token;
    }

    private Usuario getAuthenticatedUsuario() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }

        return usuarioRepository.findById(principal.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));
    }

    private String resolveStoreName(Usuario usuario) {
        if (usuario == null) {
            return "ERP";
        }

        if (usuario.getGrupoUsuario() != null && StringUtils.hasText(usuario.getGrupoUsuario().getDescricao())) {
            return usuario.getGrupoUsuario().getDescricao();
        }

        if (StringUtils.hasText(usuario.getNome())) {
            return usuario.getNome();
        }

        return "ERP";
    }
}
