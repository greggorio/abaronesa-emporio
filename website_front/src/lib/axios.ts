import axios from 'axios';
import { apiConfig } from '../config/api';

// Criando instância do axios com configurações padrão
const api = axios.create({
  baseURL: apiConfig.erpBaseUrl,
  headers: {
    'Content-Type': 'application/json'
  },
  timeout: 30000,
});

// Interceptor de requisição - adiciona token se existir
api.interceptors.request.use(
  (config) => {
    // Busca o token do localStorage
    const token = localStorage.getItem('auth_token');
    const userId = localStorage.getItem('user_id');

    // Se existir token, adiciona no header Authorization
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Adiciona o ID do usuário como header customizado se existir
    if (userId) {
      config.headers['X-User-ID'] = userId;
    }

    // Headers para forçar bypass de cache HTTP
    config.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate';
    config.headers['Pragma'] = 'no-cache';
    config.headers['Expires'] = '0';

    return config;
  },
  (error) => {
    console.error('[API] Request error:', error);
    return Promise.reject(error);
  }
);

// Interceptor de resposta - trata erros globalmente
api.interceptors.response.use(
  (response) => {
    // Qualquer status 2xx dispara esta função
    return response;
  },
  (error) => {
    // Qualquer status fora do range 2xx dispara esta função

    if (error.response) {
      // Servidor respondeu com status de erro
      console.error('[API] Response error:', error.response.status, error.response.data);

      // Se 401, limpar autenticação para qualquer endpoint
      if (error.response.status === 401) {
        // Importar e usar a função de logout do hook de autenticação
        import('../hooks/useAuth').then(({ useAuth }) => {
          // Executar logout via o hook de autenticação para manter o estado consistente
          useAuth.getState().logout();
        }).catch(err => {
          console.error('Erro ao importar useAuth:', err);
          // Fallback: limpar localStorage e redirecionar
          localStorage.removeItem('auth_token');
          localStorage.removeItem('user_id');
          localStorage.removeItem('user_nome');
          localStorage.removeItem('user_email');
          localStorage.removeItem('user_foto');
          localStorage.removeItem('user_roles');
          window.location.href = '/';
        });
      }
    } else if (error.request) {
      // Requisição foi feita mas não houve resposta
      console.error('[API] No response:', error.request);
    } else {
      // Algo aconteceu ao configurar a requisição
      console.error('[API] Request setup error:', error.message);
    }

    return Promise.reject(error);
  }
);

export default api;
