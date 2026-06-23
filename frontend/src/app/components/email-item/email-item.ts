import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { EmailSummary } from '../../models/email-summary.model';
import { RiskLevel } from '../../models/email-analysis.model';
import { SenderAvatarComponent } from '../sender-avatar/sender-avatar';
import { EmailDatePipe } from '../../pipes/email-date.pipe';

@Component({
  selector: 'app-email-item',
  standalone: true,
  imports: [SenderAvatarComponent, EmailDatePipe],
  templateUrl: './email-item.html',
  styleUrl: './email-item.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailItemComponent {
  readonly email = input.required<EmailSummary>();
  readonly selected = input<boolean>(false);
  /**
   * When the accessibility mode is on the SPA uses a small dot/badge to
   * the right of the row; when it is off a faded full-width background
   * paints the row from top to bottom (subtle fade-out).
   */
  readonly accessibilityMode = input<boolean>(true);
  readonly select = output<EmailSummary>();

  readonly unread = computed(() => !this.email().isRead);

  readonly riskLevel = computed<RiskLevel | null>(() => this.email().riskLevel ?? null);
  readonly riskPercentage = computed<number | null>(() => this.email().riskPercentage ?? null);
  /**
   * Drives the small coloured dot in accessibility mode. Only
   * YELLOW / RED get a dot — GREEN keeps the row clean.
   */
  readonly showRiskIndicator = computed<boolean>(
    () => this.riskLevel() === 'YELLOW' || this.riskLevel() === 'RED',
  );
  /**
   * Drives the full-row fade-out background. Includes GREEN so that,
   * in non-accessibility mode, analysed safe emails still show a
   * subtle colour cue (less prominent than YELLOW/RED).
   */
  readonly showRiskBackground = computed<boolean>(
    () => this.riskLevel() === 'GREEN'
        || this.riskLevel() === 'YELLOW'
        || this.riskLevel() === 'RED',
  );
  readonly riskAriaLabel = computed<string>(
    () => `${this.riskPercentage() ?? 0}% riesgo`,
  );

  onClick(): void {
    this.select.emit(this.email());
  }
}
