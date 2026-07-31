package com.baronesa.emporio.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baronesa.emporio.entity.Task;
import com.baronesa.emporio.entity.TaskHistory;
import com.baronesa.emporio.entity.TaskTimeEntry;
import com.baronesa.emporio.enums.TaskStatus;
import com.baronesa.emporio.repository.TaskTimeEntryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskTimeService {

    private final TaskTimeEntryRepository timeEntryRepository;
    private final TaskService taskService;
    private final TaskHistoryService taskHistoryService;

    @Transactional(readOnly = true)
    public List<TaskTimeEntry> findByTaskId(Long taskId) {
        return timeEntryRepository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public Optional<TaskTimeEntry> findActiveByUserId(Long userId) {
        return timeEntryRepository.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<TaskTimeEntry> findActiveByTaskId(Long taskId) {
        return timeEntryRepository.findActiveByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public Long getTaskTotalDuration(Long taskId) {
        Long duration = timeEntryRepository.getTaskTotalDuration(taskId);
        return duration != null ? duration : 0L;
    }

    @Transactional
    public TaskTimeEntry startWork(Long taskId, Long userId) {
        // Verificar se já existe uma sessão ativa para o usuário
        Optional<TaskTimeEntry> activeSession = findActiveByUserId(userId);
        if (activeSession.isPresent()) {
            throw new IllegalStateException("Usuário já tem uma sessão ativa em outra tarefa");
        }

        // Verificar se a tarefa existe
        Task task = taskService.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Tarefa não encontrada"));

        // Criar nova entrada
        TaskTimeEntry entry = new TaskTimeEntry();
        entry.setTaskId(taskId);
        entry.setUserId(userId);
        entry.setStartTime(LocalDateTime.now());

        TaskTimeEntry savedEntry = timeEntryRepository.save(entry);

        // Atualizar status da tarefa para IN_PROGRESS
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(LocalDateTime.now());
        taskService.save(task);

        // Registrar histórico
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setUserId(userId);
        history.setAction("Início de trabalho");
        history.setDetails("Usuário iniciou trabalho na tarefa");
        history.setCreatedAt(LocalDateTime.now());
        taskHistoryService.save(history);

        return savedEntry;
    }

    @Transactional
    public TaskTimeEntry stopWork(String entryId, Long userId) {
        TaskTimeEntry entry = timeEntryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Entrada de tempo não encontrada"));

        if (entry.getEndTime() != null) {
            throw new IllegalStateException("Esta sessão de trabalho já foi encerrada");
        }

        // Apenas o usuário que iniciou a sessão pode encerrá-la
        if (!entry.getUserId().equals(userId)) {
            throw new IllegalStateException("Apenas o usuário que iniciou a sessão pode encerrá-la");
        }

        LocalDateTime endTime = LocalDateTime.now();
        entry.setEndTime(endTime);

        // Calcular duração em segundos
        long durationSec = ChronoUnit.SECONDS.between(entry.getStartTime(), endTime);
        entry.setDuration(durationSec);

        TaskTimeEntry savedEntry = timeEntryRepository.save(entry);

        // Atualizar status da tarefa para PAUSED
        Task task = taskService.findById(entry.getTaskId())
                .orElseThrow(() -> new EntityNotFoundException("Tarefa não encontrada"));

        task.setStatus(TaskStatus.PAUSED);
        task.setUpdatedAt(LocalDateTime.now());
        taskService.save(task);

        // Registrar histórico
        TaskHistory history = new TaskHistory();
        history.setTaskId(entry.getTaskId());
        history.setUserId(userId);
        history.setAction("Fim de trabalho");
        history.setDetails("Usuário finalizou sessão de trabalho ("
                + formatDuration(durationSec) + ")");
        history.setCreatedAt(LocalDateTime.now());
        taskHistoryService.save(history);

        return savedEntry;
    }

    @Transactional
    public void updateTaskStatus(Long taskId, TaskStatus newStatus, Long userId) {
        Task task = taskService.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("Tarefa não encontrada"));

        // Se estiver passando para concluída, fechar qualquer sessão ativa
        if (newStatus == TaskStatus.COMPLETED) {
            Optional<TaskTimeEntry> activeSession = findActiveByTaskId(taskId);
            if (activeSession.isPresent()) {
                stopWork(activeSession.get().getId(), activeSession.get().getUserId());
            }
        }

        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        task.setUpdatedAt(LocalDateTime.now());

        if (newStatus == TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
        }

        taskService.save(task);

        // Registrar histórico
        TaskHistory history = new TaskHistory();
        history.setTaskId(taskId);
        history.setUserId(userId);
        history.setAction("Alteração de status");
        history.setDetails("Status alterado de " +
                (oldStatus != null ? oldStatus.getDisplayName() : "Não definido") +
                " para " + newStatus.getDisplayName());
        history.setCreatedAt(LocalDateTime.now());
        taskHistoryService.save(history);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}