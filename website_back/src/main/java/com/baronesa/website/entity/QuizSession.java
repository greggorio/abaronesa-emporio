package com.baronesa.website.entity;

import com.baronesa.website.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String sessionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime finishedAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer currentQuestionIndex = 0;

    @Builder.Default
    @Column(nullable = false)
    private Integer questionTimeLimit = 30; // segundos

    @Builder.Default
    @Column(nullable = false)
    private Boolean autoAdvance = false; // Avançar automaticamente após tempo limite

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "session_questions", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "question_id")
    @OrderColumn(name = "question_order")
    private List<Long> questionIds = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (sessionCode == null) {
            sessionCode = generateSessionCode();
        }
        if (status == null) {
            status = SessionStatus.WAITING;
        }
    }

    private String generateSessionCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
