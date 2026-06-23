import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  AnalysisHistoryFilters,
  AnalysisHistoryResponse,
  EmailAnalysisResult,
} from '../models/email-analysis.model';
import { environment } from '../../environments/environment';

/**
 * Thin HTTP wrapper over the {@code /api/emails/{id}/analyze*} and
 * {@code /api/analysis/history} endpoints.
 *
 * <p>Created in US 2.3 (Risk Percentage + Traffic Light) as part of the
 * service-infrastructure layer. Consumed in US 2.8 (Real-time analysis
 * panel) by {@code home.ts}, replacing the previous in-memory mock
 * analysis. Extended in US 2.4 (Analysis History) with
 * {@link #getHistory} for the {@code /history} page.</p>
 *
 * <p>All methods return cold observables — components are expected to
 * subscribe with the {@code takeUntil(this.onDestroy)} pattern.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  /**
   * Triggers a fresh heuristic analysis of the given email. The backend
   * is responsible for the 24 h cache and the trial-limit check, so a
   * repeated call within the window returns the cached result.
   *
   * <p>Throws {@code TrialLimitExceededException} on the server (HTTP
   * 403) when a trial user has exhausted their quota.</p>
   */
  analyze(emailId: number): Observable<EmailAnalysisResult> {
    return this.http.post<EmailAnalysisResult>(
      `${this.baseUrl}/emails/${emailId}/analyze`,
      {}
    );
  }

  /**
   * Returns the latest persisted analysis for the given email, or
   * {@code null} if no analysis has ever been run. A 404 from the
   * backend is mapped to {@code of(null)} so callers do not need to
   * subscribe to an error channel for the "no analysis yet" case.
   */
  getLatest(emailId: number): Observable<EmailAnalysisResult | null> {
    return this.http
      .get<EmailAnalysisResult>(`${this.baseUrl}/emails/${emailId}/analysis`)
      .pipe(catchError(() => of(null)));
  }

  /**
   * Fetches the paginated analysis history for the current user.
   * Returns a 200 with {@code items: []} when the user has no
   * analyses matching the filters (never a 404).
   */
  getHistory(
    filters: AnalysisHistoryFilters = {},
  ): Observable<AnalysisHistoryResponse> {
    let params = new HttpParams()
      .set('page', (filters.page ?? 0).toString())
      .set('size', (filters.size ?? 20).toString());
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    return this.http.get<AnalysisHistoryResponse>(
      `${this.baseUrl}/analysis/history`,
      { params },
    );
  }
}
