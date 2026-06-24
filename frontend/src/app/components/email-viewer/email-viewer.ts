import {
  ChangeDetectionStrategy,
  Component,
  input,
  model,
  output,
} from '@angular/core';
import { EmailDetail } from '../../models/email-summary.model';
import { Reminder, ReminderSummary } from '../../models/reminder.model';
import { EmailAnalysisResult, AnalysisState } from '../../models/email-analysis.model';
import { EmailViewerHeaderComponent } from '../email-viewer-header/email-viewer-header';
import { EmailAnalysisComponent } from '../email-analysis/email-analysis';
import { EmailBodyComponent } from '../email-body/email-body';
import {
  ReminderAction,
  ReminderIndicatorComponent,
} from '../reminder-indicator/reminder-indicator';

type UserRole = 'TRIAL' | 'PREMIUM' | 'ADMIN' | null;

@Component({
  selector: 'app-email-viewer',
  standalone: true,
  imports: [
    EmailViewerHeaderComponent,
    EmailAnalysisComponent,
    EmailBodyComponent,
    ReminderIndicatorComponent,
  ],
  templateUrl: './email-viewer.html',
  styleUrl: './email-viewer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailViewerComponent {
  readonly email = input.required<EmailDetail>();
  readonly analysis = input<EmailAnalysisResult | null>(null);
  readonly state = input<AnalysisState>('idle');
  readonly accessibilityMode = input<boolean>(true);
  readonly canMarkImportant = input<boolean>(false);
  readonly isImportant = input<boolean>(false);
  readonly userRole = input<UserRole>(null);
  /**
   * Full reminder for the email currently being viewed, or
   * {@code null} when the user has no reminder configured. The
   * banner uses it to render the message + actions.
   */
  readonly reminder = input<Reminder | ReminderSummary | null>(null);
  /**
   * Two-way bound open-state for the analysis panel-toggle. The
   * parent (home.ts) writes {@code true} when an analysis finishes
   * to auto-reveal the result.
   */
  readonly analysisPanelOpen = model<boolean>(false);

  readonly hide = output<void>();
  readonly delete = output<void>();
  readonly markPhishing = output<void>();
  readonly markImportant = output<void>();
  readonly createReminder = output<void>();
  readonly retry = output<void>();
  /**
   * Bubbles up the {@link EmailAnalysisComponent#analyzeRequest} event.
   * The parent (home.ts) is expected to call
   * {@code AnalysisService.analyze()} and set {@link analysisPanelOpen}
   * to {@code true} when the result arrives.
   */
  readonly analyzeRequest = output<void>();
  /**
   * Surfaces reminder chip / banner interactions. The parent
   * resolves the action (open edit dialog, mark done, delete, …).
   */
  readonly reminderAction = output<ReminderAction>();
}
