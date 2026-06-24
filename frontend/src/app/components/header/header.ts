import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, output, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { UpcomingNotification } from '../../models/notification.model';

@Component({
  selector: 'app-header',
  standalone: true,
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class HeaderComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);
  private readonly platformId = inject(PLATFORM_ID);
  readonly toggleSidebar = output<void>();
  readonly user = this.authService.user;
  readonly notificationsOpen = signal(false);

  /** Pulled straight from the polling service. */
  readonly notifications = this.notificationService.notifications;
  /** Total count shown on the bell badge. */
  readonly badgeCount = computed(() => this.notifications().length);

  private readonly onDocClick = (event: MouseEvent): void => {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.closeNotifications();
    }
  };

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.addEventListener('click', this.onDocClick);
    }
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.removeEventListener('click', this.onDocClick);
    }
  }

  toggleSidebarButton() {
    this.toggleSidebar.emit();
  }

  toggleNotifications(): void {
    this.notificationsOpen.update((v) => !v);
  }

  closeNotifications(): void {
    this.notificationsOpen.set(false);
  }

  /**
   * Renders the relative time string for a reminder date — "en
   * 2 h" / "hace 5 min" / "pasado" — depending on how close the
   * date is to now.
   */
  formatRelative(iso: string, isOverdue: boolean): string {
    const target = new Date(iso).getTime();
    if (Number.isNaN(target)) return '';
    const diffMs = target - Date.now();
    const abs = Math.abs(diffMs);
    const minutes = Math.max(1, Math.round(abs / 60_000));
    const hours = Math.max(1, Math.round(minutes / 60));
    const days = Math.max(1, Math.round(hours / 24));
    if (isOverdue) {
      if (minutes < 60) return `hace ${minutes} min`;
      if (hours < 24) return `hace ${hours} h`;
      return `hace ${days} d`;
    }
    if (minutes < 60) return `en ${minutes} min`;
    if (hours < 24) return `en ${hours} h`;
    return `en ${days} d`;
  }

  markDone(notification: UpcomingNotification, event: MouseEvent): void {
    // The PATCH runs synchronously inside the click handler — any
    // deferral (e.g. setTimeout(0)) runs the HTTP call outside
    // Angular's zone, which combined with the bell's re-render
    // inside the same tick was hanging the event loop and locking
    // the page (scroll + clicks unresponsive until the tab was
    // closed and reopened). preventDefault + stopPropagation
    // stop the click from bubbling to the <li> openEmail handler
    // and from triggering a browser default action.
    event.preventDefault();
    event.stopPropagation();
    // Subscribe with both handlers so the request can never
    // surface as an unhandled rejection. The notification service
    // already pushes a user-facing toast on failure; we just
    // swallow the error here to keep the page's change detection
    // alive.
    this.notificationService.markDone(notification).subscribe({
      next: () => {},
      error: () => {},
    });
  }

  openEmail(notification: UpcomingNotification, event: MouseEvent): void {
    event.stopPropagation();
    this.closeNotifications();
    this.router.navigate(['/home'], { queryParams: { emailId: notification.emailId } });
  }

  logout(): void {
    this.authService.logout().subscribe();
  }

  logoutAll(): void {
    this.authService.logoutAll().subscribe();
  }
}
