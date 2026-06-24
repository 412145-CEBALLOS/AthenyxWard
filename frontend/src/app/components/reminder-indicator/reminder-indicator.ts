import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { Reminder, ReminderSummary } from '../../models/reminder.model';

export type ReminderActionType = 'view' | 'edit' | 'delete' | 'markDone';

export interface ReminderAction {
  type: ReminderActionType;
  reminder: Reminder;
}

const UPCOMING_WINDOW_MS = 24 * 60 * 60 * 1000;

/**
 * Renders the reminder chip / banner used both in the email list
 * row (compact bell icon) and inside the email viewer
 * (full banner with date, message and action buttons).
 *
 * <p>The component is presentational only — it never opens
 * dialogs or performs HTTP calls. Every user action is surfaced
 * through {@link action} so the parent can route it.</p>
 */
@Component({
  selector: 'app-reminder-indicator',
  standalone: true,
  templateUrl: './reminder-indicator.html',
  styleUrl: './reminder-indicator.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReminderIndicatorComponent {
  /** When non-null, the indicator is rendered. */
  readonly reminder = input<Reminder | ReminderSummary | null>(null);
  /** {@code 'list'} renders a compact bell chip; {@code 'banner'} renders the full banner. */
  readonly variant = input<'list' | 'banner'>('list');

  readonly action = output<ReminderAction>();

  readonly isDone = computed(() => !!this.reminder()?.done);
  readonly isUpcoming = computed(() => {
    const r = this.reminder();
    if (!r || r.done) return false;
    const target = new Date(r.reminderDate).getTime();
    const now = Date.now();
    return target >= now - 60_000 && target <= now + UPCOMING_WINDOW_MS;
  });

  readonly reminderDateText = computed(() => {
    const r = this.reminder();
    if (!r) return '';
    const date = new Date(r.reminderDate);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleString('es-ES', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  });

  /**
   * Message shown in the banner body. Only the full
   * {@link Reminder} payload carries the user message — the
   * summary is only used for the list chip, so this is empty
   * for list-variant usage.
   */
  readonly bannerMessage = computed<string | null>(() => {
    const r = this.reminder();
    if (!r || !('message' in r)) return null;
    return r.message ?? null;
  });

  readonly reminderTimeLabel = computed(() => {
    const r = this.reminder();
    if (!r) return '';
    const target = new Date(r.reminderDate).getTime();
    if (Number.isNaN(target)) return '';
    const diffMs = target - Date.now();
    const abs = Math.abs(diffMs);
    const minutes = Math.round(abs / 60_000);
    const hours = Math.round(minutes / 60);
    const days = Math.round(hours / 24);
    if (diffMs < 0) {
      if (minutes < 60) return `hace ${Math.max(1, minutes)} min`;
      if (hours < 24) return `hace ${hours} h`;
      return `hace ${days} d`;
    }
    if (minutes < 60) return `en ${Math.max(1, minutes)} min`;
    if (hours < 24) return `en ${hours} h`;
    return `en ${days} d`;
  });

  onListClick(event: MouseEvent): void {
    event.stopPropagation();
    this.emit('view');
  }

  onView(): void { this.emit('view'); }
  onEdit(): void { this.emit('edit'); }
  onDelete(): void { this.emit('delete'); }
  onMarkDone(): void { this.emit('markDone'); }

  private emit(type: ReminderActionType): void {
    const r = this.reminder();
    if (!r || this.isListOnlySummary()) {
      // For the list variant we only have a summary, so we still
      // emit a view action — the parent will fetch the full
      // reminder from the API.
      const fakeReminder = this.coerceToReminder();
      if (fakeReminder) {
        this.action.emit({ type, reminder: fakeReminder });
      }
      return;
    }
    this.action.emit({ type, reminder: r as Reminder });
  }

  private isListOnlySummary(): boolean {
    const r = this.reminder();
    return r !== null && !('message' in r);
  }

  private coerceToReminder(): Reminder | null {
    const r = this.reminder();
    if (!r) return null;
    if ('message' in r) return r;
    return {
      id: r.id,
      emailId: 0,
      reminderDate: r.reminderDate,
      message: null,
      done: r.done,
      createdAt: '',
      updatedAt: '',
    };
  }
}
