import { useEffect, useMemo, useState } from 'react';
import { useIsMobile } from '@/hooks/use-mobile';
import { Outlet, Navigate, useLocation } from 'react-router-dom';
import AdminHeader from './AdminHeader';
import AdminSidebar from './AdminSidebar';

export default function AdminLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const isMobile = useIsMobile();
  const location = useLocation();

  // Ensure sidebar starts closed on mobile to avoid overlaying content
  useEffect(() => {
    if (isMobile) setSidebarOpen(false);
  }, [isMobile]);

  // Collapse sidebar automatically in payment flow (more canvas space for 1280x720)
  const shouldAutoCollapse = useMemo(
    () => location.pathname.startsWith('/admin/mesas/') && location.pathname.includes('/pagamentos'),
    [location.pathname]
  );

  useEffect(() => {
    if (isMobile) return;
    if (shouldAutoCollapse) {
      setSidebarOpen(true); // keep visible, just compact
      setSidebarCollapsed(true);
    } else {
      setSidebarCollapsed(false);
    }
  }, [isMobile, shouldAutoCollapse]);

  // Verifica se o usuário está autenticado
  const token = localStorage.getItem('auth_token');

  if (!token) {
    // Redireciona para login se não houver token
    return <Navigate to="/login" replace />;
  }

  const toggleSidebar = () => {
    setSidebarOpen(!sidebarOpen);
  };

  const toggleCollapsed = () => {
    setSidebarCollapsed(!sidebarCollapsed);
  };

  return (
    <div className="admin-layout">
      {/* Header */}
      <AdminHeader
        onToggleSidebar={toggleSidebar}
        sidebarOpen={sidebarOpen}
        sidebarCollapsed={sidebarCollapsed}
      />

      <div className="admin-content">
        {/* Sidebar */}
        <AdminSidebar
          isOpen={sidebarOpen}
          isCollapsed={sidebarCollapsed}
          onToggleCollapsed={toggleCollapsed}
        />

        {/* Main Content */}
        <main
          className={`admin-main ${sidebarCollapsed ? 'sidebar-collapsed' : ''}`}
          style={{
            // On mobile we do not reserve horizontal space for the sidebar
            marginLeft: isMobile ? '0' : (sidebarOpen ? (sidebarCollapsed ? '64px' : '250px') : '0'),
            transition: 'margin-left 0.3s ease'
          }}
        >
          <div className="admin-main-content">
            <Outlet />
          </div>
        </main>
      </div>

      <style>{`
        .admin-layout {
          height: 100vh;
          background-color: hsl(var(--soft-white));
          --admin-header-h: 80px;
          overflow: hidden;
        }

        .admin-content {
          display: flex;
          height: 100vh;
        }

        .admin-main {
          flex: 1;
          transition: margin-left 0.3s ease;
          padding-top: var(--admin-header-h);
          height: 100vh;
          overflow-y: auto;
          background-color: hsl(var(--soft-white));
        }

        .admin-main-content {
          padding: 20px;
          height: calc(100vh - var(--admin-header-h));
        }

        .sidebar-collapsed .admin-main-content {
          margin-left: 0;
        }

        /* Mobile adjustments */
        @media (max-width: 768px) {
          .admin-layout { --admin-header-h: 64px; }
          .admin-main-content { padding: 16px; }
        }
      `}</style>
    </div>
  );
}
