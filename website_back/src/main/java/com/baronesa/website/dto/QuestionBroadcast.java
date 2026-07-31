package com.baronesa.website.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBroadcast {
    private Long questionId;
    private String question;
    private List<String> options;
    private Integer questionNumber;
    private Integer totalQuestions;
    private Integer timeLimit;
    private String imageUrl;
}
