import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AiExplanation } from '../models/ai-explanation.model';
import { environment } from '../../environments/environment';

/**
 * Thin HTTP wrapper over the {@code POST /api/emails/{id}/explain}
 * endpoint (US 3.2 / US 3.3).
 *
 * <p>Backend contract:</p>
 * <ul>
 *   <li>HTTP 200 + {@link AiExplanation} — success, explanation ready</li>
 *   <li>HTTP 403 — TRIAL user exhausted their analysis quota</li>
 *   <li>HTTP 503 — AI (Ollama) is unavailable or timed out</li>
 * </ul>
 *
 * <p>Example usage (from {@code HomeComponent}):</p>
 * <pre>
 * this.aiExplanationService.explain(emailId).pipe(
 *   takeUntil(this.onDestroy)
 * ).subscribe({
 *   next: (result) => {
 *     this.aiExplanation.set(result);
 *     this.aiState.set('ready');
 *   },
 *   error: (err: HttpErrorResponse) => {
 *     if (err.status === 403) {
 *       this.aiState.set('unavailable-trial');
 *       this.toast.error('Has alcanzado el límite de análisis…');
 *     } else {
 *       this.aiState.set('error');
 *       this.toast.error('IA no disponible', {
 *         action: { label: 'Reintentar', onClick: () => this.onExplainRequest() }
 *       });
 *     }
 *   }
 * });
 * </pre>
 *
 * <p>All methods return cold observables — components are expected to
 * subscribe with the {@code takeUntil(this.onDestroy)} pattern.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class AiExplanationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiUrl;

  /**
   * Requests an AI-generated explanation for the given email.
   *
   * @param emailId the id of the email to explain
   * @returns an observable that emits the explanation on success
   * @throws error with status 403 for TRIAL quota exhaustion
   * @throws error with status 503 when AI is unavailable or times out
   */
  explain(emailId: number): Observable<AiExplanation> {
    return this.http.post<AiExplanation>(
      `${this.baseUrl}/emails/${emailId}/explain`,
      {},
    );
  }
}
