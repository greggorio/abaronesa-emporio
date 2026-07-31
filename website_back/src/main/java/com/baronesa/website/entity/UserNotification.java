package com.baronesa.website.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String deeplink;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(nullable = false, length = 50)
    private String source; // REWARD, BIRTHDAY, MANUAL, etc

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson; // Dados adicionais como JSON

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Método para marcar como lida
    public void markAsRead() {
        this.readAt = LocalDateTime.now();
    }

    // Método para verificar se está lida
    public boolean isRead() {
        return this.readAt != null;
    }
}