import {
  QuizSession,
  Leaderboard,
  SessionCreateRequest,
  Question,
  Category,
} from '@/types/quiz';

// Usa o hostname atual para suportar acesso via IP local
const getApiBaseUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
  const hostname = window.location.hostname;

  // Em produção, usa proxy do Nginx (sem porta)
  // Em desenvolvimento, usa porta 8085 do backend local (Villa Custom)
  const isDevelopment = hostname === 'localhost' || hostname === '127.0.0.1' || hostname.startsWith('192.168.');
  const port = isDevelopment ? ':8085' : '';

  return `${protocol}//${hostname}${port}/api/quiz`;
};

const API_BASE_URL = getApiBaseUrl();

// Helper para obter headers com autenticação
const getAuthHeaders = (): HeadersInit => {
  const token = localStorage.getItem('auth_token');
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return headers;
};

export const quizApi = {
  // Obter estatísticas
  async getStats(): Promise<{ totalQuestions: number; suggestedQuestions: number }> {
    const response = await fetch(`${API_BASE_URL}/session/stats`, {
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error('Erro ao obter estatísticas');
    }

    return response.json();
  },

  // Criar nova sessão
  async createSession(request: SessionCreateRequest): Promise<QuizSession> {
    const response = await fetch(`${API_BASE_URL}/session`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
    });

    if (!response.ok) {
      throw new Error('Erro ao criar sessão');
    }

    return response.json();
  },

  // Obter informações da sessão
  async getSession(sessionCode: string): Promise<QuizSession> {
    const response = await fetch(`${API_BASE_URL}/session/${sessionCode}`, {
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error('Sessão não encontrada');
    }

    return response.json();
  },

  // Iniciar quiz
  async startSession(sessionCode: string): Promise<Question> {
    const response = await fetch(`${API_BASE_URL}/session/${sessionCode}/start`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error('Erro ao iniciar quiz');
    }

    return response.json();
  },

  // Próxima pergunta
  async nextQuestion(sessionCode: string): Promise<Question | Leaderboard> {
    const response = await fetch(`${API_BASE_URL}/session/${sessionCode}/next`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error('Erro ao avançar pergunta');
    }

    return response.json();
  },

  // Obter ranking
  async getLeaderboard(sessionCode: string): Promise<Leaderboard> {
    const response = await fetch(`${API_BASE_URL}/session/${sessionCode}/leaderboard`, {
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error('Erro ao obter ranking');
    }

    return response.json();
  },

  // Finalizar sessão
  async finishSession(sessionCode: string): Promise<Leaderboard> {
    const response = await fetch(`${API_BASE_URL}/session/${sessionCode}/finish`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error('Erro ao finalizar sessão');
    }

    return response.json();
  },

  // Revelar resposta correta por timeout
  async revealCorrect(sessionCode: string): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/session/${sessionCode}/reveal`, {
      method: 'POST',
      headers: getAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error('Erro ao revelar resposta correta');
    }
  },

  // Obter categorias ativas (rota pública)
  async getCategories(): Promise<Category[]> {
    const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
    const hostname = window.location.hostname;
    const isDevelopment = hostname === 'localhost' || hostname === '127.0.0.1' || hostname.startsWith('192.168.');
    const port = isDevelopment ? ':8085' : '';
    const baseUrl = `${protocol}//${hostname}${port}/api`;

    const response = await fetch(`${baseUrl}/categories/active`, {
      headers: { 'Content-Type': 'application/json' },
    });
    if (!response.ok) {
      throw new Error('Erro ao obter categorias');
    }
    return response.json();
  },

  // Obter todas as categorias (requer autenticação)
  async getAllCategories(): Promise<Category[]> {
    const response = await fetch(`${API_BASE_URL}/../categories`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error('Erro ao obter todas as categorias');
    }
    return response.json();
  },

  // Criar nova categoria
  async createCategory(category: Omit<Category, 'id' | 'createdAt' | 'updatedAt' | 'questionCount'>): Promise<Category> {
    const response = await fetch(`${API_BASE_URL}/../categories`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(category),
    });
    if (!response.ok) {
      const errorData = await response.text();
      throw new Error(`Erro ao criar categoria: ${errorData}`);
    }
    return response.json();
  },

  // Atualizar categoria
  async updateCategory(id: number, category: Partial<Omit<Category, 'id' | 'createdAt' | 'updatedAt' | 'questionCount'>>): Promise<Category> {
    const response = await fetch(`${API_BASE_URL}/../categories/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders(),
      body: JSON.stringify(category),
    });
    if (!response.ok) {
      const errorData = await response.text();
      throw new Error(`Erro ao atualizar categoria: ${errorData}`);
    }
    return response.json();
  },

  // Alternar status da categoria
  async toggleCategoryStatus(id: number): Promise<Category> {
    const response = await fetch(`${API_BASE_URL}/../categories/${id}/toggle`, {
      method: 'PATCH',
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error('Erro ao alternar status da categoria');
    }
    return response.json();
  },

  // Deletar categoria
  async deleteCategory(id: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/../categories/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error('Erro ao deletar categoria');
    }
  },

  // Obter todas as perguntas (requer autenticação)
  async getAllQuestions(): Promise<any[]> {
    const response = await fetch(`${API_BASE_URL}/../questions`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error('Erro ao obter perguntas');
    }
    return response.json();
  },

  // Obter perguntas ativas (requer autenticação)
  async getActiveQuestions(): Promise<any[]> {
    const response = await fetch(`${API_BASE_URL}/../questions/active`, {
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error('Erro ao obter perguntas ativas');
    }
    return response.json();
  },

  // Criar nova pergunta
  async createQuestion(question: any): Promise<any> {
    const response = await fetch(`${API_BASE_URL}/../questions`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(question),
    });
    if (!response.ok) {
      const errorData = await response.text();
      throw new Error(`Erro ao criar pergunta: ${errorData}`);
    }
    return response.json();
  },

  // Atualizar pergunta
  async updateQuestion(id: number, question: any): Promise<any> {
    const response = await fetch(`${API_BASE_URL}/../questions/${id}`, {
      method: 'PUT',
      headers: getAuthHeaders(),
      body: JSON.stringify(question),
    });
    if (!response.ok) {
      const errorData = await response.text();
      throw new Error(`Erro ao atualizar pergunta: ${errorData}`);
    }
    return response.json();
  },

  // Deletar pergunta
  async deleteQuestion(id: number): Promise<void> {
    const response = await fetch(`${API_BASE_URL}/../questions/${id}`, {
      method: 'DELETE',
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error('Erro ao deletar pergunta');
    }
  },

  // Alternar status da pergunta
  async toggleQuestionStatus(id: number): Promise<any> {
    const response = await fetch(`${API_BASE_URL}/../questions/${id}/toggle`, {
      method: 'PATCH',
      headers: getAuthHeaders(),
    });
    if (!response.ok) {
      throw new Error('Erro ao alternar status da pergunta');
    }
    return response.json();
  },
};
