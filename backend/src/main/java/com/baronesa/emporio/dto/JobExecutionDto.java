package com.baronesa.emporio.dto;

import java.time.LocalDateTime;

public record JobExecutionDto(
        Long id,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String status,
        Integer recordsAffected,
        String message,
        String payloadLog
) {}
