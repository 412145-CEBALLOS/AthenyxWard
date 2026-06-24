import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CreateReminderRequest,
  Reminder,
  UpdateReminderRequest,
} from '../../models/reminder.model';
import { ReminderService } from '../../services/reminder.service';
import { ToastService } from '../../services/toast.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, takeUntil } from 'rxjs';

const MAX_MESSAGE_LENGTH = 500;

/**
 * Modal for creating or editing a reminder. When {@link reminder}
 * is non-null, the dialog operates in edit mode (PATCH) and the
 * title reflects that. When {@link emailId} is set and
 * {@link reminder} is null, the dialog creates a new reminder
 * (POST).
 *
 * <p>On 409 (the user already has a reminder for the same email
 * — only reachable when creating), the existing reminder is
 * fetched and the dialog reloads in edit mode automatically.</p>
 */
@Component({
  selector: 'app-reminder-form-dialog',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './reminder-form-dialog.html',
  styleUrl: './reminder-form-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReminderFormDialogComponent {
  private readonly reminderService = inject(ReminderService);
  private readonly toast = inject(ToastService);

  /** When set, the dialog is in edit mode. */
  readonly reminder = input<Reminder | null>(null);
  /** Required when creating. Ignored in edit mode. */
  readonly emailId = input<number | null>(null);
  /** Used as fallback for the message field when none is set. */
  readonly defaultDate = input<string | null>(null);

  readonly saved = output<Reminder>();
  readonly cancelled = output<void>();

  readonly isEdit = computed(() => this.reminder() !== null);
  readonly title = computed(() => this.isEdit() ? 'Editar recordatorio' : 'Crear recordatorio');

  readonly date = signal<string>('');
  readonly time = signal<string>('');
  readonly message = signal<string>('');
  readonly submitting = signal(false);

  readonly messageTooLong = computed(() => this.message().length > MAX_MESSAGE_LENGTH);
  /**
   * True when the user picked a date+time that is in the past (with
   * a 1-minute tolerance so "right now" is still allowed). The
   * submit button disables itself when this is true.
   */
  readonly isPast = computed(() => {
    const d = this.date();
    const t = this.time();
    if (!d || !t) return false;
    const target = new Date(`${d}T${t}:00`).getTime();
    if (Number.isNaN(target)) return false;
    return target < Date.now() - 60_000;
  });
  readonly canSubmit = computed(() => {
    if (this.submitting()) return false;
    if (!this.date() || !this.time()) return false;
    if (this.messageTooLong()) return false;
    if (this.isPast()) return false;
    if (this.isEdit()) return true;
    return this.emailId() !== null;
  });

  private readonly onDestroy = new Subject<void>();

  constructor() {
    queueMicrotask(() => this.hydrate());
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  onDateInput(event: Event): void {
    this.date.set((event.target as HTMLInputElement).value);
  }

  onTimeInput(event: Event): void {
    this.time.set((event.target as HTMLInputElement).value);
  }

  onMessageInput(event: Event): void {
    this.message.set((event.target as HTMLInputElement).value);
  }

  onSubmit(): void {
    if (!this.canSubmit()) return;
    // The <input type="date"> + <input type="time"> emit local
    // clock values (e.g. "2026-06-24" + "15:00" → local 15:00).
    // Convert to UTC ISO with Z so the backend (which compares
    // against its own UTC clock) doesn't see the reminder as
    // overdue when the user is in a non-UTC timezone.
    const reminderDate = this.buildUtcIso(this.date(), this.time());
    const message = this.message().trim() || null;

    this.submitting.set(true);

    if (this.isEdit()) {
      // Editing always reactivates the reminder (sets done=false).
      // The user can re-check it from the /reminders page or the
      // banner if they really want to keep it done.
      const request: UpdateReminderRequest = {
        reminderDate,
        message,
        done: false,
      };
      this.reminderService.update(this.reminder()!.id, request)
        .pipe(takeUntil(this.onDestroy))
        .subscribe({
          next: (saved) => {
            this.submitting.set(false);
            this.saved.emit(saved);
          },
          error: (err: HttpErrorResponse) => {
            this.submitting.set(false);
            this.toast.error('No se pudo actualizar el recordatorio. Intenta nuevamente.');
          },
        });
      return;
    }

    const emailId = this.emailId();
    if (emailId == null) {
      this.submitting.set(false);
      return;
    }

    const request: CreateReminderRequest = {
      emailId,
      reminderDate,
      message,
    };
    this.reminderService.create(request)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (saved) => {
          this.submitting.set(false);
          this.saved.emit(saved);
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          if (err.status === 403) {
            this.toast.warning('Los recordatorios están disponibles en el plan Premium.');
            this.cancelled.emit();
            return;
          }
          if (err.status === 409) {
            // Fetch the existing reminder and reopen in edit mode.
            this.reminderService.getByEmail(emailId)
              .pipe(takeUntil(this.onDestroy))
              .subscribe({
                next: (existing) => {
                  if (existing) {
                    this.toast.info('Ya tienes un recordatorio para este correo. Abriendo para editar.');
                    this.hydrateFromSummary(existing.id, existing.reminderDate);
                  } else {
                    this.toast.error('Conflicto al crear el recordatorio.');
                  }
                },
                error: () => this.toast.error('Conflicto al crear el recordatorio.'),
              });
            return;
          }
          this.toast.error('No se pudo crear el recordatorio. Intenta nuevamente.');
        },
      });
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  private hydrate(): void {
    const r = this.reminder();
    if (r) {
      this.hydrateFromSummary(r.id, r.reminderDate, r.message);
      return;
    }
    const initial = this.defaultDate() ?? new Date().toISOString();
    this.setDateTimeFromIso(initial);
    this.message.set('');
  }

  private hydrateFromSummary(id: number, iso: string, message: string | null = null): void {
    this.setDateTimeFromIso(iso);
    this.message.set(message ?? '');
  }

  private setDateTimeFromIso(iso: string): void {
    const safe = iso.length >= 16 ? iso.substring(0, 16) : iso;
    const [datePart, timePart] = safe.split('T');
    this.date.set(datePart ?? '');
    this.time.set((timePart ?? '').substring(0, 5));
  }

  /**
   * Combines a local date (YYYY-MM-DD) and time (HH:mm) into a
   * UTC ISO string with the {@code Z} suffix. The browser's
   * {@code Date} constructor treats the naive string as local
   * time, so {@code .toISOString()} does the TZ conversion.
   */
  private buildUtcIso(date: string, time: string): string {
    const local = new Date(`${date}T${time}:00`);
    if (Number.isNaN(local.getTime())) return `${date}T${time}:00`;
    return local.toISOString();
  }

  protected readonly maxMessageLength = MAX_MESSAGE_LENGTH;
}
