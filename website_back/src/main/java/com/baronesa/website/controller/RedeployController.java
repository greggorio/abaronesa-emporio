package com.baronesa.website.controller;

import com.baronesa.website.service.RedeployService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/redeploy")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Redeploy", description = "Gerenciamento de reinicialização do sistema")
public class RedeployController {

    private final RedeployService redeployService;

    @Operation(
            summary = "Solicitar redeploy do sistema",
            description = "Cria um sinal para que o monitor externo reinicie os containers. Requer permissão ADMIN.",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sinal de redeploy criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Erro ao criar sinal ou feature desabilitada")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> triggerRedeploy(@RequestParam(defaultValue = "baronesa") String tenantId) {
        log.info("Recebida solicitação de redeploy via API para tenant: {}", tenantId);
        
        boolean success = redeployService.triggerRedeploy(tenantId);
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                    "message", "Sinal de redeploy criado com sucesso. O sistema deve reiniciar em instantes.",
                    "status", "PENDING"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Não foi possível iniciar o redeploy. Verifique se a feature está habilitada ou os logs do servidor.",
                    "status", "ERROR"
            ));
        }
    }

    @Operation(
            summary = "Verificar status do redeploy",
            description = "Retorna o status atual do processo de redeploy (PENDING, STOPPING, STARTING, COMPLETED, FAILED).",
            security = @SecurityRequirement(name = "bearer-jwt")
    )
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getRedeployStatus() {
        String status = redeployService.getRedeployStatus();
        return ResponseEntity.ok(Map.of("status", status));
    }
}
