package com.baronesa.website.controller;

import com.baronesa.website.entity.Question;
import com.baronesa.website.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
@Tag(name = "Questions", description = "API para gerenciamento de perguntas do quiz")
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping
    @Operation(summary = "Listar todas as perguntas")
    public ResponseEntity<List<Question>> getAllQuestions() {
        log.debug("GET /api/questions - Listando todas as perguntas");
        List<Question> questions = questionService.getAllQuestions();
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/active")
    @Operation(summary = "Listar perguntas ativas")
    public ResponseEntity<List<Question>> getActiveQuestions() {
        log.debug("GET /api/questions/active - Listando perguntas ativas");
        List<Question> questions = questionService.getActiveQuestions();
        return ResponseEntity.ok(questions);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar pergunta por ID")
    public ResponseEntity<Question> getQuestionById(@PathVariable Long id) {
        log.debug("GET /api/questions/{} - Buscando pergunta", id);
        Question question = questionService.getQuestionById(id);
        return ResponseEntity.ok(question);
    }

    @PostMapping
    @Operation(summary = "Criar nova pergunta")
    public ResponseEntity<Question> createQuestion(@RequestBody Question question) {
        log.info("POST /api/questions - Criando nova pergunta: {}", question.getQuestion());
        Question createdQuestion = questionService.createQuestion(question);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdQuestion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar pergunta")
    public ResponseEntity<Question> updateQuestion(@PathVariable Long id, @RequestBody Question question) {
        log.info("PUT /api/questions/{} - Atualizando pergunta", id);
        Question updatedQuestion = questionService.updateQuestion(id, question);
        return ResponseEntity.ok(updatedQuestion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar pergunta")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        log.info("DELETE /api/questions/{} - Deletando pergunta", id);
        questionService.deleteQuestion(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Alternar status da pergunta (ativo/inativo)")
    public ResponseEntity<Question> toggleQuestionStatus(@PathVariable Long id) {
        log.info("PATCH /api/questions/{}/toggle - Alternando status da pergunta", id);
        Question updatedQuestion = questionService.toggleQuestionStatus(id);
        return ResponseEntity.ok(updatedQuestion);
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Listar perguntas por categoria")
    public ResponseEntity<List<Question>> getQuestionsByCategory(@PathVariable Long categoryId) {
        log.debug("GET /api/questions/category/{} - Listando perguntas por categoria", categoryId);
        List<Question> questions = questionService.getQuestionsByCategory(categoryId);
        return ResponseEntity.ok(questions);
    }
}