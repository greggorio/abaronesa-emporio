import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import apiClient from '@/lib/api-client';
import { Page, UserNotification } from '@/types/reward';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Loader2, Bell, AlertCircle, ArrowLeft } from 'lucide-react';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import { useTheme } from '@/contexts/ThemeContext';

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  return format(new Date(value), "dd/MM/yyyy 'às' HH:mm", { locale: ptBR });
};

export default function NotificacoesCliente() {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const textColor = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text))]' : 'text-foreground';
  const textMuted = theme?.tokens?.['mesa-text'] ? 'text-[hsl(var(--mesa-text)/0.65)]' : 'text-foreground/65';

  const [notifications, setNotifications] = useState<UserNotification[]>([]);
  const [loadingNotifications, setLoadingNotifications] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);
  const [selectedNotification, setSelectedNotification] = useState<UserNotification | null>(null);

  useEffect(() => {
    fetchNotifications();
    fetchUnreadCount();
  }, []);

  const fetchNotifications = async () => {
    try {
      setLoadingNotifications(true);
      const { data } = await apiClient.get<Page<UserNotification>>('/api/notifications/my', {
        params: { page: 0, size: 15 },
      });
      setNotifications(data.content);
    } catch (err) {
      console.error('Erro ao carregar notificações:', err);
    } finally {
      setLoadingNotifications(false);
    }
  };

  const fetchUnreadCount = async () => {
    try {
      const { data } = await apiClient.get<{ unreadCount: number }>('/api/notifications/my/unread-count');
      setUnreadCount(data.unreadCount);
    } catch (err) {
      console.error('Erro ao carregar contador de notificações:', err);
    }
  };

  const markAllAsRead = async () => {
    try {
      await apiClient.patch('/api/notifications/my/read-all');
      setNotifications((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
      setUnreadCount(0);
    } catch (err) {
      console.error('Erro ao marcar todas como lidas:', err);
    }
  };

  const markOneAsRead = async (id: number) => {
    try {
      await apiClient.patch(`/api/notifications/${id}/read`);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, readAt: n.readAt ?? new Date().toISOString() } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {
      console.error('Erro ao marcar notificação como lida:', err);
    }
  };

  const unreadNotifications = useMemo(() => notifications.filter((n) => !n.readAt), [notifications]);

  return (
    <div className={`min-h-screen bg-soft-white flex flex-col ${textColor}`}>
      <Header />
      <main className="flex-1 container mx-auto px-4 py-8 max-w-3xl space-y-6">
        <button
          onClick={() => navigate('/areacliente')}
          className={`inline-flex items-center gap-2 text-sm font-semibold ${textColor} hover:text-[hsl(var(--accent))] transition`}
        >
          <ArrowLeft className="w-4 h-4" />
          Voltar
        </button>

        <div className="flex flex-col gap-1">
          <p className={`text-xs uppercase tracking-widest ${textMuted}`}>Avisos</p>
          <h1 className="text-3xl font-display flex items-center gap-2">
            <Bell className="w-6 h-6" /> Notificações
          </h1>
          <p className={`text-sm ${textMuted}`}>Tudo o que você recebeu do estabelecimento.</p>
        </div>

        <div className="flex items-center justify-between">
          <div className={`text-sm ${textMuted}`}>
            {unreadNotifications.length > 0
              ? `${unreadNotifications.length} nova(s) notificação(ões)`
              : 'Nenhuma nova notificação'}
          </div>
          {notifications.length > 0 && (
            <Button variant="outline" size="sm" onClick={markAllAsRead}>
              Marcar todas como lidas
            </Button>
          )}
        </div>

        {loadingNotifications ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="w-4 h-4 animate-spin" /> Carregando notificações...
          </div>
        ) : notifications.length === 0 ? (
          <Card className="border-dashed">
            <CardContent className="p-4 text-sm text-muted-foreground flex items-center gap-2">
              <AlertCircle className="w-4 h-4" /> Nenhuma notificação por aqui.
            </CardContent>
          </Card>
        ) : (
          notifications.map((notif) => (
            <Card key={notif.id} className={`border ${notif.readAt ? 'border-muted' : 'border-[hsl(var(--accent)/0.4)]'} bg-white shadow-sm`}>
              <CardContent className="p-4 space-y-2">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold">{notif.title}</p>
                    <p className="text-xs text-muted-foreground mt-1">{notif.body}</p>
                  </div>
                  {!notif.readAt && (
                    <Button variant="ghost" size="sm" onClick={() => markOneAsRead(notif.id)}>
                      Marcar lida
                    </Button>
                  )}
                </div>
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>{formatDateTime(notif.createdAt)}</span>
                  {notif.source === 'REWARD' && <span>Recompensa</span>}
                </div>
                <button
                  className="text-sm text-[hsl(var(--accent))] hover:underline text-left"
                  onClick={() => setSelectedNotification(notif)}
                >
                  Abrir
                </button>
              </CardContent>
            </Card>
          ))
        )}
      </main>
      <Footer />

      <Dialog open={!!selectedNotification} onOpenChange={(open) => !open && setSelectedNotification(null)}>
        <DialogContent className="bg-white text-mesa-text border-accent/20 shadow-xl max-w-md">
          {selectedNotification && (
            <>
              <DialogHeader>
                <DialogTitle className="text-mesa-text">{selectedNotification.title}</DialogTitle>
              </DialogHeader>
              <div className="space-y-3">
                <p className="text-sm text-mesa-text/80">{selectedNotification.body}</p>
                {selectedNotification.imageUrl && (
                  <img
                    src={selectedNotification.imageUrl}
                    alt={selectedNotification.title}
                    className="w-full rounded-lg border border-accent/20 object-cover"
                  />
                )}
                <div className="flex items-center justify-between text-xs text-mesa-text/60">
                  <span>{formatDateTime(selectedNotification.createdAt)}</span>
                  {selectedNotification.source === 'REWARD' && <span>Recompensa</span>}
                </div>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
