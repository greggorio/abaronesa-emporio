package com.baronesa.website.service.questions.importer;

public interface QuestionImportGateway {
    Long findExistingQuestionId(String questionText, Long categoryId);

    Long createQuestion(QuestionImportRow row, boolean activeFinal);

    Long updateQuestion(Long existingId, QuestionImportRow row, boolean activeFinal);
}
