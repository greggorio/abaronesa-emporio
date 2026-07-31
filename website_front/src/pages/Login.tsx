import { useState, useEffect } from 'react';
import { Eye, EyeOff, Loader2, User, Mail, Lock, Check } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import { useAuth } from '@/hooks/useAuth';
import { apiConfig } from '@/config/api';
import { useTheme } from '@/contexts/ThemeContext';
import { getRedirectPathByRoles } from '@/utils/roleRouting';

interface ApiError {
  response?: {
    data?: {
      message?: string;
    };
    status?: number;
  };
  message?: string;
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [emailNotVerified, setEmailNotVerified] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const { login, isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme } = useTheme();

  // Define theme-based CSS classes using actual tokens from the theme JSON
  const backgroundColor = theme?.tokens?.background ? 'bg-[hsl(var(--background))]' : '';
  const surfaceColor = theme?.tokens?.card ? 'bg-[hsl(var(--card))]' : '';
  const accentColor = theme?.tokens?.accent ? 'text-[hsl(var(--accent))]' : '';
  const accentBgColor = theme?.tokens?.accent ? 'bg-[hsl(var(--accent))]' : '';
  const accentBgColorLight = theme?.tokens?.accent ? 'bg-[hsl(var(--accent))/0.1]' : '';
  const accentHoverColor = theme?.tokens?.accent ? 'hover:bg-[hsl(var(--accent))/0.9]' : '';
  const accentBorder = theme?.tokens?.accent ? 'border-[hsl(var(--accent))/0.2]' : '';
  const accentBorderMedium = theme?.tokens?.accent ? 'border-[hsl(var(--accent))/0.3]' : '';
  const accentBorderStrong = theme?.tokens?.accent ? 'border-[hsl(var(--accent))/0.4]' : '';
  const foregroundColor = theme?.tokens?.foreground ? 'text-[hsl(var(--foreground))]' : '';
  const textColor = theme?.tokens?.foreground ? 'text-[hsl(var(--foreground))]' : '';
  const textSecondaryColor = theme?.tokens?.['muted-foreground'] ? 'text-[hsl(var(--muted-foreground))]' : '';
  const textSecondaryColorLight = theme?.tokens?.['muted-foreground'] ? 'text-[hsl(var(--muted-foreground))]/60' : '';
  const borderColor = theme?.tokens?.border ? 'border-[hsl(var(--border))]' : '';
  const inputBgColor = theme?.tokens?.input ? 'bg-[hsl(var(--input))]' : '';
  const destructiveColor = theme?.tokens?.destructive ? 'text-[hsl(var(--destructive))]' : '';
  const destructiveBgColor = theme?.tokens?.destructive ? 'bg-[hsl(var(--destructive))/0.05]' : '';
  const primaryColor = theme?.tokens?.primary ? 'text-[hsl(var(--primary))]' : '';
  const primaryBgColor = theme?.tokens?.primary ? 'bg-[hsl(var(--primary))]' : '';
  const secondaryColor = theme?.tokens?.secondary ? 'text-[hsl(var(--secondary))]' : '';
  const secondaryBgColor = theme?.tokens?.secondary ? 'bg-[hsl(var(--secondary))]' : '';
  const cardForegroundColor = theme?.tokens?.['card-foreground'] ? 'text-[hsl(var(--card-foreground))]' : '';

  // Util para validar redirects seguros (evitar open redirect)
  const isSafeRedirect = (path?: string | null) => !!path && path.startsWith('/m/');

  // Redireciona usuário já logado baseado na role
  useEffect(() => {
    if (isAuthenticated && user) {
      const redirect = searchParams.get('redirect');
      if (isSafeRedirect(redirect)) {
        navigate(redirect!, { replace: true });
        return;
      }

      const fallback = getRedirectPathByRoles(user.roles);
      navigate(fallback, { replace: true });
    }
  }, [isAuthenticated, user, navigate, searchParams]);

  const loginBenefits = [
    'Acompanhe suas reservas e pedidos',
    'Histórico de visitas e consumo',
    'Ofertas e promoções exclusivas',
    'Programa de fidelidade Empório A Baronesa',
  ];

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setEmailNotVerified(false);
    setIsLoading(true);

