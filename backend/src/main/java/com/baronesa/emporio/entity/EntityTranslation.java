package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "entity_translation",
       uniqueConstraints = {
               @UniqueConstraint(name = "ux_entity_translation_key", columnNames = {"entity_type", "entity_id", "field", "locale"})
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "field", nullable = false, length = 64)
    private String field;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "source_text")
    private String sourceText;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(name = "translated_text")
    private String translatedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TranslationStatus status = TranslationStatus.OK;

    @Column(name = "provider", length = 32)
    private String provider;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
