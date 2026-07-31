import { describe, it, expect } from 'vitest';
import type {
  QuestionImportRequestDTO,
  QuestionImportPreviewDTO,
  QuestionImportResultDTO,
  QuestionImportItemResultDTO,
  ImportSummaryDTO,
  FieldErrorDTO,
  ImportItemStatus,
} from './question-import';

describe('question-import types', () => {
  it('should accept minimal QuestionImportRequestDTO', () => {
    const req: QuestionImportRequestDTO = {};
    expect(req.dedupeMode).toBeUndefined();
  });

  it('should accept full QuestionImportRequestDTO', () => {
    const req: QuestionImportRequestDTO = {
      dedupeMode: 'SKIP_DUPLICATES',
      activeMode: 'INHERIT',
      transactionMode: 'PARTIAL_OK',
      dryRun: true,
      previewLimit: 20,
    };
    expect(req.dedupeMode).toBe('SKIP_DUPLICATES');
    expect(req.previewLimit).toBe(20);
  });

  it('should accept all dedupe modes', () => {
    const modes: QuestionImportRequestDTO['dedupeMode'][] = [
      'SKIP_DUPLICATES',
      'UPDATE_EXISTING',
      'ALLOW_DUPLICATES',
    ];
    expect(modes).toHaveLength(3);
  });

  it('should accept all active modes', () => {
    const modes: QuestionImportRequestDTO['activeMode'][] = [
      'INHERIT',
      'FORCE_ACTIVE',
      'FORCE_INACTIVE',
    ];
    expect(modes).toHaveLength(3);
  });

  it('should accept all transaction modes', () => {
    const modes: QuestionImportRequestDTO['transactionMode'][] = [
      'PARTIAL_OK',
      'ALL_OR_NOTHING',
    ];
    expect(modes).toHaveLength(2);
  });

  it('should construct QuestionImportPreviewDTO', () => {
    const preview: QuestionImportPreviewDTO = {
      detectedFormat: 'JSON',
      totalParsed: 5,
      previewItems: [],
      summary: {
        totalReceived: 5,
        totalParsed: 5,
        importedCount: 3,
        updatedCount: 1,
        skippedCount: 1,
        errorCount: 0,
      },
      importId: 'abc-123',
    };
    expect(preview.detectedFormat).toBe('JSON');
    expect(preview.summary?.importedCount).toBe(3);
  });

  it('should construct QuestionImportResultDTO', () => {
    const item: QuestionImportItemResultDTO = {
      index: 1,
      status: 'IMPORTED',
      question: 'Pergunta?',
      categoryIdResolved: 100,
      messages: [],
      fieldErrors: [],
      createdQuestionId: 42,
    };
    const result: QuestionImportResultDTO = {
      summary: {
        totalReceived: 1,
        totalParsed: 1,
        importedCount: 1,
        updatedCount: 0,
        skippedCount: 0,
        errorCount: 0,
      },
      items: [item],
      importId: 'abc-123',
    };
    expect(result.items).toHaveLength(1);
    expect(result.items![0].createdQuestionId).toBe(42);
  });

  it('should accept all ImportItemStatus values', () => {
    const statuses: ImportItemStatus[] = [
      'IMPORTED',
      'UPDATED',
      'SKIPPED',
      'ERROR',
      'WARN',
      'WILL_IMPORT',
      'WILL_UPDATE',
    ];
    expect(statuses).toHaveLength(7);
  });

  it('should construct FieldErrorDTO', () => {
    const error: FieldErrorDTO = {
      field: 'question',
      message: 'Questão não pode ser vazia',
    };
    expect(error.field).toBe('question');
    expect(error.message).toContain('vazia');
  });

  it('should construct ImportSummaryDTO with transactionModeUsed', () => {
    const summary: ImportSummaryDTO = {
      totalReceived: 10,
      totalParsed: 10,
      importedCount: 8,
      updatedCount: 0,
      skippedCount: 0,
      errorCount: 2,
      transactionModeUsed: 'ALL_OR_NOTHING',
    };
    expect(summary.errorCount).toBe(2);
    expect(summary.transactionModeUsed).toBe('ALL_OR_NOTHING');
  });
});
