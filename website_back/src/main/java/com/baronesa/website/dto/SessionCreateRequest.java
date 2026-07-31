package com.baronesa.website.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SessionCreateRequest {

    @Min(5)
    @Max(50)
    private Integer numberOfQuestions = 10;

    @Min(10)
    @Max(60)
    private Integer questionTimeLimit = 30;

    private String category; // null = misto

    private Boolean autoAdvance = false; // Avançar automaticamente após tempo limite
}
