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

interface HiddenEmailDisplay {
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
  selector: 'app-hidden-emails',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './hidden-emails.html',
  styleUrl: './hidden-emails.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HiddenEmailsComponent implements OnInit, OnDestroy {
  private readonly emailService = inject(EmailService);
  private readonly router = inject(Router);
  private readonly onDestroy = new Subject<void>();

  readonly emails = signal<HiddenEmailDisplay[]>([]);
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
    this.emailService.fetchHiddenEmails().pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (emails) => {
        this.emails.set(emails.map((e) => this.toDisplay(e)));
        this.loading.set(false);
        this.emailService.refreshHiddenCount();
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  private toDisplay(e: EmailSummary): HiddenEmailDisplay {
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

  goToEmail(email: HiddenEmailDisplay): void {
    this.router.navigate(['/home'], { queryParams: { emailId: email.id } });
  }

  unhide(email: HiddenEmailDisplay): void {
    this.emailService.unhide(email.id).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: () => {
        this.emails.update((list) => list.filter((e) => e.id !== email.id));
        this.emailService.refreshHiddenCount();
      },
      error: () => {
      },
    });
  }
}
