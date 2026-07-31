package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_execution")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_definition_id", nullable = false)
    private JobDefinition jobDefinition;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "records_affected")
    private Integer recordsAffected;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "payload_log", columnDefinition = "TEXT")
    private String payloadLog;

    public enum Status {
        SUCCESS,
        FAIL
    }
}
