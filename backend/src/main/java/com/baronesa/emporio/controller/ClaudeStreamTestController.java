package com.baronesa.emporio.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller de teste para streaming em tempo real simulando Claude API
 * Demonstra streaming de mensagens incrementais (SSE - Server-Sent Events)
 */
@RestController
@RequestMapping("/api/test/claude-stream")
@RequiredArgsConstructor
@Slf4j
public class ClaudeStreamTestController {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Endpoint de streaming que simula resposta Claude em tempo real
     * Retorna tokens progressivamente simulando o comportamento de streaming da API
     */
    @GetMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClaudeResponse(
            @RequestParam(defaultValue = "Olá! Como posso ajudar você hoje?") String prompt) {

        log.info("Iniciando streaming Claude para prompt: {}", prompt);

        SseEmitter emitter = new SseEmitter(60000L); // 60 segundos timeout

        executor.execute(() -> {
            try {
                // Simula resposta incremental do Claude
                String[] tokens = {
                    "Olá", "! ", "Sou ", "Claude", ", ", "um ", "assistente ", "de ", "IA ",
                    "desenvolvido ", "pela ", "Anthropic", ".\n\n",
                    "Estou ", "testando ", "a ", "funcionalidade ", "de ", "streaming ",
                    "em ", "tempo ", "real", ".\n\n",
                    "Cada ", "palavra ", "está ", "sendo ", "enviada ", "progressivamente",
                    ", ", "simulando ", "o ", "comportamento ", "real ", "da ", "API ",
                    "Claude", ".\n\n",
                    "✓ ", "Streaming ", "funcionando ", "corretamente!"
                };

                // Envia token inicial com metadata
                sendEvent(emitter, "message_start", Map.of(
                    "type", "message_start",
                    "message", Map.of(
                        "id", "msg_" + System.currentTimeMillis(),
                        "model", "claude-3-5-sonnet-20241022",
                        "role", "assistant"
                    )
                ));

                // Envia content_block_start
                sendEvent(emitter, "content_block_start", Map.of(
                    "type", "content_block_start",
                    "index", 0,
                    "content_block", Map.of("type", "text", "text", "")
                ));

                // Envia tokens progressivamente
                StringBuilder fullText = new StringBuilder();
                for (int i = 0; i < tokens.length; i++) {
                    String token = tokens[i];
                    fullText.append(token);

                    sendEvent(emitter, "content_block_delta", Map.of(
                        "type", "content_block_delta",
                        "index", 0,
                        "delta", Map.of("type", "text_delta", "text", token)
                    ));

                    // Delay para simular streaming real
                    Thread.sleep(50 + (int)(Math.random() * 50));
                }

                // Envia content_block_stop
                sendEvent(emitter, "content_block_stop", Map.of(
                    "type", "content_block_stop",
                    "index", 0
                ));

                // Envia message_delta com estatísticas
                sendEvent(emitter, "message_delta", Map.of(
                    "type", "message_delta",
                    "delta", Map.of("stop_reason", "end_turn"),
                    "usage", Map.of("output_tokens", tokens.length)
                ));

                // Envia message_stop
                sendEvent(emitter, "message_stop", Map.of(
                    "type", "message_stop"
                ));

                log.info("Streaming Claude concluído com sucesso. Total de tokens: {}", tokens.length);
                emitter.complete();

            } catch (Exception e) {
                log.error("Erro durante streaming Claude: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Endpoint de streaming com progresso simulado
     */
    @GetMapping(value = "/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress() {
        log.info("Iniciando streaming de progresso");

        SseEmitter emitter = new SseEmitter(30000L);

        executor.execute(() -> {
            try {
                List<String> steps = List.of(
                    "Inicializando conexão com Claude API...",
                    "Processando prompt do usuário...",
                    "Gerando resposta incremental...",
                    "Validando tokens gerados...",
                    "Finalizando streaming..."
                );

                for (int i = 0; i < steps.size(); i++) {
                    sendEvent(emitter, "progress", Map.of(
                        "step", i + 1,
                        "total", steps.size(),
                        "message", steps.get(i),
                        "timestamp", Instant.now().toString()
                    ));
                    Thread.sleep(800);
                }

                sendEvent(emitter, "complete", Map.of(
                    "status", "success",
                    "message", "Streaming concluído com sucesso!",
                    "timestamp", Instant.now().toString()
                ));

                emitter.complete();

            } catch (Exception e) {
                log.error("Erro durante streaming de progresso: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Endpoint de teste simples (não streaming)
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "operational",
            "service", "Claude Stream Test",
            "timestamp", Instant.now().toString(),
            "streaming_enabled", true
        );
    }

    private void sendEvent(SseEmitter emitter, String eventType, Object data) throws IOException {
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .name(eventType)
                .data(data, MediaType.APPLICATION_JSON);
        emitter.send(event);
    }
}
