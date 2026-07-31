package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.JobDefinitionDto;
import com.baronesa.emporio.entity.JobDefinition;
import com.baronesa.emporio.repository.JobDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.job-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class JobSchedulerService {

    private final TaskScheduler taskScheduler;
    private final JobDefinitionRepository jobDefinitionRepository;
    private final JobExecutionService jobExecutionService;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        List<JobDefinition> activeJobs = jobDefinitionRepository.findAll()
                .stream()
                .filter(JobDefinition::getActive)
                .toList();
        activeJobs.forEach(this::scheduleJob);
        log.info("Agenda: {} jobs ativos agendados no startup", activeJobs.size());
    }

    public JobDefinition create(JobDefinition jobDefinition) {
        JobDefinition saved = jobDefinitionRepository.save(jobDefinition);
        if (Boolean.TRUE.equals(saved.getActive())) {
            scheduleJob(saved);
        }
        return saved;
    }

    public JobDefinition update(JobDefinition jobDefinition) {
        JobDefinition saved = jobDefinitionRepository.save(jobDefinition);
        cancelIfScheduled(saved.getId());
        if (Boolean.TRUE.equals(saved.getActive())) {
            scheduleJob(saved);
        }
        return saved;
    }

    public void delete(Long id) {
        cancelIfScheduled(id);
        jobDefinitionRepository.deleteById(id);
    }

    public void runNow(Long jobId) {
        jobDefinitionRepository.findById(jobId).ifPresent(jobExecutionService::runNow);
    }

    private void scheduleJob(JobDefinition jobDefinition) {
        try {
            CronExpression cronExpression = CronExpression.parse(jobDefinition.getCron());
            CronTrigger trigger = new CronTrigger(jobDefinition.getCron(), ZoneId.systemDefault());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> {
                        log.info("Executando job agendado key={}", jobDefinition.getKey());
                        jobExecutionService.runNow(jobDefinition);
                    },
                    trigger
            );
            scheduledTasks.put(jobDefinition.getId(), future);
            var next = cronExpression.next(java.time.ZonedDateTime.now(ZoneId.systemDefault()));
            if (next != null) {
                jobDefinition.setNextRunAt(next.toLocalDateTime());
            }
            jobDefinitionRepository.save(jobDefinition);
        } catch (Exception e) {
            log.error("Erro ao agendar job {}: {}", jobDefinition.getKey(), e.getMessage(), e);
        }
    }

    private void cancelIfScheduled(Long jobId) {
        ScheduledFuture<?> future = scheduledTasks.remove(jobId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
