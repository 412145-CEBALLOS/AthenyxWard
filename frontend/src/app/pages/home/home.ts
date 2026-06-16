import { Component, inject, signal, OnInit, OnDestroy, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EmailListComponent } from '../../components/email-list/email-list';
import { EmailPaginatorComponent } from '../../components/email-paginator/email-paginator';
import { EmailViewerComponent } from '../../components/email-viewer/email-viewer';
import { EmailService } from '../../services/email.service';
import { AuthService } from '../../services/auth.service';
import { AvatarService } from '../../services/avatar.service';
import { ToastService } from '../../services/toast.service';
import { computeMockAnalysis } from '../../utils/email-risk.util';
import { EmailDetail, EmailSummary, EmailPageResponse } from '../../models/email-summary.model';
import { EmailAnalysisResult, AnalysisState } from '../../models/email-analysis.model';
import { Subject, takeUntil } from 'rxjs';

const MAX_PAGE_CACHE = 10;

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [EmailListComponent, EmailPaginatorComponent, EmailViewerComponent],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class HomeComponent implements OnInit, OnDestroy {
  private readonly emailService = inject(EmailService);
  private readonly authService = inject(AuthService);
  private readonly avatars = inject(AvatarService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  readonly emails = signal<EmailSummary[]>([]);
  readonly selectedEmail = signal<EmailDetail | null>(null);
  readonly loading = signal(false);
  readonly showTrialExpiredModal = signal(false);
  readonly hasNextPage = signal(false);
  readonly lastKnownPage = signal<number | null>(null);
  readonly currentPage = signal(0);

  readonly analysisResult = signal<EmailAnalysisResult | null>(null);
  readonly analysisState = signal<AnalysisState>('idle');

  readonly isPremium = computed(() => this.authService.user()?.role === 'PREMIUM');
  readonly canMarkImportant = computed(() => this.authService.user()?.role !== 'TRIAL');
  readonly accessibilityMode = computed(() => this.authService.user()?.accessibilityMode ?? true);

  readonly mobileEmailDetail = signal(false);

  hasEmails = () => this.emails().length > 0;

  private readonly onDestroy = new Subject<void>();
  private readonly pageCache = new Map<number, EmailPageResponse>();
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private pendingEmailId: number | null = null;

  ngOnInit(): void {
    const user = this.authService.user();
    if (user?.trialExpired) {
      this.showTrialExpiredModal.set(true);
    } else {
      this.fetchEmails(0);
    }
    this.route.queryParamMap.pipe(takeUntil(this.onDestroy)).subscribe((params) => {
      const emailIdParam = params.get('emailId');
      if (emailIdParam) {
        this.pendingEmailId = Number(emailIdParam);
        this.tryConsumePendingSelection();
      }
    });
  }

  ngOnDestroy(): void {
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  dismissTrialExpiredModal(): void {
    this.authService.logout().subscribe();
    this.router.navigate(['/login']);
  }

  fetchEmails(page: number = 0): void {
    const cached = this.pageCache.get(page);
    if (cached) {
      this.emails.set(cached.emails);
      this.hasNextPage.set(cached.hasNextPage);
      this.avatars.precompute(cached.emails.map((e) => e.sender));
      this.prefetchNextPage(page + 1);
      this.tryConsumePendingSelection();
      return;
    }

    this.loading.set(true);
    this.emailService.fetchEmails(page).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (response) => {
        this.cachePage(page, response);
        this.emails.set(response.emails);
        this.hasNextPage.set(response.hasNextPage);
        this.loading.set(false);
        this.avatars.precompute(response.emails.map((e) => e.sender));
        this.prefetchNextPage(page + 1);
        if (!response.hasNextPage) {
          this.lastKnownPage.set(page);
        }
        this.emailService.refreshImportantCount();
        this.tryConsumePendingSelection();
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  private prefetchNextPage(page: number): void {
    if (this.pageCache.has(page)) return;
    this.emailService.fetchEmails(page).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (response) => {
        this.cachePage(page, response);
        this.avatars.precompute(response.emails.map((e) => e.sender));
      },
      error: () => {}
    });
  }

  private cachePage(page: number, response: EmailPageResponse): void {
    this.pageCache.set(page, response);
    if (this.pageCache.size <= MAX_PAGE_CACHE) return;
    const current = this.currentPage();
    const protectedKeys = new Set([current - 1, current, current + 1]);
    let oldestKey: number | null = null;
    for (const key of this.pageCache.keys()) {
      if (protectedKeys.has(key)) continue;
      if (oldestKey === null || key < oldestKey) oldestKey = key;
    }
    if (oldestKey !== null) this.pageCache.delete(oldestKey);
  }

  readonly canJumpFive = computed(() => {
    const last = this.lastKnownPage();
    if (last !== null) return this.currentPage() + 5 <= last;
    return this.hasNextPage();
  });

  goToPage(page: number): void {
    if (page < 0) return;
    this.selectedEmail.set(null);
    this.currentPage.set(page);
    this.fetchEmails(page);
  }

  selectEmail(email: EmailSummary): void {
    this.mobileEmailDetail.set(true);
    if (this.selectedEmail()?.gmailId === email.gmailId) return;
    this.emails.update((list) =>
      list.map((e) => (e.gmailId === email.gmailId ? { ...e, isRead: true } : e))
    );
    this.selectedEmail.set(null);
    this.analysisResult.set(null);
    this.analysisState.set('loading');
    this.emailService.getEmailDetail(email.id!).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (detail) => {
        this.selectedEmail.set(detail);
        this.emails.update((list) =>
          list.map((e) => e.gmailId === email.gmailId ? { ...e, isImportant: detail.isImportant } : e)
        );
        this.analysisResult.set(computeMockAnalysis(detail));
        this.analysisState.set('ready');
      },
      error: () => {
        this.analysisState.set('error');
      },
    });
  }

  private tryConsumePendingSelection(): void {
    if (this.pendingEmailId == null) return;
    const found = this.emails().find((e) => e.id === this.pendingEmailId);
    if (found) {
      this.selectEmail(found);
    } else {
      this.openStandaloneEmail(this.pendingEmailId);
    }
    this.pendingEmailId = null;
  }

  private openStandaloneEmail(emailId: number): void {
    this.mobileEmailDetail.set(true);
    this.selectedEmail.set(null);
    this.analysisResult.set(null);
    this.analysisState.set('loading');
    this.emailService.getEmailDetail(emailId).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (detail) => {
        this.selectedEmail.set(detail);
        this.analysisResult.set(computeMockAnalysis(detail));
        this.analysisState.set('ready');
      },
      error: () => {
        this.analysisState.set('error');
      },
    });
  }

  onEmailHide(): void { console.warn('TODO Sprint 3: hide email'); }
  onEmailDelete(): void { console.warn('TODO Sprint 3: delete email'); }
  onEmailMarkPhishing(): void { console.warn('TODO Sprint 3: mark as phishing'); }
  onEmailMarkImportant(): void {
    const email = this.selectedEmail();
    if (!email) return;
    this.emailService.toggleImportant(email.id).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (res) => {
        this.emails.update((list) =>
          list.map((e) => e.id === email.id ? { ...e, isImportant: res.isImportant } : e)
        );
        this.selectedEmail.update((e) => e ? { ...e, isImportant: res.isImportant } : e);
        if (res.isImportant) {
          this.toast.success('Correo marcado como importante.');
        } else {
          this.toast.info('Correo removido de importantes.');
        }
      },
      error: () => {
        this.toast.error('No se pudo cambiar el estado de importante. Intenta de nuevo.');
      },
    });
  }
  onEmailCreateReminder(): void { console.warn('TODO Sprint 3: create reminder (premium)'); }

  // TODO(Sprint 3): remove mock; wire to real backend retry flow.
  onEmailAnalysisRetry(): void {
    const current = this.selectedEmail();
    if (!current) return;
    this.analysisState.set('loading');
    if (this.retryTimer !== null) clearTimeout(this.retryTimer);
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      const live = this.selectedEmail();
      if (!live) return;
      this.analysisResult.set(computeMockAnalysis(live));
      this.analysisState.set('ready');
    }, 600);
  }
}
