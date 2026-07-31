package com.baronesa.website.service;

import com.baronesa.website.dto.SessionCreateRequest;
import com.baronesa.website.dto.SessionResponse;
import com.baronesa.website.entity.Category;
import com.baronesa.website.entity.Question;
import com.baronesa.website.entity.QuizSession;
import com.baronesa.website.enums.SessionStatus;
import com.baronesa.website.repository.CategoryRepository;
import com.baronesa.website.service.QuestionService;
import com.baronesa.website.repository.QuizSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizSessionService {

    private final QuizSessionRepository sessionRepository;
    private final QuestionService questionService;
    private final CategoryRepository categoryRepository;
    private final QRCodeService qrCodeService;

    /**
     * Cria uma nova sessão de quiz
     */
    @Transactional
    public SessionResponse createSession(SessionCreateRequest request) {
        log.info("Criando nova sessão de quiz com {} perguntas", request.getNumberOfQuestions());

        // Busca perguntas aleatórias
        List<Question> questions;
        if (request.getCategory() != null && !request.getCategory().isEmpty()) {
            Category category = categoryRepository.findByName(request.getCategory())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + request.getCategory()));
            questions = questionService.findRandomQuestionsByCategoryId(
                    category.getId(),
                    request.getNumberOfQuestions()
            );
        } else {
            questions = questionService.findRandomQuestions(request.getNumberOfQuestions());
        }

        if (questions.isEmpty()) {
            throw new RuntimeException("Nenhuma pergunta disponível no banco de dados");
        }

        // Cria a sessão
        QuizSession session = QuizSession.builder()
                .status(SessionStatus.WAITING)
                .questionTimeLimit(request.getQuestionTimeLimit())
                .autoAdvance(request.getAutoAdvance() != null ? request.getAutoAdvance() : false)
                .questionIds(questions.stream().map(Question::getId).collect(Collectors.toList()))
                .build();

        session = sessionRepository.save(session);
        log.info("✅ Sessão criada com código: {} | autoAdvance: {} | totalPerguntas: {}",
                session.getSessionCode(), session.getAutoAdvance(), session.getQuestionIds().size());

        // Gera QR Code
        String qrCodeUrl = qrCodeService.generateQRCodeBase64(session.getSessionCode());

        return SessionResponse.builder()
                .id(session.getId())
                .sessionCode(session.getSessionCode())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .totalQuestions(session.getQuestionIds().size())
                .questionTimeLimit(session.getQuestionTimeLimit())
                .autoAdvance(session.getAutoAdvance())
                .qrCodeUrl(qrCodeUrl)
                .build();
    }

    /**
     * Busca sessão por código
     */
    public QuizSession getSessionByCode(String sessionCode) {
        return sessionRepository.findBySessionCode(sessionCode)
                .orElseThrow(() -> new RuntimeException("Sessão não encontrada: " + sessionCode));
    }

    /**
     * Inicia o quiz
     */
    @Transactional
    public void startSession(String sessionCode) {
        QuizSession session = getSessionByCode(sessionCode);

        if (session.getStatus() != SessionStatus.WAITING) {
            throw new RuntimeException("Sessão não está aguardando início");
        }

        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("Sessão {} iniciada", sessionCode);
    }

    /**
     * Avança para a próxima pergunta
     */
    @Transactional
    public void nextQuestion(String sessionCode) {
        QuizSession session = getSessionByCode(sessionCode);

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new RuntimeException("Sessão não está ativa");
        }

        int nextIndex = session.getCurrentQuestionIndex() + 1;

        if (nextIndex >= session.getQuestionIds().size()) {
            // Finaliza o jogo
            session.setStatus(SessionStatus.FINISHED);
            session.setFinishedAt(LocalDateTime.now());
            log.info("Sessão {} finalizada", sessionCode);
        } else {
            session.setCurrentQuestionIndex(nextIndex);
            log.info("Sessão {} - Avançando para pergunta {}/{}",
                    sessionCode, nextIndex + 1, session.getQuestionIds().size());
        }

        sessionRepository.save(session);
    }

    /**
     * Finaliza a sessão
     */
    @Transactional
    public void finishSession(String sessionCode) {
        QuizSession session = getSessionByCode(sessionCode);
        session.setStatus(SessionStatus.FINISHED);
        session.setFinishedAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("Sessão {} finalizada manualmente", sessionCode);
    }

    /**
     * Obtém a pergunta atual da sessão
     */
    public Question getCurrentQuestion(String sessionCode) {
        QuizSession session = getSessionByCode(sessionCode);

        if (session.getCurrentQuestionIndex() >= session.getQuestionIds().size()) {
            throw new RuntimeException("Não há mais perguntas disponíveis");
        }

        Long questionId = session.getQuestionIds().get(session.getCurrentQuestionIndex());
        return questionService.getQuestionById(questionId);
    }

    /**
     * Obtém o total de perguntas ativas disponíveis
     */
    public long getTotalAvailableQuestions() {
        return questionService.getTotalActiveQuestions();
    }
}
