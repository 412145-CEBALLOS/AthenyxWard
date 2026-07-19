import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ConfigCategory, ConfigEntry, PurgeResult } from '../models/config.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ConfigService {
  constructor(private readonly http: HttpClient) {}

  getPublicConfig(): Observable<ConfigCategory[]> {
    return this.http.get<ConfigCategory[]>(`${environment.apiUrl}/public/config`);
  }

  getAllAdmin(): Observable<ConfigCategory[]> {
    return this.http.get<ConfigCategory[]>(`${environment.apiUrl}/admin/config`);
  }

  getEntry(key: string): Observable<ConfigEntry> {
    return this.http.get<ConfigEntry>(`${environment.apiUrl}/admin/config/${key}`);
  }

  updateEntry(key: string, value: string): Observable<ConfigEntry> {
    return this.http.put<ConfigEntry>(`${environment.apiUrl}/admin/config/${key}`, { value });
  }

  purgeNow(key: 'AUDIT_RETENTION_DAYS' | 'EMAIL_RETENTION_DAYS'): Observable<PurgeResult> {
    return this.http.post<PurgeResult>(`${environment.apiUrl}/admin/config/${key}/purge-now`, {});
  }
}
