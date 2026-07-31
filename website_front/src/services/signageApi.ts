import axios from 'axios';

declare global {
  interface Window {
    RuntimeConfig?: {
      signageApiUrl?: string;
    };
  }
}

const SIGNAGE_API_URL =
  window.RuntimeConfig?.signageApiUrl ||
  import.meta.env.VITE_SIGNAGE_API_URL ||
  'http://localhost:4020';

const signageApi = axios.create({
  baseURL: SIGNAGE_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
});

signageApi.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

signageApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      console.error('[Signage API] Response error:', error.response.status, error.response.data);
    } else if (error.request) {
      console.error('[Signage API] No response:', error.request);
    } else {
      console.error('[Signage API] Request setup error:', error.message);
    }
    return Promise.reject(error);
  }
);

export default signageApi;
