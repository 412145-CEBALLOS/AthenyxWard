import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog';
import { ReminderFormDialogComponent } from '../../components/reminder-form-dialog/reminder-form-dialog';
import { ReminderService } from '../../services/reminder.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
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
  imports: [PageShellComponent, ConfirmDialogComponent, ReminderFormDialogComponent],
  templateUrl: './reminders.html',
  styleUrl: './reminders.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemindersComponent implements OnInit, OnDestroy {
  private readonly reminderService = inject(ReminderService);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly onDestroy = new Subject<void>();

  readonly items = signal<Reminder[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly filter = signal<ReminderFilter>('all');
  readonly editingId = signal<number | null>(null);
  readonly editingState = signal<EditingState | null>(null);

  readonly confirmDelete = signal<Reminder | null>(null);
  readonly formDialogOpen = signal(false);
  readonly formEditing = signal<Reminder | null>(null);

  readonly isTrial = computed(() => this.authService.user()?.role === 'TRIAL');

  readonly visibleItems = computed<Reminder[]>(() => {
    const all = this.items();
    if (this.filter() === 'all') return all;
    const wantDone = this.filter() === 'done';
    return all.filter((r) => r.done === wantDone);
  });

  readonly pendingCount = computed(() => this.items().filter((r) => !r.done).length);
  readonly doneCount = computed(() => this.items().filter((r) => r.done).length);

  ngOnInit(): void {
    if (!this.isTrial()) {
      this.loadList();
    } else {
      this.loading.set(false);
    }
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

  toggleDone(reminder: Reminder): void {
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

  startEdit(reminder: Reminder): void {
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

  cancelEdit(): void {
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

  saveEdit(reminder: Reminder): void {
    const state = this.editingState();
    if (!state || !state.date || !state.time) {
      this.toast.error('Fecha y hora son obligatorias.');
      return;
    }
    const reminderDate = `${state.date}T${state.time}:00`;
    const message = state.message.trim() || null;
    const request: UpdateReminderRequest = { reminderDate, message };
    this.reminderService.update(reminder.id, request)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (updated) => {
          this.items.update((list) => list.map((r) => r.id === updated.id ? updated : r));
          this.cancelEdit();
          this.toast.success('Recordatorio actualizado.');
        },
        error: () => this.toast.error('No se pudo actualizar el recordatorio.'),
      });
  }

  openDeleteConfirm(reminder: Reminder): void {
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

  openNewReminder(): void {
    this.formEditing.set(null);
    this.formDialogOpen.set(true);
  }

  onFormSaved(saved: Reminder): void {
    this.formDialogOpen.set(false);
    this.formEditing.set(null);
    this.items.update((list) => {
      const idx = list.findIndex((r) => r.id === saved.id);
      if (idx === -1) {
        return [saved, ...list].sort((a, b) =>
          new Date(a.reminderDate).getTime() - new Date(b.reminderDate).getTime()
        );
      }
      const next = [...list];
      next[idx] = saved;
      return next;
    });
    this.toast.success('Recordatorio guardado.');
  }

  onFormCancelled(): void {
    this.formDialogOpen.set(false);
    this.formEditing.set(null);
  }

  retry(): void {
    this.error.set(false);
    this.loading.set(true);
    this.loadList();
  }

  /**
   * Test hook: re-runs the initial loader with whatever the
   * current {@link ReminderService.list} spy is configured to
   * return. Hidden behind a non-private name on purpose to be
   * reachable from the spec.
   */
  loadListPublic(): void {
    this.loadList();
  }

  goToPlan(): void {
    window.location.assign('/plan');
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
