package com.baronesa.emporio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.baronesa.emporio.entity.TaskList;

@Repository
public interface TaskListRepository extends JpaRepository<TaskList, Long> {
    // Métodos personalizados, se necessário
}