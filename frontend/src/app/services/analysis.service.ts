import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { EmailAnalysisResult } from '../models/email-analysis.model';
import { environment } from '../../environments/environment';

/**
 * Thin HTTP wrapper over the {@code /api/emails/{id}/analyze*} endpoints.
 *
 * <p>Created in US 2.3 (Risk Percentage + Traffic Light) as part of the
 * service-infrastructure layer. The consumer in
 * {@code home.ts} (replacing the previous {@code computeMockAnalysis}
 * mock) is intentionally out of scope for US 2.3 and is implemented in
 * US 2.8 (Real-time analysis panel).</p>
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
}
