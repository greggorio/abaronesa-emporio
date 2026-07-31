package com.baronesa.website.service;

import com.baronesa.website.dto.*;
import com.baronesa.website.entity.Player;
import com.baronesa.website.entity.Question;
import com.baronesa.website.entity.QuizSession;
import com.baronesa.website.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizGameService {

    private final PlayerRepository playerRepository;
    private final QuizSessionService sessionService;
    private final QuizNotificationService notificationService;

    // Armazena o vencedor de cada pergunta: "sessionCode:questionId" -> QuestionWinner
    private final Map<String, QuestionWinner> questionWinners = new ConcurrentHashMap<>();

    /**
     * Adiciona um jogador à sessão
     */
    @Transactional
    public PlayerResponse joinSession(PlayerJoinRequest request, String connectionId) {
        QuizSession session = sessionService.getSessionByCode(request.getSessionCode());

        // Verifica se o nickname já está em uso
        if (playerRepository.existsBySessionCodeAndNickname(request.getSessionCode(), request.getNickname())) {
            throw new RuntimeException("Nickname já está em uso nesta sessão");
        }

        // Cria o jogador
        Player player = Player.builder()
                .nickname(request.getNickname())
                .sessionCode(request.getSessionCode())
                .connectionId(connectionId)
                .build();

        player = playerRepository.save(player);
        log.info("Jogador {} entrou na sessão {}", request.getNickname(), request.getSessionCode());

        // Notifica outros jogadores
        notificationService.broadcastPlayerJoined(request.getSessionCode(), request.getNickname());

        // Low-risk improvement: if the game is already ACTIVE, re-broadcast the
        // current question so late joiners don't get stuck waiting for the next
        // broadcast. This does not advance state and is safe to repeat.
        if (session.getStatus().name().equals("ACTIVE")) {
            try {
                prepareNextQuestion(session.getSessionCode());
                updateLeaderboard(session.getSessionCode());
            } catch (Exception e) {
                log.warn("[WS] Falha ao reemitir pergunta atual para join tardio na sessão {}: {}",
                        session.getSessionCode(), e.getMessage());
            }
        }

        return PlayerResponse.builder()
                .id(player.getId())
                .nickname(player.getNickname())
                .score(player.getScore())
                .correctAnswers(player.getCorrectAnswers())
                .wrongAnswers(player.getWrongAnswers())
                .build();
    }

    /**
     * Processa a resposta de um jogador
     */
    @Transactional
    public AnswerResult submitAnswer(String connectionId, AnswerSubmitRequest request) {
        // Busca o jogador pela conexão
        Player player = playerRepository.findByConnectionId(connectionId)
                .orElseThrow(() -> new RuntimeException("Jogador não encontrado"));

        // Busca a pergunta
        Question question = sessionService.getCurrentQuestion(player.getSessionCode());

        if (!question.getId().equals(request.getQuestionId())) {
            throw new RuntimeException("Pergunta inválida ou expirada");
        }

        // Chave para identificar esta pergunta nesta sessão
        String questionKey = player.getSessionCode() + ":" + question.getId();

        // Verifica se já existe um vencedor para esta pergunta
        QuestionWinner existingWinner = questionWinners.get(questionKey);

        if (existingWinner != null) {
            // Pergunta já foi respondida corretamente por outro jogador
            log.info("Jogador {} tentou responder após bloqueio - Vencedor: {}",
                    player.getNickname(), existingWinner.getNickname());

            // Retorna resultado indicando que está bloqueada
            return AnswerResult.builder()
                    .correct(false)
                    .correctOption(question.getCorrectAnswer())
                    .pointsEarned(0)
                    .totalScore(player.getScore())
                    .rank(calculatePlayerRank(player))
                    .build();
        }

        // Verifica se a resposta está correta
        boolean correct = request.getSelectedOption().equals(question.getCorrectAnswer());

        int pointsEarned = 0;
        if (correct) {
            // Calcula pontuação baseada no tempo de resposta
            pointsEarned = calculatePoints(question.getPoints(), request.getResponseTimeMs());
            player.setScore(player.getScore() + pointsEarned);
            player.setCorrectAnswers(player.getCorrectAnswers() + 1);

            // Marca este jogador como vencedor da pergunta
            QuestionWinner winner = QuestionWinner.builder()
                    .playerId(player.getId())
                    .nickname(player.getNickname())
                    .pointsEarned(pointsEarned)
                    .timestamp(LocalDateTime.now())
                    .build();
            questionWinners.put(questionKey, winner);

            log.info("🏆 Jogador {} acertou primeiro! Pontos: {}", player.getNickname(), pointsEarned);

            // Notifica todos que a pergunta foi bloqueada
            QuestionLockedNotification notification = QuestionLockedNotification.builder()
                    .questionId(question.getId())
                    .winnerNickname(player.getNickname())
                    .pointsEarned(pointsEarned)
                    .message(player.getNickname() + " acertou primeiro!")
                    .correctOption(question.getCorrectAnswer())
                    .build();
            notificationService.broadcastQuestionLocked(player.getSessionCode(), notification);

        } else {
            player.setWrongAnswers(player.getWrongAnswers() + 1);
        }

        playerRepository.save(player);
        log.debug("Jogador {} respondeu: {} - Pontos: {}", player.getNickname(), correct ? "CORRETO" : "ERRADO", pointsEarned);

        // Atualiza ranking
        updateLeaderboard(player.getSessionCode());

        // Calcula rank do jogador
        int rank = calculatePlayerRank(player);

        return AnswerResult.builder()
                .correct(correct)
                .correctOption(question.getCorrectAnswer())
                .pointsEarned(pointsEarned)
                .totalScore(player.getScore())
                .rank(rank)
                .build();
    }

    /**
     * Calcula pontos baseado no tempo de resposta
     */
    private int calculatePoints(int basePoints, long responseTimeMs) {
        // Pontuação máxima se responder em menos de 5 segundos
        // Pontuação decrescente até 30 segundos
        if (responseTimeMs < 5000) {
            return basePoints + 5; // Bonus de velocidade
        } else if (responseTimeMs < 10000) {
            return basePoints + 3;
        } else if (responseTimeMs < 20000) {
            return basePoints;
        } else {
            return Math.max(basePoints - 2, 1); // Mínimo 1 ponto
        }
    }

    /**
     * Atualiza e envia o ranking para todos os jogadores
     */
    public void updateLeaderboard(String sessionCode) {
        LeaderboardResponse leaderboard = getLeaderboard(sessionCode);
        notificationService.broadcastLeaderboard(sessionCode, leaderboard);
    }

    /**
     * Obtém o ranking atual da sessão
     */
    public LeaderboardResponse getLeaderboard(String sessionCode) {
        QuizSession session = sessionService.getSessionByCode(sessionCode);
        List<Player> players = playerRepository.findBySessionCodeAndActiveTrueOrderByScoreDesc(sessionCode);

        AtomicInteger rank = new AtomicInteger(1);
        List<PlayerResponse> playerResponses = players.stream()
                .map(player -> PlayerResponse.builder()
                        .id(player.getId())
                        .nickname(player.getNickname())
                        .score(player.getScore())
                        .correctAnswers(player.getCorrectAnswers())
                        .wrongAnswers(player.getWrongAnswers())
                        .rank(rank.getAndIncrement())
                        .build())
                .collect(Collectors.toList());

        return LeaderboardResponse.builder()
                .players(playerResponses)
                .currentQuestion(session.getCurrentQuestionIndex() + 1)
                .totalQuestions(session.getQuestionIds().size())
                .build();
    }

    /**
     * Calcula o rank de um jogador específico
     */
    private int calculatePlayerRank(Player player) {
        List<Player> players = playerRepository.findBySessionCodeAndActiveTrueOrderByScoreDesc(player.getSessionCode());
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(player.getId())) {
                return i + 1;
            }
        }
        return players.size();
    }

    /**
     * Prepara e envia a próxima pergunta
     */
    public QuestionBroadcast prepareNextQuestion(String sessionCode) {
        QuizSession session = sessionService.getSessionByCode(sessionCode);
        Question question = sessionService.getCurrentQuestion(sessionCode);

        QuestionBroadcast broadcast = QuestionBroadcast.builder()
                .questionId(question.getId())
                .question(question.getQuestion())
                .options(question.getOptions())
                .questionNumber(session.getCurrentQuestionIndex() + 1)
                .totalQuestions(session.getQuestionIds().size())
                .timeLimit(session.getQuestionTimeLimit())
                .imageUrl(question.getImageUrl())
                .build();

        notificationService.broadcastQuestion(sessionCode, broadcast);
        return broadcast;
    }

    /**
     * Revela a resposta correta quando o tempo esgota e ninguém acertou.
     * Emite question-locked sem vencedor, incluindo a opção correta atual.
     */
    public void revealCorrectOnTimeout(String sessionCode) {
        QuizSession session = sessionService.getSessionByCode(sessionCode);
        Question question = sessionService.getCurrentQuestion(sessionCode);

        String questionKey = sessionCode + ":" + question.getId();
        QuestionWinner existingWinner = questionWinners.get(questionKey);
        if (existingWinner != null) {
            log.info("[WS] Reveal ignorado: pergunta já possui vencedor na sessão {}", sessionCode);
            return;
        }

        QuestionLockedNotification notification = QuestionLockedNotification.builder()
                .questionId(question.getId())
                .winnerNickname(null)
                .pointsEarned(0)
                .message("Tempo esgotado!")
                .correctOption(question.getCorrectAnswer())
                .build();
        notificationService.broadcastQuestionLocked(sessionCode, notification);
        log.info("[WS] Reveal enviado por timeout na sessão {}", sessionCode);
    }
}
