// Types for KDS (Kitchen Display System)

/**
 * Status of an order item
 */
export type StatusItem =
  | 'queued'
  | 'accepted'
  | 'preparing'
  | 'ready'
  | 'delivered'
  | 'canceled';

/**
 * Kitchen station types
 */
export type Estacao = 'kitchen' | 'bar';

/**
 * Represents a single ticket in the KDS queue
 */
export interface KdsTicket {
  itemPedidoId: number;
  pedidoId: number;
  estacao: Estacao;
  status: StatusItem;
  atualizadoEm: string; // ISO 8601 format
  tipo?: 'mesa' | 'delivery';
  serviceMode?: 'waiter_delivery' | 'customer_pickup';
  deliveryItemId?: number;
  delivery?: {
    deliveryId?: string | null;
    externalId?: string | null;
    customerName?: string | null;
    dropoffAddress?: string | null;
    status?: string | null;
  };
  item: {
    nome: string;
    quantidade: number;
    observacoes?: string | null;
    necessitaPreparacao?: boolean;
    skuId?: number;
    variacao?: string | null;
  };
  mesa: {
    slug: string;
    rotulo: string;
    referencia?: string | null;
  };
  pedido: {
    criadoEm: string; // ISO 8601 format
  };
}

/**
 * Response from GET /api/kds/queue
 */
export interface KdsQueueResponse {
  tickets: KdsTicket[];
}
