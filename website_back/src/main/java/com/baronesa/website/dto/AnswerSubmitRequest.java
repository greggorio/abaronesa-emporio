package com.baronesa.website.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnswerSubmitRequest {

    @NotNull
    private Long questionId;

    @NotNull
    @Min(0)
    private Integer selectedOption;

    @NotNull
    private Long responseTimeMs; // Tempo de resposta em milissegundos
}
