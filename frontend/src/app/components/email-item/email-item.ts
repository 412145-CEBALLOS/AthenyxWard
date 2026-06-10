import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { EmailSummary } from '../../models/email-summary.model';
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
  readonly select = output<EmailSummary>();

  readonly unread = computed(() => !this.email().isRead);

  onClick(): void {
    this.select.emit(this.email());
  }
}
