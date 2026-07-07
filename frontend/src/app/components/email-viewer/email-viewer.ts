import {
  ChangeDetectionStrategy,
  Component,
  input,
  model,
  output,
} from '@angular/core';
import { EmailDetail } from '../../models/email-summary.model';
import { Reminder, ReminderSummary } from '../../models/reminder.model';
import { EmailAction } from '../../models/email-action.model';
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
  readonly userRole = input<UserRole>(null);
  readonly reminder = input<Reminder | ReminderSummary | null>(null);
  readonly analysisPanelOpen = model<boolean>(false);

  readonly retry = output<void>();
  readonly analyzeRequest = output<void>();
  readonly action = output<EmailAction>();
  readonly reminderAction = output<ReminderAction>();
}
