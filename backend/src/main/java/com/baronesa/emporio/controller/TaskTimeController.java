package com.baronesa.emporio.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.entity.TaskTimeEntry;
import com.baronesa.emporio.enums.TaskStatus;
import com.baronesa.emporio.security.UserPrincipal;
import com.baronesa.emporio.service.TaskTimeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/tasks/{taskId}/time")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Time Tracking", description = "Endpoints para controle de tempo das tarefas")
public class TaskTimeController {

    private final TaskTimeService taskTimeService;

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
    @Operation(summary = "Obter entradas de tempo", description = "Retorna todas as entradas de tempo de uma tarefa")
    @ApiResponse(responseCode = "200", description = "Entradas de tempo retornadas com sucesso")
    public ResponseEntity<List<TaskTimeEntry>> getTaskTimeEntries(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId) {
        return new ResponseEntity<>(taskTimeService.findByTaskId(taskId), HttpStatus.OK);
    }

    @PostMapping("/start")
    @Operation(summary = "Iniciar trabalho", description = "Inicia uma nova sessão de trabalho em uma tarefa")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Sessão de trabalho iniciada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Erro na validação (usuário já tem sessão ativa)"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<?> startWork(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId) {
        try {
            Long userId = getCurrentUserId();
            TaskTimeEntry entry = taskTimeService.startWork(taskId, userId);
            return new ResponseEntity<>(entry, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{entryId}/stop")
    @Operation(summary = "Parar trabalho", description = "Encerra uma sessão de trabalho ativa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sessão de trabalho encerrada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Erro na validação (sessão já encerrada ou usuário inválido)"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<?> stopWork(
            @Parameter(description = "ID da entrada de tempo") @PathVariable String entryId) {
        try {
            Long userId = getCurrentUserId();
            TaskTimeEntry entry = taskTimeService.stopWork(entryId, userId);
            return new ResponseEntity<>(entry, HttpStatus.OK);
        } catch (IllegalStateException e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/status")
    @Operation(summary = "Atualizar status da tarefa", description = "Atualiza o status de uma tarefa com controle de sessões ativas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Status inválido"),
            @ApiResponse(responseCode = "500", description = "Erro interno do servidor")
    })
    public ResponseEntity<?> updateTaskStatus(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId,
            @Parameter(description = "Novo status") @RequestParam String status) {
        try {
            Long userId = getCurrentUserId();
            TaskStatus taskStatus = TaskStatus.valueOf(status);
            taskTimeService.updateTaskStatus(taskId, taskStatus, userId);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(
                    Map.of("error", "Status inválido. Valores permitidos: " +
                            java.util.Arrays.toString(TaskStatus.values())),
                    HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>(Map.of("error", e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/duration")
    @Operation(summary = "Obter duração total", description = "Retorna a duração total de trabalho em uma tarefa")
    @ApiResponse(responseCode = "200", description = "Duração total retornada com sucesso")
    public ResponseEntity<Map<String, Long>> getTaskTotalDuration(
            @Parameter(description = "ID da tarefa") @PathVariable Long taskId) {
        Long duration = taskTimeService.getTaskTotalDuration(taskId);
        return new ResponseEntity<>(Map.of("duration", duration), HttpStatus.OK);
    }
}