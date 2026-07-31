import { useState, useEffect, useCallback } from 'react';

export interface BrowserNotificationOptions {
  title: string;
  body: string;
  icon?: string;
  tag?: string;
}

export function useNotifications() {
  const [permission, setPermission] = useState<NotificationPermission>(
    typeof Notification !== 'undefined' ? Notification.permission : 'default'
  );
  const [supported, setSupported] = useState(false);

  useEffect(() => {
    if (typeof Notification !== 'undefined') {
      setSupported(true);
      setPermission(Notification.permission);
    }
  }, []);

  /**
   * Request permission to show browser notifications
   */
  const requestPermission = useCallback(async (): Promise<NotificationPermission> => {
    if (!supported || !('Notification' in window)) {
      console.warn('Browser notifications not supported');
      return 'denied';
    }

    if (permission === 'granted') {
      return 'granted';
    }

    try {
      const result = await Notification.requestPermission();
      setPermission(result);
      return result;
    } catch (error) {
      console.error('Error requesting notification permission:', error);
      return 'denied';
    }
  }, [supported, permission]);

  /**
   * Show a browser notification
   */
  const showNotification = useCallback(
    (options: BrowserNotificationOptions) => {
      if (!supported || permission !== 'granted') {
        console.warn('Cannot show notification: not supported or permission denied');
        return;
      }

      try {
        const notification = new Notification(options.title, {
          body: options.body,
          icon: options.icon || '/favicon.ico',
          tag: options.tag,
          requireInteraction: false,
        });

        // Auto close after 10 seconds
        setTimeout(() => {
          notification.close();
        }, 10000);
      } catch (error) {
        console.error('Error showing notification:', error);
      }
    },
    [supported, permission]
  );

  return {
    supported,
    permission,
    requestPermission,
    showNotification,
  };
}
