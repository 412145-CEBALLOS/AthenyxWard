import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '../services/toast.service';
import { resolveErrorMessage } from './http-error-messages';

/**
 * Set this token on a request to suppress the automatic error toast
 * for that single call. Useful when the caller wants to handle the
 * error itself (e.g. login page showing inline validation).
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

/**
 * Functional HTTP interceptor that turns non-401 errors into a
 * user-visible toast.
 *
 * <p>401s are intentionally left to {@link refreshInterceptor} — the
 * user will see a toast only if the refresh itself fails (sign-in
 * required). Messages are resolved via
 * {@link resolveErrorMessage} (prefers backend error strings, falls
 * back to a Spanish status-code map).</p>
 */
export const errorToastInterceptor: HttpInterceptorFn = (req, next) => {
  const platformId = inject(PLATFORM_ID);

  if (!isPlatformBrowser(platformId)) {
    return next(req);
  }

  if (req.context.get(SKIP_ERROR_TOAST)) {
    return next(req);
  }

  const toast = inject(ToastService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401) {
        toast.error(resolveErrorMessage(err));
      }
      return throwError(() => err);
    }),
  );
};
