package com.baronesa.emporio.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Representa uma questão gerada para import do quiz.
 */
@Data
@Builder
public class QuizQuestionDTO {
    private Integer index;
    private String question;
    private List<String> options;
    private Integer correctAnswer;
    private Integer points;
    private Boolean active;
    private Long categoryId;
}
