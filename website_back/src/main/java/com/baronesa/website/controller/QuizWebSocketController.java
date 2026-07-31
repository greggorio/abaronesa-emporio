package com.baronesa.website.controller;

import com.baronesa.website.dto.AnswerResult;
import com.baronesa.website.dto.AnswerSubmitRequest;
import com.baronesa.website.dto.PlayerJoinRequest;
import com.baronesa.website.dto.PlayerResponse;
import com.baronesa.website.service.QuizGameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class QuizWebSocketController {

    private final QuizGameService gameService;

    /**
     * Jogador entra na sessão via WebSocket
     * Endpoint: /app/quiz/join
     */
    @MessageMapping("/quiz/join")
    @SendToUser("/queue/quiz/join-response")
    public PlayerResponse joinSession(
            @Valid @Payload PlayerJoinRequest request,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal
    ) {
        String sessionId = headerAccessor.getSessionId();
        log.info("[WS] Jogador {} tentando entrar na sessão {} (sessionId: {})",
                request.getNickname(), request.getSessionCode(), sessionId);

        try {
            PlayerResponse response = gameService.joinSession(request, sessionId);
            log.info("[WS] Jogador {} entrou com sucesso na sessão {}",
                    request.getNickname(), request.getSessionCode());
            return response;
        } catch (Exception e) {
            log.error("[WS] Erro ao adicionar jogador à sessão", e);
            throw e;
        }
    }

    /**
     * Jogador submete resposta via WebSocket
     * Endpoint: /app/quiz/answer
     */
    @MessageMapping("/quiz/answer")
    @SendToUser("/queue/quiz/answer-result")
    public AnswerResult submitAnswer(
            @Valid @Payload AnswerSubmitRequest request,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        String sessionId = headerAccessor.getSessionId();
        log.debug("[WS] Resposta recebida (sessionId: {}) - Pergunta: {}, Opção: {}",
                sessionId, request.getQuestionId(), request.getSelectedOption());

        try {
            AnswerResult result = gameService.submitAnswer(sessionId, request);
            log.debug("[WS] Resposta processada (sessionId: {}) - Correto: {}, Pontos: {}",
                    sessionId, result.getCorrect(), result.getPointsEarned());
            return result;
        } catch (Exception e) {
            log.error("[WS] Erro ao processar resposta", e);
            throw e;
        }
    }
}
