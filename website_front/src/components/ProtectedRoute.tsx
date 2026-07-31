import { Navigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { getRedirectPathByRoles, hasRequiredRole } from '@/utils/roleRouting';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: string | string[]; // Role específica necessária (CLIENTE, ADMIN, etc)
}

export default function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const { isAuthenticated, user } = useAuth();

  // Verifica se está autenticado
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const hasAccess = hasRequiredRole(user?.roles, requiredRole);

  // Se uma role específica for necessária, verifica se o usuário tem
  if (requiredRole && !hasAccess) {
    // Se for admin tentando acessar área do cliente, redireciona para admin
    const fallback = getRedirectPathByRoles(user?.roles);
    return <Navigate to={fallback} replace />;
  }

  return <>{children}</>;
}
