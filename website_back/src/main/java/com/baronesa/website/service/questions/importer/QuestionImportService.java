package com.baronesa.website.service.questions.importer;

import com.baronesa.website.dto.questions.FieldErrorDTO;
import com.baronesa.website.dto.questions.ImportSummaryDTO;
import com.baronesa.website.dto.questions.QuestionImportItemResultDTO;
import com.baronesa.website.dto.questions.QuestionImportRequestDTO;
import com.baronesa.website.dto.questions.QuestionImportResultDTO;
import com.baronesa.website.enums.questions.ActiveMode;
import com.baronesa.website.enums.questions.DedupeMode;
import com.baronesa.website.enums.questions.ImportItemStatus;
import com.baronesa.website.enums.questions.TransactionMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionImportService {

    private final ImportParserRegistry parserRegistry;
    private final QuestionImportGateway questionImportGateway;

    public QuestionImportResultDTO importQuestions(
            String filename,
            String contentType,
            InputStream inputStream,
            QuestionImportRequestDTO request) throws Exception {

        if (request == null) {
            request = new QuestionImportRequestDTO();
        }
        applyDefaults(request);

        log.info("Starting import questions - filename: {}, contentType: {}", filename, contentType);

        ImportParser parser = parserRegistry.getParser(filename, contentType, inputStream);
        log.info("Parser selected: {}", parser.getFormatName());

        List<QuestionImportRow> rows = parser.parse(inputStream);
        if (rows == null) {
            rows = Collections.emptyList();
        }

        log.info("Parsed {} rows from input", rows.size());

        List<ActionInfo> actions = buildActions(rows, request);
        boolean hasValidationErrors = actions.stream()
                .anyMatch(a -> a.result.getStatus() == ImportItemStatus.ERROR);
        boolean abortAllOrNothing = request.getTransactionMode() == TransactionMode.ALL_OR_NOTHING && hasValidationErrors;
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());

        List<QuestionImportItemResultDTO> results = new ArrayList<>();
        for (ActionInfo action : actions) {
            QuestionImportItemResultDTO result = action.result;
            ensureLists(result);

            if (abortAllOrNothing && result.getStatus() != ImportItemStatus.ERROR) {
                result.setStatus(ImportItemStatus.SKIPPED);
                result.getMessages().add("Operação abortada devido a outro erro (modo ALL_OR_NOTHING)");
                results.add(result);
                continue;
            }

            if (dryRun) {
                results.add(result);
                continue;
            }

            if (result.getStatus() == ImportItemStatus.ERROR || action.actionType == ActionType.SKIP) {
                results.add(result);
                continue;
            }

            switch (action.actionType) {
                case CREATE:
                    Long createdId = questionImportGateway.createQuestion(action.row, action.activeFinal);
                    result.setCreatedQuestionId(createdId);
                    break;
                case UPDATE:
                    if (action.existingId != null) {
                        Long updatedId = questionImportGateway.updateQuestion(action.existingId, action.row, action.activeFinal);
                        result.setExistingQuestionId(updatedId);
                    }
                    break;
                default:
                    break;
            }

            results.add(result);
        }

        ImportSummaryDTO summary = buildSummary(rows.size(), results);
        QuestionImportResultDTO resultDto = new QuestionImportResultDTO();
        resultDto.setSummary(summary);
        resultDto.setItems(results);
        resultDto.setImportId(UUID.randomUUID().toString());
        return resultDto;
    }

    private List<ActionInfo> buildActions(List<QuestionImportRow> rows, QuestionImportRequestDTO request) {
        List<ActionInfo> actions = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            actions.add(determineAction(rows.get(i), i, request));
        }
        return actions;
    }

    private ActionInfo determineAction(
            QuestionImportRow row,
            int index,
            QuestionImportRequestDTO request) {

        QuestionImportItemResultDTO result = new QuestionImportItemResultDTO();
        result.setIndex(row.getIndex() != null ? row.getIndex() : index + 1);
        result.setQuestion(row.getQuestion());
        result.setCategoryIdResolved(row.getCategoryId());
        result.setMessages(new ArrayList<>());
        result.setFieldErrors(new ArrayList<>());

        boolean hasValidationError = validateRow(row, result.getMessages(), result.getFieldErrors());
        if (hasValidationError) {
            result.setStatus(ImportItemStatus.ERROR);
            return new ActionInfo(ActionType.ERROR, result, row, null, false);
        }

        boolean activeFinal = calculateActiveFinal(row, request.getActiveMode());
        Long existingId = questionImportGateway.findExistingQuestionId(row.getQuestion(), row.getCategoryId());
        DedupeMode dedupeMode = request.getDedupeMode();

        ActionType actionType;
        ImportItemStatus status;
        Long questionId = null;

        switch (dedupeMode) {
            case ALLOW_DUPLICATES:
                actionType = ActionType.CREATE;
                status = ImportItemStatus.IMPORTED;
                break;
            case UPDATE_EXISTING:
                if (existingId != null) {
                    actionType = ActionType.UPDATE;
                    status = ImportItemStatus.UPDATED;
                    questionId = existingId;
                } else {
                    actionType = ActionType.CREATE;
                    status = ImportItemStatus.IMPORTED;
                }
                break;
            case SKIP_DUPLICATES:
            default:
                if (existingId != null) {
                    actionType = ActionType.SKIP;
                    status = ImportItemStatus.SKIPPED;
                    questionId = existingId;
                } else {
                    actionType = ActionType.CREATE;
                    status = ImportItemStatus.IMPORTED;
                }
                break;
        }

        result.setStatus(status);
        if (questionId != null) {
            if (actionType == ActionType.UPDATE || actionType == ActionType.SKIP) {
                result.setExistingQuestionId(questionId);
            } else {
                result.setCreatedQuestionId(questionId);
            }
        }

        return new ActionInfo(actionType, result, row, existingId, activeFinal);
    }

    private void applyDefaults(QuestionImportRequestDTO request) {
        if (request.getDedupeMode() == null) {
            request.setDedupeMode(DedupeMode.SKIP_DUPLICATES);
        }
        if (request.getActiveMode() == null) {
            request.setActiveMode(ActiveMode.INHERIT);
        }
        if (request.getTransactionMode() == null) {
            request.setTransactionMode(TransactionMode.PARTIAL_OK);
        }
        if (request.getDryRun() == null) {
            request.setDryRun(false);
        }
    }

    private boolean validateRow(QuestionImportRow row, List<String> messages, List<FieldErrorDTO> fieldErrors) {
        boolean hasError = false;
        log.debug("Validating row - question: '{}', options: {}, correctAnswer: {}, categoryId: {}",
                row.getQuestion(), row.getOptions(), row.getCorrectAnswer(), row.getCategoryId());

        if (row.getQuestion() == null || row.getQuestion().trim().isEmpty()) {
            log.warn("Validation error: question is null or empty");
            fieldErrors.add(new FieldErrorDTO("question", "Questão não pode ser vazia"));
            messages.add("Questão não pode ser vazia");
            hasError = true;
        }
        if (row.getOptions() == null || row.getOptions().size() < 2) {
            log.warn("Validation error: options is null or has less than 2 items - options: {}", row.getOptions());
            fieldErrors.add(new FieldErrorDTO("options", "Opções devem conter pelo menos 2 itens"));
            messages.add("Opções devem conter pelo menos 2 itens");
            hasError = true;
        }
        if (row.getCorrectAnswer() == null) {
            log.warn("Validation error: correctAnswer is null");
            fieldErrors.add(new FieldErrorDTO("correctAnswer", "Resposta correta é obrigatória"));
            messages.add("Resposta correta é obrigatória");
            hasError = true;
        } else if (row.getOptions() != null) {
            int maxIndex = row.getOptions().size() - 1;
            if (row.getCorrectAnswer() < 0 || row.getCorrectAnswer() > maxIndex) {
                log.warn("Validation error: correctAnswer {} is out of range [0, {}]", row.getCorrectAnswer(), maxIndex);
                fieldErrors.add(new FieldErrorDTO("correctAnswer", "Índice da resposta correta fora do intervalo"));
                messages.add("Índice da resposta correta fora do intervalo");
                hasError = true;
            }
        }

        if (row.getCategoryId() == null) {
            log.warn("Validation error: categoryId is null");
            fieldErrors.add(new FieldErrorDTO("categoryId", "Categoria é obrigatória"));
            messages.add("Categoria é obrigatória");
            hasError = true;
        }

        if (hasError) {
            log.warn("Row validation failed - errors: {}", messages);
        } else {
            log.debug("Row validation passed");
        }

        return hasError;
    }

    private boolean calculateActiveFinal(QuestionImportRow row, ActiveMode activeMode) {
        switch (activeMode) {
            case FORCE_ACTIVE:
                return true;
            case FORCE_INACTIVE:
                return false;
            case INHERIT:
            default:
                return row.getActive() != null ? row.getActive() : true;
        }
    }

    private void ensureLists(QuestionImportItemResultDTO result) {
        if (result.getMessages() == null) {
            result.setMessages(new ArrayList<>());
        }
        if (result.getFieldErrors() == null) {
            result.setFieldErrors(new ArrayList<>());
        }
    }

    private ImportSummaryDTO buildSummary(int totalRows, List<QuestionImportItemResultDTO> results) {
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        for (QuestionImportItemResultDTO result : results) {
            ImportItemStatus status = result.getStatus();
            if (status == null) {
                continue;
            }
            switch (status) {
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
        summary.setTotalReceived(totalRows);
        summary.setTotalParsed(totalRows);
        summary.setImportedCount(imported);
        summary.setUpdatedCount(updated);
        summary.setSkippedCount(skipped);
        summary.setErrorCount(errors);
        return summary;
    }

    private enum ActionType {
        CREATE, UPDATE, SKIP, ERROR
    }

    private static class ActionInfo {
        private final ActionType actionType;
        private final QuestionImportItemResultDTO result;
        private final QuestionImportRow row;
        private final Long existingId;
        private final boolean activeFinal;

        private ActionInfo(ActionType actionType, QuestionImportItemResultDTO result, QuestionImportRow row, Long existingId, boolean activeFinal) {
            this.actionType = actionType;
            this.result = result;
            this.row = row;
            this.existingId = existingId;
            this.activeFinal = activeFinal;
        }
    }
}
