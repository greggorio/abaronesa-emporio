import axios from 'axios';
import { apiConfig } from '@/config/api';

export interface QuizAiRequest {
  tema: string;
  quantidade?: number;
  dificuldade?: string;
  idioma?: string;
  categoryId?: number;
  points?: number;
}

export interface QuizAiQuestion {
  index: number;
  question: string;
  options: string[];
  correctAnswer: number;
  points: number;
  active: boolean;
  categoryId?: number;
}

export const generateQuizQuestions = async (
  payload: QuizAiRequest
): Promise<QuizAiQuestion[]> => {
  const token = localStorage.getItem('auth_token');

  const response = await axios.post(`${apiConfig.erpBaseUrl}/api/openai/quiz/generate`, payload, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  });

  return response.data as QuizAiQuestion[];
};
