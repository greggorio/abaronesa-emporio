package com.baronesa.website.dto;

import com.baronesa.website.entity.RewardStatus;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RewardWithCustomerName {
    private Long id;
    private Long userId;
    private String customerName;
    private String title;
    private String description;
    private String imageUrl;
    private LocalDateTime validUntil;
    private RewardStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime redeemedAt;
    private Long notificationHistoryId;

    // Constructor for JPA/Hibernate queries
    public RewardWithCustomerName(Long id, Long userId, String customerName, String title, String description,
                                  String imageUrl, LocalDateTime validUntil, RewardStatus status, LocalDateTime createdAt,
                                  LocalDateTime redeemedAt, Long notificationHistoryId) {
        this.id = id;
        this.userId = userId;
        this.customerName = customerName;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.validUntil = validUntil;
        this.status = status;
        this.createdAt = createdAt;
        this.redeemedAt = redeemedAt;
        this.notificationHistoryId = notificationHistoryId;
    }
}
