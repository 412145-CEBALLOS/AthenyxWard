import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import {
  CreateReminderRequest,
  Reminder,
  ReminderFilter,
  ReminderSummary,
  UpdateReminderRequest,
} from '../models/reminder.model';
import { environment } from '../../environments/environment';

/**
 * HTTP wrapper over {@code /api/reminders/*}. Returns cold
 * observables; components are expected to subscribe with the
 * {@code takeUntil(this.onDestroy)} pattern.
 *
 * <p>For endpoints that return a single reminder, a 404 is mapped
 * to {@code of(null)} so the caller can branch on the value
 * without handling an error channel.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class ReminderService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  /**
   * Lists the current user's reminders. {@code filter} maps to the
   * {@code ?filter=} query param (see {@link ReminderFilter}).
   */
  list(filter: ReminderFilter = 'all'): Observable<Reminder[]> {
    const params = new HttpParams().set('filter', filter);
    return this.http
      .get<{ items: Reminder[] }>(`${this.baseUrl}/reminders`, { params })
      .pipe(map((res) => res.items ?? []));
  }

  /**
   * Returns the reminder configured for a given email, or
   * {@code null} when none exists. 404 is mapped to
   * {@code of(null)} — callers don't need an error branch.
   */
  getByEmail(emailId: number): Observable<ReminderSummary | null> {
    return this.http
      .get<ReminderSummary>(`${this.baseUrl}/reminders/by-email/${emailId}`)
      .pipe(catchError(() => of(null)));
  }

  /**
   * Creates a new reminder for the given email. Backend returns 403
   * for TRIAL users and 409 when a reminder already exists — both
   * cases propagate as HttpErrorResponse.
   */
  create(request: CreateReminderRequest): Observable<Reminder> {
    return this.http.post<Reminder>(`${this.baseUrl}/reminders`, request);
  }

  /**
   * Patches an existing reminder. Every field on
   * {@link UpdateReminderRequest} is optional — the backend only
   * touches non-null members.
   */
  update(id: number, request: UpdateReminderRequest): Observable<Reminder> {
    return this.http.patch<Reminder>(`${this.baseUrl}/reminders/${id}`, request);
  }

  /**
   * Removes a reminder. Returns a void observable that completes
   * on success.
   */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/reminders/${id}`);
  }

  /**
   * Bulk-deletes every completed reminder for the caller.
   * Returns the number of rows removed.
   */
  clearCompleted(): Observable<number> {
    return this.http
      .delete<{ deleted: number }>(`${this.baseUrl}/reminders/completed`)
      .pipe(map((res) => res.deleted ?? 0));
  }
}
