import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, output, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { NavigationEnd, Router } from '@angular/router';
import { Subject, filter, takeUntil } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { EmailSearchService } from '../../services/email-search.service';
import { EmailSearchResultsComponent } from '../email-search-results/email-search-results';
import { UpcomingNotification } from '../../models/notification.model';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [EmailSearchResultsComponent],
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class HeaderComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);
  private readonly platformId = inject(PLATFORM_ID);
  /** Bound to the header search input (US 3.7). */
  readonly emailSearchService = inject(EmailSearchService);
  readonly toggleSidebar = output<void>();
  readonly user = this.authService.user;
  readonly notificationsOpen = signal(false);

  /** Pulled straight from the polling service. */
  readonly notifications = this.notificationService.notifications;
  /** Total count shown on the bell badge. */
  readonly badgeCount = computed(() => this.notifications().length);
  /**
   * True while the active route is the inbox. Used to gate the
   * desktop search dropdown (which only makes sense on the home
   * page — other pages don't have a queryable email list).
   */
  readonly isHomeRoute = signal(false);

  private blurCloseTimer: ReturnType<typeof setTimeout> | null = null;
  private readonly onDestroy$ = new Subject<void>();

  private readonly onDocClick = (event: MouseEvent): void => {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.closeNotifications();
    }
  };

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.addEventListener('click', this.onDocClick);
    }
    this.isHomeRoute.set(this.router.url.startsWith('/home'));
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      takeUntil(this.onDestroy$),
    ).subscribe((e) => this.isHomeRoute.set(e.urlAfterRedirects.startsWith('/home')));
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.removeEventListener('click', this.onDocClick);
    }
    if (this.blurCloseTimer !== null) {
      clearTimeout(this.blurCloseTimer);
      this.blurCloseTimer = null;
    }
    this.onDestroy$.next();
    this.onDestroy$.complete();
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
   * Bound to the search input's {@code (input)} event. Pushes the
   * raw value into {@link EmailSearchService}, which debounces it
   * 300 ms before the home page reacts.
   */
  onSearchInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.emailSearchService.set(value);
  }

  /**
   * Bound to the inline "Limpiar" button. Goes through the same
   * debounce pipe as user input so the home page only refetches
   * once, 300 ms after the click.
   */
  onClearSearch(): void {
    this.emailSearchService.clear();
  }

  /**
   * Bound to the search input's {@code (focus)} event. Opens the
   * desktop dropdown (US 3.7) and cancels any pending blur-close
   * timer so the dropdown stays open while the user is moving the
   * focus between the input and the results.
   */
  onSearchFocus(): void {
    if (this.blurCloseTimer !== null) {
      clearTimeout(this.blurCloseTimer);
      this.blurCloseTimer = null;
    }
    this.emailSearchService.open();
  }

  /**
   * Bound to the search input's {@code (blur)} event. Schedules a
   * 150 ms delayed close so a {@code mousedown} on a result row
   * (which fires before {@code blur}) has time to register before
   * the dropdown disappears.
   */
  onSearchBlur(): void {
    if (this.blurCloseTimer !== null) clearTimeout(this.blurCloseTimer);
    this.blurCloseTimer = setTimeout(() => {
      this.emailSearchService.close();
      this.blurCloseTimer = null;
    }, 150);
  }

  /**
   * Bound to the search input's {@code keydown.escape}. Closes the
   * dropdown immediately and blurs the input so the user can keep
   * tabbing through the page.
   */
  onSearchEscape(event: Event): void {
    event.preventDefault();
    this.emailSearchService.close();
    (event.target as HTMLInputElement).blur();
  }

  /**
   * Bound to the search input's {@code keydown.enter} (US 3.7). On
   * desktop, applies the current search to the inbox and closes
   * the dropdown — same effect as the "Ver todos los resultados"
   * footer. On mobile the inbox is already filtered live, so this
   * is a no-op for the inbox but still closes the dropdown if
   * somehow open.
   */
  onSearchEnter(event: Event): void {
    event.preventDefault();
    const term = this.emailSearchService.term().trim();
    if (term) {
      this.emailSearchService.applyToInbox(term);
    }
    this.emailSearchService.close();
    (event.target as HTMLInputElement).blur();
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
