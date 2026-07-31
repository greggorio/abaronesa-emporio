package com.baronesa.emporio.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.baronesa.emporio.entity.TaskHistory;
import com.baronesa.emporio.repository.TaskHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskHistoryService {

    private final TaskHistoryRepository taskHistoryRepository;

    public List<TaskHistory> findAll() {
        return taskHistoryRepository.findAll();
    }

    public Optional<TaskHistory> findById(Long id) {
        return taskHistoryRepository.findById(id);
    }

    public List<TaskHistory> findByTaskId(Long taskId) {
        return taskHistoryRepository.findByTaskId(taskId);
    }

    public List<TaskHistory> findByTaskIdOrderByCreatedAtDesc(Long taskId) {
        return taskHistoryRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    public TaskHistory save(TaskHistory history) {
        if (history.getCreatedAt() == null) {
            history.setCreatedAt(LocalDateTime.now());
        }
        return taskHistoryRepository.save(history);
    }

    public void delete(Long id) {
        taskHistoryRepository.deleteById(id);
    }
}