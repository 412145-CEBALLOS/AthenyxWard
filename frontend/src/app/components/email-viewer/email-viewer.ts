import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  input,
  model,
  output,
  ViewChild,
} from '@angular/core';
import { EmailDetail } from '../../models/email-summary.model';
import { Reminder, ReminderSummary } from '../../models/reminder.model';
import { EmailAction } from '../../models/email-action.model';
import { EmailAnalysisResult, AnalysisState } from '../../models/email-analysis.model';
import { AiExplanation, AiState, AI_ORIGIN_LABELS } from '../../models/ai-explanation.model';
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
  @ViewChild('aiCard', { read: ElementRef }) aiCardRef?: ElementRef<HTMLElement>;
  @ViewChild('aiLoading', { read: ElementRef }) aiLoadingRef?: ElementRef<HTMLElement>;

  readonly email = input.required<EmailDetail>();
  readonly analysis = input<EmailAnalysisResult | null>(null);
  readonly state = input<AnalysisState>('idle');
  readonly accessibilityMode = input<boolean>(true);
  readonly userRole = input<UserRole>(null);
  readonly reminder = input<Reminder | ReminderSummary | null>(null);
  readonly analysisPanelOpen = model<boolean>(false);
  readonly isHidden = input<boolean>(false);
  readonly aiExplanation = input<AiExplanation | null>(null);
  readonly aiState = input<AiState>('idle');

  readonly retry = output<void>();
  readonly analyzeRequest = output<void>();
  readonly action = output<EmailAction>();
  readonly reminderAction = output<ReminderAction>();
  readonly explainRequest = output<void>();

  private hasScrolledToAiCard = false;

  onExplainRequest(): void {
    this.explainRequest.emit();
    this.hasScrolledToAiCard = false;
    setTimeout(() => {
      const el = this.aiLoadingRef?.nativeElement || this.aiCardRef?.nativeElement;
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }, 100);
  }

  constructor() {
    effect(() => {
      const aiState = this.aiState();
      const aiExp = this.aiExplanation();
      if (aiState === 'ready' && aiExp && !this.hasScrolledToAiCard) {
        this.hasScrolledToAiCard = true;
        setTimeout(() => {
          const el = this.aiCardRef?.nativeElement;
          if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        }, 100);
      }
    });
  }

  relativeTime(dateStr: string): string {
    const date = new Date(dateStr);
    const now = Date.now();
    const diffMs = now - date.getTime();
    const diffSec = Math.abs(Math.round(diffMs / 1000));
    const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
    if (diffSec < 60) return rtf.format(-diffSec, 'second');
    const diffMin = Math.round(diffSec / 60);
    if (diffMin < 60) return rtf.format(-diffMin, 'minute');
    const diffH = Math.round(diffMin / 60);
    if (diffH < 24) return rtf.format(-diffH, 'hour');
    const diffD = Math.round(diffH / 24);
    return rtf.format(-diffD, 'day');
  }
}
