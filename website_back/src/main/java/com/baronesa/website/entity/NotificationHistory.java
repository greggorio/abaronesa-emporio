package com.baronesa.website.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private Integer recipientsCount = 0;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
