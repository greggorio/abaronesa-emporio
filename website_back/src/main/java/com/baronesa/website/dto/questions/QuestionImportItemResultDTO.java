package com.baronesa.website.dto.questions;

import com.baronesa.website.enums.questions.ImportItemStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportItemResultDTO {
    private Integer index;
    private String question;
    private Long categoryIdResolved;
    private ImportItemStatus status;
    private List<String> messages;
    private List<FieldErrorDTO> fieldErrors;
    private Long existingQuestionId;
    private Long createdQuestionId;
}
