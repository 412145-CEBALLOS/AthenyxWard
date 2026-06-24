import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { EmailSummary } from '../../models/email-summary.model';
import { ReminderSummary } from '../../models/reminder.model';
import { EmailItemComponent } from '../email-item/email-item';
import { ReminderAction } from '../reminder-indicator/reminder-indicator';

@Component({
  selector: 'app-email-list',
  standalone: true,
  imports: [EmailItemComponent],
  templateUrl: './email-list.html',
  styleUrl: './email-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailListComponent {
  readonly emails = input.required<ReadonlyArray<EmailSummary>>();
  readonly selectedGmailId = input<string | null>(null);
  /**
   * Propagated to each {@link EmailItemComponent} so the risk indicator
   * can switch between the dot (accessibility ON) and the faded
   * background (accessibility OFF) visual modes.
   */
  readonly accessibilityMode = input<boolean>(true);
  /**
   * Sparse map of {@code emailId -> reminder summary}. Only emails
   * with a configured reminder are keyed; the rest fall through to
   * `null` in the row component.
   */
  readonly remindersByEmail = input<ReadonlyMap<number, ReminderSummary>>(new Map());
  readonly select = output<EmailSummary>();
  readonly reminderAction = output<ReminderAction>();

  reminderFor(emailId: number | null): ReminderSummary | null {
    if (emailId == null) return null;
    return this.remindersByEmail().get(emailId) ?? null;
  }

  trackByGmailId = (_: number, email: EmailSummary): string => email.gmailId;
}
