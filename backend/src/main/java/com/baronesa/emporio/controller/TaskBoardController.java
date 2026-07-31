package com.baronesa.emporio.controller;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.dto.BoardDataDTO;
import com.baronesa.emporio.dto.CommentDTO;
import com.baronesa.emporio.dto.HistoryItemDTO;
import com.baronesa.emporio.dto.ListDTO;
import com.baronesa.emporio.dto.TaskDTO;
import com.baronesa.emporio.entity.Comment;
import com.baronesa.emporio.entity.Task;
import com.baronesa.emporio.entity.TaskHistory;
import com.baronesa.emporio.entity.TaskList;
import com.baronesa.emporio.entity.TaskTimeEntry;
import com.baronesa.emporio.entity.TaskWatcher;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.enums.TaskStatus;
import com.baronesa.emporio.service.CommentService;
import com.baronesa.emporio.service.TaskHistoryService;
import com.baronesa.emporio.service.TaskListService;
import com.baronesa.emporio.service.TaskService;
import com.baronesa.emporio.service.TaskTimeService;
import com.baronesa.emporio.service.TaskWatcherService;
import com.baronesa.emporio.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/taskboard")
@RequiredArgsConstructor
@Tag(name = "Task Board", description = "Endpoints para dashboard do quadro de tarefas")
public class TaskBoardController {

