import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * {@link CanActivateFn} that allows the route only for users whose
 * cached role is {@code ADMIN}. Non-admins get an error toast and a
 * redirect to {@code /home}.
 *
 * <p>Assumes {@link authGuard} ran first so the user signal is
 * populated.</p>
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  const role = auth.user()?.role;
  if (role === 'ADMIN') {
    return true;
  }

  toast.error('Acceso restringido a administradores.');
  return router.createUrlTree(['/home']);
};
