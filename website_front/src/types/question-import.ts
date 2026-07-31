// Tipos para importação de perguntas

export interface QuestionImportRequestDTO {
  dedupeMode?: 'SKIP_DUPLICATES' | 'UPDATE_EXISTING' | 'ALLOW_DUPLICATES';
  activeMode?: 'INHERIT' | 'FORCE_ACTIVE' | 'FORCE_INACTIVE';
  transactionMode?: 'PARTIAL_OK' | 'ALL_OR_NOTHING';
  dryRun?: boolean;
  previewLimit?: number;
}

export interface QuestionImportPreviewDTO {
  detectedFormat?: string;
  totalParsed?: number;
  previewItems?: QuestionImportItemResultDTO[];
  summary?: ImportSummaryDTO;
  importId?: string;
}

export interface QuestionImportResultDTO {
  summary?: ImportSummaryDTO;
  items?: QuestionImportItemResultDTO[];
  importId?: string;
}

export interface QuestionImportItemResultDTO {
  index?: number;
  status?: ImportItemStatus;
  question?: string;
  categoryIdResolved?: number;
  messages?: string[];
  fieldErrors?: FieldErrorDTO[];
  createdQuestionId?: number;
  existingQuestionId?: number;
}

export interface ImportSummaryDTO {
  totalReceived?: number;
  totalParsed?: number;
  importedCount?: number;
  updatedCount?: number;
  skippedCount?: number;
  errorCount?: number;
  transactionModeUsed?: 'PARTIAL_OK' | 'ALL_OR_NOTHING';
}

export interface FieldErrorDTO {
  field?: string;
  message?: string;
}

export type ImportItemStatus = 
  | 'IMPORTED' 
  | 'UPDATED' 
  | 'SKIPPED' 
  | 'ERROR' 
  | 'WARN'
  | 'WILL_IMPORT'
  | 'WILL_UPDATE';