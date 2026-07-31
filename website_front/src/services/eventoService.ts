import axios from 'axios';

// Helper para obter configuração em runtime (Docker) ou build time (dev)
declare global {
  interface Window {
    RuntimeConfig?: {
      erpApiUrl: string;
      websiteApiUrl: string;
    };
  }
}

const WEBSITE_API_URL = window.RuntimeConfig?.websiteApiUrl || import.meta.env.VITE_WEBSITE_API_URL;

// Configure axios interceptor to add JWT token
axios.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Enum types matching backend
export enum GeneroMusical {
  ROCK = 'ROCK',
  METAL = 'METAL',
  ACUSTICO = 'ACUSTICO',
  SERTANEJO = 'SERTANEJO',
  MPB = 'MPB',
  BLUES = 'BLUES',
  JAZZ = 'JAZZ',
  OUTRO = 'OUTRO'
}

export enum EventoStatus {
  AGENDADO = 'AGENDADO',
  REALIZADO = 'REALIZADO',
  CANCELADO = 'CANCELADO'
}

// DTO types matching backend
export interface EventoResponseDTO {
  id: number;
  titulo: string;
  descricao: string;
  dataEvento: string; // ISO string
  dataHoraFim?: string; // ISO string
  preco?: number;
  gratuito: boolean;
  banda: string;
  genero: GeneroMusical;
  generoDescricao: string;
  imagemUrl?: string;
  ativo: boolean;
  status: EventoStatus;
  statusDescricao: string;
  criadoEm: string;
  atualizadoEm: string;
}

export interface EventoDTO {
  titulo: string;
  descricao: string;
  dataEvento: string; // ISO string
  dataHoraFim?: string; // ISO string
  preco?: number;
  gratuito: boolean;
  banda: string;
  genero: GeneroMusical;
  imagemUrl?: string;
  ativo?: boolean;
  status?: EventoStatus;
}

// Paginated response type
export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// Public API - No authentication required
export const eventoService = {
  /**
   * Retorna os próximos 4 eventos futuros para exibir na home page
   */
  listarProximosEventos: async (): Promise<EventoResponseDTO[]> => {
    const response = await axios.get(`${WEBSITE_API_URL}/api/eventos/proximos`);
    return response.data;
  },

  /**
   * Retorna eventos realizados para a seção "Eventos Realizados"
   */
  listarEventosRealizados: async (): Promise<EventoResponseDTO[]> => {
    const response = await axios.get(`${WEBSITE_API_URL}/api/eventos/realizados`);
    return response.data;
  },

  /**
   * Busca um evento específico por ID
   */
  buscarPorId: async (id: number): Promise<EventoResponseDTO> => {
    const response = await axios.get(`${WEBSITE_API_URL}/api/eventos/${id}`);
    return response.data;
  },

  // Admin endpoints - Authentication required
  /**
   * Lista todos os eventos (admin) com filtro opcional por status
   */
  listarTodos: async (status?: EventoStatus): Promise<EventoResponseDTO[]> => {
    const params = status ? { status } : {};
    const response = await axios.get(`${WEBSITE_API_URL}/api/eventos`, { params });
    return response.data;
  },

  /**
   * Cria um novo evento (admin)
   */
  criar: async (dto: EventoDTO): Promise<EventoResponseDTO> => {
    const response = await axios.post(`${WEBSITE_API_URL}/api/eventos`, dto);
    return response.data;
  },

  /**
   * Atualiza um evento existente (admin)
   */
  atualizar: async (id: number, dto: EventoDTO): Promise<EventoResponseDTO> => {
    const response = await axios.put(`${WEBSITE_API_URL}/api/eventos/${id}`, dto);
    return response.data;
  },

  /**
   * Exclui (soft delete) um evento (admin)
   */
  excluir: async (id: number): Promise<void> => {
    await axios.delete(`${WEBSITE_API_URL}/api/eventos/${id}`);
  },

  /**
   * Lista eventos com paginação e busca (admin)
   */
  listarComPaginacao: async (
    page: number = 0,
    size: number = 10,
    search?: string,
    status?: EventoStatus
  ): Promise<PageResponse<EventoResponseDTO>> => {
    const params: any = { page, size };
    if (search) params.search = search;
    if (status) params.status = status;

    const response = await axios.get(`${WEBSITE_API_URL}/api/eventos/admin`, { params });
    return response.data;
  }
};
