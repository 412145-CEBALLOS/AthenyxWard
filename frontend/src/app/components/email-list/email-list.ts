import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { EmailSummary } from '../../models/email-summary.model';
import { EmailItemComponent } from '../email-item/email-item';

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
  readonly select = output<EmailSummary>();

  trackByGmailId = (_: number, email: EmailSummary): string => email.gmailId;
}
