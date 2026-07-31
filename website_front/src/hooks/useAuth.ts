import { create } from 'zustand';
import api from '@/lib/axios';
import { notificationService } from '@/services/notificationService';

// Interface para dados do usuário
interface User {
  id: string;
  nome: string;
  email: string;
  fotoPerfil?: string;
  roles?: string[];
}

// Interface para resposta do login
interface LoginResponse {
  accessToken: string;
  refreshToken?: string;
  id: string;
  nome: string;
  email: string;
  fotoPerfil?: string;
  roles?: string[];
}

// Interface para resposta do /me
interface MeResponse {
  id: string;
  nome: string;
  email: string;
  fotoPerfil?: string;
  roles?: string[];
  grupoId?: number;
  grupoNome?: string;
  emailVerificado?: boolean;
  origemCadastro?: string;
  perfilCompleto?: boolean;
}

// Interface para o estado de autenticação
interface AuthState {
  // Estado
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isHydrated: boolean;

  // Ações
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  checkAuth: () => void;
  setLoading: (loading: boolean) => void;
  setAuth: (auth: Partial<AuthState>) => void;

  // Métodos auxiliares para roles
  hasRole: (role: string) => boolean;
  isAdmin: () => boolean;
  isFuncionario: () => boolean;
  isCliente: () => boolean;
  isKds: () => boolean;
  isWaiter: () => boolean;
  isCaixa: () => boolean;
  isSystem: () => boolean;
}

/**
 * Função para recuperar dados do usuário do localStorage
 */
const getInitialUser = (): User | null => {
  const token = localStorage.getItem('auth_token');
  const userId = localStorage.getItem('user_id');
  const userRoles = localStorage.getItem('user_roles');
  const userNome = localStorage.getItem('user_nome');
  const userEmail = localStorage.getItem('user_email');
  const userFoto = localStorage.getItem('user_foto');

  // Se não tem token ou user_id, não tem usuário
  if (!token || !userId) return null;

  // Recuperar dados completos do usuário
  return {
    id: userId,
    nome: userNome || '',
    email: userEmail || '',
    fotoPerfil: userFoto || undefined,
    roles: userRoles ? JSON.parse(userRoles) : []
  };
};

/**
 * Hook de autenticação usando Zustand
 */
