package com.baronesa.emporio.entity;

import com.baronesa.emporio.converter.ProductSignageStatusConverter;
import com.baronesa.emporio.enums.ProductSignageStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_signage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSignage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Produto produto;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    @Column(length = 100)
    private String templatePreference;

    @Column(length = 20, nullable = false)
    @Convert(converter = ProductSignageStatusConverter.class)
    @Builder.Default
    private ProductSignageStatus status = ProductSignageStatus.PENDING;

    @Column(length = 255)
    private String renderHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String palette;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String phrases;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String colorMapping;

    @Column(length = 255)
    private String templateApplied;

    @Column(length = 20)
    @Builder.Default
    private String metadataSource = "MANUAL";

    @Column(length = 500)
    private String generatedImagePath;

    @Column(name = "mp4_url", length = 500)
    private String mp4Url;

    private LocalDateTime lastAttemptAt;

    private LocalDateTime lastResultAt;

    @Column(name = "use_ai_image", nullable = false)
    @Builder.Default
    private Boolean useAiImage = false;

    @Column(name = "ai_image_hash", length = 64)
    private String aiImageHash;

    @Column(name = "ai_generated_at")
    private LocalDateTime aiGeneratedAt;

    @Column(name = "ai_revision")
    @Builder.Default
    private Integer aiRevision = 0;

    @Column(name = "duration_seconds")
    @Builder.Default
    private Integer durationSeconds = 10;

    @Column(name = "signage_screen_id", length = 255)
    private String signageScreenId;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public boolean hasAiImage() {
        return this.useAiImage != null && this.useAiImage 
            && this.aiImageHash != null && !this.aiImageHash.isEmpty();
    }
}
