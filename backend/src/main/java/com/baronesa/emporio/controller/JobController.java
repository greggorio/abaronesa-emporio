package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.JobDefinitionDto;
import com.baronesa.emporio.dto.JobExecutionDto;
import com.baronesa.emporio.entity.JobDefinition;
import com.baronesa.emporio.repository.JobDefinitionRepository;
import com.baronesa.emporio.service.JobExecutionService;
import com.baronesa.emporio.service.JobSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM')")
@ConditionalOnProperty(name = "app.job-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class JobController {

    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionService jobExecutionService;
    private final JobSchedulerService jobSchedulerService;

    @GetMapping
    public List<JobDefinitionDto> list() {
        return jobDefinitionRepository.findAll()
                .stream()
                .map(j -> new JobDefinitionDto(
                        j.getId(),
                        j.getKey(),
                        j.getName(),
                        j.getCron(),
                        j.getActive(),
                        j.getDescription(),
                        j.getLastRunAt(),
                        j.getNextRunAt(),
                        jobExecutionService.findLastExecution(j)
                ))
                .toList();
    }

    @PostMapping
    public ResponseEntity<JobDefinitionDto> create(@RequestBody JobDefinition jobDefinition) {
        JobDefinition saved = jobSchedulerService.create(jobDefinition);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobDefinitionDto> update(@PathVariable Long id, @RequestBody JobDefinition incoming) {
        return jobDefinitionRepository.findById(id)
                .map(existing -> {
                    existing.setKey(incoming.getKey());
                    existing.setName(incoming.getName());
                    existing.setCron(incoming.getCron());
                    existing.setActive(incoming.getActive());
                    existing.setDescription(incoming.getDescription());
                    JobDefinition saved = jobSchedulerService.update(existing);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (jobDefinitionRepository.existsById(id)) {
            jobSchedulerService.delete(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<JobExecutionDto> runNow(@PathVariable Long id) {
        return jobDefinitionRepository.findById(id)
                .map(jobDefinition -> {
                    var exec = jobExecutionService.runNow(jobDefinition);
                    return ResponseEntity.ok(new JobExecutionDto(
                            exec.getId(),
                            exec.getStartedAt(),
                            exec.getFinishedAt(),
                            exec.getStatus().name(),
                            exec.getRecordsAffected(),
                            exec.getMessage(),
                            exec.getPayloadLog()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<JobExecutionDto>> history(@PathVariable Long id,
                                                         @RequestParam(defaultValue = "20") int limit) {
        List<JobExecutionDto> executions = jobExecutionService.listExecutions(id, limit);
        return ResponseEntity.ok(executions);
    }

    private JobDefinitionDto toDto(JobDefinition j) {
        return new JobDefinitionDto(
                j.getId(),
                j.getKey(),
                j.getName(),
                j.getCron(),
                j.getActive(),
                j.getDescription(),
                j.getLastRunAt(),
                j.getNextRunAt(),
                jobExecutionService.findLastExecution(j)
        );
    }
}
