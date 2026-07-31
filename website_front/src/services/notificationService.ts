import { messaging, getToken, onMessage, VAPID_KEY } from '@/lib/firebaseConfig';
import apiClient from '@/lib/api-client';
import { Capacitor } from '@capacitor/core';
import { PushNotifications } from '@capacitor/push-notifications';

interface SendNotificationRequest {
  title: string;
  body: string;
  imageUrl?: string;
}

interface NotificationHistory {
  id: number;
  title: string;
  body: string;
  imageUrl?: string;
  sentAt: string;
  recipientsCount: number;
}

// Verifica se o app está rodando em modo instalado (PWA standalone)
const isStandalone = (): boolean => {
  if (typeof window === 'undefined') return false;

  const mqStandalone = window.matchMedia?.('(display-mode: standalone)').matches;
  const iosStandalone = (window as any).navigator?.standalone === true;

  return mqStandalone || iosStandalone;
};

class NotificationService {
  /**
   * Solicita permissão para notificações e registra o token no backend
   */
  async requestPermissionAndSubscribe(): Promise<string | null> {
    // Caminho nativo (APK via Capacitor)
    if (Capacitor.isNativePlatform()) {
      return this.registerNativePush();
    }

    // PWA/web desativado para evitar instalação duplicada
    console.log('Notificações via PWA desativadas. Use o app nativo.');
    return null;

    try {
      // Registra o service worker sempre, para habilitar instalação PWA e push em segundo plano
      const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');
      console.log('Service Worker registrado:', registration);

      // Só buscamos token quando o app estiver instalado em modo standalone
      if (!isStandalone()) {
        console.log('App ainda não está em modo standalone; SW registrado, mas token será buscado após instalação.');
        return null;
      }

      // Solicita permissão (ou reaproveita se já tiver sido concedida)
      const permission = Notification.permission === 'granted'
        ? 'granted'
        : await Notification.requestPermission();

      if (permission !== 'granted') {
        console.log('Permissão de notificação negada');
        return null;
      }

      // Obtém token FCM
      const token = await getToken(messaging, {
        vapidKey: VAPID_KEY,
        serviceWorkerRegistration: registration
      });

      if (token) {
        console.log('Token FCM obtido:', token);

        // Envia token para backend
        await this.subscribeToken(token);

        // Escuta mensagens em foreground
        this.listenToForegroundMessages();

        return token;
      } else {
        console.log('Não foi possível obter o token FCM');
        return null;
      }
    } catch (error) {
      console.error('Erro ao solicitar permissão de notificação:', error);
      return null;
    }
  }

  /**
   * Envia token para backend
   */
  private async subscribeToken(token: string, deviceInfo?: string): Promise<void> {
    try {
      const info = deviceInfo || `${navigator.userAgent} - ${new Date().toLocaleString()}`;

      await apiClient.post('/api/notifications/subscribe', {
        token,
        deviceInfo: info
      });

      console.log('Token inscrito no backend com sucesso');

      // Salvar o token FCM localmente para reutilização após login
      localStorage.setItem('fcm_token', token);
    } catch (error) {
      const details = (error as any)?.response?.data || error;
      console.error('Erro ao inscrever token no backend:', details);
      throw error;
    }
  }

  /**
   * Reenvia o token FCM armazenado para o backend com o JWT atual
   */
  async subscribeStoredToken(): Promise<void> {
    try {
      const storedToken = localStorage.getItem('fcm_token');

      if (!storedToken) {
        console.info('Nenhum FCM token armazenado para re-subscrição');
        return;
      }

      console.info('Re-subscribing after login');

      // A instância apiClient já adiciona o JWT e X-User-ID automaticamente se estiverem presentes no localStorage
      await apiClient.post('/api/notifications/subscribe', {
        token: storedToken,
        deviceInfo: 'Stored token re-subscription'
      });

      console.info('Re-subscribe done');
    } catch (error) {
      console.error('Erro ao re-inscrever token FCM após login:', error);
      throw error;
    }
  }

