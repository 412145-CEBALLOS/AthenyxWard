import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog';
import { AuthService } from '../../services/auth.service';
import { UserService } from '../../services/user.service';
import { ToastService } from '../../services/toast.service';
import { AppConfigInitializerService } from '../../services/app-config-initializer.service';
import { ThemeService, Theme } from '../../services/theme.service';
import { UserUsage } from '../../models/user-usage.model';
import { ActiveSession } from '../../models/session.model';
import { UserInfo } from '../../models/user-info.model';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [PageShellComponent, ConfirmDialogComponent],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly toast = inject(ToastService);
  private readonly appConfig = inject(AppConfigInitializerService);
  private readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly onDestroy = new Subject<void>();

  readonly user = this.authService.user;
  readonly usage = signal<UserUsage | null>(null);
  readonly sessions = signal<ActiveSession[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly confirmRevoke = signal<ActiveSession | null>(null);
  readonly supportEmail = this.appConfig.supportEmail;
  readonly currentTheme = this.themeService.theme;

  onThemeChange(theme: Theme): void {
    this.themeService.setTheme(theme);
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  ngOnInit(): void {
    this.loading.set(true);
    this.error.set(false);

    this.userService.getUsage().pipe(takeUntil(this.onDestroy)).subscribe({
      next: (u) => {
        this.usage.set(u);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });

    this.userService.getSessions().pipe(takeUntil(this.onDestroy)).subscribe({
      next: (s) => this.sessions.set(s),
      error: () => {},
    });
  }

  onAccessibilityToggle(checked: boolean): void {
    this.authService.updateAccessibilityMode(checked).pipe(takeUntil(this.onDestroy)).subscribe({
      error: () => this.toast.error('No se pudo guardar el modo accesibilidad'),
    });
  }

  onRevokeRequest(session: ActiveSession): void {
    this.confirmRevoke.set(session);
  }

  onRevokeConfirm(): void {
    const session = this.confirmRevoke();
    if (!session) return;
    this.confirmRevoke.set(null);

    this.userService.revokeSession(session.id).pipe(takeUntil(this.onDestroy)).subscribe({
      next: () => {
        this.sessions.update((list) => list.filter((s) => s.id !== session.id));
        this.usage.update((u) => {
          if (!u) return u;
          return { ...u, sessions: { ...u.sessions, active: u.sessions.active - 1 } };
        });
        this.toast.error('Sesión revocada');
      },
      error: () => this.toast.error('No se pudo revocar la sesión'),
    });
  }

  onLogoutAll(): void {
    this.authService.logoutAll().pipe(takeUntil(this.onDestroy)).subscribe({
      next: () => {
        this.authService.currentUser.set(null);
        this.router.navigate(['/login']);
      },
      error: () => this.toast.error('No se pudieron cerrar las sesiones'),
    });
  }

  onRevokeCancel(): void {
    this.confirmRevoke.set(null);
  }

  roleChip(role: UserInfo['role']): { label: string; cssClass: string } {
    switch (role) {
      case 'ADMIN': return { label: 'Administrador', cssClass: 'role-admin' };
      case 'PREMIUM': return { label: 'Premium', cssClass: 'role-premium' };
      case 'TRIAL': return { label: 'Prueba', cssClass: 'role-trial' };
    }
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) return '—';
    try {
      const date = new Date(iso);
      const diffMs = Date.now() - date.getTime();
      const diffMins = Math.floor(diffMs / 60000);
      const diffHrs = Math.floor(diffMins / 60);
      const diffDays = Math.floor(diffHrs / 24);

      if (diffMins < 2) return 'ahora mismo';
      if (diffMins < 60) return `hace ${diffMins} min`;
      if (diffHrs < 24) return `hace ${diffHrs} h`;
      if (diffDays < 30) return `hace ${diffDays} días`;

      return new Intl.DateTimeFormat('es', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
    } catch {
      return '—';
    }
  }

  formatCount(n: number): string {
    if (n >= 1000) return `${(n / 1000).toFixed(1)}k`.replace('.0k', 'k');
    return n.toString();
  }

  formatOldestDate(iso: string | null): string {
    if (!iso) return 'Sin datos';
    try {
      return new Intl.DateTimeFormat('es', { day: '2-digit', month: 'long', year: 'numeric' }).format(new Date(iso));
    } catch {
      return '—';
    }
  }

  trialProgressPercent(used: number, limit: number): number {
    return Math.min(100, Math.round((used / limit) * 100));
  }

  readonly roleInfo = computed(() => this.roleChip(this.user()?.role ?? 'TRIAL'));
}
