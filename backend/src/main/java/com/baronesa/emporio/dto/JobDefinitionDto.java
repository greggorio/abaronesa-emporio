package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public record JobDefinitionDto(
        Long id,
        String key,
        String name,
        String cron,
        Boolean active,
        String description,
        LocalDateTime lastRunAt,
        LocalDateTime nextRunAt,
        JobExecutionSummary lastExecution
) {
    public record JobExecutionSummary(
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String status,
            Integer recordsAffected,
            String message
    ) {}
}
