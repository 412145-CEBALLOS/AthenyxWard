import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, shareReplay, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

/**
 * Endpoints that must not trigger the refresh-and-retry flow.
 */
const REFRESH_BLACKLIST = ['/api/auth/refresh', '/api/auth/login-url', '/api/auth/logout', '/api/auth/logout-all'];

/**
 * HTTP interceptor that transparently rotates the access token on 401.
 *
 * <p>On a 401 response from any non-blacklisted endpoint the
 * interceptor:
 * <ol>
 *     <li>Calls {@code POST /api/auth/refresh} (only one in-flight at
 *         a time — concurrent 401s share the same refresh via
 *         {@code shareReplay}).</li>
 *     <li>On success, replays the original request with
 *         {@code withCredentials: true}.</li>
 *     <li>On failure, marks the session as unrecoverable
 *         ({@link AuthService.refreshFailed}), clears the cached user
 *         and routes to {@code /login}.</li>
 * </ol>
 *
 * <p>Skipped entirely on the server (no cookies, no refresh).</p>
 */
export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!isPlatformBrowser(platformId)) {
    return next(req);
  }

  if (REFRESH_BLACKLIST.some((p) => req.url.includes(p))) {
    return next(req);
  }

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401) {
        return throwError(() => err);
      }

      return performRefresh(authService, router).pipe(
        switchMap(() => next(req.clone({ withCredentials: true }))),
      );
    }),
  );
};

let inflightRefresh: Observable<unknown> | null = null;

function performRefresh(authService: AuthService, router: Router): Observable<unknown> {
  if (!inflightRefresh) {
    inflightRefresh = authService.refresh().pipe(
      catchError((refreshErr) => {
        authService.refreshFailed.set(true);
        authService.currentUser.set(null);
        router.navigate(['/login']);
        return throwError(() => refreshErr);
      }),
      finalize(() => {
        inflightRefresh = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
  }
  return inflightRefresh;
}
