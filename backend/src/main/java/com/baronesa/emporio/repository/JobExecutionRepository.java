package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.JobDefinition;
import com.baronesa.emporio.entity.JobExecution;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {

    List<JobExecution> findByJobDefinitionOrderByStartedAtDesc(JobDefinition jobDefinition, Pageable pageable);
}
