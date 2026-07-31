package com.baronesa.website.service;

import com.baronesa.website.entity.Category;
import com.baronesa.website.entity.Question;
import com.baronesa.website.repository.CategoryRepository;
import com.baronesa.website.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<Question> getAllQuestions() {
        log.debug("Buscando todas as perguntas");
        return questionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Question> getActiveQuestions() {
        log.debug("Buscando perguntas ativas");
        return questionRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Question getQuestionById(Long id) {
        log.debug("Buscando pergunta com ID: {}", id);
        return questionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pergunta não encontrada com ID: " + id));
    }

    @Transactional
    public Question createQuestion(Question question) {
        log.info("Criando nova pergunta: {}", question.getQuestion());
        
        // Verificar se a categoria existe
        if (question.getCategory() != null && question.getCategory().getId() != null) {
            Category category = categoryRepository.findById(question.getCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + question.getCategory().getId()));
            question.setCategory(category);
        }
        
        Question savedQuestion = questionRepository.save(question);
        log.info("Pergunta criada com sucesso, ID: {}", savedQuestion.getId());
        return savedQuestion;
    }

    @Transactional
    public Question updateQuestion(Long id, Question questionDetails) {
        log.info("Atualizando pergunta com ID: {}", id);
        
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pergunta não encontrada com ID: " + id));
        
        // Atualizar campos
        question.setQuestion(questionDetails.getQuestion());
        question.setOptions(questionDetails.getOptions());
        question.setCorrectAnswer(questionDetails.getCorrectAnswer());
        question.setPoints(questionDetails.getPoints());
        question.setImageUrl(questionDetails.getImageUrl());
        question.setActive(questionDetails.getActive());
        
        // Atualizar categoria se fornecida
        if (questionDetails.getCategory() != null && questionDetails.getCategory().getId() != null) {
            Category category = categoryRepository.findById(questionDetails.getCategory().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada: " + questionDetails.getCategory().getId()));
            question.setCategory(category);
        }
        
        Question updatedQuestion = questionRepository.save(question);
        log.info("Pergunta atualizada com sucesso, ID: {}", updatedQuestion.getId());
        return updatedQuestion;
    }

    @Transactional
    public void deleteQuestion(Long id) {
        log.info("Deletando pergunta com ID: {}", id);
        
        if (!questionRepository.existsById(id)) {
            throw new NoSuchElementException("Pergunta não encontrada com ID: " + id);
        }
        
        questionRepository.deleteById(id);
        log.info("Pergunta deletada com sucesso, ID: {}", id);
    }

    @Transactional
    public Question toggleQuestionStatus(Long id) {
        log.info("Alternando status da pergunta com ID: {}", id);
        
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pergunta não encontrada com ID: " + id));
        
        question.setActive(!question.getActive());
        Question updatedQuestion = questionRepository.save(question);
        log.info("Status da pergunta alternado com sucesso, ID: {}, ativo: {}", 
                 updatedQuestion.getId(), updatedQuestion.getActive());
        return updatedQuestion;
    }

    @Transactional(readOnly = true)
    public List<Question> getQuestionsByCategory(Long categoryId) {
        log.debug("Buscando perguntas para a categoria com ID: {}", categoryId);
        return questionRepository.findByCategoryIdAndActiveTrue(categoryId);
    }

    @Transactional(readOnly = true)
    public long getTotalQuestions() {
        log.debug("Contando total de perguntas");
        return questionRepository.count();
    }

    @Transactional(readOnly = true)
    public long getTotalActiveQuestions() {
        log.debug("Contando total de perguntas ativas");
        return questionRepository.countByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Question> findRandomQuestions(int limit) {
        log.debug("Buscando {} perguntas aleatórias", limit);
        return questionRepository.findRandomQuestions(limit);
    }

    @Transactional(readOnly = true)
    public List<Question> findRandomQuestionsByCategoryId(Long categoryId, int limit) {
        log.debug("Buscando {} perguntas aleatórias para a categoria {}", limit, categoryId);
        return questionRepository.findRandomQuestionsByCategoryId(categoryId, limit);
    }
}