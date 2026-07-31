package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.openai.OpenAiConfigDTO;
import com.baronesa.emporio.dto.ai.QuizGenerationRequest;
import com.baronesa.emporio.dto.ai.QuizQuestionDTO;
import com.baronesa.emporio.service.OpenAiConfigService;
import com.baronesa.emporio.service.QuizGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * Controller para gerenciar configurações OpenAI
 */
@Slf4j
@RestController
@RequestMapping("/api/openai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OpenAiController {

    private final OpenAiConfigService openAiConfigService;
    private final QuizGenerationService quizGenerationService;

    /**
     * Busca as configurações OpenAI atuais
     */
    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<OpenAiConfigDTO> getConfig() {
        try {
            OpenAiConfigDTO config = openAiConfigService.getConfig();
            // Não retornar a API key completa por segurança
            if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                config.setApiKey("********");
            }
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Erro ao buscar configurações OpenAI", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Salva as configurações OpenAI
     */
    @PutMapping("/salvar")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody OpenAiConfigDTO config) {
        try {
            // Se a API key vier como "********", não atualizar (mantém a existente)
            if ("********".equals(config.getApiKey())) {
                OpenAiConfigDTO currentConfig = openAiConfigService.getConfig();
                config.setApiKey(currentConfig.getApiKey());
            }

            boolean success = openAiConfigService.saveConfig(config);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", success);
            response.put("mensagem", success ? "Configurações salvas com sucesso" : "Erro ao salvar configurações");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao salvar configurações OpenAI", e);
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", false);
            response.put("mensagem", "Erro ao salvar: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Testa a conexão com a API OpenAI
     */
    @PostMapping("/testar-conexao")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody OpenAiConfigDTO config) {
        try {
            boolean success = openAiConfigService.testConnection(config);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", success);
            response.put("mensagem", success ?
                "Conexão estabelecida com sucesso!" :
                "Falha ao conectar à API OpenAI");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao testar conexão OpenAI", e);
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", false);
            response.put("erro", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Verifica o status da configuração OpenAI
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            boolean enabled = openAiConfigService.isEnabled();
            OpenAiConfigDTO config = openAiConfigService.getConfig();

            Map<String, Object> response = new HashMap<>();
            response.put("habilitado", enabled);
            response.put("configurado", config.getApiKey() != null && !config.getApiKey().isEmpty());
            response.put("modelo", config.getModel());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao verificar status OpenAI", e);
            Map<String, Object> response = new HashMap<>();
            response.put("habilitado", false);
            response.put("configurado", false);
            response.put("erro", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Testa um prompt simples com a OpenAI
     */
    @PostMapping("/testar-prompt")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> testPrompt(@RequestBody Map<String, String> payload) {
        try {
            String prompt = payload.get("prompt");
            if (prompt == null || prompt.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("sucesso", false);
                response.put("erro", "Prompt não pode ser vazio");
                return ResponseEntity.badRequest().body(response);
            }

            String resposta = openAiConfigService.testPrompt(prompt);

            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", true);
            response.put("resposta", resposta);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao testar prompt OpenAI", e);
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", false);
            response.put("erro", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Gera questões de quiz via OpenAI no formato esperado pelo import.
     */
    @PostMapping("/quiz/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<?> generateQuizQuestions(@RequestBody QuizGenerationRequest request) {
        try {
            List<QuizQuestionDTO> questions = quizGenerationService.generateQuestions(request);
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            log.error("Erro ao gerar questões de quiz", e);
            Map<String, Object> response = new HashMap<>();
            response.put("sucesso", false);
            response.put("erro", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
