import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { UserUsage } from '../models/user-usage.model';
import { ActiveSession } from '../models/session.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private readonly http: HttpClient) {}

  getUsage(): Observable<UserUsage> {
    return this.http.get<UserUsage>(`${environment.apiUrl}/auth/me/usage`, { withCredentials: true });
  }

  getSessions(): Observable<ActiveSession[]> {
    return this.http.get<ActiveSession[]>(`${environment.apiUrl}/auth/me/sessions`, { withCredentials: true });
  }

  revokeSession(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/auth/me/sessions/${id}`, { withCredentials: true });
  }
}
