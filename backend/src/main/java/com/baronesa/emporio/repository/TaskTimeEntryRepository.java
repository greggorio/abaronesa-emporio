package com.baronesa.emporio.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.baronesa.emporio.entity.TaskTimeEntry;

@Repository
public interface TaskTimeEntryRepository extends JpaRepository<TaskTimeEntry, String> {

    List<TaskTimeEntry> findByTaskId(Long taskId);

    @Query("SELECT t FROM TaskTimeEntry t WHERE t.userId = :userId AND t.endTime IS NULL")
    Optional<TaskTimeEntry> findActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM TaskTimeEntry t WHERE t.taskId = :taskId AND t.endTime IS NULL")
    Optional<TaskTimeEntry> findActiveByTaskId(@Param("taskId") Long taskId);

    @Query("SELECT SUM(t.duration) FROM TaskTimeEntry t WHERE t.taskId = :taskId AND t.duration IS NOT NULL")
    Long getTaskTotalDuration(@Param("taskId") Long taskId);
}