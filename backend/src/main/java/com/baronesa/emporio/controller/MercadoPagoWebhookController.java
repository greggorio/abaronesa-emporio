package com.baronesa.emporio.controller;

import com.baronesa.emporio.service.MercadoPagoWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/mercadopago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookService webhookService;

    /**
     * Endpoint principal para receber notificações do Mercado Pago
     * URL: https://seu-dominio.com/api/webhooks/mercadopago
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> receberWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId) {

        try {
            log.info("=== WEBHOOK MERCADO PAGO RECEBIDO ===");
            log.info("Request ID: {}", requestId);
            log.info("Payload: {}", payload);

            // 1. Validar assinatura (se configurado)
            if (!webhookService.validarAssinatura(payload, signature, requestId)) {
                log.error("Assinatura inválida para webhook");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("status", "unauthorized"));
            }

            // 2. Processar webhook de forma assíncrona
            webhookService.processarWebhookAsync(payload, requestId);

            // 3. Retornar 200 OK imediatamente (importante para o Mercado Pago)
            log.info("Webhook recebido e enfileirado para processamento");
            return ResponseEntity.ok(Map.of("status", "received"));

        } catch (Exception e) {
            log.error("Erro ao receber webhook: {}", e.getMessage(), e);
            // Mesmo com erro, retornar 200 para evitar retry do MP
            return ResponseEntity.ok(Map.of("status", "error"));
        }
    }

    /**
     * Endpoint de teste para verificar se o webhook está funcionando
     * Útil para configuração inicial
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testarWebhook() {
        log.info("Teste de webhook solicitado");

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Webhook endpoint está funcionando",
                "timestamp", System.currentTimeMillis(),
                "configured", webhookService.isWebhookConfigurado()
        ));
    }

    /**
     * Endpoint para reprocessar um webhook específico (admin)
     * Útil para debug ou reprocessamento manual
     */
    @PostMapping("/reprocess/{webhookLogId}")
    public ResponseEntity<Map<String, String>> reprocessarWebhook(
            @PathVariable Long webhookLogId,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {

        try {
            // Validar token admin (implementar conforme sua segurança)
            if (!isAdminTokenValido(adminToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("status", "forbidden"));
            }

            log.info("Reprocessamento de webhook solicitado - ID: {}", webhookLogId);
            boolean reprocessado = webhookService.reprocessarWebhook(webhookLogId);

            if (reprocessado) {
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Webhook reprocessado com sucesso"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "status", "not_found",
                                "message", "Webhook log não encontrado"
                        ));
            }

        } catch (Exception e) {
            log.error("Erro ao reprocessar webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Endpoint para listar webhooks recebidos (admin)
     * Útil para monitoramento e debug
     */
    @GetMapping("/logs")
    public ResponseEntity<?> listarWebhookLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {

        try {
            // Validar token admin
            if (!isAdminTokenValido(adminToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("status", "forbidden"));
            }

            var logs = webhookService.listarWebhookLogs(page, size, status);

            return ResponseEntity.ok(logs);

        } catch (Exception e) {
            log.error("Erro ao listar webhook logs: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage()
                    ));
        }
    }

    /**
     * Validar token admin (implementar conforme sua segurança)
     */
    private boolean isAdminTokenValido(String token) {
        // TODO: Implementar validação real
        // Por enquanto, apenas verificar se não está vazio
        return token != null && !token.isEmpty();
    }
}

/**
 * DTOs para o controller
 */
class WebhookNotification {
    private String id;
    private String live_mode;
    private String type;
    private String date_created;
    private String action;
    private Map<String, Object> data;

    // Getters e setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLive_mode() { return live_mode; }
    public void setLive_mode(String live_mode) { this.live_mode = live_mode; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDate_created() { return date_created; }
    public void setDate_created(String date_created) { this.date_created = date_created; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
