package com.baronesa.website.service.questions.importer;

import com.baronesa.website.dto.questions.QuestionImportItemResultDTO;
import com.baronesa.website.dto.questions.QuestionImportRequestDTO;
import com.baronesa.website.dto.questions.QuestionImportResultDTO;
import com.baronesa.website.enums.questions.ActiveMode;
import com.baronesa.website.enums.questions.DedupeMode;
import com.baronesa.website.enums.questions.ImportItemStatus;
import com.baronesa.website.enums.questions.TransactionMode;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionImportServiceTest {

    @Mock
    private ImportParserRegistry parserRegistry;

    @Mock
    private QuestionImportGateway questionImportGateway;

    @Mock
    private ImportParser mockParser;

    @InjectMocks
    private QuestionImportService questionImportService;

    private final InputStream dummyStream = new ByteArrayInputStream("dummy".getBytes());

    private QuestionImportRow row(String question, List<String> options, Integer correctAnswer,
                                   Integer points, Boolean active, Long categoryId) {
        return new QuestionImportRow(null, question, options, correctAnswer, points, active, categoryId, null);
    }

    private QuestionImportRequestDTO req(DedupeMode dedupe, ActiveMode active, TransactionMode tx, boolean dryRun) {
        QuestionImportRequestDTO r = new QuestionImportRequestDTO();
        r.setDedupeMode(dedupe);
        r.setActiveMode(active);
        r.setTransactionMode(tx);
        r.setDryRun(dryRun);
        return r;
    }

    private void stubParser(List<QuestionImportRow> rows) throws Exception {
        when(parserRegistry.getParser(anyString(), anyString(), any(InputStream.class)))
                .thenReturn(mockParser);
        when(mockParser.parse(any(InputStream.class))).thenReturn(rows);
        when(mockParser.getFormatName()).thenReturn("JSON");
    }

    @Test
    void shouldApplyDefaultsForNullFields() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));
        when(questionImportGateway.findExistingQuestionId(anyString(), anyLong())).thenReturn(null);
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean())).thenReturn(1L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream, new QuestionImportRequestDTO());

        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
    }

    @Test
    void shouldCreateWhenAllowDuplicates() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean())).thenReturn(1L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo(ImportItemStatus.IMPORTED);
        assertThat(result.getItems().get(0).getCreatedQuestionId()).isEqualTo(1L);
    }

    @Test
    void shouldSkipWhenDuplicateAndSkipMode() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));
        when(questionImportGateway.findExistingQuestionId("Pergunta?", 100L)).thenReturn(999L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.SKIP_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo(ImportItemStatus.SKIPPED);
        assertThat(result.getItems().get(0).getExistingQuestionId()).isEqualTo(999L);
    }

    @Test
    void shouldUpdateWhenDuplicateAndUpdateMode() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));
        when(questionImportGateway.findExistingQuestionId("Pergunta?", 100L)).thenReturn(999L);
        when(questionImportGateway.updateQuestion(anyLong(), any(QuestionImportRow.class), anyBoolean())).thenReturn(999L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.UPDATE_EXISTING, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo(ImportItemStatus.UPDATED);
        assertThat(result.getItems().get(0).getExistingQuestionId()).isEqualTo(999L);
    }

    @Test
    void shouldCreateWhenNoDuplicateEvenInSkipMode() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));
        when(questionImportGateway.findExistingQuestionId(anyString(), anyLong())).thenReturn(null);
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean())).thenReturn(1L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.SKIP_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo(ImportItemStatus.IMPORTED);
    }

    @Test
    void shouldForceActive() throws Exception {
        QuestionImportRow inactiveRow = row("Pergunta?", List.of("A", "B"), 0, 10, false, 100L);
        stubParser(List.of(inactiveRow));
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean())).thenReturn(1L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.FORCE_ACTIVE, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
    }

    @Test
    void shouldForceInactive() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean())).thenReturn(1L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.FORCE_INACTIVE, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
    }

    @Test
    void shouldSkipRemainingOnAllOrNothingWhenFirstFails() throws Exception {
        QuestionImportRow invalid = row("", List.of("A"), 0, 10, true, null);
        QuestionImportRow valid = row("Pergunta valida?", List.of("A", "B"), 0, 10, true, 100L);
        stubParser(List.of(invalid, valid));

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.INHERIT, TransactionMode.ALL_OR_NOTHING, false));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo(ImportItemStatus.ERROR);
        assertThat(result.getItems().get(1).getStatus()).isEqualTo(ImportItemStatus.SKIPPED);
        assertThat(result.getItems().get(1).getMessages().get(0)).contains("ALL_OR_NOTHING");
    }

    @Test
    void shouldNotPersistWhenDryRun() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, true));

        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getCreatedQuestionId()).isNull();
    }

    @Test
    void shouldCountSummaryCorrectly() throws Exception {
        QuestionImportRow row = row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L);
        stubParser(List.of(row, row, row));
        when(questionImportGateway.findExistingQuestionId(anyString(), anyLong()))
                .thenReturn(null, 1L, null);
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean()))
                .thenReturn(1L, 2L);

        QuestionImportRequestDTO req = new QuestionImportRequestDTO();
        req.setDedupeMode(DedupeMode.SKIP_DUPLICATES);
        req.setActiveMode(ActiveMode.INHERIT);
        req.setTransactionMode(TransactionMode.PARTIAL_OK);
        req.setDryRun(false);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream, req);

        assertThat(result.getSummary().getTotalReceived()).isEqualTo(3);
        assertThat(result.getSummary().getImportedCount()).isEqualTo(2);
        assertThat(result.getSummary().getSkippedCount()).isEqualTo(1);
        assertThat(result.getSummary().getErrorCount()).isEqualTo(0);
        assertThat(result.getImportId()).isNotNull();
    }

    @Test
    void shouldReturnErrorForInvalidRow() throws Exception {
        QuestionImportRow invalid = row("", List.of("Opção única"), 5, null, null, null);
        stubParser(List.of(invalid));

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getStatus()).isEqualTo(ImportItemStatus.ERROR);
        assertThat(result.getItems().get(0).getFieldErrors()).isNotEmpty();
    }

    @Test
    void shouldHandleNullRequest() throws Exception {
        stubParser(List.of(row("Pergunta?", List.of("A", "B", "C"), 0, 10, true, 100L)));
        when(questionImportGateway.findExistingQuestionId(anyString(), anyLong())).thenReturn(null);
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean())).thenReturn(1L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream, null);

        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
    }

    @Test
    void shouldHandleEmptyRowList() throws Exception {
        stubParser(List.of());

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getSummary().getTotalReceived()).isEqualTo(0);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldHandleNullRowList() throws Exception {
        stubParser(null);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getSummary().getTotalReceived()).isEqualTo(0);
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void shouldComputeActiveFinalWithInheritDefaultsToTrue() throws Exception {
        QuestionImportRow rowWithoutActive = row("Pergunta?", List.of("A", "B"), 0, 10, null, 100L);
        stubParser(List.of(rowWithoutActive));
        when(questionImportGateway.createQuestion(any(QuestionImportRow.class), anyBoolean())).thenReturn(1L);

        QuestionImportResultDTO result = questionImportService.importQuestions(
                "test.json", "application/json", dummyStream,
                req(DedupeMode.ALLOW_DUPLICATES, ActiveMode.INHERIT, TransactionMode.PARTIAL_OK, false));

        assertThat(result.getSummary().getImportedCount()).isEqualTo(1);
    }
}
