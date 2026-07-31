package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageTemplateAiConfigDTO {
    private String aiMode;
    private String aiPrompt;
    private String aiPromptVersion;
    private Boolean aiEnabled;
    private String aiOutputSize;
}