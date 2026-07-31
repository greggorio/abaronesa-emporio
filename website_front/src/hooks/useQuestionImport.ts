import { useMutation } from '@tanstack/react-query';
import apiClient from '@/lib/api-client';
import type {
  QuestionImportRequestDTO,
  QuestionImportPreviewDTO,
  QuestionImportResultDTO
} from '../types/question-import';

export const useQuestionImport = () => {
  // Mutation para preview
  const previewImport = useMutation({
    mutationFn: async ({ file, request }: { file: File; request: QuestionImportRequestDTO }) => {
      const formData = new FormData();
      formData.append('file', file);
      
      // Converter o request para JSON string e adicionar ao form data
      formData.append('request', JSON.stringify(request));

      const response = await apiClient.post('/api/questions/import/preview', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      return response.data as QuestionImportPreviewDTO;
    },
  });

  // Mutation para commit
  const commitImport = useMutation({
    mutationFn: async ({ file, request }: { file: File; request: QuestionImportRequestDTO }) => {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('request', JSON.stringify(request));

      try {
        const response = await apiClient.post('/api/questions/import/commit', formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        });
        return response.data as QuestionImportResultDTO;
      } catch (error: any) {
        if (error.response?.status === 422) {
          return error.response.data as QuestionImportResultDTO;
        }
        throw error;
      }
    },
  });

  // Função para baixar template JSON
  const downloadTemplateJson = async () => {
    try {
      const response = await apiClient.get('/api/questions/import/template.json', {
        responseType: 'blob',
      });

      // Criar e disparar download
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'questions_import_template.json');
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Erro ao baixar template JSON:', error);
      throw error;
    }
  };

  // Função para baixar template CSV
  const downloadTemplateCsv = async () => {
    try {
      const response = await apiClient.get('/api/questions/import/template.csv', {
        responseType: 'blob',
      });

      // Criar e disparar download
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'questions_import_template.csv');
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Erro ao baixar template CSV:', error);
      throw error;
    }
  };

  return {
    previewImport,
    commitImport,
    downloadTemplateJson,
    downloadTemplateCsv,
    isLoading: previewImport.isPending || commitImport.isPending,
  };
};
