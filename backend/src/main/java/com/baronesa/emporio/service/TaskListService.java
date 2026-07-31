package com.baronesa.emporio.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.baronesa.emporio.entity.TaskList;
import com.baronesa.emporio.repository.TaskListRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaskListService {

    private final TaskListRepository taskListRepository;

    public List<TaskList> findAll() {
        return taskListRepository.findAll();
    }

    public Optional<TaskList> findById(Long id) {
        return taskListRepository.findById(id);
    }

    public TaskList save(TaskList taskList) {
        if (taskList.getCreatedAt() == null) {
            taskList.setCreatedAt(LocalDateTime.now());
        }
        taskList.setUpdatedAt(LocalDateTime.now());
        return taskListRepository.save(taskList);
    }

    public void delete(Long id) {
        taskListRepository.deleteById(id);
    }
}