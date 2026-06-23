import {
  ChangeDetectionStrategy,
  Component,
  input,
  model,
  output,
} from '@angular/core';
import { EmailDetail } from '../../models/email-summary.model';
import { EmailAnalysisResult, AnalysisState } from '../../models/email-analysis.model';
import { EmailViewerHeaderComponent } from '../email-viewer-header/email-viewer-header';
import { EmailAnalysisComponent } from '../email-analysis/email-analysis';
import { EmailBodyComponent } from '../email-body/email-body';

type UserRole = 'TRIAL' | 'PREMIUM' | 'ADMIN' | null;

@Component({
  selector: 'app-email-viewer',
  standalone: true,
  imports: [EmailViewerHeaderComponent, EmailAnalysisComponent, EmailBodyComponent],
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
}
