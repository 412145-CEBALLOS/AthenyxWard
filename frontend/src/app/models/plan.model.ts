export type BillingCycle = 'MONTHLY' | 'ANNUAL';
export type PaymentProvider = 'PAYPAL' | 'MERCADOPAGO' | 'CARD';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'EXPIRED' | 'REFUNDED';

export interface SubscriptionResponse {
  planTier: string;
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELED';
  startedAt: string | null;
  renewsAt: string | null;
  canceledAt: string | null;
  paymentMethod: string | null;
  autoRenew: boolean;
  billingCycle: BillingCycle | null;
  priceAmount: number;
  priceCurrency: string;
  annualSavingsPercent: string;
  enabledProviders: string;
  cancelAtPeriodEnd: boolean;
}

export interface CreateCheckoutRequest {
  provider: string;
  billingCycle: BillingCycle;
  planTier: string;
}

export interface CreateCheckoutResponse {
  paymentId: number;
  redirectUrl: string;
  expiresAt: string;
}

export interface ConfirmPaymentRequest {
  paymentId: number;
  token: string;
}

export interface CheckoutStatusResponse {
  paymentId: number;
  status: PaymentStatus;
  provider: PaymentProvider;
  amount: number;
  currency: string;
  billingCycle: BillingCycle;
  createdAt: string;
  expiresAt: string;
}

export interface PaymentResponse {
  id: number | null;
  planTier: string;
  status: PaymentStatus;
  amount: number;
  currency: string;
  provider: PaymentProvider;
  providerRef: string;
  billingCycle: BillingCycle;
  createdAt: string;
  completedAt: string | null;
  expiresAt: string | null;
  canceledAt: string | null;
  failureReason: string | null;
}

export interface PaymentHistoryResponse {
  items: PaymentResponse[];
  currentPage: number;
  totalPages: number;
  totalItems: number;
}

export interface PublicPricingResponse {
  monthlyPrice: number;
  annualPrice: number;
  currency: string;
  annualSavingsPercent: number;
  enabledProviders: string[];
}
