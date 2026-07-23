import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AdminStatsResponse, StatsPeriod, UserStatsResponse } from '../models/stats.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class StatsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  getUserStats(period: StatsPeriod = 'week'): Observable<UserStatsResponse> {
    const params = new HttpParams().set('period', period);
    return this.http.get<UserStatsResponse>(`${this.baseUrl}/stats/user`, {
      params,
    });
  }

  getAdminStats(period: StatsPeriod = 'week'): Observable<AdminStatsResponse> {
    const params = new HttpParams().set('period', period);
    return this.http.get<AdminStatsResponse>(`${this.baseUrl}/stats/admin`, {
      params,
    });
  }
}
