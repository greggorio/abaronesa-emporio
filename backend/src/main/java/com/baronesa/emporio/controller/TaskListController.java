package com.baronesa.emporio.controller;

import java.time.LocalDateTime;
import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.entity.TaskList;
import com.baronesa.emporio.enums.ListType;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.TaskListService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/tasklists")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Task Lists", description = "Endpoints para gerenciamento de listas de tarefas")
public class TaskListController {

    private final TaskListService taskListService;

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
    @Operation(summary = "Listar todas as listas de tarefas", description = "Retorna todas as listas de tarefas do sistema")
    @ApiResponse(responseCode = "200", description = "Listas retornadas com sucesso")
    public ResponseEntity<List<TaskList>> getAllTaskLists() {
        List<TaskList> taskLists = taskListService.findAll();
        return new ResponseEntity<>(taskLists, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar lista por ID", description = "Retorna uma lista específica pelo seu ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista encontrada"),
            @ApiResponse(responseCode = "404", description = "Lista não encontrada")
    })
    public ResponseEntity<TaskList> getTaskListById(
            @Parameter(description = "ID da lista") @PathVariable Long id) {
        return taskListService.findById(id)
                .map(taskList -> new ResponseEntity<>(taskList, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @Operation(summary = "Criar nova lista de tarefas", description = "Cria uma nova lista no sistema")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lista criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<TaskList> createTaskList(@RequestBody TaskList taskList) {
        taskList.setType(ListType.GERAL);
        taskList.setCreatedAt(LocalDateTime.now());
        taskList.setCreatedBy(getCurrentUserId());
        TaskList savedTaskList = taskListService.save(taskList);
        return new ResponseEntity<>(savedTaskList, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar lista de tarefas", description = "Atualiza uma lista existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista atualizada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Lista não encontrada")
    })
    public ResponseEntity<TaskList> updateTaskList(
            @Parameter(description = "ID da lista") @PathVariable Long id,
            @RequestBody TaskList taskList) {
        return taskListService.findById(id)
                .map(existingTaskList -> {
                    taskList.setId(id);
                    TaskList updatedTaskList = taskListService.save(taskList);
                    return new ResponseEntity<>(updatedTaskList, HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir lista de tarefas", description = "Remove uma lista do sistema")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lista excluída com sucesso"),
            @ApiResponse(responseCode = "404", description = "Lista não encontrada")
    })
    public ResponseEntity<Void> deleteTaskList(
            @Parameter(description = "ID da lista") @PathVariable Long id) {
        return taskListService.findById(id)
                .map(taskList -> {
                    taskListService.delete(id);
                    return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
}