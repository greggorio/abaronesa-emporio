package com.baronesa.website.service.questions.importer;

import com.baronesa.website.dto.questions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportPreviewService {

    private final QuestionImportService questionImportService;
    private final ImportParserRegistry parserRegistry;

    public QuestionImportPreviewDTO previewImport(
            String filename,
            String contentType,
            InputStream inputStream,
            QuestionImportRequestDTO request) throws Exception {

        if (request == null) {
            request = new QuestionImportRequestDTO();
        }

        request.setDryRun(true);
        int previewLimit = (request.getPreviewLimit() == null || request.getPreviewLimit() <= 0)
                ? 20
                : request.getPreviewLimit();

        byte[] payload = inputStream.readAllBytes();
        ImportParser parser = parserRegistry.getParser(filename, contentType, new ByteArrayInputStream(payload));

        QuestionImportRequestDTO previewRequest = new QuestionImportRequestDTO(
                request.getDedupeMode(),
                request.getActiveMode(),
                request.getTransactionMode(),
                true,
                previewLimit
        );

        QuestionImportResultDTO result = questionImportService.importQuestions(
                filename,
                contentType,
                new ByteArrayInputStream(payload),
                previewRequest
        );

        List<QuestionImportItemResultDTO> items = result.getItems() != null ? result.getItems() : new ArrayList<>();
        List<QuestionImportItemResultDTO> previewItems = new ArrayList<>(
                items.subList(0, Math.min(items.size(), previewLimit))
        );

        ImportSummaryDTO summary = buildSummary(previewItems);

        QuestionImportPreviewDTO previewResult = new QuestionImportPreviewDTO();
        previewResult.setDetectedFormat(parser.getFormatName());
        previewResult.setTotalParsed(previewItems.size());
        previewResult.setPreviewItems(previewItems);
        previewResult.setSummary(summary);
        previewResult.setImportId(result.getImportId());

        return previewResult;
    }

    private ImportSummaryDTO buildSummary(List<QuestionImportItemResultDTO> items) {
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        int errors = 0;

        for (QuestionImportItemResultDTO item : items) {
            if (item.getStatus() == null) {
                continue;
            }
            switch (item.getStatus()) {
                case IMPORTED:
                    imported++;
                    break;
                case UPDATED:
                    updated++;
                    break;
                case SKIPPED:
                    skipped++;
                    break;
                case ERROR:
                    errors++;
                    break;
                default:
                    break;
            }
        }

        ImportSummaryDTO summary = new ImportSummaryDTO();
        summary.setTotalReceived(items.size());
        summary.setTotalParsed(items.size());
        summary.setImportedCount(imported);
        summary.setUpdatedCount(updated);
        summary.setSkippedCount(skipped);
        summary.setErrorCount(errors);
        return summary;
    }
}
