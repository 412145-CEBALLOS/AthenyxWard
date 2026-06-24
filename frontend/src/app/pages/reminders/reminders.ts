import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog';
import { ReminderService } from '../../services/reminder.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { NotificationService } from '../../services/notification.service';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Reminder,
  ReminderFilter,
  UpdateReminderRequest,
} from '../../models/reminder.model';

const UPCOMING_WINDOW_MS = 24 * 60 * 60 * 1000;

interface EditingState {
  date: string;
  time: string;
  message: string;
}

@Component({
  selector: 'app-reminders',
  standalone: true,
  imports: [PageShellComponent, ConfirmDialogComponent],
  templateUrl: './reminders.html',
  styleUrl: './reminders.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemindersComponent implements OnInit, OnDestroy {
  private readonly reminderService = inject(ReminderService);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly onDestroy = new Subject<void>();

  readonly items = signal<Reminder[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly filter = signal<ReminderFilter>('all');
  readonly editingId = signal<number | null>(null);
  readonly editingState = signal<EditingState | null>(null);

  readonly confirmDelete = signal<Reminder | null>(null);
  readonly confirmClearAll = signal(false);

  readonly isTrial = computed(() => this.authService.user()?.role === 'TRIAL');

  readonly pendingItems = computed<Reminder[]>(() => {
    const all = this.items();
    if (this.filter() === 'done') return [];
    return all
      .filter((r) => !r.done)
      .sort((a, b) => new Date(a.reminderDate).getTime() - new Date(b.reminderDate).getTime());
  });

  readonly doneItems = computed<Reminder[]>(() => {
    const all = this.items();
    if (this.filter() === 'pending') return [];
    return all
      .filter((r) => r.done)
      .sort((a, b) => new Date(b.reminderDate).getTime() - new Date(a.reminderDate).getTime());
  });

  readonly pendingCount = computed(() => this.items().filter((r) => !r.done).length);
  readonly doneCount = computed(() => this.items().filter((r) => r.done).length);
  readonly canClearCompleted = computed(() => this.doneCount() > 0);

  ngOnInit(): void {
    if (this.isTrial()) {
      this.loading.set(false);
      return;
    }
    // Skip the loader on the server — there are no cookies there
    // and the 401 response would be toasted into the SSR'd HTML
    // where the dismiss button can't be re-hydrated cleanly. The
    // browser-side ngOnInit run will issue the real request.
    if (!isPlatformBrowser(this.platformId)) {
      this.loading.set(false);
      return;
    }
    this.loadList();
  }

  constructor() {
    // Listen to the bell's done$ stream: when the user marks a
    // reminder done from the bell panel (or the email-viewer
    // banner), we flip the local copy so the card moves from
    // Pendientes to Completados without a refetch.
    this.notificationService.done$
      .pipe(takeUntil(this.onDestroy))
      .subscribe((reminderId) => {
        this.items.update((list) =>
          list.map((r) => (r.id === reminderId ? { ...r, done: true } : r))
        );
      });
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  setFilter(filter: ReminderFilter): void {
    this.filter.set(filter);
  }

  isUpcoming(reminder: Reminder): boolean {
    if (reminder.done) return false;
    const target = new Date(reminder.reminderDate).getTime();
    if (Number.isNaN(target)) return false;
    const now = Date.now();
    return target >= now - 60_000 && target <= now + UPCOMING_WINDOW_MS;
  }

  formatDate(reminder: Reminder): string {
    const date = new Date(reminder.reminderDate);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleString('es-ES', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  toggleDone(reminder: Reminder, event?: MouseEvent): void {
    event?.stopPropagation();
    const request: UpdateReminderRequest = { done: !reminder.done };
    this.reminderService.update(reminder.id, request)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (updated) => {
          this.items.update((list) => list.map((r) => r.id === updated.id ? updated : r));
          this.toast.success(updated.done ? 'Recordatorio completado.' : 'Recordatorio reactivado.');
        },
        error: () => this.toast.error('No se pudo actualizar el recordatorio.'),
      });
  }

  startEdit(reminder: Reminder, event?: MouseEvent): void {
    event?.stopPropagation();
    this.editingId.set(reminder.id);
    const iso = reminder.reminderDate;
    const safe = iso.length >= 16 ? iso.substring(0, 16) : iso;
    const [date, time] = safe.split('T');
    this.editingState.set({
      date: date ?? '',
      time: (time ?? '').substring(0, 5),
      message: reminder.message ?? '',
    });
  }

  cancelEdit(event?: MouseEvent): void {
    event?.stopPropagation();
    this.editingId.set(null);
    this.editingState.set(null);
  }

  onEditDate(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    const state = this.editingState();
    if (state) this.editingState.set({ ...state, date: value });
  }

  onEditTime(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    const state = this.editingState();
    if (state) this.editingState.set({ ...state, time: value });
  }

  onEditMessage(event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    const state = this.editingState();
    if (state) this.editingState.set({ ...state, message: value });
  }

  saveEdit(reminder: Reminder, event?: MouseEvent): void {
    event?.stopPropagation();
    const state = this.editingState();
    if (!state || !state.date || !state.time) {
      this.toast.error('Fecha y hora son obligatorias.');
      return;
    }
    // Build a UTC ISO with Z so the backend's UTC clock sees the
    // same instant the user picked on their local calendar.
    const local = new Date(`${state.date}T${state.time}:00`);
    if (!Number.isNaN(local.getTime()) && local.getTime() < Date.now() - 60_000) {
      this.toast.error('La fecha y hora deben ser en el futuro.');
      return;
    }
    const reminderDate = Number.isNaN(local.getTime())
      ? `${state.date}T${state.time}:00`
      : local.toISOString();
    const message = state.message.trim() || null;
    // Editing always reactivates the reminder.
    const request: UpdateReminderRequest = { reminderDate, message, done: false };
    this.reminderService.update(reminder.id, request)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (updated) => {
          this.items.update((list) => list.map((r) => r.id === updated.id ? updated : r));
          this.cancelEdit();
          this.toast.success('Recordatorio reactivado.');
        },
        error: () => this.toast.error('No se pudo actualizar el recordatorio.'),
      });
  }

  openDeleteConfirm(reminder: Reminder, event?: MouseEvent): void {
    event?.stopPropagation();
    this.confirmDelete.set(reminder);
  }

  closeDeleteConfirm(): void {
    this.confirmDelete.set(null);
  }

  confirmDeleteReminder(): void {
    const target = this.confirmDelete();
    if (!target) return;
    this.reminderService.delete(target.id)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: () => {
          this.items.update((list) => list.filter((r) => r.id !== target.id));
          this.toast.success('Recordatorio eliminado.');
          this.confirmDelete.set(null);
        },
        error: (err: HttpErrorResponse) => {
          this.toast.error('No se pudo eliminar el recordatorio.');
          this.confirmDelete.set(null);
        },
      });
  }

  openClearCompletedConfirm(): void {
    this.confirmClearAll.set(true);
  }

  closeClearCompletedConfirm(): void {
    this.confirmClearAll.set(false);
  }

  confirmClearAllCompleted(): void {
    this.confirmClearAll.set(false);
    this.reminderService.clearCompleted()
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (deleted) => {
          this.items.update((list) => list.filter((r) => !r.done));
          this.toast.success(
            deleted === 1
              ? 'Se eliminó 1 recordatorio completado.'
              : `Se eliminaron ${deleted} recordatorios completados.`
          );
        },
        error: () => this.toast.error('No se pudieron eliminar los recordatorios completados.'),
      });
  }

  retry(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.error.set(false);
    this.loading.set(true);
    this.loadList();
  }

  goToPlan(): void {
    window.location.assign('/plan');
  }

  /**
   * Click handler for the card body — navigates to the email
   * viewer for the reminder's email. Action buttons inside the
   * card stop propagation so they don't trigger this.
   */
  openEmail(reminder: Reminder, event?: MouseEvent): void {
    event?.stopPropagation();
    this.router.navigate(['/home'], { queryParams: { emailId: reminder.emailId } });
  }

  /**
   * Test hook: re-runs the initial loader with whatever the
   * current {@link ReminderService.list} spy is configured to
   * return.
   */
  loadListPublic(): void {
    this.loadList();
  }

  private loadList(): void {
    this.loading.set(true);
    this.error.set(false);
    this.reminderService.list('all')
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (items) => {
          this.items.set(items);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
          this.toast.error('No se pudieron cargar los recordatorios. Intenta nuevamente.');
        },
      });
  }
}
