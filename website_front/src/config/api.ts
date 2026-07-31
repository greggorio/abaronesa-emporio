/**
 * Configuração centralizada das URLs de API
 */

// Helper para obter configuração em runtime (Docker) ou build time (dev)
declare global {
  interface Window {
    RuntimeConfig?: {
      erpApiUrl: string;
      websiteApiUrl: string;
    };
  }
}

// Helper simples para validar URL http(s)
const isValidHttpUrl = (s?: string | null): s is string => {
  if (!s) return false;
  try {
    const u = new URL(s);
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
};

// URL do ERP (backend de Bares)
// Prioridades (da maior para a menor):
// 1) Query param ?erp=... (persistido em localStorage)
// 2) localStorage.erpApiUrl
// 3) window.RuntimeConfig.erpApiUrl (produção/Docker)
// 4) VITE_ERP_API_URL (fallback somente se não for localhost em ambiente externo)
// 5) Host dinâmico: mesmo hostname do front + :8080
const ERP_API_URL = (() => {
  // 1) Query param override
  try {
    const qp = new URLSearchParams(window.location.search).get('erp');
    if (isValidHttpUrl(qp)) {
      localStorage.setItem('erpApiUrl', qp);
      return qp;
    }
  } catch {}

  // 2) localStorage override
  try {
    const stored = localStorage.getItem('erpApiUrl');
    if (isValidHttpUrl(stored)) return stored!;
  } catch {}

  // 3) RuntimeConfig
  const runtime = window.RuntimeConfig?.erpApiUrl;
  if (isValidHttpUrl(runtime)) return runtime;

  // 4) Env var — se apontar para localhost mas o host atual não é localhost, preferir fallback dinâmico
  const env = import.meta.env.VITE_ERP_API_URL as string | undefined;
  const host = typeof window !== 'undefined' ? window.location.hostname : 'localhost';
  const isLocalHost = host === 'localhost' || host === '127.0.0.1';
  if (isValidHttpUrl(env)) {
    const envUrl = new URL(env);
    const envIsLocalhost = envUrl.hostname === 'localhost' || envUrl.hostname === '127.0.0.1';
    if (!(envIsLocalhost && !isLocalHost)) {
      return env;
    }
  }

  // 5) Fallback dinâmico: mesmo hostname do front + porta 8080
  return `${window.location.protocol}//${host}:8080`;
})();

export const apiConfig = {
  // URL base do ERP
  erpBaseUrl: ERP_API_URL,

  // Endpoints do ERP
  erp: {
    cardapio: `${ERP_API_URL}/api/public/cardapio`,
    destaques: `${ERP_API_URL}/api/public/cardapio/destaques`,
    deliveryCardapio: `${ERP_API_URL}/api/public/cardapio/delivery`,
    deliveryOrders: `${ERP_API_URL}/api/delivery/orders`,
  },

  // Função auxiliar para construir URL completa de mídia
  getMediaUrl: (path: string | null): string | undefined => {
    if (!path) return undefined;
    // Se o path já é uma URL completa, retorna como está
    if (path.startsWith('http://') || path.startsWith('https://')) {
      return path;
    }
    // Caso contrário, concatena com a URL base do ERP
    return `${ERP_API_URL}${path}`;
  },
};
