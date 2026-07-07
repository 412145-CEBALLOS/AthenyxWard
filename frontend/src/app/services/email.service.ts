import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { EmailDetail, EmailPageResponse, EmailSummary, ImportantToggleResponse } from '../models/email-summary.model';
import { environment } from '../../environments/environment';

/**
 * Thin HTTP wrapper over the {@code /api/emails/*} endpoints. Returns
 * cold observables — components are expected to subscribe with the
 * {@code takeUntil(this.onDestroy)} pattern.
 */
@Injectable({
  providedIn: 'root',
})
export class EmailService {
  readonly importantCount = signal<number>(0);
  readonly hiddenCount = signal<number>(0);

  constructor(private readonly http: HttpClient) {}

  /**
   * Fetches a page of email summaries. Pages are 0-indexed. The
   * default page size is 20; pass {@code size} to override
   * (server-side cap is 50 — out-of-range values are clamped).
   *
   * <p>When {@code q} is non-blank, the backend runs a case-insensitive
   * {@code @Query} LIKE search over the persisted inbox
   * ({@code subject}, {@code sender}, {@code senderName}, {@code snippet}).
   * Null / undefined / whitespace-only values are dropped from the
   * URL so the request falls back to the default Gmail listing.</p>
   *
   * @param page zero-based page index (default {@code 0})
   * @param q optional search term (US 3.7); trimmed client-side
   * @param size optional page size; null/undefined → server default
   */
  fetchEmails(page: number = 0, q?: string | null, size?: number | null): Observable<EmailPageResponse> {
    let params = new HttpParams().set('page', page.toString());
    const trimmed = q?.trim();
    if (trimmed) {
      params = params.set('q', trimmed);
    }
    if (size && size > 0) {
      params = params.set('size', size.toString());
    }
    return this.http.get<EmailPageResponse>(`${environment.apiUrl}/emails/fetch`, { params });
  }

  /**
   * Loads the full body for a single email. Marks the message as
   * read server-side.
   *
   * @param emailId local database id of the email
   */
  getEmailDetail(emailId: number): Observable<EmailDetail> {
    return this.http.get<EmailDetail>(`${environment.apiUrl}/emails/${emailId}`);
  }

  /**
   * Fetches all emails marked as important for the current user.
   */
  fetchImportantEmails(): Observable<EmailSummary[]> {
    return this.http.get<EmailSummary[]>(`${environment.apiUrl}/emails/important`);
  }

  /**
   * Refreshes the important email count signal from the backend.
   */
  refreshImportantCount(): void {
    this.http.get<{ count: number }>(`${environment.apiUrl}/emails/important/count`).pipe(
      tap((res) => this.importantCount.set(res.count))
    ).subscribe();
  }

  /**
   * Toggles the important flag on an email. Returns the new state.
   *
   * @param emailId local database id of the email
   */
  toggleImportant(emailId: number): Observable<ImportantToggleResponse> {
    return this.http.post<ImportantToggleResponse>(
      `${environment.apiUrl}/emails/${emailId}/important`,
      {}
    ).pipe(
      tap((res) => {
        this.importantCount.update((c) => res.isImportant ? c + 1 : c - 1);
      })
    );
  }

  hide(emailId: number): Observable<{ emailId: number; isHidden: boolean }> {
    return this.http.post<{ emailId: number; isHidden: boolean }>(
      `${environment.apiUrl}/emails/${emailId}/hide`,
      {}
    ).pipe(
      tap((res) => {
        this.hiddenCount.update((c) => res.isHidden ? c + 1 : c - 1);
      })
    );
  }

  unhide(emailId: number): Observable<{ emailId: number; isHidden: boolean }> {
    return this.http.post<{ emailId: number; isHidden: boolean }>(
      `${environment.apiUrl}/emails/${emailId}/unhide`,
      {}
    ).pipe(
      tap((res) => {
        this.hiddenCount.update((c) => res.isHidden ? c + 1 : c - 1);
      })
    );
  }

  fetchHiddenEmails(): Observable<EmailSummary[]> {
    return this.http.get<EmailSummary[]>(`${environment.apiUrl}/emails/hidden`);
  }

  refreshHiddenCount(): void {
    this.http.get<{ count: number }>(`${environment.apiUrl}/emails/hidden/count`).pipe(
      tap((res) => this.hiddenCount.set(res.count))
    ).subscribe();
  }
}
