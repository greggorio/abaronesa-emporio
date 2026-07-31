import { initializeApp } from "firebase/app";
import { getMessaging, getToken, onMessage, Messaging } from "firebase/messaging";

const firebaseConfig = {
  apiKey: "AIzaSyAoDS0Q4ofzKYQWv3zFnFKxI9EriSYx0Ng",
  authDomain: "monicaleila-pwa-notificacoes.firebaseapp.com",
  projectId: "monicaleila-pwa-notificacoes",
  storageBucket: "monicaleila-pwa-notificacoes.firebasestorage.app",
  messagingSenderId: "648681417982",
  appId: "1:648681417982:web:3dddc895c4e63adb0209af"
};

// VAPID Key (public key)
const VAPID_KEY = "BA6JmEKqmq-4U0_ivXzO7bUdc0NgxZFLR-zrUGJiAfXONhEr4MH61WQgRHDp5R0HVE4NID7-mHdRPOH6X24_pNA";

// Initialize Firebase
const app = initializeApp(firebaseConfig);

// Initialize Firebase Cloud Messaging
let messaging: Messaging | null = null;

try {
  if (typeof window !== 'undefined' && 'serviceWorker' in navigator) {
    messaging = getMessaging(app);
  }
} catch (error) {
  console.error('Firebase messaging initialization error:', error);
}

export { app, messaging, getToken, onMessage, VAPID_KEY };
