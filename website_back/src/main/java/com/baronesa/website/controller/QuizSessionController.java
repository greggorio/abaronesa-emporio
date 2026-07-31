package com.baronesa.website.controller;

import com.baronesa.website.dto.LeaderboardResponse;
import com.baronesa.website.dto.QuestionBroadcast;
import com.baronesa.website.dto.SessionCreateRequest;
import com.baronesa.website.dto.SessionResponse;
import com.baronesa.website.service.QuizGameService;
import com.baronesa.website.service.QuizNotificationService;
import com.baronesa.website.service.QuizSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/quiz/session")
@RequiredArgsConstructor
@Tag(name = "Quiz Session", description = "Gerenciamento de sessões de quiz")
public class QuizSessionController {

    private final QuizSessionService sessionService;
    private final QuizGameService gameService;
    private final QuizNotificationService notificationService;

    /**
     * Obtém estatísticas do quiz
     */
    @GetMapping("/stats")
    @Operation(summary = "Estatísticas", description = "Retorna estatísticas do sistema de quiz")
    public ResponseEntity<?> getStats() {
        log.info("GET /api/quiz/session/stats - Obtendo estatísticas");
        long totalQuestions = sessionService.getTotalAvailableQuestions();
        return ResponseEntity.ok(java.util.Map.of(
            "totalQuestions", totalQuestions,
            "suggestedQuestions", totalQuestions >= 50 ? 50 : (int) totalQuestions
        ));
    }

    /**
     * Cria uma nova sessão de quiz
     */
    @PostMapping
    @Operation(summary = "Criar nova sessão", description = "Cria uma nova sessão de quiz com as configurações especificadas")
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody SessionCreateRequest request) {
        log.info("POST /api/quiz/session - Criando nova sessão");
        SessionResponse response = sessionService.createSession(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtém informações de uma sessão
     */
    @GetMapping("/{sessionCode}")
    @Operation(summary = "Obter sessão", description = "Retorna informações de uma sessão específica")
    public ResponseEntity<SessionResponse> getSession(@PathVariable String sessionCode) {
        log.info("GET /api/quiz/session/{} - Buscando sessão", sessionCode);
        var session = sessionService.getSessionByCode(sessionCode);

        SessionResponse response = SessionResponse.builder()
                .id(session.getId())
                .sessionCode(session.getSessionCode())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .totalQuestions(session.getQuestionIds().size())
                .questionTimeLimit(session.getQuestionTimeLimit())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Inicia o quiz
     */
    @PostMapping("/{sessionCode}/start")
    @Operation(summary = "Iniciar quiz", description = "Inicia a sessão de quiz e envia a primeira pergunta")
    public ResponseEntity<QuestionBroadcast> startSession(@PathVariable String sessionCode) {
        log.info("POST /api/quiz/session/{}/start - Iniciando quiz", sessionCode);

        sessionService.startSession(sessionCode);
        notificationService.broadcastGameStart(sessionCode);

        QuestionBroadcast question = gameService.prepareNextQuestion(sessionCode);

        return ResponseEntity.ok(question);
    }

    /**
     * Avança para a próxima pergunta
     */
    @PostMapping("/{sessionCode}/next")
    @Operation(summary = "Próxima pergunta", description = "Avança para a próxima pergunta ou finaliza o quiz")
    public ResponseEntity<?> nextQuestion(@PathVariable String sessionCode) {
        log.info("POST /api/quiz/session/{}/next - Próxima pergunta", sessionCode);

        sessionService.nextQuestion(sessionCode);
        var session = sessionService.getSessionByCode(sessionCode);

        if (session.getStatus().name().equals("FINISHED")) {
            // Jogo terminou - envia ranking final
            LeaderboardResponse finalLeaderboard = gameService.getLeaderboard(sessionCode);
            notificationService.broadcastGameEnd(sessionCode, finalLeaderboard);
            return ResponseEntity.ok(finalLeaderboard);
        } else {
            // Envia próxima pergunta
            QuestionBroadcast question = gameService.prepareNextQuestion(sessionCode);
            return ResponseEntity.ok(question);
        }
    }

    /**
     * Obtém o ranking atual
     */
    @GetMapping("/{sessionCode}/leaderboard")
    @Operation(summary = "Obter ranking", description = "Retorna o ranking atual da sessão")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(@PathVariable String sessionCode) {
        log.info("GET /api/quiz/session/{}/leaderboard - Obtendo ranking", sessionCode);
        LeaderboardResponse leaderboard = gameService.getLeaderboard(sessionCode);
        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Finaliza a sessão
     */
    @PostMapping("/{sessionCode}/finish")
    @Operation(summary = "Finalizar sessão", description = "Finaliza a sessão de quiz manualmente")
    public ResponseEntity<LeaderboardResponse> finishSession(@PathVariable String sessionCode) {
        log.info("POST /api/quiz/session/{}/finish - Finalizando sessão", sessionCode);

        sessionService.finishSession(sessionCode);
        LeaderboardResponse finalLeaderboard = gameService.getLeaderboard(sessionCode);
        notificationService.broadcastGameEnd(sessionCode, finalLeaderboard);

        return ResponseEntity.ok(finalLeaderboard);
    }

    /**
     * Revela a resposta correta por timeout (sem vencedor)
     */
    @PostMapping("/{sessionCode}/reveal")
    @Operation(summary = "Revelar resposta por timeout", description = "Revela a resposta correta quando o tempo esgota e ninguém acertou")
    public ResponseEntity<Void> revealCorrectAnswer(@PathVariable String sessionCode) {
        log.info("POST /api/quiz/session/{}/reveal - Revelando resposta correta por timeout", sessionCode);
        gameService.revealCorrectOnTimeout(sessionCode);
        return ResponseEntity.ok().build();
    }

    
}
