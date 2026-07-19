import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser, DOCUMENT } from '@angular/common';
import { CanActivateFn, Router, RouterStateSnapshot, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { map, of, take } from 'rxjs';

const LEGAL_WHITELIST = ['/legal/terms', '/legal/privacy', '/login', '/account-disabled'];

function isLegalWhitelisted(url: string): boolean {
  return LEGAL_WHITELIST.some((prefix) => url.startsWith(prefix));
}

/**
 * {@link CanActivateFn} that allows the route only when the user is
 * logged in and has accepted the legal terms. Calls
 * {@link AuthService.checkAuth} which both populates the user signal
 * and reports the auth status in a single request.
 *
 * <p>During SSR the guard always returns {@code true} — Angular will
 * not pre-render the page as a redirect.</p>
 */
export const authGuard: CanActivateFn = (
  _route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  if (!isPlatformBrowser(platformId)) {
    return of(true);
  }

  return authService.checkAuth().pipe(
    take(1),
    map((user) => {
      if (!user) {
        return router.createUrlTree(['/login']);
      }
      if (user.termsAcceptedAt == null && !isLegalWhitelisted(state.url)) {
        return router.createUrlTree(['/legal/terms'], { queryParams: { next: state.url } });
      }
      return true;
    }),
  );
};
