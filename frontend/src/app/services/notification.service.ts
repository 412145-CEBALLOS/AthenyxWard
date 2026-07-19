import {
  DestroyRef,
  Injectable,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of, Subject, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { UpcomingNotification } from '../models/notification.model';
import { ReminderService } from './reminder.service';
import { ToastService } from './toast.service';
import { AppConfigInitializerService } from './app-config-initializer.service';
import { AuthService } from './auth.service';

const UPCOMING_WINDOW_MS = 24 * 60 * 60 * 1000;
const SOON_WINDOW_MS = 24 * 60 * 60 * 1000;

/**
 * Self-reactive notification polling service.
 *
 * <p>Polls {@code GET /api/notifications/upcoming} on a configurable
 * interval (default 120 s) and exposes the latest snapshot as a signal.
 * Polling starts/stops automatically based on the user's role
 * (PREMIUM/ADMIN only) and the {@code NOTIFICATIONS_POLL_INTERVAL_SECONDS}
 * config value — no external orchestration needed.</p>
 *
 * <p>Three consumption paths:
 * <ul>
 *     <li><strong>Bell panel</strong> — every entry of the latest
 *         poll is rendered (no dedupe). Re-opening the bell shows
 *         the same list as long as the poll keeps returning it.</li>
 *     <li><strong>"Coming up" toast</strong> — fired once per
 *         reminder when it enters the 24h window before its due
 *         time. {@link ToastType} {@code info}, no action button.
 *         Message adapts to the distance: "en 5 min", "en 3 h",
 *         "en 1 d".</li>
 *     <li><strong>"Just fired" toast</strong> — fired once per
 *         reminder when the due time has passed. Warning type with
 *         a "Marcar hecho" action button. Also scheduled precisely
 *         via {@code setTimeout} so it fires at the exact moment
 *         the reminder becomes overdue, independent of the poll
 *         interval.</li>
 * </ul>
 *
 * <p>Both toasts are deduped in-memory via {@link shownIds}.
 * Refreshing the page re-fires them for still-pending reminders.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly reminderService = inject(ReminderService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly appConfig = inject(AppConfigInitializerService);
  private readonly authService = inject(AuthService);

  readonly notifications = signal<UpcomingNotification[]>([]);
  readonly lastError = signal<string | null>(null);

  readonly count = computed(() => this.notifications().length);
  readonly overdueCount = computed(() =>
    this.notifications().filter((n) => n.isOverdue).length
  );

  /**
   * Fires every time a reminder is successfully marked as done —
   * either from the bell or from the email viewer. Components that
   * hold their own copy of the reminder state (the {@code /reminders}
   * page, the home banner) subscribe to this to refresh locally
   * without the round-trip costs of a global signal + effect cascade.
   *
   * <p>Replaces the previous {@code recentlyDoneIds} signal, which
   * had a side effect of triggering up to five synchronous signal
   * updates in the same tick (notifications filter + recentlyDoneIds
   * set + remindersByEmail set + currentReminder set + items update)
   * and was hanging the render thread on click.</p>
   */
  readonly done$ = new Subject<number>();

  private readonly shownIds = new Set<number>();
  private readonly inflightMarkDone = new Set<number>();
  private intervalHandle: ReturnType<typeof setInterval> | null = null;
  private currentIntervalMs = 0;
  private inFlight = false;

  /** Maps reminderId → scheduled setTimeout handle for "just fired" toasts. */
  private readonly overdueTimers = new Map<number, ReturnType<typeof setTimeout>>();

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopPollingLoop();
      this.clearAllOverdueTimers();
    });

    effect(() => {
      const role = this.authService.user()?.role;
      const intervalMs = this.appConfig.pollIntervalSeconds() * 1000;
      const shouldPoll = (role === 'PREMIUM' || role === 'ADMIN') && intervalMs > 0;
      if (shouldPoll) {
        this.startPollingLoop(intervalMs);
      } else {
        this.stopPollingLoop();
      }
    });
  }

  fetchOnce(): Observable<UpcomingNotification[]> {
    return this.http
      .get<UpcomingNotification[]>(`${environment.apiUrl}/notifications/upcoming`)
      .pipe(catchError(() => of([] as UpcomingNotification[])));
  }

  /**
   * @deprecated No-op. Kept for backward compatibility with components
   * that call it directly. Polling is now self-managed internally via
   * the {@code effect()} in the constructor.
   */
  startPolling(intervalMs: number = 120_000): void {}

  /** @deprecated No-op. Polling is stopped internally via the {@code effect()}. */
  stopPolling(): void {}

  markShown(id: number): void {
    this.shownIds.add(id);
  }

  markDone(notification: UpcomingNotification): Observable<void> {
    return this.markDoneById(notification.reminderId);
  }

  markDoneById(reminderId: number): Observable<void> {
    if (this.inflightMarkDone.has(reminderId)) {
      return of(undefined);
    }
    this.inflightMarkDone.add(reminderId);
    return this.reminderService
      .update(reminderId, { done: true })
      .pipe(
        tap(() => {
          this.inflightMarkDone.delete(reminderId);
          this.markDoneLocally(reminderId);
        }),
        map(() => undefined),
        catchError((err: HttpErrorResponse) => {
          this.inflightMarkDone.delete(reminderId);
          if (err?.status === 404) {
            console.info(`[NotificationService] markDone 404 — stale notification for reminderId=${reminderId}, removed silently`);
            this.markDoneLocally(reminderId);
            return of(undefined);
          }
          this.toast.error('No se pudo marcar el recordatorio como hecho.');
          return throwError(() => err);
        })
      );
  }

  markDoneLocally(id: number): void {
    this.clearOverdueTimer(id);
    this.removeLocally(id);
    this.done$.next(id);
  }

  removeLocally(id: number): void {
    this.clearOverdueTimer(id);
    this.notifications.update((list) => list.filter((n) => n.reminderId !== id));
  }

  startPollingLoop(intervalMs: number): void {
    if (this.intervalHandle !== null && this.currentIntervalMs === intervalMs) {
      return;
    }
    this.stopPollingLoop();
    this.currentIntervalMs = intervalMs;
    this.poll();
    this.intervalHandle = setInterval(() => this.poll(), intervalMs);
  }

  stopPollingLoop(): void {
    if (this.intervalHandle !== null) {
      clearInterval(this.intervalHandle);
      this.intervalHandle = null;
      this.currentIntervalMs = 0;
    }
  }

  private poll(): void {
    if (this.inFlight) return;
    if (this.inflightMarkDone.size > 0) return;
    this.inFlight = true;
    this.fetchOnce().subscribe({
      next: (items) => {
        this.inFlight = false;
        this.lastError.set(null);
        this.notifications.set(items);
        this.fireToasts(items);
        this.scheduleOverdueTimers(items);
      },
      error: () => {
        this.inFlight = false;
        this.lastError.set('No se pudieron cargar las notificaciones.');
      },
    });
  }

  private fireToasts(items: UpcomingNotification[]): void {
    const now = Date.now();
    for (const item of items) {
      if (this.shownIds.has(item.reminderId)) continue;
      const target = new Date(item.reminderDate).getTime();
      if (Number.isNaN(target)) continue;
      const diffMs = target - now;
      if (item.isOverdue) {
        this.shownIds.add(item.reminderId);
        const subject = item.emailSubject || item.emailSender || 'un correo';
        this.toast.warning(`Tu recordatorio de "${subject}" acaba de vencer.`, {
          action: {
            label: 'Marcar hecho',
            onClick: () => this.markDone(item).subscribe(),
          },
        });
      } else if (diffMs > 0 && diffMs <= SOON_WINDOW_MS) {
        this.shownIds.add(item.reminderId);
        const subject = item.emailSubject || item.emailSender || 'un correo';
        const minutes = Math.max(1, Math.round(diffMs / 60_000));
        const hours = Math.max(1, Math.round(minutes / 60));
        const days = Math.max(1, Math.round(hours / 24));
        const when =
          days >= 1 ? `${days} d` :
          hours >= 1 ? `${hours} h` :
          `${minutes} min`;
        this.toast.info(`Recordatorio próximo: "${subject}" en ${when}.`);
      }
    }
  }

  /**
   * Schedules precise {@code setTimeout} timers for each upcoming
   * (non-overdue) reminder so the "just fired" toast fires at the
   * exact overdue moment, independent of the poll interval.
   *
   * <p>Timers are stored in {@link overdueTimers} and cleared when:
   * <ul>
   *   <li>the reminder is removed locally ({@link markDoneLocally},
   *       {@link removeLocally})</li>
   *   <li>a new poll returns the same reminder with a different date</li>
   *   <li>the service is destroyed</li>
   * </ul>
   */
  private scheduleOverdueTimers(items: UpcomingNotification[]): void {
    const currentIds = new Set(items.map(i => i.reminderId));
    for (const [id, handle] of this.overdueTimers) {
      if (!currentIds.has(id)) {
        clearTimeout(handle);
        this.overdueTimers.delete(id);
      }
    }
    const now = Date.now();
    for (const item of items) {
      if (item.isOverdue) continue;
      const target = new Date(item.reminderDate).getTime();
      if (Number.isNaN(target)) continue;
      const diffMs = target - now;
      if (diffMs <= 0) continue;
      if (diffMs > UPCOMING_WINDOW_MS) continue;
      const existing = this.overdueTimers.get(item.reminderId);
      if (existing !== undefined) continue;
      const handle = setTimeout(() => {
        this.overdueTimers.delete(item.reminderId);
        if (this.shownIds.has(item.reminderId)) return;
        this.shownIds.add(item.reminderId);
        const subject = item.emailSubject || item.emailSender || 'un correo';
        this.toast.warning(`Tu recordatorio de "${subject}" acaba de vencer.`, {
          action: {
            label: 'Marcar hecho',
            onClick: () => this.markDone(item).subscribe(),
          },
        });
      }, diffMs);
      this.overdueTimers.set(item.reminderId, handle);
    }
  }

  private clearOverdueTimer(id: number): void {
    const handle = this.overdueTimers.get(id);
    if (handle !== undefined) {
      clearTimeout(handle);
      this.overdueTimers.delete(id);
    }
  }

  private clearAllOverdueTimers(): void {
    for (const handle of this.overdueTimers.values()) {
      clearTimeout(handle);
    }
    this.overdueTimers.clear();
  }
}
