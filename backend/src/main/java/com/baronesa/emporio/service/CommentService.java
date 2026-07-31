package com.baronesa.emporio.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baronesa.emporio.entity.Comment;
import com.baronesa.emporio.entity.TaskHistory;
import com.baronesa.emporio.repository.CommentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskHistoryService taskHistoryService;

    @Transactional(readOnly = true)
    public List<Comment> findByTaskId(Long taskId) {
        return commentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Transactional(readOnly = true)
    public Optional<Comment> findById(Long id) {
        return commentRepository.findById(id); // JPA padrão
    }

    @Transactional
    public Comment addComment(Comment comment) {
        comment.setCreatedAt(LocalDateTime.now());
        Comment savedComment = commentRepository.save(comment); // JPA padrão

        // Registrar no histórico
        TaskHistory history = new TaskHistory();
        history.setTaskId(comment.getTaskId());
        history.setUserId(comment.getUserId());
        history.setAction("Comentário adicionado");
        history.setDetails("Um novo comentário foi adicionado");
        history.setCreatedAt(LocalDateTime.now());
        taskHistoryService.save(history);

        return savedComment;
    }

    @Transactional
    public Comment updateComment(Long id, Comment commentDetails) {
        Comment comment = commentRepository.findById(id) // JPA padrão
                .orElseThrow(() -> new EntityNotFoundException("Comentário não encontrado"));

        // Só permitir edição de texto
        comment.setText(commentDetails.getText());

        return commentRepository.save(comment); // JPA padrão
    }

    @Transactional
    public void deleteComment(Long id, Long userId) {
        Comment comment = commentRepository.findById(id) // JPA padrão
                .orElseThrow(() -> new EntityNotFoundException("Comentário não encontrado"));

        // Opcional: verificar se o usuário é o autor do comentário
        if (!comment.getUserId().equals(userId)) {
            throw new IllegalStateException("Você não tem permissão para excluir este comentário");
        }

        commentRepository.delete(comment); // JPA padrão

        // Registrar no histórico
        TaskHistory history = new TaskHistory();
        history.setTaskId(comment.getTaskId());
        history.setUserId(userId);
        history.setAction("Comentário removido");
        history.setDetails("Um comentário foi removido");
        history.setCreatedAt(LocalDateTime.now());
        taskHistoryService.save(history);
    }
}