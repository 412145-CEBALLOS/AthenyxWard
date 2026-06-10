import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { signal } from '@angular/core';
import { adminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

describe('adminGuard', () => {
  let mockAuth: { user: ReturnType<typeof signal<{ role: 'ADMIN' | 'PREMIUM' | 'TRIAL' } | null>> };
  let mockRouter: { createUrlTree: jasmine.Spy };
  let mockToast: { error: jasmine.Spy };

  beforeEach(() => {
    mockAuth = { user: signal<{ role: 'ADMIN' | 'PREMIUM' | 'TRIAL' } | null>(null) };
    mockRouter = { createUrlTree: jasmine.createSpy('createUrlTree').and.returnValue({} as UrlTree) };
    mockToast = { error: jasmine.createSpy('error') };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuth },
        { provide: Router, useValue: mockRouter },
        { provide: ToastService, useValue: mockToast },
      ],
    });
  });

  it('should allow access for ADMIN role', () => {
    mockAuth.user.set({ role: 'ADMIN' });
    let result: boolean | UrlTree | undefined;
    TestBed.runInInjectionContext(() => {
      result = adminGuard({} as never, { url: '/admin/users' } as never) as boolean | UrlTree;
    });
    expect(result).toBe(true);
    expect(mockToast.error).not.toHaveBeenCalled();
    expect(mockRouter.createUrlTree).not.toHaveBeenCalled();
  });

  it('should redirect and toast for non-admin role', () => {
    mockAuth.user.set({ role: 'PREMIUM' });
    let result: boolean | UrlTree | undefined;
    TestBed.runInInjectionContext(() => {
      result = adminGuard({} as never, { url: '/admin/users' } as never) as boolean | UrlTree;
    });
    expect(mockToast.error).toHaveBeenCalledWith('Acceso restringido a administradores.');
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/home']);
    expect(result).toBeDefined();
  });

  it('should redirect and toast when user is null', () => {
    mockAuth.user.set(null);
    TestBed.runInInjectionContext(() => {
      adminGuard({} as never, { url: '/admin/users' } as never);
    });
    expect(mockToast.error).toHaveBeenCalled();
    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/home']);
  });
});
