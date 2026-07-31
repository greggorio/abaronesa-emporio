package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiImageGenerationResponseDTO {
    private String assetUrl;
    private String assetHash;
    private Boolean cached;
    private LocalDateTime generatedAt;
}