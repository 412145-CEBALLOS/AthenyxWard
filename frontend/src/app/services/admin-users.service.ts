import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AdminUser,
  AdminUserDetail,
  AdminUserListResponse,
  ResetTrialResponse,
  UpdateActiveRequest,
  UpdateRoleRequest,
  UserFilters,
  UserSearchResult,
} from '../models/admin-user.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AdminUsersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  list(filters: UserFilters = {}, page = 0, size = 20): Observable<AdminUserListResponse> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (filters.query) params = params.set('query', filters.query);
    if (filters.role) params = params.set('role', filters.role);
    if (filters.active !== undefined && filters.active !== '') {
      params = params.set('active', filters.active.toString());
    }

    return this.http.get<AdminUserListResponse>(`${this.baseUrl}/admin/users`, { params });
  }

  getDetail(id: number): Observable<AdminUserDetail> {
    return this.http.get<AdminUserDetail>(`${this.baseUrl}/admin/users/${id}`);
  }

  updateRole(id: number, role: string): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.baseUrl}/admin/users/${id}/role`, {
      role,
    } as UpdateRoleRequest);
  }

  updateActive(id: number, active: boolean): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`${this.baseUrl}/admin/users/${id}/active`, {
      active,
    } as UpdateActiveRequest);
  }

  resetTrial(id: number): Observable<ResetTrialResponse> {
    return this.http.post<ResetTrialResponse>(
      `${this.baseUrl}/admin/users/${id}/reset-trial`,
      {},
    );
  }

  softDelete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/admin/users/${id}`);
  }

  search(query: string, limit = 5): Observable<UserSearchResult[]> {
    const params = new HttpParams()
      .set('query', query)
      .set('limit', limit.toString());
    return this.http.get<UserSearchResult[]>(`${this.baseUrl}/admin/users/search`, {
      params,
    });
  }
}