    try {
      await login(email, password);

      // Redirecionamento será feito pelo useEffect
    } catch (err) {
      console.error('Erro no login:', err);

      const error = err as ApiError;
      if (error.response?.status === 403) {
        setEmailNotVerified(true);
        setError('Sua conta ainda não foi ativada. Verifique seu email e clique no link de ativação.');
      } else {
        setError('Erro ao fazer login. Verifique suas credenciais.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = () => {
    // Sinaliza ao ERP que a origem é o Villa
    document.cookie = "oauth_origin=villa; path=/; max-age=300";
    // Se houver redirect seguro, persistir para restaurar no handler
    const redirect = searchParams.get('redirect');
    if (isSafeRedirect(redirect)) {
      try { localStorage.setItem('post_login_redirect', redirect!); } catch {}
    }
    window.location.href = `${apiConfig.erpBaseUrl}/api/auth/oauth2/google/ecommerce`;
  };

  const handleResendVerification = async () => {
    setError('');
    try {
      const response = await fetch(`${apiConfig.erpBaseUrl}/api/auth/resend-verification`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email })
      });

      const data = await response.json();

      if (data.success) {
        setError('Email de verificação reenviado! Verifique sua caixa de entrada.');
        setEmailNotVerified(false);
      } else {
        setError(data.message || 'Erro ao reenviar email');
      }
    } catch (err) {
      setError('Erro ao reenviar email de verificação');
    }
  };

  return (
    <div className={`min-h-screen ${backgroundColor || 'bg-soft-white'} flex flex-col`}>
      <Header />

      <div className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-4xl mx-auto">
          <div className={`${surfaceColor || 'bg-white'} rounded-2xl shadow-xl overflow-hidden flex border ${accentBorder || 'border-coral-accent/20'}`}>
            {/* Left Side - Benefits */}
            <div className={`hidden lg:flex lg:w-1/2 ${accentBgColorLight || 'bg-coral-accent/10'} p-12 flex-col justify-center border-r ${accentBorder || 'border-coral-accent/20'}`}>
              <div className="max-w-md">
                <h1 className={`text-4xl font-display ${foregroundColor || 'text-forest-dark'} mb-6`}>
                  Acesse sua
                  <span className={`block ${accentColor || 'text-coral-accent'}`}>conta</span>
                </h1>
                <p className={`${textSecondaryColor || 'text-forest-green/70'} text-base mb-8`}>
                  Entre na sua conta para acompanhar suas reservas e aproveitar benefícios exclusivos.
                </p>

                <div className="space-y-4">
                  {loginBenefits.map((benefit, index) => (
                    <div key={index} className="flex items-center gap-3">
                      <Check className={`w-5 h-5 ${accentColor || 'text-coral-accent'}`} />
                      <span className={`${textSecondaryColor || 'text-forest-green/80'} text-sm`}>{benefit}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Right Side - Login Form */}
            <div className={`flex-1 flex items-center justify-center p-8 lg:p-12 ${surfaceColor || 'bg-white'}`}>
              <div className="w-full max-w-sm">
                <div className="text-center mb-8">
                  <div className={`w-20 h-20 ${accentBgColor}/20 rounded-full flex items-center justify-center mx-auto mb-6 border-2 ${accentBorderStrong || 'border-coral-accent/40'}`}>
                    <User className={`w-10 h-10 ${accentColor || 'text-coral-accent'}`} />
                  </div>
                  <h2 className={`text-2xl font-display ${foregroundColor || 'text-forest-dark'} mb-2`}>Entrar na Conta</h2>
                  <p className={textSecondaryColorLight || 'text-forest-green/60'}>Use suas credenciais para acessar</p>
                </div>

                {/* Error Alert */}
                {error && (
                  <div className={`mb-6 p-4 rounded-lg flex items-start gap-3 ${
                    emailNotVerified ? 'bg-amber-50 border border-amber-200' : 'bg-red-50 border border-red-200'
                  }`}>
                    <div className="mt-0.5">
                      <svg className="w-5 h-5 text-amber-600" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <div className="flex-1">
                      <p className="text-sm text-gray-800">{error}</p>
                      {emailNotVerified && (
                        <button
                          type="button"
                          className={`mt-2 text-sm ${accentColor || 'text-coral-accent'} hover:underline font-medium`}
                          onClick={handleResendVerification}
                        >
                          Clique aqui para reenviar o email de verificação
                        </button>
                      )}
                    </div>
                  </div>
                )}

                <form onSubmit={handleSubmit} className="space-y-6">
                  {/* Email Field */}
                  <div>
                    <div className="relative">
                      <Mail className={`absolute left-4 top-1/2 transform -translate-y-1/2 w-5 h-5 ${textSecondaryColor || 'text-forest-green/40'}`} />
                      <input
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        className={`w-full pl-12 pr-4 py-3 ${inputBgColor || 'bg-soft-white'} border ${accentBorderMedium || 'border-coral-accent/30'} rounded-lg focus:ring-2 focus:ring-coral-accent focus:border-transparent outline-none transition ${foregroundColor || 'text-forest-dark'} ${textSecondaryColor ? `placeholder:text-[hsl(var(--muted-foreground))/40]` : 'placeholder:text-forest-green/40'}`}
                        placeholder="E-mail *"
                        required
                      />
                    </div>
                  </div>

                  {/* Password Field */}
                  <div>
                    <div className="relative">
                      <Lock className={`absolute left-4 top-1/2 transform -translate-y-1/2 w-5 h-5 ${textSecondaryColor || 'text-forest-green/40'}`} />
                      <input
                        type={showPassword ? 'text' : 'password'}
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        className={`w-full pl-12 pr-12 py-3 ${inputBgColor || 'bg-soft-white'} border ${accentBorderMedium || 'border-coral-accent/30'} rounded-lg focus:ring-2 focus:ring-coral-accent focus:border-transparent outline-none transition ${foregroundColor || 'text-forest-dark'} ${textSecondaryColor ? `placeholder:text-[hsl(var(--muted-foreground))/40]` : 'placeholder:text-forest-green/40'}`}
                        placeholder="Senha *"
                        required
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className={`absolute right-4 top-1/2 transform -translate-y-1/2 ${textSecondaryColor || 'text-forest-green/40'} hover:${textSecondaryColor || 'text-forest-green/60'}`}
                      >
                        {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                      </button>
                    </div>
                  </div>

                  {/* Remember Me & Forgot Password */}
                  <div className="flex items-center justify-between">
                    <label className="flex items-center cursor-pointer">
                      <input
                        type="checkbox"
                        checked={rememberMe}
                        onChange={(e) => setRememberMe(e.target.checked)}
                        className={`w-4 h-4 ${accentColor || 'text-coral-accent'} border ${accentBorderMedium || 'border-coral-accent/40'} rounded focus:ring-coral-accent ${inputBgColor || 'bg-soft-white'}`}
                      />
                      <span className={`ml-2 text-sm ${textSecondaryColor || 'text-forest-green/70'}`}>Lembrar de mim</span>
                    </label>
                    <a
                      href="/esqueci-senha"
                      className={`text-sm ${accentColor || 'text-coral-accent'} hover:underline`}
                    >
                      Esqueci minha senha
                    </a>
                  </div>

                  {/* Submit Button */}
                  <button
                    type="submit"
                    disabled={isLoading}
                    className={`w-full ${accentBgColor || 'bg-coral-accent'} ${foregroundColor || 'text-forest-dark'} py-3 px-4 rounded-lg font-display text-xl ${accentHoverColor || 'hover:bg-coral-accent/90'} transition duration-200 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2`}
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="w-5 h-5 animate-spin" />
                        Entrando...
                      </>
                    ) : (
                      <>
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
                        </svg>
                        Entrar
                      </>
                    )}
                  </button>

                  {/* Divider */}
                  <div className="relative my-6">
                    <div className="absolute inset-0 flex items-center">
                      <div className={`w-full border-t ${accentBorder || 'border-coral-accent/20'}`}></div>
                    </div>
                    <div className="relative flex justify-center text-sm">
                      <span className={`px-4 ${surfaceColor || 'bg-white'} ${textSecondaryColorLight || 'text-forest-green/60'}`}>ou</span>
                    </div>
                  </div>

                  {/* Google Login */}
                  <button
                    type="button"
                    onClick={handleGoogleLogin}
                    className={`w-full border ${accentBorderStrong || 'border-coral-accent/40'} ${foregroundColor || 'text-forest-dark'} py-3 px-4 rounded-lg font-medium ${accentBgColorLight || 'hover:bg-coral-accent/10'} transition duration-200 flex items-center justify-center gap-2`}
                  >
                    <img
                      src="https://www.google.com/favicon.ico"
                      alt="Google"
                      className="w-5 h-5"
                    />
                    Continuar com Google
                  </button>

                  {/* Sign Up Link */}
                  <p className={`text-center text-sm ${textSecondaryColorLight || 'text-forest-green/60'} mt-6`}>
                    Não tem uma conta?
                    <a href="/register" className={`text-sm ${accentColor || 'text-coral-accent'} hover:underline font-medium ml-1`}>
                      Criar conta
                    </a>
                  </p>
                </form>
              </div>
            </div>
          </div>
        </div>
      </div>

      <Footer />
    </div>
  );
}
