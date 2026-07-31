package com.baronesa.emporio.dto;

import com.baronesa.emporio.dto.ProductSignagePalette;
import com.baronesa.emporio.enums.ProductSignageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProdutoSignageDTO {
    private Long id;
    private Boolean enabled;
    private String templatePreference;
    private ProductSignageStatus status;
    private String renderHash;
    private String mp4Url;
    private ProductSignagePalette palette;
    private String phrases;
    private String templateApplied;
    private String metadataSource;
    private SignageColorMapping colorMapping;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime lastResultAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
