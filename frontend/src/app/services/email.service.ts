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

  constructor(private readonly http: HttpClient) {}

  /**
   * Fetches a page of up to 20 email summaries. Pages are 0-indexed.
   *
   * @param page zero-based page index (default {@code 0})
   */
  fetchEmails(page: number = 0): Observable<EmailPageResponse> {
    const params = new HttpParams().set('page', page.toString());
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
}
