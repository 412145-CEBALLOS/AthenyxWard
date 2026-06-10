import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EmailDetail, EmailPageResponse, EmailSummary } from '../models/email-summary.model';
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
}
