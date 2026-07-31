package com.baronesa.website.service.questions.importer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportRow {
    private Integer index;
    private String question;
    private List<String> options;
    private Integer correctAnswer;
    private Integer points;
    private Boolean active;
    private Long categoryId;
    private String imageUrl;
}