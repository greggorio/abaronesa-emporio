import { useMemo } from 'react';
import { useIsMobile } from '@/hooks/use-mobile';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Menu,
  User,
  Settings,
  LogOut,
  ChevronDown,
  Home,
  ChevronRight
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuth } from '@/hooks/useAuth';

interface AdminHeaderProps {
  onToggleSidebar: () => void;
  sidebarOpen: boolean;
  sidebarCollapsed: boolean;
}

export default function AdminHeader({ onToggleSidebar, sidebarOpen, sidebarCollapsed }: AdminHeaderProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();
  const isMobile = useIsMobile();

  const { currentPage, subTitle } = useMemo(() => {
    const path = location.pathname;
    // Pagamentos da mesa: título apenas no Header (mesa exibida no sidebar da página)
    if (/^\/admin\/mesas\/(\d+)\/pagamentos$/.test(path)) {
      return { currentPage: 'Gerenciar Pagamentos', subTitle: undefined };
    }
    // Mesas abertas
    if (path.startsWith('/admin/mesas')) {
      return { currentPage: 'Mesas', subTitle: undefined };
    }
    if (path === '/admin' || path === '/admin/dashboard') return { currentPage: 'Dashboard', subTitle: undefined };
    if (path.startsWith('/admin/eventos')) return { currentPage: 'Eventos', subTitle: undefined };
    if (path.startsWith('/admin/relatorios/vendas')) return { currentPage: 'Relatório de Vendas', subTitle: undefined };
    if (path.startsWith('/admin/relatorios/pedidos')) return { currentPage: 'Relatório de Pedidos', subTitle: undefined };
    if (path.startsWith('/admin/reservas')) return { currentPage: 'Reservas', subTitle: undefined };
    if (path.startsWith('/admin/quiz')) return { currentPage: 'Quiz', subTitle: undefined };
    if (path.startsWith('/admin/cardapio')) return { currentPage: 'Cardápio', subTitle: undefined };
    if (path.startsWith('/admin/settings')) return { currentPage: 'Configurações', subTitle: undefined };
    return { currentPage: 'Área Administrativa', subTitle: undefined };
  }, [location.pathname, (location as any).state]);

  const handleGoHome = () => {
    navigate('/');
  };

  // On mobile the sidebar overlays the content, so header should span full width
  const sidebarWidth = isMobile ? 0 : (sidebarOpen ? (sidebarCollapsed ? 64 : 250) : 0);

  return (
    <header className="admin-header" style={{ left: sidebarWidth, width: `calc(100% - ${sidebarWidth}px)` }}>
      {/* Menu Toggle */}
      <Button
        variant="ghost"
        size="sm"
        onClick={onToggleSidebar}
        className="menu-toggle"
      >
        <Menu size={20} />
      </Button>

      <div className="header-content">
        {/* Left Side - Breadcrumbs */}
        <div className="header-left">
          <nav className="breadcrumbs">
            <span
              className="breadcrumb-item clickable"
              onClick={handleGoHome}
            >
              <Home size={14} />
              Home
            </span>
            <ChevronRight size={14} className="breadcrumb-separator" />
            <span className="breadcrumb-item current">{currentPage}</span>
          </nav>

          <div className="page-title">
            <h1>{currentPage}</h1>
            {subTitle && <div className="page-subtitle">{subTitle}</div>}
          </div>
        </div>

        {/* Right Side - User Controls */}
        <div className="header-right">
          {/* User Menu */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="user-menu-trigger">
                <div className="user-avatar">
                  {user?.fotoPerfil ? (
                    <img src={user.fotoPerfil} alt={user.nome} />
                  ) : (
                    <div className="avatar-fallback">{user?.nome?.charAt(0) || 'U'}</div>
                  )}
                </div>
                <div className="user-info">
                  <span className="user-name">{user?.nome || 'Usuário'}</span>
                  <span className="user-role">Administrador</span>
                </div>
                <ChevronDown size={16} />
              </Button>
            </DropdownMenuTrigger>

            <DropdownMenuContent align="end" className="user-menu-content">
              <div className="user-menu-header">
                <div className="user-avatar-large">
                  {user?.fotoPerfil ? (
                    <img src={user.fotoPerfil} alt={user.nome} />
                  ) : (
                    <div className="avatar-fallback-large">{user?.nome?.charAt(0) || 'U'}</div>
                  )}
                </div>
                <div className="user-details">
                  <div className="user-name-large">{user?.nome || 'Usuário'}</div>
                  <div className="user-email">{user?.email || ''}</div>
                </div>
              </div>

              <DropdownMenuSeparator />

              <DropdownMenuItem>
                <User size={16} />
                Meu Perfil
              </DropdownMenuItem>

              <DropdownMenuItem>
                <Settings size={16} />
                Configurações
              </DropdownMenuItem>

              <DropdownMenuSeparator />

              <DropdownMenuItem onClick={logout} className="logout-item">
                <LogOut size={16} />
                Sair
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <style>{`
        .admin-header {
          background-color: white;
          border-bottom: 1px solid #D7B899; /* cafe-latte-suave */
          padding: 0 16px;
          height: var(--admin-header-h);
          display: flex;
          align-items: center;
          position: fixed;
          top: 0;
          z-index: 200;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
          transition: left 0.3s ease, width 0.3s ease;
        }

        .menu-toggle {
          color: #2A1F1B; /* cafe-dark-roast */
          margin-right: 16px;
        }

        .menu-toggle:hover {
          color: #6B3E26; /* cafe-espresso */
          background-color: #D7B899; /* cafe-latte-suave */
        }

        .header-content {
          display: flex;
          align-items: center;
          justify-content: space-between;
          width: 100%;
        }

        .header-left {
          flex: 1;
          min-width: 0; /* allow flex children to shrink for ellipsis */
        }

        .breadcrumbs {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 12px;
          color: #8B7355; /* cafe-com-leite */
          margin-bottom: 8px;
          white-space: nowrap;
          overflow: hidden;
        }

        .breadcrumb-item {
          display: flex;
          align-items: center;
          gap: 4px;
        }

        .breadcrumb-item.clickable {
          cursor: pointer;
          color: #6B3E26; /* cafe-espresso */
        }

        .breadcrumb-item.clickable:hover {
          text-decoration: underline;
        }

        .breadcrumb-item.current {
          color: #2A1F1B; /* cafe-dark-roast */
          flex: 1;
          min-width: 0;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .breadcrumb-separator {
          color: #8B7355; /* cafe-com-leite */
        }

        .page-title h1 {
          font-size: 24px;
          font-weight: 700;
          color: #6B3E26; /* cafe-espresso */
          margin: 0;
          font-family: var(--font-display);
          letter-spacing: 0.1em;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .page-subtitle {
          font-size: 13px;
          color: #8B7355; /* cafe-com-leite */
          margin-top: 2px;
        }

        .header-right {
          display: flex;
          align-items: center;
          gap: 12px;
          background-color: #FBF6F2; /* cafe-latte-claro */
          border-radius: 50px;
          padding: 8px 16px;
          border: 1px solid #D7B899; /* cafe-latte-suave */
        }

        .user-menu-trigger {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 4px 8px;
          color: #2A1F1B; /* cafe-dark-roast */
          border: none;
          background: transparent;
        }

        .user-menu-trigger:hover {
          background-color: #D7B899; /* cafe-latte-suave */
        }

        .user-avatar {
          width: 35px;
          height: 35px;
          border-radius: 50%;
          overflow: hidden;
          border: 2px solid #6B3E26; /* cafe-espresso */
        }

        .user-avatar img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .avatar-fallback {
          width: 100%;
          height: 100%;
          background-color: #6B3E26; /* cafe-espresso */
          color: white;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 600;
          font-size: 14px;
        }

        .user-info {
          display: flex;
          flex-direction: column;
          align-items: flex-start;
          text-align: left;
        }

        .user-name {
          font-weight: 500;
          font-size: 14px;
          line-height: 1.2;
          color: #2A1F1B; /* cafe-dark-roast */
        }

        .user-role {
          font-size: 11px;
          color: #8B7355; /* cafe-com-leite */
          line-height: 1;
        }

        .user-menu-content {
          width: 300px;
          padding: 16px;
          background-color: white;
          border: 1px solid #D7B899; /* cafe-latte-suave */
          color: #2A1F1B; /* cafe-dark-roast */
        }

        .user-menu-header {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 16px;
        }

        .user-avatar-large {
          width: 48px;
          height: 48px;
          border-radius: 50%;
          overflow: hidden;
          border: 2px solid #6B3E26; /* cafe-espresso */
        }

        .user-avatar-large img {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .avatar-fallback-large {
          width: 100%;
          height: 100%;
          background-color: #6B3E26; /* cafe-espresso */
          color: white;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: 600;
          font-size: 18px;
        }

        .user-details {
          flex: 1;
        }

        .user-name-large {
          font-weight: 600;
          font-size: 16px;
          color: #2A1F1B; /* cafe-dark-roast */
        }

        .user-email {
          font-size: 14px;
          color: #8B7355; /* cafe-com-leite */
        }

        .logout-item {
          color: #D65A31; /* negative color from bakery palette */
        }

        .logout-item:hover {
          background-color: #D65A31; /* negative color from bakery palette */
          color: white;
        }

        @media (max-width: 768px) {
          .admin-header {
            padding: 0 12px;
          }

          .user-info {
            display: none;
          }

          .header-right {
            gap: 8px;
            padding: 6px 12px;
          }

          .menu-toggle { margin-right: 8px; }

          .breadcrumbs { gap: 6px; margin-bottom: 6px; }

          .page-title h1 { font-size: 18px; letter-spacing: 0.06em; }
        }
      `}</style>
    </header>
  );
}
