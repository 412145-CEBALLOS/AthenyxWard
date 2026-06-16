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
import { ImportantEmailDatePipe } from '../../pipes/important-email-date.pipe';
import { EmailService } from '../../services/email.service';
import { EmailSummary } from '../../models/email-summary.model';
import { computeMockAnalysis } from '../../utils/email-risk.util';

interface ImportantEmailDisplay {
  id: number;
  gmailId: string;
  sender: string;
  senderName: string;
  subject: string;
  snippet: string;
  receivedAt: string;
  risk: number;
}

@Component({
  selector: 'app-important-emails',
  standalone: true,
  imports: [PageShellComponent, ImportantEmailDatePipe],
  templateUrl: './important-emails.html',
  styleUrl: './important-emails.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportantEmailsComponent implements OnInit, OnDestroy {
  private readonly emailService = inject(EmailService);
  private readonly router = inject(Router);
  private readonly onDestroy = new Subject<void>();

  readonly emails = signal<ImportantEmailDisplay[]>([]);
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
    this.emailService.fetchImportantEmails().pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (emails) => {
        this.emails.set(emails.map((e) => this.toDisplay(e)));
        this.loading.set(false);
        this.emailService.refreshImportantCount();
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  private toDisplay(e: EmailSummary): ImportantEmailDisplay {
    const mockAnalysis = computeMockAnalysis({
      ...e,
      contentForAnalysis: e.snippet,
      htmlContent: null,
      fetchedAt: e.fetchedAt,
      isRead: e.isRead,
      originalDateHeader: e.originalDateHeader,
      isImportant: true,
    } as import('../../models/email-summary.model').EmailDetail);
    return {
      id: e.id!,
      gmailId: e.gmailId,
      sender: e.sender,
      senderName: e.senderName,
      subject: e.subject,
      snippet: e.snippet,
      receivedAt: e.receivedAt,
      risk: mockAnalysis.riskPercentage,
    };
  }

  goToEmail(email: ImportantEmailDisplay): void {
    this.router.navigate(['/home'], { queryParams: { emailId: email.id } });
  }

  unmark(email: ImportantEmailDisplay): void {
    this.emailService.toggleImportant(email.id).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: () => {
        this.emails.update((list) => list.filter((e) => e.id !== email.id));
      },
      error: () => {
      },
    });
  }
}
