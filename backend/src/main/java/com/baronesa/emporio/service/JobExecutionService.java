package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.JobDefinitionDto;
import com.baronesa.emporio.dto.JobExecutionDto;
import com.baronesa.emporio.entity.JobDefinition;
import com.baronesa.emporio.entity.JobExecution;
import com.baronesa.emporio.repository.JobDefinitionRepository;
import com.baronesa.emporio.repository.JobExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobExecutionService {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobRegistry jobRegistry;

    @Transactional
    public JobExecution runNow(JobDefinition jobDefinition) {
        JobExecution execution = JobExecution.builder()
                .jobDefinition(jobDefinition)
                .startedAt(LocalDateTime.now())
                .status(JobExecution.Status.FAIL) // default until success
                .build();
        execution = jobExecutionRepository.save(execution);

        try {
            JobRegistry.ExecutionResult result = jobRegistry.run(jobDefinition.getKey());
            execution.setStatus(JobExecution.Status.SUCCESS);
            execution.setRecordsAffected(result.recordsAffected());
            execution.setMessage(result.message());
        } catch (Exception e) {
            execution.setStatus(JobExecution.Status.FAIL);
            execution.setMessage(e.getMessage());
            log.error("Erro ao executar job {}: {}", jobDefinition.getKey(), e.getMessage(), e);
        } finally {
            execution.setFinishedAt(LocalDateTime.now());
            jobExecutionRepository.save(execution);
        }

        jobDefinition.setLastRunAt(execution.getFinishedAt());
        jobDefinitionRepository.save(jobDefinition);

        return execution;
    }

    @Transactional(readOnly = true)
    public List<JobExecutionDto> listExecutions(Long jobId, int limit) {
        Optional<JobDefinition> jobOpt = jobDefinitionRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return List.of();
        }
        List<JobExecution> executions = jobExecutionRepository
                .findByJobDefinitionOrderByStartedAtDesc(jobOpt.get(), PageRequest.of(0, limit));
        return executions.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDefinitionDto.JobExecutionSummary findLastExecution(JobDefinition jobDefinition) {
        List<JobExecution> executions = jobExecutionRepository
                .findByJobDefinitionOrderByStartedAtDesc(jobDefinition, PageRequest.of(0, 1));
        if (executions.isEmpty()) {
            return null;
        }
        JobExecution e = executions.get(0);
        return new JobDefinitionDto.JobExecutionSummary(
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getStatus().name(),
                e.getRecordsAffected(),
                e.getMessage()
        );
    }

    private JobExecutionDto toDto(JobExecution e) {
        return new JobExecutionDto(
                e.getId(),
                e.getStartedAt(),
                e.getFinishedAt(),
                e.getStatus().name(),
                e.getRecordsAffected(),
                e.getMessage(),
                e.getPayloadLog()
        );
    }
}