    private final TaskListService taskListService;
    private final TaskService taskService;
    private final CommentService commentService;
    private final TaskHistoryService taskHistoryService;
    private final TaskWatcherService taskWatcherService;
    private final UsuarioService usuarioService;
    private final TaskTimeService taskTimeService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping
    @Operation(
            summary = "Obter dados completos do board",
            description = "Retorna todas as listas com suas tarefas, comentários, histórico e informações agregadas"
    )
    @ApiResponse(responseCode = "200", description = "Dados do board retornados com sucesso")
    public ResponseEntity<BoardDataDTO> getBoardData() {
        // Buscar todas as listas
        List<TaskList> allLists = taskListService.findAll();

        // Criar map de usuários para rápido acesso (id -> nome)
        Map<Long, String> userNamesMap = usuarioService.listarPorRole(Usuario.Role.CLIENTE).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));

        // Incluir outros roles também
        usuarioService.listarPorRole(Usuario.Role.FUNCIONARIO).forEach(u ->
                userNamesMap.put(u.getId(), u.getNome()));
        usuarioService.listarPorRole(Usuario.Role.ADMIN).forEach(u ->
                userNamesMap.put(u.getId(), u.getNome()));

        // Converter para o formato desejado
        List<ListDTO> listDTOs = new ArrayList<>();

        for (TaskList taskList : allLists) {
            ListDTO listDTO = new ListDTO();
            listDTO.setId(taskList.getId());
            listDTO.setName(taskList.getName());
            listDTO.setType(taskList.getType().toString());

            // Pegar o nome do criador a partir do ID
            String creatorName = userNamesMap.getOrDefault(taskList.getCreatedBy(), "Usuário Desconhecido");
            listDTO.setCreated_by(creatorName);

            // Buscar tarefas para a lista atual
            List<Task> tasksInList = taskService.findByListId(taskList.getId());
            List<TaskDTO> taskDTOs = new ArrayList<>();

            for (Task task : tasksInList) {
                TaskDTO taskDTO = new TaskDTO();
                taskDTO.setId(task.getId().toString());
                taskDTO.setListId(task.getListId());
                taskDTO.setCreatedAt(task.getCreatedAt());
                taskDTO.setAssigneeId(task.getAssigneeId());
                taskDTO.setCreatedBy(task.getCreatedBy());
                TaskStatus currentStatus = task.calculateCurrentStatus();
                taskDTO.setStatusCode(currentStatus.name());
                taskDTO.setStatus(currentStatus.getDisplayName());
                taskDTO.setTitle(task.getTitle());
                taskDTO.setDescription(task.getDescription());
                taskDTO.setPriority(task.getPriority());

                List<TaskTimeEntry> timeEntries = taskTimeService.findByTaskId(task.getId());
                Long totalDuration = timeEntries.stream()
                        .filter(entry -> entry.getDuration() != null)
                        .mapToLong(TaskTimeEntry::getDuration)
                        .sum();

                // Verificar se há uma sessão ativa
                Optional<TaskTimeEntry> activeSession = timeEntries.stream()
                        .filter(entry -> entry.getEndTime() == null)
                        .findFirst();

                // Adicionar ao DTO
                taskDTO.setTotalDurationSeconds(totalDuration);
                taskDTO.setHasActiveSession(activeSession.isPresent());
                if (activeSession.isPresent()) {
                    taskDTO.setActiveSessionStartTime(activeSession.get().getStartTime());
                    taskDTO.setActiveSessionUserId(activeSession.get().getUserId());
                    taskDTO.setActiveSessionUserName(userNamesMap.getOrDefault(activeSession.get().getUserId(), "Usuário Desconhecido"));
                }

                // Data de vencimento formatada
                if (task.getDueDate() != null) {
                    taskDTO.setDueDate(task.getDueDate().format(DATE_FORMATTER));
                }

                // Obter nome do responsável
                if (task.getAssigneeId() != null) {
                    String assigneeName = userNamesMap.getOrDefault(task.getAssigneeId(), "Não atribuído");
                    taskDTO.setAssignee(assigneeName);
                }

                // Buscar observadores
                List<TaskWatcher> watchers = taskWatcherService.findByTaskId(task.getId());
                List<Map<String, Object>> watcherInfoList = watchers.stream()
                        .map(watcher -> {
                            Map<String, Object> watcherInfo = new HashMap<>();
                            watcherInfo.put("id", watcher.getUserId());
                            watcherInfo.put("nome", userNamesMap.getOrDefault(watcher.getUserId(), "Usuário Desconhecido"));
                            return watcherInfo;
                        })
                        .collect(Collectors.toList());

                taskDTO.setWatchers(watcherInfoList);

                // Buscar comentários
                List<Comment> comments = commentService.findByTaskId(task.getId());
                List<CommentDTO> commentDTOs = new ArrayList<>();

                for (Comment comment : comments) {
                    CommentDTO commentDTO = new CommentDTO();
                    commentDTO.setId(comment.getId().toString());
                    commentDTO.setText(comment.getText());
                    commentDTO.setId_usuario(comment.getUserId().toString());

                    if (comment.getCreatedAt() != null) {
                        commentDTO.setData(comment.getCreatedAt().format(DATETIME_FORMATTER));
                    }

                    commentDTOs.add(commentDTO);
                }
                taskDTO.setComments(commentDTOs);

                // Buscar histórico
                List<TaskHistory> historyItems = taskHistoryService.findByTaskIdOrderByCreatedAtDesc(task.getId());
                List<HistoryItemDTO> historyDTOs = new ArrayList<>();

                for (TaskHistory history : historyItems) {
                    HistoryItemDTO historyDTO = new HistoryItemDTO();
                    historyDTO.setId(history.getId().toString());
                    historyDTO.setAcao(history.getAction());

                    if (history.getCreatedAt() != null) {
                        historyDTO.setData(history.getCreatedAt().format(DATETIME_FORMATTER));
                    }

                    String userName = userNamesMap.getOrDefault(history.getUserId(), "Usuário Desconhecido");
                    historyDTO.setUsuario(userName);

                    historyDTOs.add(historyDTO);
                }
                taskDTO.setHistorico(historyDTOs);

                taskDTOs.add(taskDTO);
            }

            listDTO.setTasks(taskDTOs);
            listDTOs.add(listDTO);
        }

        BoardDataDTO boardData = new BoardDataDTO();
        boardData.setListas(listDTOs);

        return new ResponseEntity<>(boardData, HttpStatus.OK);
    }
}