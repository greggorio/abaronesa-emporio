// Firebase Cloud Messaging Service Worker
importScripts('https://www.gstatic.com/firebasejs/12.6.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/12.6.0/firebase-messaging-compat.js');

// Initialize Firebase
firebase.initializeApp({
  apiKey: "AIzaSyAoDS0Q4ofzKYQWv3zFnFKxI9EriSYx0Ng",
  authDomain: "monicaleila-pwa-notificacoes.firebaseapp.com",
  projectId: "monicaleila-pwa-notificacoes",
  storageBucket: "monicaleila-pwa-notificacoes.firebasestorage.app",
  messagingSenderId: "648681417982",
  appId: "1:648681417982:web:3dddc895c4e63adb0209af"
});

// Retrieve Firebase Messaging instance
const messaging = firebase.messaging();

// Handle background messages
messaging.onBackgroundMessage((payload) => {
  console.log('[firebase-messaging-sw.js] Received background message ', payload);

  const notificationTitle = payload.notification?.title || payload.data?.title || 'Nova Notificação';
  const notificationOptions = {
    body: payload.notification?.body || payload.data?.body || '',
    icon: payload.notification?.image || payload.data?.imageUrl || '/favicon-192.png',
    badge: '/favicon-192.png',
    vibrate: [200, 100, 200],
    tag: 'monicaleila-notification',
    requireInteraction: false,
    data: {
      url: payload.data?.url || '/'
    }
  };

  return self.registration.showNotification(notificationTitle, notificationOptions);
});

// Fallback: handle raw push events (caso onBackgroundMessage não dispare)
self.addEventListener('push', (event) => {
  try {
    const payload = event.data?.json?.() || {};

    const notificationTitle = payload.notification?.title || payload.data?.title || payload.title || 'Nova Notificação';
    const notificationOptions = {
      body: payload.notification?.body || payload.data?.body || payload.body || '',
      icon: payload.notification?.image || payload.data?.imageUrl || '/favicon-192.png',
      badge: '/favicon-192.png',
      vibrate: [200, 100, 200],
      tag: 'monicaleila-notification',
      requireInteraction: false,
      data: {
        url: payload.data?.url || payload.url || '/',
      },
    };

    event.waitUntil(self.registration.showNotification(notificationTitle, notificationOptions));
  } catch (err) {
    console.error('[Service Worker] Error handling push event:', err);
  }
});

// Handle notification click
self.addEventListener('notificationclick', (event) => {
  console.log('[Service Worker] Notification click received.');

  event.notification.close();

  const urlToOpen = event.notification.data?.url || '/';

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((windowClients) => {
        // Check if there is already a window open
        for (let i = 0; i < windowClients.length; i++) {
          const client = windowClients[i];
          if (client.url === urlToOpen && 'focus' in client) {
            return client.focus();
          }
        }
        // If not, open a new window
        if (clients.openWindow) {
          return clients.openWindow(urlToOpen);
        }
      })
  );
});
