package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_notification_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(nullable = false)
    private Integer ano;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private NotificationType tipo;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public enum NotificationType {
        PRE, DAY
    }
}
