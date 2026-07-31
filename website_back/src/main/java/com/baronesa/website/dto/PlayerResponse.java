package com.baronesa.website.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerResponse {
    private Long id;
    private String nickname;
    private Integer score;
    private Integer correctAnswers;
    private Integer wrongAnswers;
    private Integer rank;
}
