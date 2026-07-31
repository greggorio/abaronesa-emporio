import { useNavigate, useLocation } from 'react-router-dom';
import {
  Grid3x3,
  ChevronLeft,
  ChevronRight,
  Home,
  Calendar,
  Users,
  Settings,
  BarChart3,
  Trophy,
  Bell,
  Image as ImageIcon,
  BookOpen
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useTheme } from '@/contexts/ThemeContext';

interface AdminSidebarProps {
  isOpen: boolean;
  isCollapsed: boolean;
  onToggleCollapsed: () => void;
}

export default function AdminSidebar({ isOpen, isCollapsed, onToggleCollapsed }: AdminSidebarProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { theme } = useTheme();

  const menuGroups = [
    {
      title: 'Operações',
      items: [
        { title: 'Dashboard', icon: Home, route: '/admin/dashboard', active: location.pathname === '/admin/dashboard' },
        { title: 'Mesas', icon: Grid3x3, route: '/admin/mesas', active: location.pathname.startsWith('/admin/mesas') },
        { title: 'Eventos', icon: Calendar, route: '/admin/eventos', active: location.pathname.startsWith('/admin/eventos') },
        { title: 'Gamificação', icon: Trophy, route: '/admin/gamificacao', active: location.pathname.startsWith('/admin/gamificacao'), disabled: false },
        { title: 'Quiz', icon: BookOpen, route: '/admin/quiz-management', active: location.pathname.startsWith('/admin/quiz-management') },
      ]
    },
    {
      title: 'Relatórios',
      items: [
        { title: 'Vendas', icon: BarChart3, route: '/admin/relatorios/vendas', active: location.pathname.startsWith('/admin/relatorios/vendas') },
        { title: 'Pedidos', icon: Grid3x3, route: '/admin/relatorios/pedidos', active: location.pathname.startsWith('/admin/relatorios/pedidos') },
      ]
    },
    {
      title: 'Conteúdo',
      items: [
        { title: 'Galeria', icon: ImageIcon, route: '/admin/galeria', active: location.pathname.startsWith('/admin/galeria') },
        { title: 'Signage', icon: ImageIcon, route: '/admin/signage', active: location.pathname.startsWith('/admin/signage') },
      ]
    },
    {
      title: 'Sistema',
      items: [
        { title: 'Temas', icon: Settings, route: '/admin/temas', active: location.pathname.startsWith('/admin/temas'), disabled: false },
        { title: 'Notificacoes', icon: Bell, route: '/admin/notificacoes', active: location.pathname.startsWith('/admin/notificacoes') },
        { title: 'Recompensas', icon: Trophy, route: '/admin/rewards', active: location.pathname.startsWith('/admin/rewards') }
      ]
    }
  ];

  const handleNavigation = (route: string, disabled?: boolean, external?: boolean) => {
    if (disabled) return;
    if (external) {
      window.open(route, '_blank', 'noopener,noreferrer');
      return;
    }
    navigate(route);
  };

  if (!isOpen) {
    return null;
  }

  return (
    <aside className={`admin-sidebar ${isCollapsed ? 'collapsed' : ''} ${isOpen ? 'open' : ''}`}>
      {/* Logo */}
      <div className="sidebar-logo">
        <div className="logo-content">
          {isCollapsed ? (
            <span className="logo-small">{theme?.content?.name?.charAt(0) || 'A'}</span>
          ) : (
            <span className="logo-full">{theme?.content?.name || 'Admin'}</span>
          )}
        </div>
      </div>

      {/* Collapse Toggle */}
      <Button
        variant="ghost"
        size="sm"
        onClick={onToggleCollapsed}
        className="collapse-toggle"
      >
        {isCollapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
      </Button>

      {/* Programs Launcher */}
      <div className="programs-launcher">
        <Button variant="ghost" className="launcher-btn">
          <Grid3x3 size={20} />
          {!isCollapsed && <span>Programas</span>}
        </Button>
      </div>

      {/* Navigation Menu */}
      <nav className="sidebar-nav">
        <div className="nav-scroll">
          {menuGroups.map((group, groupIndex) => (
            <div key={groupIndex} className="nav-group">
              {!isCollapsed && (
                <div className="nav-group-title">{group.title}</div>
              )}

              <div className="nav-items">
                {group.items.map((item: any, itemIndex) => {
                  const IconComponent = item.icon;
                  return (
                    <button
                      key={itemIndex}
                      onClick={() => handleNavigation(item.route, item.disabled, item.external)}
                      className={`nav-item ${item.active ? 'active' : ''} ${isCollapsed ? 'collapsed' : ''} ${item.disabled ? 'disabled' : ''}`}
                      disabled={!!item.disabled}
                      title={isCollapsed ? item.title : ''}
                    >
                      <div className="nav-item-icon">
                        <IconComponent size={20} />
                      </div>
                      {!isCollapsed && (
                        <span className="nav-item-text">{item.title}</span>
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      </nav>

      <style>{`
        .admin-sidebar {
          position: fixed;
          top: 0;
          left: 0;
          width: 250px;
          height: 100vh;
          background-color: #FBF6F2; /* cafe-latte-claro */
          border-right: 1px solid #D7B899; /* cafe-latte-suave */
          box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05);
          transition: width 0.3s ease;
          z-index: 100;
          overflow: hidden;
          display: flex;
          flex-direction: column;
        }

        .admin-sidebar.collapsed {
          width: 64px;
        }

        .sidebar-logo {
          padding: 16px;
          border-bottom: 1px solid #D7B899; /* cafe-latte-suave */
          display: flex;
          align-items: center;
          justify-content: center;
          height: 60px;
        }

        .logo-content {
          display: flex;
          align-items: center;
        }

        .logo-small {
          font-size: 20px;
          font-weight: 700;
          color: #6B3E26; /* cafe-espresso */
          font-family: var(--font-display);
        }

        .logo-full {
          font-size: 20px;
          font-weight: 700;
          color: #6B3E26; /* cafe-espresso */
          font-family: var(--font-display);
          letter-spacing: 0.1em;
        }

        .collapse-toggle {
          position: absolute;
          top: 16px;
          right: 8px;
          width: 24px;
          height: 24px;
          padding: 0;
          color: #8B7355; /* cafe-com-leite */
          z-index: 10;
        }

        .collapse-toggle:hover {
          color: #6B3E26; /* cafe-espresso */
          background-color: #D7B899; /* cafe-latte-suave */
        }

        .programs-launcher {
          padding: 12px 16px;
          border-bottom: 1px solid #D7B899; /* cafe-latte-suave */
        }

        .launcher-btn {
          width: 100%;
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 8px;
          color: #2A1F1B; /* cafe-dark-roast */
          font-weight: 500;
          justify-content: flex-start;
        }

        .launcher-btn:hover {
          color: #6B3E26; /* cafe-espresso */
          background-color: #D7B899; /* cafe-latte-suave */
        }

        .admin-sidebar.collapsed .launcher-btn {
          justify-content: center;
        }

        .sidebar-nav { flex: 1; overflow: hidden; }
        .nav-scroll { flex: 1; overflow-y: auto; padding: 16px 0; }

        .nav-scroll::-webkit-scrollbar {
          width: 4px;
        }

        .nav-scroll::-webkit-scrollbar-track {
          background: transparent;
        }

        .nav-scroll::-webkit-scrollbar-thumb {
          background: #D7B899; /* cafe-latte-suave */
          border-radius: 2px;
        }

        .nav-scroll::-webkit-scrollbar-thumb:hover {
          background: #6B3E26; /* cafe-espresso */
        }

        .nav-group {
          margin-bottom: 24px;
        }

        .nav-group-title {
          font-size: 12px;
          font-weight: 600;
          color: #8B7355; /* cafe-com-leite */
          text-transform: uppercase;
          letter-spacing: 0.5px;
          padding: 0 16px;
          margin-bottom: 8px;
        }

        .nav-items {
          display: flex;
          flex-direction: column;
          gap: 2px;
        }

        .nav-item {
          display: flex;
          align-items: center;
          gap: 12px;
          padding: 12px 16px;
          color: #2A1F1B; /* cafe-dark-roast */
          background: none;
          border: none;
          text-align: left;
          cursor: pointer;
          transition: all 0.2s ease;
          width: 100%;
          font-size: 14px;
          font-weight: 500;
        }

        .nav-item:hover {
          color: #6B3E26; /* cafe-espresso */
          background-color: #D7B899; /* cafe-latte-suave */
        }

        .nav-item.active {
          color: #6B3E26; /* cafe-espresso */
          background-color: #F5EDE6; /* cafe-latte-medio */
          border-right: 3px solid #6B3E26; /* cafe-espresso */
        }

        .nav-item.disabled {
          opacity: 0.4;
          cursor: not-allowed;
        }

        .nav-item.collapsed {
          justify-content: center;
          padding: 12px 8px;
        }

        .nav-item-icon {
          display: flex;
          align-items: center;
          justify-content: center;
          min-width: 20px;
        }

        .nav-item-text {
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        @media (max-width: 768px) {
          .admin-sidebar {
            transform: translateX(-100%);
            transition: transform 0.3s ease;
          }

          .admin-sidebar.open {
            transform: translateX(0);
          }
        }
      `}</style>
    </aside>
  );
}
