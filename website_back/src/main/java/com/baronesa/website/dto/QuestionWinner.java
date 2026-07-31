package com.baronesa.website.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionWinner {
    private Long playerId;
    private String nickname;
    private int pointsEarned;
    private LocalDateTime timestamp;
}
