package com.baronesa.website.dto.questions;

import com.baronesa.website.enums.questions.ActiveMode;
import com.baronesa.website.enums.questions.DedupeMode;
import com.baronesa.website.enums.questions.TransactionMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportRequestDTO {
    private DedupeMode dedupeMode;
    private ActiveMode activeMode;
    private TransactionMode transactionMode;
    private Boolean dryRun;
    private Integer previewLimit;
}
