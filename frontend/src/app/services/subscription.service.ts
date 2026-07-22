import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SubscriptionResponse, PaymentHistoryResponse } from '../models/plan.model';
import { UserInfo } from '../models/user-info.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class SubscriptionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/subscription`;

  getCurrent(): Observable<SubscriptionResponse> {
    return this.http.get<SubscriptionResponse>(`${this.baseUrl}/current`);
  }

  cancel(): Observable<UserInfo> {
    return this.http.post<UserInfo>(`${this.baseUrl}/cancel`, {});
  }

  getHistory(page = 0, size = 20): Observable<PaymentHistoryResponse> {
    return this.http.get<PaymentHistoryResponse>(`${this.baseUrl}/history`, {
      params: { page, size },
    });
  }
}