  /**
   * Escuta mensagens quando o app está aberto (foreground)
   */
  private listenToForegroundMessages(): void {
    if (!messaging) return;

    onMessage(messaging, (payload) => {
      console.log('Mensagem recebida em foreground:', payload);

      const notificationTitle = payload.notification?.title || 'Nova Notificação';
      const notificationOptions: NotificationOptions = {
        body: payload.notification?.body || '',
        icon: payload.notification?.image || '/favicon-192.png',
        badge: '/favicon-192.png',
        vibrate: [200, 100, 200],
        tag: 'monicaleila-notification',
      };

      // Mostra notificação mesmo com app aberto
      if (Notification.permission === 'granted') {
        new Notification(notificationTitle, notificationOptions);
      }
    });
  }

  /**
   * Push nativo (Capacitor/Android) para APK
   */
  private async registerNativePush(): Promise<string | null> {
    try {
      const permStatus = await PushNotifications.requestPermissions();
      if (permStatus.receive !== 'granted') {
        console.log('Permissão de notificação (nativo) negada');
        return null;
      }

      await PushNotifications.register();

      const token = await new Promise<string>((resolve, reject) => {
        let removeRegistration: (() => void) | undefined;
        let removeError: (() => void) | undefined;

        const cleanup = () => {
          removeRegistration?.();
          removeError?.();
        };

        removeRegistration = PushNotifications.addListener('registration', async (t) => {
          cleanup();
          try {
            console.log('Push token (nativo) obtido:', t.value);
            const cachedToken = localStorage.getItem('native-fcm-token');
            if (cachedToken !== t.value) {
              await this.subscribeToken(t.value, 'capacitor-android');
              localStorage.setItem('native-fcm-token', t.value);
            } else {
              console.log('Token nativo já inscrito, pulando reenvio');
            }
            resolve(t.value);
          } catch (err) {
            console.error('Erro ao inscrever token (nativo):', err);
            reject(err);
          }
        }).remove;

        removeError = PushNotifications.addListener('registrationError', (err) => {
          cleanup();
          console.error('registrationError (nativo):', err);
          reject(err?.error || err);
        }).remove;
      });

      // Apenas loga push recebido em foreground no nativo (sistema já exibe em background)
      PushNotifications.addListener('pushNotificationReceived', (notification) => {
        console.log('Push recebido (nativo):', notification);
      });

      // Ao tocar na notificação, navegar para a URL enviada no payload (deeplink)
      PushNotifications.addListener('pushNotificationActionPerformed', (action) => {
        const data = (action?.notification as any)?.data || {};
        const targetUrl = data.url || data.deeplink;
        if (!targetUrl) return;

        try {
          const url = new URL(targetUrl);
          const nextPath = `${url.pathname}${url.search}${url.hash}`;
          window.history.pushState({}, '', nextPath);
          window.dispatchEvent(new PopStateEvent('popstate'));
        } catch {
          window.location.href = targetUrl;
        }
      });

      return token;
    } catch (error) {
      console.error('Erro ao registrar push nativo:', error);
      return null;
    }
  }

  /**
   * Envia notificação (admin)
   */
  async sendNotification(data: SendNotificationRequest): Promise<NotificationHistory> {
    try {
      const response = await apiClient.post<NotificationHistory>('/api/notifications/send', data);
      return response.data;
    } catch (error: any) {
      throw new Error(error.response?.data || 'Erro ao enviar notificação');
    }
  }

  /**
   * Obtém histórico de notificações (admin)
   */
  async getHistory(): Promise<NotificationHistory[]> {
    try {
      const response = await apiClient.get<NotificationHistory[]>('/api/notifications/history');
      return response.data;
    } catch (error: any) {
      throw new Error(error.response?.data || 'Erro ao buscar histórico');
    }
  }

  /**
   * Verifica se notificações estão suportadas
   */
  isSupported(): boolean {
    return 'Notification' in window && 'serviceWorker' in navigator;
  }

  /**
   * Verifica se permissão foi concedida
   */
  isPermissionGranted(): boolean {
    return Notification.permission === 'granted';
  }
}

export const notificationService = new NotificationService();
export type { SendNotificationRequest, NotificationHistory };
