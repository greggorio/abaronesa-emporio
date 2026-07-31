package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.EntityTranslation;
import com.baronesa.emporio.entity.TranslationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class TranslationResponse {
    private Long id;
    private String entityType;
    private Long entityId;
    private String field;
    private String locale;
    private String sourceText;
    private String translatedText;
    private TranslationStatus status;
    private String provider;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TranslationResponse fromEntity(EntityTranslation t) {
        return TranslationResponse.builder()
                .id(t.getId())
                .entityType(t.getEntityType())
                .entityId(t.getEntityId())
                .field(t.getField())
                .locale(t.getLocale())
                .sourceText(t.getSourceText())
                .translatedText(t.getTranslatedText())
                .status(t.getStatus())
                .provider(t.getProvider())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
