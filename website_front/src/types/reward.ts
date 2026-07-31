export type RewardStatus = 'AVAILABLE' | 'REDEEMED' | 'EXPIRED';

export interface Reward {
  id: number;
  userId: number;
  title: string;
  description?: string;
  imageUrl?: string;
  validUntil: string;
  status: RewardStatus;
  createdAt: string;
  redeemedAt?: string;
  notificationHistoryId?: number;
}

export interface UserNotification {
  id: number;
  userId?: number;
  title: string;
  body: string;
  imageUrl?: string;
  deeplink?: string;
  createdAt: string;
  readAt?: string;
  source: string;
  payloadJson?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
