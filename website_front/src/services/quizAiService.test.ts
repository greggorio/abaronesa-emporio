import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { generateQuizQuestions } from './quizAiService';

vi.mock('axios');

describe('quizAiService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('should call axios.post with correct URL and payload', async () => {
    const mockResponse = {
      data: [
        {
          index: 1,
          question: 'Pergunta de teste?',
          options: ['A', 'B', 'C'],
          correctAnswer: 0,
          points: 10,
          active: true,
          categoryId: 1,
        },
      ],
    };
    (axios.post as any).mockResolvedValue(mockResponse);

    const payload = {
      tema: 'Cafeteria',
      quantidade: 5,
      dificuldade: 'facil',
      idioma: 'pt-BR',
      categoryId: 1,
      points: 10,
    };

    const result = await generateQuizQuestions(payload);

    expect(axios.post).toHaveBeenCalledTimes(1);
    expect(axios.post).toHaveBeenCalledWith(
      expect.stringContaining('/api/openai/quiz/generate'),
      payload,
      expect.objectContaining({
        headers: {
          'Content-Type': 'application/json',
        },
      })
    );
    expect(result).toHaveLength(1);
    expect(result[0].question).toBe('Pergunta de teste?');
  });

  it('should include auth token when available', async () => {
    localStorage.setItem('auth_token', 'test-token-123');
    (axios.post as any).mockResolvedValue({ data: [] });

    await generateQuizQuestions({ tema: 'test' });

    expect(axios.post).toHaveBeenCalledWith(
      expect.any(String),
      expect.any(Object),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer test-token-123',
        }),
      })
    );
  });

  it('should not include auth header when no token', async () => {
    (axios.post as any).mockResolvedValue({ data: [] });

    await generateQuizQuestions({ tema: 'test' });

    const callArgs = (axios.post as any).mock.calls[0];
    const headers = callArgs[2].headers;
    expect(headers.Authorization).toBeUndefined();
  });

  it('should return empty array when API returns empty', async () => {
    (axios.post as any).mockResolvedValue({ data: [] });

    const result = await generateQuizQuestions({ tema: 'test', quantidade: 0 });
    expect(result).toEqual([]);
  });

  it('should throw on network error', async () => {
    (axios.post as any).mockRejectedValue(new Error('Network error'));

    await expect(generateQuizQuestions({ tema: 'test' })).rejects.toThrow('Network error');
  });
});
