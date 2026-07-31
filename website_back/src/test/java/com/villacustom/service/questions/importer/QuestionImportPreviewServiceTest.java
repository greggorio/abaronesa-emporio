package com.baronesa.website.service.questions.importer;

import com.baronesa.website.dto.questions.ImportSummaryDTO;
import com.baronesa.website.dto.questions.QuestionImportItemResultDTO;
import com.baronesa.website.dto.questions.QuestionImportPreviewDTO;
import com.baronesa.website.dto.questions.QuestionImportRequestDTO;
import com.baronesa.website.dto.questions.QuestionImportResultDTO;
import com.baronesa.website.enums.questions.ImportItemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportPreviewServiceTest {

    @Mock
    private QuestionImportService questionImportService;

    @Mock
    private ImportParserRegistry parserRegistry;

    @Mock
    private ImportParser mockParser;

    @InjectMocks
    private QuestionImportPreviewService previewService;

    private final InputStream dummyStream = new ByteArrayInputStream("dummy".getBytes());

    private QuestionImportItemResultDTO item(int index, ImportItemStatus status) {
        QuestionImportItemResultDTO item = new QuestionImportItemResultDTO();
        item.setIndex(index);
        item.setQuestion("Question " + index);
        item.setStatus(status);
        return item;
    }

    @Test
    void shouldForceDryRun() throws Exception {
        when(parserRegistry.getParser(anyString(), anyString(), any(InputStream.class)))
                .thenReturn(mockParser);
        when(mockParser.getFormatName()).thenReturn("JSON");

        QuestionImportRequestDTO req = new QuestionImportRequestDTO();
        req.setDryRun(false);

        QuestionImportResultDTO serviceResult = new QuestionImportResultDTO();
        serviceResult.setSummary(new ImportSummaryDTO(0, 0, 0, 0, 0, 0));
        serviceResult.setItems(List.of());
        serviceResult.setImportId("import-1");

        when(questionImportService.importQuestions(
                anyString(), anyString(), any(InputStream.class), any(QuestionImportRequestDTO.class)))
                .thenReturn(serviceResult);

        QuestionImportPreviewDTO result = previewService.previewImport(
                "test.json", "application/json", dummyStream, req);

        assertThat(result).isNotNull();
        assertThat(result.getDetectedFormat()).isEqualTo("JSON");
    }

    @Test
    void shouldUseDefaultPreviewLimit() throws Exception {
        when(parserRegistry.getParser(anyString(), anyString(), any(InputStream.class)))
                .thenReturn(mockParser);
        when(mockParser.getFormatName()).thenReturn("JSON");

        List<QuestionImportItemResultDTO> items = List.of(
                item(1, ImportItemStatus.IMPORTED),
                item(2, ImportItemStatus.IMPORTED)
        );

        QuestionImportResultDTO serviceResult = new QuestionImportResultDTO();
        serviceResult.setSummary(new ImportSummaryDTO(2, 2, 2, 0, 0, 0));
        serviceResult.setItems(items);
        serviceResult.setImportId("import-1");

        when(questionImportService.importQuestions(
                anyString(), anyString(), any(InputStream.class), any(QuestionImportRequestDTO.class)))
                .thenReturn(serviceResult);

        QuestionImportPreviewDTO result = previewService.previewImport(
                "test.json", "application/json", dummyStream, new QuestionImportRequestDTO());

        assertThat(result.getTotalParsed()).isEqualTo(2);
    }

    @Test
    void shouldTruncateToCustomPreviewLimit() throws Exception {
        when(parserRegistry.getParser(anyString(), anyString(), any(InputStream.class)))
                .thenReturn(mockParser);
        when(mockParser.getFormatName()).thenReturn("JSON");

        QuestionImportRequestDTO req = new QuestionImportRequestDTO();
        req.setPreviewLimit(1);

        List<QuestionImportItemResultDTO> items = List.of(
                item(1, ImportItemStatus.IMPORTED),
                item(2, ImportItemStatus.IMPORTED)
        );

        QuestionImportResultDTO serviceResult = new QuestionImportResultDTO();
        serviceResult.setSummary(new ImportSummaryDTO(2, 2, 2, 0, 0, 0));
        serviceResult.setItems(items);
        serviceResult.setImportId("import-1");

        when(questionImportService.importQuestions(
                anyString(), anyString(), any(InputStream.class), any(QuestionImportRequestDTO.class)))
                .thenReturn(serviceResult);

        QuestionImportPreviewDTO result = previewService.previewImport(
                "test.json", "application/json", dummyStream, req);

        assertThat(result.getTotalParsed()).isEqualTo(1);
        assertThat(result.getPreviewItems()).hasSize(1);
    }

    @Test
    void shouldHandleNullRequest() throws Exception {
        when(parserRegistry.getParser(anyString(), anyString(), any(InputStream.class)))
                .thenReturn(mockParser);
        when(mockParser.getFormatName()).thenReturn("JSON");

        QuestionImportResultDTO serviceResult = new QuestionImportResultDTO();
        serviceResult.setSummary(new ImportSummaryDTO(0, 0, 0, 0, 0, 0));
        serviceResult.setItems(List.of());
        serviceResult.setImportId("import-1");

        when(questionImportService.importQuestions(
                anyString(), anyString(), any(InputStream.class), any(QuestionImportRequestDTO.class)))
                .thenReturn(serviceResult);

        QuestionImportPreviewDTO result = previewService.previewImport(
                "test.json", "application/json", dummyStream, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldBuildSummaryFromPreviewItems() throws Exception {
        when(parserRegistry.getParser(anyString(), anyString(), any(InputStream.class)))
                .thenReturn(mockParser);
        when(mockParser.getFormatName()).thenReturn("JSON");

        List<QuestionImportItemResultDTO> items = List.of(
                item(1, ImportItemStatus.IMPORTED),
                item(2, ImportItemStatus.IMPORTED),
                item(3, ImportItemStatus.SKIPPED),
                item(4, ImportItemStatus.ERROR)
        );

        QuestionImportResultDTO serviceResult = new QuestionImportResultDTO();
        serviceResult.setSummary(new ImportSummaryDTO(4, 4, 2, 0, 1, 1));
        serviceResult.setItems(items);
        serviceResult.setImportId("import-1");

        when(questionImportService.importQuestions(
                anyString(), anyString(), any(InputStream.class), any(QuestionImportRequestDTO.class)))
                .thenReturn(serviceResult);

        QuestionImportPreviewDTO result = previewService.previewImport(
                "test.json", "application/json", dummyStream, new QuestionImportRequestDTO());

        assertThat(result.getSummary().getImportedCount()).isEqualTo(2);
        assertThat(result.getSummary().getSkippedCount()).isEqualTo(1);
        assertThat(result.getSummary().getErrorCount()).isEqualTo(1);
    }
}
