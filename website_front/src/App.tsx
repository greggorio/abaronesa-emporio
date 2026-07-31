import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom";
import { useEffect } from "react";
import { ThemeProvider } from '@/contexts/ThemeContext';
import Index from "./pages/Index";
import MenuPage from "./pages/MenuPage";
import NotFound from "./pages/NotFound";
import QuizAdmin from "./pages/QuizAdmin";
import QuizPlayer from "./pages/QuizPlayer";
import QuizManagement from "./pages/admin/QuizManagement";
import OAuth2Handler from "./pages/OAuth2Handler";
import Login from "./pages/Login";
import AreaCliente from "./pages/AreaCliente";
import FinanceiroPage from "./pages/cliente/FinanceiroPage";
import GamificacaoExtrato from "./pages/cliente/GamificacaoExtrato";
import RecompensasInbox from "./pages/cliente/RecompensasInbox";
import NotificacoesCliente from "./pages/cliente/NotificacoesCliente";
import EventosCliente from "./pages/cliente/EventosCliente";
import AdminLayout from "./layouts/AdminLayout";
import AdminDashboard from "./pages/admin/AdminDashboard";
import MesasDashboard from "./pages/admin/MesasDashboard";
import PagamentosPage from "./pages/admin/PagamentosPage";
import GamificacaoAdmin from "./pages/admin/GamificacaoAdmin";
import EventosAdmin from "./pages/admin/EventosAdmin";
import SalesReportsPage from "./pages/admin/SalesReportsPage";
import OrdersReportsPage from "./pages/admin/OrdersReportsPage";
import CancelamentosPage from "./pages/admin/CancelamentosPage";
import GaleriaAdmin from "./pages/admin/GaleriaAdmin";
import TemasPage from "./pages/admin/TemasPage";
import Notificacoes from "./pages/admin/Notificacoes";
import RewardsDrawPage from "./pages/admin/rewards/DrawPage";
import RewardsAdminPanel from "./pages/admin/rewards/RewardsAdminPanel";
import ClientesDashboard from "./pages/admin/ClientesDashboard";
import ProtectedRoute from "./components/ProtectedRoute";
import SignageAdmin from "./pages/admin/SignageAdmin";
import SignageNoticeAdmin from "./pages/admin/SignageNoticeAdmin";
import SignageScheduleAdmin from "./pages/admin/SignageScheduleAdmin";
import SignageDevicesAdmin from "./pages/admin/SignageDevicesAdmin";
import MesaPage from "./pages/MesaPage";
import KdsPage from "./pages/KdsPage";
import WaiterPage from "./pages/WaiterPage";
import FavoritosPage from "./pages/FavoritosPage";
import DeliveryTestPage from "./pages/DeliveryTestPage";
import DeliveryMenuPage from "./pages/DeliveryMenuPage";
import DeliveryOrderConfirmationPage from "./pages/DeliveryOrderConfirmationPage";
import DeliveryTrackingPage from "./pages/cliente/DeliveryTrackingPage";
import MesaPaymentSuccessPage from "./pages/MesaPaymentSuccessPage";
import { Capacitor } from "@capacitor/core";
import { notificationService } from "./services/notificationService";

const queryClient = new QueryClient();

const ScrollToTop = () => {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: "auto" });
  }, [pathname]);
  return null;
};

const NativePushBootstrap = () => {
  useEffect(() => {
    if (!Capacitor.isNativePlatform()) return;
    notificationService.requestPermissionAndSubscribe().catch((err) => {
      console.error("Erro ao registrar push nativo:", err);
    });
  }, []);
  return null;
};

