import {
  DestroyRef,
  Injectable,
  computed,
  inject,
  signal,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, Subject, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { UpcomingNotification } from '../models/notification.model';
import { ReminderService } from './reminder.service';
import { ToastService } from './toast.service';

const UPCOMING_WINDOW_MS = 24 * 60 * 60 * 1000;
const SOON_WINDOW_MS = 24 * 60 * 60 * 1000; // "coming up" toast covers the whole 24h window

/**
 * Polls {@code GET /api/notifications/upcoming} every 2 minutes
 * (default) and exposes the latest snapshot as a signal.
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
 *         a "Marcar hecho" action button.</li>
 * </ul>
 *
 * <p>Both toasts are deduped in-memory via {@link shownIds}.
 * Refreshing the page re-fires them for still-pending reminders.
 * The polling loop is started/stopped by
 * {@link LayoutComponent} (only for non-TRIAL users). It fires
 * once immediately on start and every {@link intervalMs}
 * thereafter.</p>
 */
@Injectable({
  providedIn: 'root',
})
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly reminderService = inject(ReminderService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  /** Latest poll response (empty until the first poll lands). */
  readonly notifications = signal<UpcomingNotification[]>([]);
  /** Most recent poll error, or {@code null} when the last poll succeeded. */
  readonly lastError = signal<string | null>(null);

  /** Derived: count of items the panel will display. */
  readonly count = computed(() => this.notifications().length);
  /** Derived: count of overdue items (those that triggered the warning toast). */
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

  constructor() {
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  /**
   * Returns the raw list once. Cold observable — useful for one-off
   * lookups. The polling loop calls this internally as well.
   */
  fetchOnce(): Observable<UpcomingNotification[]> {
    return this.http
      .get<UpcomingNotification[]>(`${environment.apiUrl}/notifications/upcoming`)
      .pipe(catchError(() => of([] as UpcomingNotification[])));
  }

  /**
   * Starts (or restarts) the polling loop. The first poll is fired
   * immediately; subsequent polls happen every {@link intervalMs}.
   * Safe to call multiple times — a running loop is reset to the
   * new interval.
   */
  startPolling(intervalMs: number = 120_000): void {
    this.stopPolling();
    this.currentIntervalMs = intervalMs;
    this.poll();
    this.intervalHandle = setInterval(() => this.poll(), intervalMs);
  }

  /** Stops the polling loop. No-op when already stopped. */
  stopPolling(): void {
    if (this.intervalHandle !== null) {
      clearInterval(this.intervalHandle);
      this.intervalHandle = null;
    }
  }

  /**
   * Marks a notification as already-shown so the toast won't
   * re-fire on the next poll. Idempotent.
   */
  markShown(id: number): void {
    this.shownIds.add(id);
  }

  /**
   * Marks the reminder as done on the backend. Idempotent: a
   * second call while the first is in flight is dropped silently.
   * On success the id is removed from the local notifications
   * cache and {@link done$} fires with the id.
   */
  markDone(notification: UpcomingNotification): Observable<void> {
    return this.markDoneById(notification.reminderId);
  }

  /**
   * Overload for callers that only know the reminder id (e.g. the
   * "Marcar hecho" button inside the email viewer, which already
   * has the reminder object but no {@link UpcomingNotification}
   * wrapper). Same idempotency + cache-update guarantees as
   * {@link markDone}.
   */
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
        catchError((err) => {
          this.inflightMarkDone.delete(reminderId);
          this.toast.error('No se pudo marcar el recordatorio como hecho.');
          return throwError(() => err);
        })
      );
  }

  /**
   * Removes a notification from the local cache and fires
   * {@link done$} so other components (the email-viewer banner,
   * the {@code /reminders} page) can refresh their own copy of the
   * state. Local + targeted — no global signal cascade.
   */
  markDoneLocally(id: number): void {
    this.removeLocally(id);
    this.done$.next(id);
  }

  /** Removes a notification from the local cache (after deletion, etc). */
  removeLocally(id: number): void {
    this.notifications.update((list) => list.filter((n) => n.reminderId !== id));
  }

  private poll(): void {
    // Don't overlap with a previous in-flight poll.
    if (this.inFlight) return;
    // Don't race with a markDone: the backend hasn't committed yet,
    // so the poll would re-fetch the just-done reminder and put it
    // back in the bell. The next poll cycle (≤ 2 min later) will
    // pick it up.
    if (this.inflightMarkDone.size > 0) return;
    this.inFlight = true;
    this.fetchOnce().subscribe({
      next: (items) => {
        this.inFlight = false;
        this.lastError.set(null);
        // The backend query already filters `done = false`, so the
        // response is authoritative once a PATCH has committed.
        // We don't need a client-side set lookup — `done$` plus
        // the in-flight guard is enough to keep the bell consistent.
        this.notifications.set(items);
        this.fireToasts(items);
      },
      error: () => {
        this.inFlight = false;
        this.lastError.set('No se pudieron cargar las notificaciones.');
      },
    });
  }

  /**
   * Fires two kinds of toasts based on the latest poll result:
   *
   * <ul>
   *   <li>items in the next 24h → info toast "Recordatorio próximo:
   *       … en X min/h/d".</li>
   *   <li>items that have just passed their due time → warning toast
   *       with "Marcar hecho" action.</li>
   * </ul>
   * Both are deduped via {@link shownIds}.
   */
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
      } else if (diffMs <= 0 && diffMs >= -UPCOMING_WINDOW_MS) {
        // Edge case: an item that just became overdue this poll
        // (isOverdue was false in the previous poll, true now).
        // Same code path as the overdue branch — but already handled
        // above. Keep the comment for clarity.
      }
    }
  }
}
