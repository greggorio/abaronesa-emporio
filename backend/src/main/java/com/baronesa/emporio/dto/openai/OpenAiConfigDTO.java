package com.baronesa.emporio.dto.openai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiConfigDTO {
    private String apiKey;
    private String model;
    private Integer maxTokens;
    private Integer timeout;
    private Boolean habilitado;
}
