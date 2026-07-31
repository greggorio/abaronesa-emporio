package com.baronesa.emporio.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.entity.Task;
import com.baronesa.emporio.entity.TaskHistory;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.TaskHistoryService;
import com.baronesa.emporio.service.TaskService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tasks", description = "Endpoints para gerenciamento de tarefas")
public class TaskController {

    private final TaskService taskService;
    private final TaskHistoryService taskHistoryService;
    private final UsuarioRepository usuarioRepository;

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
    @Operation(summary = "Listar todas as tarefas", description = "Retorna uma lista de todas as tarefas")
    @ApiResponse(responseCode = "200", description = "Lista de tarefas retornada com sucesso")
    public ResponseEntity<List<Task>> getAllTasks() {
        List<Task> tasks = taskService.findAll();
        return new ResponseEntity<>(tasks, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar tarefa por ID", description = "Retorna uma tarefa específica pelo seu ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarefa encontrada"),
            @ApiResponse(responseCode = "404", description = "Tarefa não encontrada")
    })
    public ResponseEntity<Task> getTaskById(
            @Parameter(description = "ID da tarefa") @PathVariable Long id) {
        return taskService.findById(id)
                .map(task -> new ResponseEntity<>(task, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/list/{listId}")
    @Operation(summary = "Buscar tarefas por lista", description = "Retorna todas as tarefas de uma lista específica")
    public ResponseEntity<List<Task>> getTasksByListId(
            @Parameter(description = "ID da lista") @PathVariable Long listId) {
        List<Task> tasks = taskService.findByListId(listId);
        return new ResponseEntity<>(tasks, HttpStatus.OK);
    }

    @GetMapping("/assignee/{assigneeId}")
    @Operation(summary = "Buscar tarefas por responsável", description = "Retorna todas as tarefas atribuídas a um usuário")
    public ResponseEntity<List<Task>> getTasksByAssigneeId(
            @Parameter(description = "ID do usuário responsável") @PathVariable Long assigneeId) {
        List<Task> tasks = taskService.findByAssigneeId(assigneeId);
        return new ResponseEntity<>(tasks, HttpStatus.OK);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Buscar tarefas por status", description = "Retorna todas as tarefas com um status específico")
    public ResponseEntity<List<Task>> getTasksByStatus(
            @Parameter(description = "Status da tarefa") @PathVariable String status) {
        List<Task> tasks = taskService.findByStatus(status);
        return new ResponseEntity<>(tasks, HttpStatus.OK);
    }

    @GetMapping("/priority/{priority}")
    @Operation(summary = "Buscar tarefas por prioridade", description = "Retorna todas as tarefas com uma prioridade específica")
    public ResponseEntity<List<Task>> getTasksByPriority(
            @Parameter(description = "Prioridade da tarefa") @PathVariable String priority) {
        List<Task> tasks = taskService.findByPriority(priority);
        return new ResponseEntity<>(tasks, HttpStatus.OK);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Buscar histórico da tarefa", description = "Retorna o histórico de alterações de uma tarefa")
    public ResponseEntity<List<TaskHistory>> getTaskHistory(
            @Parameter(description = "ID da tarefa") @PathVariable Long id) {
        List<TaskHistory> history = taskHistoryService.findByTaskIdOrderByCreatedAtDesc(id);
        return new ResponseEntity<>(history, HttpStatus.OK);
    }

    @PostMapping
    @Operation(summary = "Criar nova tarefa", description = "Cria uma nova tarefa no sistema")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tarefa criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        Long currentUserId = getCurrentUserId();
        Task savedTask = taskService.save(task, currentUserId);
        return new ResponseEntity<>(savedTask, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar tarefa", description = "Atualiza uma tarefa existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarefa atualizada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Tarefa não encontrada")
    })
    public ResponseEntity<Task> updateTask(
            @Parameter(description = "ID da tarefa") @PathVariable Long id,
            @RequestBody Task task) {
        return taskService.findById(id)
                .map(existingTask -> {
                    task.setId(id);
                    task.setCreatedAt(existingTask.getCreatedAt());
                    Task updatedTask = taskService.save(task);
                    return new ResponseEntity<>(updatedTask, HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/{id}/move")
    @Operation(summary = "Mover tarefa para outra lista", description = "Move uma tarefa de uma lista para outra")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarefa movida com sucesso"),
            @ApiResponse(responseCode = "404", description = "Tarefa não encontrada"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<Task> moveTask(
            @Parameter(description = "ID da tarefa") @PathVariable Long id,
            @Parameter(description = "ID da nova lista") @RequestParam Long listId) {

        try {
            Long currentUserId = getCurrentUserId();

            // Buscar a tarefa existente
            Optional<Task> taskOpt = taskService.findById(id);
            if (!taskOpt.isPresent()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            Task task = taskOpt.get();
            Long oldListId = task.getListId();

            // Atualizar apenas o campo listId e updatedAt
            task.setListId(listId);
            task.setUpdatedAt(LocalDateTime.now());

            // Salvar a tarefa
            Task updatedTask = taskService.save(task);

            // Registrar histórico
            TaskHistory history = new TaskHistory();
            history.setTaskId(id);
            history.setUserId(currentUserId);
            history.setAction("Movimentação de lista");
            history.setDetails("Tarefa movida da lista " + oldListId + " para lista " + listId);
            history.setCreatedAt(LocalDateTime.now());

            taskHistoryService.save(history);

            return new ResponseEntity<>(updatedTask, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Atualizar status da tarefa", description = "Atualiza o status de uma tarefa específica")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Tarefa não encontrada")
    })
    public ResponseEntity<Task> updateTaskStatus(
            @Parameter(description = "ID da tarefa") @PathVariable Long id,
            @Parameter(description = "Novo status") @RequestParam String status) {

        try {
            Long currentUserId = getCurrentUserId();
            Task updatedTask = taskService.updateStatus(id, status, currentUserId);
            return new ResponseEntity<>(updatedTask, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}/assign")
    @Operation(summary = "Atribuir tarefa", description = "Atribui uma tarefa a um usuário específico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarefa atribuída com sucesso"),
            @ApiResponse(responseCode = "404", description = "Tarefa não encontrada")
    })
    public ResponseEntity<Task> assignTask(
            @Parameter(description = "ID da tarefa") @PathVariable Long id,
            @Parameter(description = "ID do novo responsável") @RequestParam Long assigneeId) {

        try {
            Long currentUserId = getCurrentUserId();
            Task updatedTask = taskService.assignTask(id, assigneeId, currentUserId);
            return new ResponseEntity<>(updatedTask, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir tarefa", description = "Remove uma tarefa do sistema")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tarefa excluída com sucesso"),
            @ApiResponse(responseCode = "404", description = "Tarefa não encontrada")
    })
    public ResponseEntity<Void> deleteTask(
            @Parameter(description = "ID da tarefa") @PathVariable Long id) {
        return taskService.findById(id)
                .map(task -> {
                    taskService.delete(id);
                    return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}