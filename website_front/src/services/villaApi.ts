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

// Criando instância do axios para a API do Villa (Site/Backend Java)
const villaApi = axios.create({
  baseURL: WEBSITE_API_URL,
  headers: {
    'Content-Type': 'application/json'
  },
  timeout: 30000,
});

// Interceptor de requisição - adiciona token se existir
villaApi.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Interceptor de resposta - log de erros
villaApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      console.error('[Villa API] Response error:', error.response.status, error.response.data);
    } else if (error.request) {
      console.error('[Villa API] No response:', error.request);
    } else {
      console.error('[Villa API] Request setup error:', error.message);
    }
    return Promise.reject(error);
  }
);

export default villaApi;
