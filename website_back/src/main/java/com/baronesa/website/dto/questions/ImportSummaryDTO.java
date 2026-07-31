package com.baronesa.website.dto.questions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportSummaryDTO {
    private Integer totalReceived;
    private Integer totalParsed;
    private Integer importedCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private Integer errorCount;
}