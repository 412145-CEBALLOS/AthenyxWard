import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { map, take } from 'rxjs';

/**
 * {@link CanActivateFn} that allows the route only for users whose
 * role is {@code ADMIN}. Non-admins get an error toast and a redirect
 * to {@code /home}.
 *
 * <p>During SSR the guard always returns {@code true} — the client
 * handles the real redirect after hydration.</p>
 *
 * <p>On the client, if the user signal is not yet populated (e.g., guard
 * runs before {@link authGuard} resolved), this guard calls
 * {@link AuthService.checkAuth} directly to ensure the user is loaded
 * before checking the role.</p>
 */
export const adminGuard: CanActivateFn = () => {
  if (!isPlatformBrowser(inject(PLATFORM_ID))) {
    return true;
  }

  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  if (auth.user()?.role === 'ADMIN') {
    return true;
  }

  if (auth.user() !== null) {
    toast.error('Acceso restringido a administradores.');
    return router.createUrlTree(['/home']);
  }

  return auth.checkAuth().pipe(
    take(1),
    map((user) => {
      if (user?.role === 'ADMIN') {
        return true;
      }
      toast.error('Acceso restringido a administradores.');
      return router.createUrlTree(['/home']);
    }),
  );
};