export const useAuth = create<AuthState>((set, get) => {
  const initialUser = getInitialUser();
  const hasValidSession = !!localStorage.getItem('auth_token') && !!localStorage.getItem('user_id') && initialUser !== null;

  return {
    // Estado inicial - recupera TODOS os dados salvos do localStorage
    user: initialUser,
    token: localStorage.getItem('auth_token'),
    isAuthenticated: hasValidSession,
    isLoading: false,
    isHydrated: hasValidSession,

    // Ação de login
    login: async (email: string, password: string) => {
      set({ isLoading: true });

      try {
        // Faz a requisição de login
        const response = await api.post<LoginResponse>('/api/auth/login', {
          email,
          password
        });

        // Backend retorna apenas tokens; precisamos buscar /auth/me para preencher o usuário
        const accessToken = response.data.accessToken;

        // Persistir token para que /auth/me funcione
        localStorage.setItem('auth_token', accessToken);

        // Buscar dados do usuário autenticado
        const me = await api.get<MeResponse>('/api/auth/me');
        const userData = me.data;

        // Persistir dados
        localStorage.setItem('user_id', userData.id);
        localStorage.setItem('user_nome', userData.nome);
        localStorage.setItem('user_email', userData.email);
        localStorage.setItem('user_foto', userData.fotoPerfil || '');
        if (userData.roles) {
          localStorage.setItem('user_roles', JSON.stringify(userData.roles));
        }

        // Atualizar estado global
        set({
          user: {
            id: userData.id,
            nome: userData.nome,
            email: userData.email,
            fotoPerfil: userData.fotoPerfil,
            roles: userData.roles
          },
          token: accessToken,
          isAuthenticated: true,
          isLoading: false,
          isHydrated: true
        });

        // Após login, re-associa o token FCM ao usuário autenticado
        notificationService.subscribeStoredToken().catch((err) => {
          console.error('[Auth] Falha ao re-subscrever token de notificações:', err);
        });

        console.log('[Auth] Login realizado com sucesso');
        console.log('[Auth] Roles do usuário:', userData.roles);
      } catch (error) {
        console.error('[Auth] Erro no login:', error);

        // Limpa o estado em caso de erro
        set({
          user: null,
          token: null,
          isAuthenticated: false,
          isLoading: false
        });

        // Re-throw o erro para ser tratado no componente
        throw error;
      }
    },

    // Ação de logout
    logout: () => {
      // Limpa TODOS os dados do localStorage
      localStorage.removeItem('auth_token');
      localStorage.removeItem('user_id');
      localStorage.removeItem('user_nome');
      localStorage.removeItem('user_email');
      localStorage.removeItem('user_foto');
      localStorage.removeItem('user_roles');

      // Limpa o estado global
      set({
        user: null,
        token: null,
        isAuthenticated: false,
        isHydrated: true
      });

      console.log('[Auth] Logout realizado');

      // Redirecionar para home
      window.location.href = '/';
    },

    // Verifica se usuário está autenticado
    checkAuth: async () => {
      set({ isHydrated: false });

      const token = localStorage.getItem('auth_token');
      const userId = localStorage.getItem('user_id');

      if (token && userId) {
        try {
          // Buscar dados atuais do usuário
          const response = await api.get<MeResponse>('/api/auth/me');
          const userData = response.data;

          // Atualizar TODOS os dados no localStorage
          localStorage.setItem('user_nome', userData.nome);
          localStorage.setItem('user_email', userData.email);
          localStorage.setItem('user_foto', userData.fotoPerfil || '');
          if (userData.roles) {
            localStorage.setItem('user_roles', JSON.stringify(userData.roles));
          }

          set({
            isAuthenticated: true,
            token,
            user: {
              id: userData.id,
              nome: userData.nome,
              email: userData.email,
              fotoPerfil: userData.fotoPerfil,
              roles: userData.roles
            },
            isHydrated: true
          });

          console.log('[Auth] Usuário autenticado');
          console.log('[Auth] Roles:', userData.roles);
        } catch (error) {
          // Se falhar, limpar autenticação
          console.error('[Auth] Erro ao verificar autenticação:', error);
          localStorage.removeItem('auth_token');
          localStorage.removeItem('user_id');
          localStorage.removeItem('user_nome');
          localStorage.removeItem('user_email');
          localStorage.removeItem('user_foto');
          localStorage.removeItem('user_roles');

          set({
            isAuthenticated: false,
            token: null,
            user: null,
            isHydrated: true
          });
        }
      } else {
        set({
          isAuthenticated: false,
          token: null,
          user: null,
          isHydrated: true
        });

        console.log('[Auth] Usuário não autenticado');
      }
    },

    // Ação auxiliar para controlar loading
    setLoading: (loading: boolean) => set({ isLoading: loading }),

    // Ação para definir autenticação (usado pelo OAuth2)
    setAuth: (auth: Partial<AuthState>) => set(auth),

    // Métodos para verificação de roles
    hasRole: (role: string) => {
      const state = get();
      return state.user?.roles?.includes(role) || false;
    },

    isAdmin: () => {
      const state = get();
      return state.user?.roles?.includes('ADMIN') || false;
    },

    isFuncionario: () => {
      const state = get();
      return state.user?.roles?.includes('FUNCIONARIO') || false;
    },

    isCliente: () => {
      const state = get();
      return state.user?.roles?.includes('CLIENTE') || false;
    },

    isKds: () => {
      const state = get();
      return state.user?.roles?.includes('KDS') || false;
    },

    isWaiter: () => {
      const state = get();
      return state.user?.roles?.includes('WAITER') || false;
    },

    isCaixa: () => {
      const state = get();
      return state.user?.roles?.includes('CAIXA') || false;
    },

    isSystem: () => {
      const state = get();
      return state.user?.roles?.includes('SYSTEM') || false;
    }
  };
});

/**
 * Hook auxiliar para verificar autenticação ao montar componente
 */
export const useAuthCheck = () => {
  const checkAuth = useAuth((state) => state.checkAuth);

  // Verifica autenticação quando o hook é usado
  if (typeof window !== 'undefined') {
    checkAuth();
  }
};
