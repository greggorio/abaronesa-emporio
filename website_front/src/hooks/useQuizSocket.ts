import { useEffect, useRef, useState, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// Usa o hostname atual para suportar acesso via IP local
const getSocketUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'https:' : 'http:';
  const hostname = window.location.hostname;

  // Em produção, usa proxy do Nginx (sem porta)
  // Em desenvolvimento, usa porta 8085 do backend local (Villa Custom)
  const isDevelopment = hostname === 'localhost' || hostname === '127.0.0.1' || hostname.startsWith('192.168.');
  const port = isDevelopment ? ':8085' : '';

  return `${protocol}//${hostname}${port}/ws`;
};

const SOCKET_URL = getSocketUrl();

export interface QuizSocketConfig {
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: any) => void;
}

export const useQuizSocket = (config?: QuizSocketConfig) => {
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, any>>(new Map());
  const pendingRef = useRef<Array<{ destination: string; callback: (message: any) => void }>>([]);
  const configRef = useRef(config);

  // Atualizar referência do config
  useEffect(() => {
    configRef.current = config;
  }, [config]);

  // Conectar ao WebSocket
  const connect = useCallback(() => {
    if (clientRef.current?.connected) {
      console.log('[WebSocket] Já conectado');
      return;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(SOCKET_URL),
      debug: (str) => {
        console.log('[STOMP Debug]', str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('[WebSocket] Conectado com sucesso');
        setIsConnected(true);
        const pending = pendingRef.current.splice(0);
        pending.forEach(({ destination, callback }) => subscribeNow(destination, callback));
        configRef.current?.onConnect?.();
      },
      onDisconnect: () => {
        console.log('[WebSocket] Desconectado');
        setIsConnected(false);
        configRef.current?.onDisconnect?.();
      },
      onStompError: (frame) => {
        console.error('[WebSocket] Erro STOMP:', frame);
        configRef.current?.onError?.(frame);
      },
    });

    client.activate();
    clientRef.current = client;
  }, []);

  // Desconectar
  const disconnect = useCallback(() => {
    if (clientRef.current) {
      console.log('[WebSocket] Desconectando...');
      clientRef.current.deactivate();
      clientRef.current = null;
      setIsConnected(false);
    }
  }, []);

  // Subscribe em um tópico
  const subscribe = useCallback(
    (destination: string, callback: (message: any) => void) => {
      if (clientRef.current?.connected) {
        return subscribeNow(destination, callback);
      }

      const entry = { destination, callback };
      pendingRef.current.push(entry);

      return () => {
        pendingRef.current = pendingRef.current.filter(
          (p) => p.callback !== callback || p.destination !== destination
        );
        const sub = subscriptionsRef.current.get(destination);
        if (sub) {
          sub.unsubscribe();
          subscriptionsRef.current.delete(destination);
        }
      };
    },
    []
  );

  const subscribeNow = (destination: string, callback: (message: any) => void) => {
    if (!clientRef.current) return () => {};

    console.log('[WebSocket] Inscrevendo em:', destination);

    const subscription = clientRef.current.subscribe(destination, (message: IMessage) => {
      try {
        const data = JSON.parse(message.body);
        console.log('[WebSocket] Mensagem recebida de', destination, ':', data);
        callback(data);
      } catch (error) {
        console.error('[WebSocket] Erro ao parsear mensagem:', error);
      }
    });

    subscriptionsRef.current.set(destination, subscription);

    // Retorna função para cancelar inscrição
    return () => {
      console.log('[WebSocket] Cancelando inscrição de:', destination);
      subscription.unsubscribe();
      subscriptionsRef.current.delete(destination);
    };
  };

  // Enviar mensagem
  const send = useCallback((destination: string, body: any) => {
    if (!clientRef.current?.connected) {
      console.error('[WebSocket] Não conectado, não é possível enviar mensagem');
      return;
    }

    console.log('[WebSocket] Enviando para', destination, ':', body);
    clientRef.current.publish({
      destination,
      body: JSON.stringify(body),
    });
  }, []);

  // Conectar automaticamente ao montar
  useEffect(() => {
    connect();

    return () => {
      pendingRef.current = [];
      // Limpar todas as inscrições
      subscriptionsRef.current.forEach((subscription) => {
        subscription.unsubscribe();
      });
      subscriptionsRef.current.clear();

      // Desconectar
      if (clientRef.current) {
        console.log('[WebSocket] Limpando conexão...');
        clientRef.current.deactivate();
        clientRef.current = null;
        setIsConnected(false);
      }
    };
  }, []); // Roda apenas uma vez na montagem

  return {
    isConnected,
    connect,
    disconnect,
    subscribe,
    send,
  };
};
