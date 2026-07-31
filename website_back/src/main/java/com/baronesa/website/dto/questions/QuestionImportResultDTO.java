package com.baronesa.website.dto.questions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportResultDTO {
    private ImportSummaryDTO summary;
    private List<QuestionImportItemResultDTO> items;
    private String importId;
}