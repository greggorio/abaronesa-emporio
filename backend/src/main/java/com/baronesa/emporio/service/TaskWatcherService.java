package com.baronesa.emporio.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baronesa.emporio.entity.TaskWatcher;
import com.baronesa.emporio.repository.TaskWatcherRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskWatcherService {

    private final TaskWatcherRepository taskWatcherRepository;

    public List<TaskWatcher> findAll() {
        return taskWatcherRepository.findAll();
    }

    public List<TaskWatcher> findByTaskId(Long taskId) {
        return taskWatcherRepository.findByTaskId(taskId);
    }

    public List<TaskWatcher> findByUserId(Long userId) {
        return taskWatcherRepository.findByUserId(userId);
    }

    public TaskWatcher save(TaskWatcher taskWatcher) {
        if (taskWatcher.getAddedAt() == null) {
            taskWatcher.setAddedAt(LocalDateTime.now());
        }
        return taskWatcherRepository.save(taskWatcher);
    }

    public void delete(Long id) {
        taskWatcherRepository.deleteById(id);
    }
}