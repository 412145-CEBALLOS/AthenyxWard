import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AuditEntryResponse,
  AuditFilters,
  AuditPageResponse,
} from '../models/audit.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  getEntries(filters: AuditFilters = {}): Observable<AuditPageResponse> {
    let params = new HttpParams()
      .set('page', (filters.page ?? 0).toString())
      .set('size', (filters.size ?? 20).toString());

    if (filters.from) params = params.set('from', filters.from);
    if (filters.to) params = params.set('to', filters.to);
    if (filters.actor) params = params.set('actor', filters.actor);
    if (filters.action) params = params.set('action', filters.action);
    if (filters.severity) params = params.set('severity', filters.severity);
    if (filters.query) params = params.set('query', filters.query);

    return this.http.get<AuditPageResponse>(`${this.baseUrl}/admin/audit`, {
      params,
    });
  }

  getExportUrl(filters: AuditFilters = {}): string {
    const params = new URLSearchParams();
    if (filters.from) params.set('from', filters.from);
    if (filters.to) params.set('to', filters.to);
    if (filters.actor) params.set('actor', filters.actor);
    if (filters.action) params.set('action', filters.action);
    if (filters.severity) params.set('severity', filters.severity);
    return `${this.baseUrl}/admin/audit/export?${params.toString()}`;
  }
}
