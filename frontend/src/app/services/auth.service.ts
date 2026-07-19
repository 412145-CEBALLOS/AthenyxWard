import { Injectable, signal, computed, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { isPlatformBrowser } from '@angular/common';
import { Observable, tap, catchError, of } from 'rxjs';
import { RefreshResponse, UserInfo } from '../models/user-info.model';
import { environment } from '../../environments/environment';

/**
 * Frontend authentication facade.
 *
 * <p>Holds the current user in a {@link signal} (read-only view
 * exposed via {@link user}) and exposes thin wrappers over the
 * {@code /api/auth/*} endpoints. The HTTP layer relies on cookies —
 * the SPA never reads JWTs directly.</p>
 *
 * <p>{@link refreshFailed} is flipped to {@code true} by the
 * {@link refreshInterceptor} when the refresh endpoint itself fails;
 * guards and components can branch on it if needed.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class AuthService {
  /** Writable signal holding the current user, or {@code null} when logged out. */
  readonly currentUser = signal<UserInfo | null>(null);
  /** Read-only alias for {@link currentUser}, suitable for templates. */
  readonly user = this.currentUser.asReadonly();
  /** Convenience boolean derived from {@link currentUser}. */
  readonly isLoggedIn = computed(() => this.currentUser() !== null);
  /** Set by the refresh interceptor when token rotation fails irrecoverably. */
  readonly refreshFailed = signal(false);

  private readonly platformId = inject(PLATFORM_ID);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  /**
   * Hits {@code GET /api/auth/me} and caches the response. On failure
   * (e.g. cookie missing/expired) the signal is reset to {@code null}
   * and the observable emits {@code null} — never throws.
   */
  checkAuth(): Observable<UserInfo | null> {
    return this.http.get<UserInfo>(`${environment.apiUrl}/auth/me`).pipe(
      tap((user) => this.currentUser.set(user)),
      catchError(() => {
        this.currentUser.set(null);
        return of(null);
      }),
    );
  }

  /** Returns the relative OAuth2 start URL exposed by the backend. */
  getLoginUrl(): Observable<{ url: string }> {
    return this.http.get<{ url: string }>(`${environment.apiUrl}/auth/login-url`);
  }

  /**
   * Revokes the current session and navigates to {@code /login} (browser only).
   */
  logout(): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${environment.apiUrl}/auth/logout`, null, { withCredentials: true }).pipe(
      tap(() => {
        this.currentUser.set(null);
        if (isPlatformBrowser(this.platformId)) {
          this.router.navigate(['/login']);
        }
      }),
    );
  }

  /**
   * Revokes every active session for the current user (logout from all
   * devices) and navigates to {@code /login}.
   */
  logoutAll(): Observable<{ message: string; revoked: number }> {
    return this.http
      .post<{ message: string; revoked: number }>(`${environment.apiUrl}/auth/logout-all`, null, { withCredentials: true })
      .pipe(
        tap(() => {
          this.currentUser.set(null);
          if (isPlatformBrowser(this.platformId)) {
            this.router.navigate(['/login']);
          }
        }),
      );
  }

  /**
   * Rotates the refresh token. The {@link refreshInterceptor} calls
   * this on 401 responses; calling it manually is rarely useful.
   */
  refresh(): Observable<RefreshResponse> {
    return this.http.post<RefreshResponse>(`${environment.apiUrl}/auth/refresh`, {}, { withCredentials: true });
  }

  /**
   * Persists the accessibility-mode toggle and updates the cached
   * user signal.
   *
   * @param enabled desired state of the accessibility mode
   */
  updateAccessibilityMode(enabled: boolean): Observable<UserInfo> {
    return this.http
      .put<UserInfo>(
        `${environment.apiUrl}/auth/me/accessibility-mode`,
        { accessibilityMode: enabled },
        { withCredentials: true },
      )
      .pipe(
        tap((user) => this.currentUser.set(user)),
      );
  }

  /**
   * Persists the legal terms acceptance and updates the cached user signal.
   * Idempotent — a second call with the same version returns the existing user
   * without modifying the original acceptance timestamp.
   *
   * @param version the version identifier of the accepted terms (e.g. "v1.0")
   */
  acceptTerms(version: string): Observable<UserInfo> {
    return this.http
      .post<UserInfo>(
        `${environment.apiUrl}/auth/accept-terms`,
        { version },
        { withCredentials: true },
      )
      .pipe(
        tap((user) => this.currentUser.set(user)),
      );
  }
}
