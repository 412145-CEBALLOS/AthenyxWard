import {
  ChangeDetectionStrategy,
  Component,
  input,
} from '@angular/core';
import { EmailDetail } from '../../models/email-summary.model';
import { SenderAvatarComponent } from '../sender-avatar/sender-avatar';
import { EmailDatePipe } from '../../pipes/email-date.pipe';

@Component({
  selector: 'app-email-viewer-header',
  standalone: true,
  imports: [SenderAvatarComponent, EmailDatePipe],
  templateUrl: './email-viewer-header.html',
  styleUrl: './email-viewer-header.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailViewerHeaderComponent {
  readonly email = input.required<EmailDetail>();
}
