package com.baronesa.website.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionLockedNotification {
    private Long questionId;
    private String winnerNickname;
    private int pointsEarned;
    private String message;
    private Integer correctOption;
}
