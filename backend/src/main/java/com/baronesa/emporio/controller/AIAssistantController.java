package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ai.AIProcessRequest;
import com.baronesa.emporio.dto.ai.AIProcessResponse;
import com.baronesa.emporio.service.AIAssistantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller para gerenciar o assistente AI
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AIAssistantController {

    private final AIAssistantService aiAssistantService;

    /**
     * Endpoint legacy compatível com sistema de referência
     * GET /dashboard/requestia/{prompt}
     */
    @GetMapping("/requestia/{prompt}")
    public String requestIA(@PathVariable String prompt) {
        log.info("Recebendo prompt via GET: {}", prompt);
        try {
            return aiAssistantService.processPromptLegacy(prompt);
        } catch (Exception e) {
            log.error("Erro ao processar prompt: ", e);
            return "{\"tipo\":\"erro\",\"retorno\":\"Erro ao processar solicitação: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Processa um comando do usuário via AI (nova rota POST)
     */
    @PostMapping("/ai/process")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AIProcessResponse> processCommand(@RequestBody AIProcessRequest request) {
        try {
            log.info("Recebida requisição para processar comando: {}", request.getPrompt());

            // Validar requisição
            if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
                AIProcessResponse errorResponse = AIProcessResponse.builder()
                        .tipo("erro")
                        .retorno("Comando não pode ser vazio")
                        .mensagem("Comando não pode ser vazio")
                        .erro("Comando não pode ser vazio")
                        .sucesso(false)
                        .build();
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Processar comando
            AIProcessResponse response = aiAssistantService.processPrompt(request);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao processar comando via AI", e);

            AIProcessResponse errorResponse = AIProcessResponse.builder()
                    .tipo("erro")
                    .retorno("Erro ao processar comando: " + e.getMessage())
                    .mensagem("Erro ao processar comando: " + e.getMessage())
                    .erro(e.getMessage())
                    .sucesso(false)
                    .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Busca templates de exemplo por tipo
     */
    @GetMapping("/ai/templates/{tipo}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getTemplates(@PathVariable String tipo) {
        try {
            Map<String, List<String>> exemplos = aiAssistantService.getExemplos(tipo);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("templates", exemplos);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao buscar templates", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("sucesso", false);
            errorResponse.put("erro", e.getMessage());

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Verifica status do assistente AI
     */
    @GetMapping("/ai/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("disponivel", true);
            response.put("mensagem", "Assistente AI disponível");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao verificar status do assistente AI", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("disponivel", false);
            errorResponse.put("erro", e.getMessage());

            return ResponseEntity.ok(errorResponse);
        }
    }
}
