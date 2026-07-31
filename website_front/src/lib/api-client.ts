import axios from 'axios';

/**
 * Resolve a base URL para a API:
 * - Produção: window.RuntimeConfig.websiteApiUrl (injetado pelo entrypoint)
 * - Dev: VITE_WEBSITE_API_URL ou fallback host+:8085
 * Obs: base sem /api porque os endpoints já incluem /api/...
 */
const normalizeBase = (url: string): string => {
  try {
    const u = new URL(url);
    if (u.pathname === '/api') {
      u.pathname = '';
    } else if (u.pathname.endsWith('/api')) {
      u.pathname = u.pathname.slice(0, -4);
    }
    u.pathname = u.pathname.replace(/\/+$/, '');
    return u.toString().replace(/\/+$/, '');
  } catch {
    return url;
  }
};

const getApiUrl = (): string => {
  const runtime = (window as any).RuntimeConfig?.websiteApiUrl;
  if (runtime?.startsWith('http')) return normalizeBase(runtime);

  const env = import.meta.env.VITE_WEBSITE_API_URL as string | undefined;
  if (env?.startsWith('http')) return normalizeBase(env);

  const host = window.location.hostname;
  return `${window.location.protocol}//${host}:8085`;
};

export const apiClient = axios.create({
  baseURL: getApiUrl(),
  // Removido withCredentials: true para evitar conflitos com autenticação baseada em token JWT
  headers: {
    'Content-Type': 'application/json',
  },
});

// Interceptor de requisição - adiciona token se existir
apiClient.interceptors.request.use(
  (config) => {
    // Busca o token do localStorage
    const token = localStorage.getItem('auth_token');

    // Se existir token, adiciona no header Authorization
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    console.error('[API] Request error:', error);
    return Promise.reject(error);
  }
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      window.location.href = '/admin-login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
