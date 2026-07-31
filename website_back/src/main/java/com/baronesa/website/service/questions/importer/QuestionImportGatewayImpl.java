package com.baronesa.website.service.questions.importer;

import com.baronesa.website.entity.Category;
import com.baronesa.website.entity.Question;
import com.baronesa.website.repository.CategoryRepository;
import com.baronesa.website.repository.QuestionRepository;
import com.baronesa.website.service.QuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionImportGatewayImpl implements QuestionImportGateway {

    private final QuestionService questionService;
    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public Long findExistingQuestionId(String questionText, Long categoryId) {
        if (questionText == null || questionText.trim().isEmpty() || categoryId == null) {
            return null;
        }
        Optional<Question> existingQuestion = questionRepository.findByQuestionAndCategoryId(questionText, categoryId);
        return existingQuestion.map(Question::getId).orElse(null);
    }

    @Override
    @Transactional
    public Long createQuestion(QuestionImportRow row, boolean activeFinal) {
        Question question = buildQuestion(row, activeFinal);
        Question savedQuestion = questionService.createQuestion(question);
        log.info("Pergunta criada via gateway: id={}, texto={}", savedQuestion.getId(), savedQuestion.getQuestion());
        return savedQuestion.getId();
    }

    @Override
    @Transactional
    public Long updateQuestion(Long existingId, QuestionImportRow row, boolean activeFinal) {
        Question question = buildQuestion(row, activeFinal);
        Question updatedQuestion = questionService.updateQuestion(existingId, question);
        log.info("Pergunta atualizada via gateway: id={}, texto={}", updatedQuestion.getId(), updatedQuestion.getQuestion());
        return updatedQuestion.getId();
    }

    private Question buildQuestion(QuestionImportRow row, boolean activeFinal) {
        Question question = new Question();
        question.setQuestion(row.getQuestion());
        question.setOptions(row.getOptions());
        question.setCorrectAnswer(row.getCorrectAnswer());
        question.setPoints(row.getPoints() != null ? row.getPoints() : 10);
        question.setActive(activeFinal);
        question.setImageUrl(row.getImageUrl());

        if (row.getCategoryId() != null) {
            log.debug("Searching for category with ID: {}", row.getCategoryId());
            Category category = categoryRepository.findById(row.getCategoryId())
                    .orElseThrow(() -> {
                        log.error("Category not found with ID: {}. Available categories: {}",
                                row.getCategoryId(), categoryRepository.findAll().stream()
                                        .map(c -> c.getId() + ":" + c.getName())
                                        .toList());
                        return new IllegalArgumentException("Categoria não encontrada: " + row.getCategoryId());
                    });
            question.setCategory(category);
        } else {
            throw new IllegalArgumentException("Categoria é obrigatória para importar perguntas");
        }

        return question;
    }
}
