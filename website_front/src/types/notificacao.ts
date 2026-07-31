export interface Notificacao {
  id: number;
  tipo: 'guest_joined' | 'order_created';
  titulo: string;
  mensagem: string;
  lida: boolean;
  criadoEm: string;
  lidaEm?: string;
  payloadJson?: string;
}

export interface NotificacaoContador {
  count: number;
}
