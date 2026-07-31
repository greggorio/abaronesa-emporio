package com.baronesa.website.dto.questions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionImportPreviewDTO {
    private String detectedFormat;
    private Integer totalParsed;
    private List<QuestionImportItemResultDTO> previewItems;
    private ImportSummaryDTO summary;
    private String importId;
}