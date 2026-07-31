export type ServiceMode = "DELIVERY" | "PICKUP" | "waiter_delivery" | "customer_pickup";

export type DeliveryOrderItemDetail = {
  nome?: string;
  quantidade?: number;
  preco?: number;
  observacoes?: string | null;
};

export type CustomerData = {
  customerName: string;
  customerPhone: string;
  customerEmail: string;
  customerCpf: string;
  dropoffAddress: string;
  dropoffNotes: string;
};

export type PaymentCardData = {
  cardNumber: string;
  cardHolder: string;
  cardExpMonth: string;
  cardExpYear: string;
  cardCvv: string;
  cardDoc: string;
  cardToken: string;
  paymentMethodId: string;
  installments: number;
};

export type DeliveryOrder = {
  id: number;
  serviceMode?: ServiceMode;
  status?: string;
};

export type DeliveryOrderDetail = {
  id?: number;
  status?: string;
  deliveryFeeCents?: number;
  total?: number;
  customerName?: string;
  customerPhone?: string;
  customerEmail?: string;
  dropoffAddress?: string;
  dropoffNotes?: string;
  trackingUrl?: string;
  dropoffEta?: string;
  pickupAddress?: string;
  updatedAt?: string;
  serviceMode?: ServiceMode;
  items?: DeliveryOrderItemDetail[];
  externalId?: string;
  deliveryId?: string;
};

export type DeliveryTrackingDetail = DeliveryOrderDetail;

export type PaymentInfo = {
  id?: string | number;
  status?: string;
  statusDetail?: string;
  friendlyMessage?: string;
} | null;

declare global {
  interface Window {
    PagSeguro?: any;
  }
}
