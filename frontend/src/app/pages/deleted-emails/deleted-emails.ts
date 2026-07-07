import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { EmailService } from '../../services/email.service';
import { EmailSummary } from '../../models/email-summary.model';
import { RiskLevel } from '../../models/email-analysis.model';

interface DeletedEmailDisplay {
  id: number;
  gmailId: string;
  sender: string;
  senderName: string;
  subject: string;
  snippet: string;
  receivedAt: string;
  risk: number | null;
  riskLevel: RiskLevel | null;
}

@Component({
  selector: 'app-deleted-emails',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './deleted-emails.html',
  styleUrl: './deleted-emails.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeletedEmailsComponent implements OnInit, OnDestroy {
  private readonly emailService = inject(EmailService);
  private readonly router = inject(Router);
  private readonly onDestroy = new Subject<void>();

  readonly emails = signal<DeletedEmailDisplay[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);

  ngOnInit(): void {
    this.loadEmails();
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  private loadEmails(): void {
    this.loading.set(true);
    this.error.set(false);
    this.emailService.fetchDeletedEmails().pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (emails) => {
        this.emails.set(emails.map((e) => this.toDisplay(e)));
        this.loading.set(false);
        this.emailService.refreshDeletedCount();
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  private toDisplay(e: EmailSummary): DeletedEmailDisplay {
    return {
      id: e.id!,
      gmailId: e.gmailId,
      sender: e.sender,
      senderName: e.senderName,
      subject: e.subject,
      snippet: e.snippet,
      receivedAt: e.receivedAt,
      risk: e.riskPercentage ?? null,
      riskLevel: e.riskLevel ?? null,
    };
  }

  goToEmail(email: DeletedEmailDisplay): void {
    this.router.navigate(['/home'], { queryParams: { emailId: email.id } });
  }
}
