package com.baronesa.emporio.print;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.service.PrintAgentPairingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class PrintWebSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final PrintAgentPairingService pairingService;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String agentId = session.getHandshakeHeaders().getFirst("X-Agent-ID");
        if (agentId != null) {
            sessions.put(agentId, session);
            pairingService.updateHeartbeat(agentId);
            System.out.println("[WS/PRINT] conectado → agent_id=" + agentId);
        } else {
            System.out.println("[WS/PRINT] conectado (sem agent_id)");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String agentId = getAgentIdFromSession(session);
        sessions.values().removeIf(ws -> ws.getId().equals(session.getId()));

        if (agentId != null) {
            pairingService.markAgentDisconnected(agentId);
        }

        System.out.println("[WS/PRINT] desconectado: " + status);
    }

    public boolean hasAvailableAgent() {
        return sessions.values().stream().anyMatch(WebSocketSession::isOpen);
    }

    public void sendPrintJob(Map<String, Object> job) {
        var optional = sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .findAny();

        if (optional.isEmpty()) {
            throw new IllegalStateException("Nenhum agente conectado");
        }

        try {
            Map<String, Object> message = Map.of(
                    "type", "print_job",
                    "job", job
            );
            optional.get().sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(message)));
        } catch (IOException e) {
            throw new RuntimeException("Falha ao enviar job para o agente", e);
        }
    }

    public void sendTestPrint(String route, String content, String jobId) {
        Map<String, Object> message = Map.of(
                "type", "print_job",
                "job", Map.of(
                        "id", jobId,
                        "route", route,
                        "tipo", "TEST",
                        "kind", "TEST",
                        "content_type", "TEXT",
                        "content", content,
                        "copies", 1,
                        "idempotency_key", jobId,
                        "payload", Map.of("text", content)
                )
        );

        var optional = sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .findAny();

        if (optional.isEmpty()) {
            throw new IllegalStateException("Nenhum agente conectado");
        }

        try {
            optional.get().sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(message)));
        } catch (IOException e) {
            throw new RuntimeException("Falha ao enviar teste para o agente", e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String agentId = getAgentIdFromSession(session);
        System.out.println("[WS/PRINT] msg=" + message.getPayload());

        // Update heartbeat when receiving any message from the agent
        if (agentId != null) {
            pairingService.updateHeartbeat(agentId);
        }
    }

    private String getAgentIdFromSession(WebSocketSession session) {
        return session.getHandshakeHeaders().getFirst("X-Agent-ID");
    }
}