const App = () => (
  <QueryClientProvider client={queryClient}>
    <ThemeProvider>
      <TooltipProvider>
        <Toaster />
        <Sonner />
        <BrowserRouter>
          <ScrollToTop />
          <NativePushBootstrap />
          <Routes>
            <Route path="/" element={<Index />} />
            <Route path="/cardapio" element={<MenuPage />} />
            <Route path="/login" element={<Login />} />
            <Route path="/oauth2/handler" element={<OAuth2Handler />} />
            {/* QR Mesa */}
            <Route path="/m/:mesaSlug" element={<MesaPage />} />
            <Route path="/m/:mesaSlug/pagamento-sucesso" element={<MesaPaymentSuccessPage />} />
            {/* KDS - Kitchen Display System */}
            <Route
              path="/kds"
              element={
                <ProtectedRoute requiredRole={['KDS', 'ADMIN', 'SYSTEM']}>
                  <KdsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/waiter"
              element={
                <ProtectedRoute requiredRole={['WAITER', 'CAIXA', 'ADMIN', 'SYSTEM']}>
                  <WaiterPage />
                </ProtectedRoute>
              }
            />
            <Route path="/delivery" element={<DeliveryTestPage />} />
            <Route path="/delivery-menu" element={<DeliveryMenuPage />} />
            <Route path="/delivery/pedido/:orderId/confirmado" element={<DeliveryOrderConfirmationPage />} />
            <Route path="/quiz/admin" element={
              <ProtectedRoute>
                <QuizAdmin />
              </ProtectedRoute>
            } />
            <Route path="/quiz/join/:sessionCode" element={<QuizPlayer />} />

            {/* Favoritos */}
            <Route path="/favoritos" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <FavoritosPage />
              </ProtectedRoute>
            } />

            {/* Área do Cliente */}
            <Route path="/areacliente" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <AreaCliente />
              </ProtectedRoute>
            } />

            <Route path="/areacliente/financeiro" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <FinanceiroPage />
              </ProtectedRoute>
            } />
            <Route path="/areacliente/gamificacao" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <GamificacaoExtrato />
              </ProtectedRoute>
            } />
            <Route path="/areacliente/recompensas" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <RecompensasInbox />
              </ProtectedRoute>
            } />
            <Route path="/areacliente/notificacoes" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <NotificacoesCliente />
              </ProtectedRoute>
            } />
            <Route path="/areacliente/eventos" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <EventosCliente />
              </ProtectedRoute>
            } />
            <Route path="/areacliente/delivery/:orderId" element={
              <ProtectedRoute requiredRole="CLIENTE">
                <DeliveryTrackingPage />
              </ProtectedRoute>
            } />

            {/* Admin Routes */}
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<AdminDashboard />} />
              <Route path="dashboard" element={<AdminDashboard />} />
              <Route path="mesas" element={<MesasDashboard />} />
              <Route path="mesas/:sessaoMesaId/pagamentos" element={<PagamentosPage />} />
              <Route path="eventos" element={<EventosAdmin />} />
              <Route path="gamificacao" element={<GamificacaoAdmin />} />
              <Route path="galeria" element={<GaleriaAdmin />} />
              <Route path="temas" element={<TemasPage />} />
              <Route path="notificacoes" element={<Notificacoes />} />
              <Route path="rewards" element={<RewardsAdminPanel />} />
              <Route path="rewards/draw" element={<RewardsDrawPage />} />
              <Route path="clientes" element={<ClientesDashboard />} />
              <Route path="relatorios/vendas" element={<SalesReportsPage />} />
              <Route path="relatorios/pedidos" element={<OrdersReportsPage />} />
              <Route path="cancelamentos" element={<CancelamentosPage />} />
              <Route path="quiz-management" element={<QuizManagement />} />
              <Route path="signage" element={<SignageAdmin />} />
              <Route path="signage/notice" element={<SignageNoticeAdmin />} />
              <Route path="signage/schedules" element={<SignageScheduleAdmin />} />
              <Route path="signage/devices" element={<SignageDevicesAdmin />} />
            </Route>

            {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
            <Route path="*" element={<NotFound />} />
          </Routes>
        </BrowserRouter>
      </TooltipProvider>
    </ThemeProvider>
  </QueryClientProvider>
);

export default App;
