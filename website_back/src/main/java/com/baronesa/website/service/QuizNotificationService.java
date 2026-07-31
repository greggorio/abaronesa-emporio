package com.baronesa.website.service;

import com.baronesa.website.dto.LeaderboardResponse;
import com.baronesa.website.dto.QuestionBroadcast;
import com.baronesa.website.dto.QuestionLockedNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Envia pergunta para todos os jogadores de uma sessão
     */
    public void broadcastQuestion(String sessionCode, QuestionBroadcast question) {
        String destination = "/topic/quiz/" + sessionCode + "/question";
        messagingTemplate.convertAndSend(destination, question);
        log.debug("[WS] Enviada pergunta para sessão {} - Q{}", sessionCode, question.getQuestionNumber());
    }

    /**
     * Envia atualização do ranking para todos os jogadores de uma sessão
     */
    public void broadcastLeaderboard(String sessionCode, LeaderboardResponse leaderboard) {
        String destination = "/topic/quiz/" + sessionCode + "/leaderboard";
        messagingTemplate.convertAndSend(destination, leaderboard);
        log.debug("[WS] Enviado ranking para sessão {} - {} jogadores", sessionCode, leaderboard.getPlayers().size());
    }

    /**
     * Notifica que o jogo iniciou
     */
    public void broadcastGameStart(String sessionCode) {
        String destination = "/topic/quiz/" + sessionCode + "/start";
        messagingTemplate.convertAndSend(destination, Map.of("status", "STARTED"));
        log.debug("[WS] Jogo iniciado na sessão {}", sessionCode);
    }

    /**
     * Notifica que o jogo terminou
     */
    public void broadcastGameEnd(String sessionCode, LeaderboardResponse finalLeaderboard) {
        String destination = "/topic/quiz/" + sessionCode + "/end";
        messagingTemplate.convertAndSend(destination, finalLeaderboard);
        log.debug("[WS] Jogo finalizado na sessão {}", sessionCode);
    }

    /**
     * Notifica quando um novo jogador entra
     */
    public void broadcastPlayerJoined(String sessionCode, String nickname) {
        String destination = "/topic/quiz/" + sessionCode + "/player-joined";
        messagingTemplate.convertAndSend(destination, Map.of("nickname", nickname));
        log.debug("[WS] Jogador {} entrou na sessão {}", nickname, sessionCode);
    }

    /**
     * Envia mensagem pessoal para um jogador específico
     */
    public void sendPersonalMessage(String playerId, Object payload) {
        String destination = "/queue/quiz/player";
        messagingTemplate.convertAndSendToUser(playerId, destination, payload);
        log.debug("[WS] Mensagem pessoal enviada para jogador {}", playerId);
    }

    /**
     * Notifica que a pergunta foi bloqueada (alguém acertou)
     */
    public void broadcastQuestionLocked(String sessionCode, QuestionLockedNotification notification) {
        String destination = "/topic/quiz/" + sessionCode + "/question-locked";
        messagingTemplate.convertAndSend(destination, notification);
        log.info("[WS] Pergunta bloqueada na sessão {} - Vencedor: {}", sessionCode, notification.getWinnerNickname());
    }
}
