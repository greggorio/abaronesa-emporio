package com.baronesa.emporio.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.baronesa.emporio.entity.TaskWatcher;

@Repository
public interface TaskWatcherRepository extends JpaRepository<TaskWatcher, Long> {

    List<TaskWatcher> findByTaskId(Long taskId);

    List<TaskWatcher> findByUserId(Long userId);
}