package com.baronesa.emporio.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class TaskDTO {
    private String id;
    private Long listId;
    private Long assigneeId;
    private String title;
    private String description;
    private LocalDateTime createdAt;
    private Long createdBy;
    private String status;
    private String statusCode;
    private String dueDate;
    private String priority;
    private String assignee;
    private Long totalDurationSeconds;
    private Boolean hasActiveSession;
    private LocalDateTime activeSessionStartTime;
    private Long activeSessionUserId;
    private String activeSessionUserName;
    private List<Map<String, Object>> watchers = new ArrayList<>();
    private List<CommentDTO> comments = new ArrayList<>();
    private List<HistoryItemDTO> historico = new ArrayList<>();
}