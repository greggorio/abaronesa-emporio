package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageTemplateResponseDTO {
    private String templateId;
    private String name;
    private String description;
    private String imageMode;
    private Boolean isActive;
    
    // Campos de IA
    private Boolean aiEnabled;
    private String aiMode;
    private String aiPrompt;
    private String aiPromptVersion;
    private String aiOutputSize;
}
