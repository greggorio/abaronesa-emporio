package com.baronesa.website.dto;

import com.baronesa.website.enums.ThemeStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThemeResponseDTO {

    private Long id;
    private String name;
    private Long baseThemeId;
    private ThemeStatus status;
    private Map<String, String> tokens;
    private Map<String, Object> assets;
    private Map<String, Object> content;
    private String tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}