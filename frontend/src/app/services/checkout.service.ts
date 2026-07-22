import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateCheckoutRequest,
  CreateCheckoutResponse,
  ConfirmPaymentRequest,
  CheckoutStatusResponse,
  PublicPricingResponse,
} from '../models/plan.model';
import { UserInfo } from '../models/user-info.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class CheckoutService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/checkout`;

  getStatus(paymentId: number): Observable<CheckoutStatusResponse> {
    return this.http.get<CheckoutStatusResponse>(`${this.baseUrl}/${paymentId}`);
  }

  createCheckout(request: CreateCheckoutRequest): Observable<CreateCheckoutResponse> {
    return this.http.post<CreateCheckoutResponse>(`${this.baseUrl}/create`, request);
  }

  confirmPayment(paymentId: number, token: string): Observable<UserInfo> {
    const request: ConfirmPaymentRequest = { paymentId, token };
    return this.http.post<UserInfo>(`${this.baseUrl}/confirm`, request);
  }

  cancelCheckout(paymentId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/cancel/${paymentId}`, {});
  }

  openInPopup(url: string, width = 600, height = 720): Window | null {
    if (typeof window === 'undefined' || !window.open) return null;
    const left = Math.max(0, (window.screen.width - width) / 2);
    const top = Math.max(0, (window.screen.height - height) / 2);
    return window.open(
      url,
      'mp-checkout',
      `width=${width},height=${height},left=${left},top=${top},resizable=yes,scrollbars=yes,status=no,toolbar=no,menubar=no`,
    );
  }

  getPricing(): Observable<PublicPricingResponse> {
    return this.http.get<PublicPricingResponse>(`${environment.apiUrl}/public/pricing`);
  }
}
