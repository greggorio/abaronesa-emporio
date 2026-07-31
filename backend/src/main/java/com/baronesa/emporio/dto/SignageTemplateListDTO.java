package com.baronesa.emporio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignageTemplateListDTO {
    private String templateId;
    private String name;
    private String description;
    private Boolean aiEnabled;
}