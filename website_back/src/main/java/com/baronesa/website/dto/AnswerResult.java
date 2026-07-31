package com.baronesa.website.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResult {
    private Boolean correct;
    private Integer correctOption;
    private Integer pointsEarned;
    private Integer totalScore;
    private Integer rank;
}
