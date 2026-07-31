package com.baronesa.emporio.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.baronesa.emporio.entity.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByListId(Long listId);

    List<Task> findByAssigneeId(Long assigneeId);

    List<Task> findByPriority(String priority);

    List<Task> findByStatus(String status);

    List<Task> findByCreatedBy(Long createdBy);
}