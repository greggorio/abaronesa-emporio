package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.JobDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JobDefinitionRepository extends JpaRepository<JobDefinition, Long> {
    Optional<JobDefinition> findByKey(String key);
}
