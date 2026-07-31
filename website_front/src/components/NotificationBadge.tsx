import { useState, useEffect } from 'react';
import { Bell } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Notificacao } from '@/types/notificacao';
import axios from '@/lib/axios';
import { apiConfig } from '@/config/api';

interface NotificationBadgeProps {
  guestToken: string | null;
  unreadCount: number;
  onCountUpdate: (count: number) => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
  locale: string;
}

export function NotificationBadge({
  guestToken,
  unreadCount,
  onCountUpdate,
  t,
  locale,
}: NotificationBadgeProps) {
  const [notifications, setNotifications] = useState<Notificacao[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  /**
   * Fetch unread notifications when popover opens
   */
  useEffect(() => {
    if (open && guestToken) {
      fetchNotifications();
    }
  }, [open, guestToken]);

  const fetchNotifications = async () => {
    if (!guestToken) return;

    try {
      setLoading(true);
      const response = await axios.get<Notificacao[]>(
        `${apiConfig.erpBaseUrl}/api/notificacoes/nao-lidas`,
        {
          headers: {
            'X-Guest-Token': guestToken,
            'Accept-Language': locale,
          },
        }
      );
      setNotifications(response.data);
    } catch (error) {
      console.error('Error fetching notifications:', error);
    } finally {
      setLoading(false);
    }
  };

  const markAllAsRead = async () => {
    if (!guestToken) return;

    try {
      await axios.patch(
        `${apiConfig.erpBaseUrl}/api/notificacoes/marcar-todas-lidas`,
        {},
        {
          headers: { 'X-Guest-Token': guestToken },
        }
      );

      setNotifications([]);
      onCountUpdate(0);
      setOpen(false);
    } catch (error) {
      console.error('Error marking notifications as read:', error);
    }
  };

  if (!guestToken || unreadCount === 0) {
    return null;
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="icon"
          className="relative border-coral-accent/30 text-forest-dark bg-white hover:bg-coral-accent/10 shadow-sm"
        >
          <Bell className="h-5 w-5" />
          {unreadCount > 0 && (
            <Badge
              variant="destructive"
              className="absolute -top-2 -right-2 h-5 w-5 flex items-center justify-center p-0 text-xs"
            >
              {unreadCount > 9 ? '9+' : unreadCount}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="w-80 bg-white border border-coral-accent/20 text-forest-dark p-0 shadow-lg"
        align="end"
      >
        {/* Header */}
        <div className="px-4 py-3 border-b border-coral-accent/20 flex items-center justify-between bg-coral-accent/5">
          <h3 className="text-[12px] uppercase tracking-wide text-forest-dark/70 font-medium">
            {t('mesa.notifications.title')}
          </h3>
          {notifications.length > 0 && (
            <button
              onClick={markAllAsRead}
              className="text-[12px] text-forest-green hover:text-forest-green/80 transition font-medium"
            >
              {t('mesa.notifications.markAll')}
            </button>
          )}
        </div>

        {/* Notifications list */}
        <div className="p-3">
          {loading ? (
            <div className="text-center py-6 text-sm text-forest-dark/60">
              {t('mesa.notifications.loading')}
            </div>
          ) : notifications.length === 0 ? (
            <div className="text-center py-6 text-sm text-forest-dark/60">
              {t('mesa.notifications.empty')}
            </div>
          ) : (
            <ScrollArea className="h-[300px] pr-1">
              <div className="space-y-2">
                {notifications.map((notif) => (
                  <div
                    key={notif.id}
                    className="p-3 rounded-lg border border-coral-accent/20 bg-coral-accent/5 hover:bg-coral-accent/10 transition-colors shadow-sm"
                  >
                    <p className="font-medium text-sm text-forest-dark">{notif.titulo}</p>
                    <p className="text-xs text-forest-dark/70 mt-1">{notif.mensagem}</p>
                    <p className="text-[11px] text-forest-dark/50 mt-2">
                      {new Intl.DateTimeFormat(locale, {
                        hour: '2-digit',
                        minute: '2-digit',
                      }).format(new Date(notif.criadoEm))}
                    </p>
                  </div>
                ))}
              </div>
            </ScrollArea>
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
}
