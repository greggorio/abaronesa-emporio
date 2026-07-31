package com.baronesa.emporio.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.entity.Comment;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.CommentService;
import com.baronesa.emporio.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Comments", description = "Endpoints para gerenciamento de comentários de tarefas")
public class CommentController {

    private final CommentService commentService;
    private final UsuarioService usuarioService;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Usuario getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
                return usuarioService.findById(userPrincipal.getId()).orElse(null);
            }
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado: {}", e.getMessage());
        }
        return null;
    }

    @GetMapping
    @Operation(summary = "Listar comentários", description = "Retorna todos os comentários de uma tarefa")
    @ApiResponse(responseCode = "200", description = "Lista de comentários retornada com sucesso")
    public ResponseEntity<List<Map<String, Object>>> getComments(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId) {
        List<Comment> comments = commentService.findByTaskId(taskId);

        // Mapear comentários para incluir nome do usuário
        List<Map<String, Object>> result = comments.stream().map(comment -> {
            Map<String, Object> commentMap = new HashMap<>();
            commentMap.put("id", comment.getId());
            commentMap.put("text", comment.getText());
            commentMap.put("id_usuario", comment.getUserId());

            // Buscar nome do usuário
            Usuario user = usuarioService.findById(comment.getUserId()).orElse(null);
            commentMap.put("usuario", user != null ? user.getNome() : "Usuário Desconhecido");

            // Formatação da data
            commentMap.put("data", comment.getCreatedAt().format(DATETIME_FORMATTER));

            return commentMap;
        }).collect(Collectors.toList());

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping
    @Operation(summary = "Adicionar comentário", description = "Adiciona um novo comentário a uma tarefa")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comentário adicionado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Texto do comentário inválido"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<?> addComment(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId,
            @RequestBody Map<String, String> payload) {

        try {
            Usuario currentUser = getCurrentUser();
            if (currentUser == null) {
                return new ResponseEntity<>(
                        Map.of("error", "Usuário não autenticado"),
                        HttpStatus.UNAUTHORIZED
                );
            }

            String text = payload.get("text");
            if (text == null || text.trim().isEmpty()) {
                return new ResponseEntity<>(
                        Map.of("error", "O texto do comentário não pode estar vazio"),
                        HttpStatus.BAD_REQUEST
                );
            }

            Comment comment = new Comment();
            comment.setTaskId(taskId);
            comment.setUserId(currentUser.getId());
            comment.setText(text);
            comment.setCreatedAt(LocalDateTime.now());

            Comment savedComment = commentService.addComment(comment);

            // Preparar resposta
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedComment.getId());
            response.put("text", savedComment.getText());
            response.put("id_usuario", savedComment.getUserId());
            response.put("usuario", currentUser.getNome());
            response.put("data", savedComment.getCreatedAt().format(DATETIME_FORMATTER));

            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(
                    Map.of("error", e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Excluir comentário", description = "Remove um comentário de uma tarefa")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comentário removido com sucesso"),
            @ApiResponse(responseCode = "401", description = "Usuário não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não tem permissão para excluir o comentário"),
            @ApiResponse(responseCode = "404", description = "Comentário não encontrado"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<?> deleteComment(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId,
            @Parameter(description = "ID do comentário") @PathVariable Long commentId) {

        try {
            Usuario currentUser = getCurrentUser();
            if (currentUser == null) {
                return new ResponseEntity<>(
                        Map.of("error", "Usuário não autenticado"),
                        HttpStatus.UNAUTHORIZED
                );
            }

            commentService.deleteComment(commentId, currentUser.getId());
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (EntityNotFoundException e) {
            return new ResponseEntity<>(
                    Map.of("error", e.getMessage()),
                    HttpStatus.NOT_FOUND
            );
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(
                    Map.of("error", e.getMessage()),
                    HttpStatus.FORBIDDEN
            );
        } catch (Exception e) {
            return new ResponseEntity<>(
                    Map.of("error", e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}