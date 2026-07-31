package com.baronesa.emporio.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baronesa.emporio.enums.TaskStatus;

import lombok.Data;
import lombok.ToString;

@Data
@Entity
@Table(name = "tasks")
@ToString(exclude = {"taskList", "watchers", "timeEntries"})
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private String priority;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "list_id", nullable = false)
    private Long listId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_id", referencedColumnName = "id", insertable = false, updatable = false)
    @JsonIgnore
    private TaskList taskList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", referencedColumnName = "id", insertable = false, updatable = false)
    @JsonIgnore
    private Usuario assignee;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<TaskHistory> historyItems = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<TaskWatcher> watchers = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Attachment> attachments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TaskStatus status;

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<TaskTimeEntry> timeEntries = new ArrayList<>();

    @PrePersist
    @PreUpdate
    public void ensureStatusIsSet() {
        if (this.status == null) {
            this.status = calculateCurrentStatus();
        }
    }

    @Transient
    public TaskStatus calculateCurrentStatus() {
        if (status == TaskStatus.COMPLETED || status == TaskStatus.CANCELLED) {
            return status;
        }

        boolean hasActiveSession = timeEntries.stream()
                .anyMatch(entry -> entry.getStartTime() != null && entry.getEndTime() == null);

        if (hasActiveSession) {
            return TaskStatus.IN_PROGRESS;
        }

        boolean hasAnySessions = !timeEntries.isEmpty();

        if (hasAnySessions) {
            return TaskStatus.PAUSED;
        }

        if (dueDate == null) {
            return TaskStatus.BACKLOG;
        }

        return TaskStatus.PLANNED;
    }
}