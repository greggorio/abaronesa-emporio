import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import { useQuestionImport } from './useQuestionImport';
import type { QuestionImportPreviewDTO, QuestionImportResultDTO } from '@/types/question-import';
import React from 'react';

vi.mock('@/lib/api-client', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

function createFile(contents: string, name = 'test.json', type = 'application/json'): File {
  return new File([contents], name, { type });
}

describe('useQuestionImport', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    global.URL.createObjectURL = vi.fn(() => 'blob:test');
    global.URL.revokeObjectURL = vi.fn();
  });

  describe('previewImport', () => {
    it('should call preview endpoint with multipart form data', async () => {
      const mockPreviewResponse: QuestionImportPreviewDTO = {
        detectedFormat: 'JSON',
        totalParsed: 1,
        previewItems: [
          {
            index: 1,
            status: 'WILL_IMPORT',
            question: 'Test?',
          },
        ],
        summary: {
          totalReceived: 1,
          totalParsed: 1,
          importedCount: 1,
          updatedCount: 0,
          skippedCount: 0,
          errorCount: 0,
        },
        importId: 'preview-1',
      };

      (apiClient.post as any).mockResolvedValue({ data: mockPreviewResponse });

      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });

      const file = createFile('[{"question":"Test?"}]');

      await act(async () => {
        const data = await result.current.previewImport.mutateAsync({
          file,
          request: { dedupeMode: 'SKIP_DUPLICATES', dryRun: true },
        });
        expect(data).toEqual(mockPreviewResponse);
      });

      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/questions/import/preview',
        expect.any(FormData),
        expect.objectContaining({
          headers: { 'Content-Type': 'multipart/form-data' },
        })
      );
    });
  });

  describe('commitImport', () => {
    it('should call commit endpoint with multipart form data', async () => {
      const mockCommitResponse: QuestionImportResultDTO = {
        summary: {
          totalReceived: 1,
          totalParsed: 1,
          importedCount: 1,
          updatedCount: 0,
          skippedCount: 0,
          errorCount: 0,
        },
        items: [
          {
            index: 1,
            status: 'IMPORTED',
            question: 'Test?',
            createdQuestionId: 42,
          },
        ],
        importId: 'commit-1',
      };

      (apiClient.post as any).mockResolvedValue({ data: mockCommitResponse });

      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });

      const file = createFile('[{"question":"Test?"}]');

      await act(async () => {
        const data = await result.current.commitImport.mutateAsync({
          file,
          request: { dedupeMode: 'SKIP_DUPLICATES', dryRun: false },
        });
        expect(data).toEqual(mockCommitResponse);
      });

      expect(apiClient.post).toHaveBeenCalledWith(
        '/api/questions/import/commit',
        expect.any(FormData),
        expect.objectContaining({
          headers: { 'Content-Type': 'multipart/form-data' },
        })
      );
    });

    it('should return data on 422 error', async () => {
      const errorResponse: QuestionImportResultDTO = {
        summary: {
          totalReceived: 1,
          totalParsed: 1,
          importedCount: 0,
          updatedCount: 0,
          skippedCount: 0,
          errorCount: 1,
          transactionModeUsed: 'ALL_OR_NOTHING',
        },
        items: [
          {
            index: 1,
            status: 'ERROR',
            question: '',
            messages: ['Questão não pode ser vazia'],
            fieldErrors: [{ field: 'question', message: 'Questão não pode ser vazia' }],
          },
        ],
        importId: 'commit-error',
      };

      (apiClient.post as any).mockRejectedValue({
        response: { status: 422, data: errorResponse },
      });

      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });

      const file = createFile('[{"question":""}]');

      await act(async () => {
        const data = await result.current.commitImport.mutateAsync({
          file,
          request: { dedupeMode: 'SKIP_DUPLICATES', dryRun: false },
        });
        expect(data).toEqual(errorResponse);
        expect(data.summary?.errorCount).toBe(1);
      });
    });

    it('should throw on non-422 error', async () => {
      (apiClient.post as any).mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });

      const file = createFile('[{"question":"Test?"}]');

      await expect(
        result.current.commitImport.mutateAsync({
          file,
          request: { dedupeMode: 'SKIP_DUPLICATES', dryRun: false },
        })
      ).rejects.toThrow('Network error');
    });
  });

  describe('downloadTemplateJson', () => {
    it('should download JSON template', async () => {
      const blobContent = new Blob(['{"template": true}'], { type: 'application/json' });
      (apiClient.get as any).mockResolvedValue({ data: blobContent });

      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });

      await act(async () => {
        await result.current.downloadTemplateJson();
      });

      expect(apiClient.get).toHaveBeenCalledWith('/api/questions/import/template.json', {
        responseType: 'blob',
      });
    });

    it('should throw on download error', async () => {
      (apiClient.get as any).mockRejectedValue(new Error('Download failed'));

      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });

      await expect(result.current.downloadTemplateJson()).rejects.toThrow('Download failed');
    });
  });

  describe('downloadTemplateCsv', () => {
    it('should download CSV template', async () => {
      const blobContent = new Blob(['question,options'], { type: 'text/csv' });
      (apiClient.get as any).mockResolvedValue({ data: blobContent });

      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });

      await act(async () => {
        await result.current.downloadTemplateCsv();
      });

      expect(apiClient.get).toHaveBeenCalledWith('/api/questions/import/template.csv', {
        responseType: 'blob',
      });
    });
  });

  describe('isLoading', () => {
    it('should be false when no mutations are pending', () => {
      const { result } = renderHook(() => useQuestionImport(), { wrapper: createWrapper() });
      expect(result.current.isLoading).toBe(false);
    });
  });
});
