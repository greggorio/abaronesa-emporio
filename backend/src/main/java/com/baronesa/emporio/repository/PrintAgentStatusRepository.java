package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.PrintAgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrintAgentStatusRepository extends JpaRepository<PrintAgentStatus, Long> {
    Optional<PrintAgentStatus> findByAgentId(String agentId);
}