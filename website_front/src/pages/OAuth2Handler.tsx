import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import api from '@/lib/axios';
import { useTheme } from '@/contexts/ThemeContext';
import { notificationService } from '@/services/notificationService';
import { getRedirectPathByRoles } from '@/utils/roleRouting';

interface UserData {
  id: string;
  nome: string;
  email: string;
  fotoPerfil?: string;
  roles?: string[];
}

export default function OAuth2Handler() {
  console.log('🔍 OAuth2Handler montado!');

  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setAuth = useAuth((state) => state.setAuth);
  const { theme } = useTheme();

  // Define theme-based CSS classes using actual tokens from the theme JSON
  const backgroundColor = theme?.tokens?.background ? 'bg-[hsl(var(--background))]' : '';
  const surfaceColor = theme?.tokens?.card ? 'bg-[hsl(var(--card))]' : '';
  const accentColor = theme?.tokens?.accent ? 'text-[hsl(var(--accent))]' : '';
  const accentBgColor = theme?.tokens?.accent ? 'bg-[hsl(var(--accent))]' : '';
  const accentHoverColor = theme?.tokens?.accent ? 'hover:bg-[hsl(var(--accent)/0.9)]' : '';
  const foregroundColor = theme?.tokens?.['primary-foreground'] ? 'text-[hsl(var(--primary-foreground))]' : '';
  const textColor = theme?.tokens?.foreground ? 'text-[hsl(var(--foreground))]' : '';
  const textSecondaryColor = theme?.tokens?.['muted-foreground'] ? 'text-[hsl(var(--muted-foreground))]' : '';
  const textTertiaryColor = theme?.tokens?.['muted-foreground'] ? 'text-[hsl(var(--muted-foreground))]/50' : '';
  const borderColor = theme?.tokens?.border ? 'border-[hsl(var(--border))]/20' : '';
  const successColor = theme?.tokens?.['primary-foreground'] ? 'text-[hsl(var(--primary-foreground))]' : '';
  const successBgColor = theme?.tokens?.primary ? 'bg-[hsl(var(--primary))/20]' : '';
  const errorColor = theme?.tokens?.destructive ? 'text-[hsl(var(--destructive))]' : '';
  const errorBgColor = theme?.tokens?.destructive ? 'bg-[hsl(var(--destructive))/20]' : '';

  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [message, setMessage] = useState('Processando autenticação...');
  const [isProcessing, setIsProcessing] = useState(false);

  // Util para validar redirects seguros (evitar open redirect)
  const isSafeRedirect = (path?: string | null) => !!path && path.startsWith('/m/');

  useEffect(() => {
    // Evitar processamento duplo
    if (isProcessing) return;

    const processOAuth2Token = async () => {
      setIsProcessing(true);

      try {
        // Pegar o token da URL
        const token = searchParams.get('token');
        console.log('🔍 Token encontrado:', token ? 'Sim' : 'Não');

        if (!token) {
          throw new Error('Token não encontrado');
        }

        // Salvar o token
        localStorage.setItem('auth_token', token);

        // Buscar dados do usuário
        console.log('🔍 Buscando dados do usuário...');
        const response = await api.get<UserData>('/api/auth/me');
        const userData = response.data;
        console.log('✅ Usuário autenticado:', userData.nome);
        console.log('📋 Roles do usuário:', userData.roles);

        // Salvar TODOS os dados do usuário no localStorage
        localStorage.setItem('user_id', userData.id);
        localStorage.setItem('user_nome', userData.nome);
        localStorage.setItem('user_email', userData.email);
        localStorage.setItem('user_foto', userData.fotoPerfil || '');
        if (userData.roles) {
          localStorage.setItem('user_roles', JSON.stringify(userData.roles));
        }

        // Atualizar o estado global
        setAuth({
          user: {
            id: userData.id,
            nome: userData.nome,
            email: userData.email,
            fotoPerfil: userData.fotoPerfil,
            roles: userData.roles
          },
          token,
          isAuthenticated: true,
          isLoading: false,
          isHydrated: true
        });

        setStatus('success');
        setMessage('Login realizado com sucesso!');

        // Após login bem-sucedido, reenviar o token FCM armazenado para vincular ao usuário
        try {
          await notificationService.subscribeStoredToken();
        } catch (error) {
          console.error('Erro ao re-inscrever token FCM após login:', error);
        }

        // Redirecionar após 1.5s: prioriza destino salvo (início em /m/:mesa)
        setTimeout(() => {
          // 1) Tentar redirect guardado no localStorage
          const savedRedirect = (() => {
            try { return localStorage.getItem('post_login_redirect'); } catch { return null; }
          })();
          if (isSafeRedirect(savedRedirect)) {
            try { localStorage.removeItem('post_login_redirect'); } catch {}
            navigate(savedRedirect!, { replace: true });
            return;
          }

          // 2) (Opcional) aceitar ?redirect da URL se presente e seguro
          const qpRedirect = searchParams.get('redirect');
          if (isSafeRedirect(qpRedirect)) {
            navigate(qpRedirect!, { replace: true });
            return;
          }

          // 3) Fallback por role (comportamento atual)
          const fallback = getRedirectPathByRoles(userData.roles);
          navigate(fallback, { replace: true });
        }, 1500);

      } catch (error) {
        console.error('❌ Erro ao processar OAuth2:', error);
        setStatus('error');

        const err = error as Error;
        setMessage(err.message || 'Erro ao processar autenticação');

        // Limpar TODOS os dados em caso de erro
        localStorage.removeItem('auth_token');
        localStorage.removeItem('user_id');
        localStorage.removeItem('user_nome');
        localStorage.removeItem('user_email');
        localStorage.removeItem('user_foto');
        localStorage.removeItem('user_roles');
        try { localStorage.removeItem('post_login_redirect'); } catch {}

        // Redirecionar para home após 3 segundos
        setTimeout(() => {
          navigate('/');
        }, 3000);
      }
    };

    processOAuth2Token();
  }, []);

  return (
    <div className={`min-h-screen ${backgroundColor} flex items-center justify-center p-4`}>
      <div className={`${surfaceColor} border ${borderColor} rounded-lg p-8 max-w-md w-full text-center`}>
        {status === 'processing' && (
          <>
            <Loader2 className={`w-12 h-12 ${accentColor} animate-spin mx-auto mb-4`} />
            <h2 className={`text-2xl font-display ${accentColor} mb-2`}>Processando...</h2>
            <p className={textSecondaryColor}>{message}</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div className={`w-12 h-12 ${successBgColor} rounded-full flex items-center justify-center mx-auto mb-4`}>
              <svg className={`w-6 h-6 ${successColor}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <h2 className={`text-2xl font-display ${successColor} mb-2`}>Sucesso!</h2>
            <p className={textSecondaryColor}>{message}</p>
            <p className={`text-sm ${textTertiaryColor} mt-2`}>Redirecionando...</p>
          </>
        )}

        {status === 'error' && (
          <>
            <div className={`w-12 h-12 ${errorBgColor} rounded-full flex items-center justify-center mx-auto mb-4`}>
              <svg className={`w-6 h-6 ${errorColor}`} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h2 className={`text-2xl font-display ${errorColor} mb-2`}>Erro</h2>
            <p className={textSecondaryColor}>{message}</p>
            <button
              onClick={() => navigate('/')}
              className={`mt-4 px-4 py-2 ${accentBgColor} ${foregroundColor} rounded font-display ${accentHoverColor} transition`}
            >
              Voltar para Home
            </button>
          </>
        )}
      </div>
    </div>
  );
}
