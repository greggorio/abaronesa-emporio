package com.baronesa.emporio.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.dto.UserDTO;
import com.baronesa.emporio.entity.TaskHistory;
import com.baronesa.emporio.entity.TaskWatcher;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.TaskHistoryService;
import com.baronesa.emporio.service.TaskWatcherService;
import com.baronesa.emporio.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/tasks/{taskId}/watchers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Task Watchers", description = "Endpoints para gerenciamento de observadores de tarefas")
public class TaskWatcherController {

    private final TaskWatcherService taskWatcherService;
    private final UsuarioService usuarioService;
    private final TaskHistoryService taskHistoryService;

    private Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
                return userPrincipal.getId();
            }
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado: {}", e.getMessage());
        }
        return 1L; // Fallback
    }

    @GetMapping
    @Operation(summary = "Listar observadores", description = "Retorna todos os observadores de uma tarefa")
    @ApiResponse(responseCode = "200", description = "Lista de observadores retornada com sucesso")
    public ResponseEntity<List<UserDTO>> getTaskWatchers(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId) {
        List<TaskWatcher> watchers = taskWatcherService.findByTaskId(taskId);

        List<UserDTO> watcherDTOs = watchers.stream()
                .map(watcher -> {
                    UserDTO dto = new UserDTO();
                    Usuario user = usuarioService.findById(watcher.getUserId()).orElse(null);
                    if (user != null) {
                        dto.setId(user.getId());
                        dto.setNome(user.getNome());
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        return new ResponseEntity<>(watcherDTOs, HttpStatus.OK);
    }

    @PostMapping
    @Operation(summary = "Adicionar observador", description = "Adiciona um usuário como observador da tarefa")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Observador adicionado com sucesso"),
            @ApiResponse(responseCode = "409", description = "Usuário já é observador desta tarefa")
    })
    public ResponseEntity<TaskWatcher> addWatcher(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId,
            @Parameter(description = "ID do usuário a ser adicionado") @RequestParam Long userId) {

        // Verificar se o watcher já existe
        List<TaskWatcher> existingWatchers = taskWatcherService.findByTaskId(taskId);
        boolean alreadyWatching = existingWatchers.stream()
                .anyMatch(w -> w.getUserId().equals(userId));

        if (alreadyWatching) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        // Adicionar novo watcher
        TaskWatcher watcher = new TaskWatcher();
        watcher.setTaskId(taskId);
        watcher.setUserId(userId);
        watcher.setAddedAt(LocalDateTime.now());

        TaskWatcher savedWatcher = taskWatcherService.save(watcher);

        // Registrar histórico
        Long currentUserId = getCurrentUserId();
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setUserId(currentUserId);
        history.setAction("Adição de observador");
        history.setCreatedAt(LocalDateTime.now());

        Usuario addedUser = usuarioService.findById(userId).orElse(null);
        String userName = addedUser != null ? addedUser.getNome() : "ID " + userId;
        history.setDetails("Usuário " + userName + " adicionado como observador");

        taskHistoryService.save(history);

        return new ResponseEntity<>(savedWatcher, HttpStatus.CREATED);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remover observador", description = "Remove um usuário dos observadores da tarefa")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Observador removido com sucesso"),
            @ApiResponse(responseCode = "404", description = "Observador não encontrado")
    })
    public ResponseEntity<Void> removeWatcher(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId,
            @Parameter(description = "ID do usuário a ser removido") @PathVariable Long userId) {

        // Buscar o watcher específico
        List<TaskWatcher> watchers = taskWatcherService.findByTaskId(taskId);
        TaskWatcher watcherToRemove = watchers.stream()
                .filter(w -> w.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        if (watcherToRemove == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // Remover watcher
        taskWatcherService.delete(watcherToRemove.getId());

        // Registrar histórico
        Long currentUserId = getCurrentUserId();
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setUserId(currentUserId);
        history.setAction("Remoção de observador");
        history.setCreatedAt(LocalDateTime.now());

        Usuario removedUser = usuarioService.findById(userId).orElse(null);
        String userName = removedUser != null ? removedUser.getNome() : "ID " + userId;
        history.setDetails("Usuário " + userName + " removido dos observadores");

        taskHistoryService.save(history);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}