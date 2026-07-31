package com.baronesa.emporio.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baronesa.emporio.entity.Task;
import com.baronesa.emporio.entity.TaskHistory;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.TaskHistoryRepository;
import com.baronesa.emporio.repository.TaskRepository;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.security.UserPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final UsuarioRepository usuarioRepository;

    public List<Task> findAll() {
        return taskRepository.findAll();
    }

    public Optional<Task> findById(Long id) {
        return taskRepository.findById(id);
    }

    public List<Task> findByListId(Long listId) {
        return taskRepository.findByListId(listId);
    }

    public List<Task> findByAssigneeId(Long assigneeId) {
        return taskRepository.findByAssigneeId(assigneeId);
    }

    public List<Task> findByPriority(String priority) {
        return taskRepository.findByPriority(priority);
    }

    public List<Task> findByStatus(String status) {
        return taskRepository.findByStatus(status);
    }

    private Long getCurrentUserId() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
                return userPrincipal.getId();
            }
        } catch (Exception e) {
            log.warn("Não foi possível obter usuário logado: {}", e.getMessage());
        }
        return 1L; // Fallback
    }

    @Transactional
    public Task save(Task task, Long currentUserId) {
        boolean isNewTask = task.getId() == null;

        if (isNewTask) {
            task.setCreatedAt(LocalDateTime.now());

            // Se não tiver createdBy definido, use o usuário atual
            if (task.getCreatedBy() == null) {
                task.setCreatedBy(currentUserId);
            }
        }

        task.setUpdatedAt(LocalDateTime.now());
        Task savedTask = taskRepository.save(task);

        // Registrando histórico com userId garantido
        TaskHistory history = new TaskHistory();
        history.setTaskId(savedTask.getId());
        history.setUserId(currentUserId);
        history.setAction(isNewTask ? "Tarefa criada" : "Tarefa atualizada");
        history.setCreatedAt(LocalDateTime.now());
        history.setDetails("Status: " + task.getStatus());

        taskHistoryRepository.save(history);

        return savedTask;
    }

    // Mantenha uma versão sobrecarregada para compatibilidade com código existente
    @Transactional
    public Task save(Task task) {
        return save(task, getCurrentUserId());
    }

    @Transactional
    public Task updateStatus(Long id, String status, Long userId) {
        Optional<Task> optTask = taskRepository.findById(id);
        if (!optTask.isPresent()) {
            throw new RuntimeException("Tarefa não encontrada com id: " + id);
        }

        Task task = optTask.get();
        task.setUpdatedAt(LocalDateTime.now());

        Task updatedTask = taskRepository.save(task);

        // Registrando histórico de mudança de status
        TaskHistory history = new TaskHistory();
        history.setTaskId(updatedTask.getId());
        history.setUserId(userId);
        history.setAction("Alteração de status");
        history.setCreatedAt(LocalDateTime.now());
        history.setDetails("Status alterado");

        taskHistoryRepository.save(history);

        return updatedTask;
    }

    @Transactional
    public Task assignTask(Long id, Long assigneeId, Long userId) {
        Optional<Task> optTask = taskRepository.findById(id);
        if (!optTask.isPresent()) {
            throw new RuntimeException("Tarefa não encontrada com id: " + id);
        }

        Task task = optTask.get();
        Long oldAssigneeId = task.getAssigneeId();
        task.setAssigneeId(assigneeId);
        task.setUpdatedAt(LocalDateTime.now());

        Task updatedTask = taskRepository.save(task);

        // Registrando histórico de atribuição
        TaskHistory history = new TaskHistory();
        history.setTaskId(updatedTask.getId());
        history.setUserId(userId);
        history.setAction("Atribuição de tarefa");
        history.setCreatedAt(LocalDateTime.now());
        history.setDetails("Tarefa atribuída do usuário ID " + oldAssigneeId + " para usuário ID " + assigneeId);

        taskHistoryRepository.save(history);

        return updatedTask;
    }

    @Transactional
    public void delete(Long id) {
        taskRepository.deleteById(id);
    }
}