import { Component, inject, signal, OnInit, OnDestroy, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EmailListComponent } from '../../components/email-list/email-list';
import { EmailPaginatorComponent } from '../../components/email-paginator/email-paginator';
import { EmailViewerComponent } from '../../components/email-viewer/email-viewer';
import { EmailService } from '../../services/email.service';
import { AuthService } from '../../services/auth.service';
import { AvatarService } from '../../services/avatar.service';
import { ToastService } from '../../services/toast.service';
import { AnalysisService } from '../../services/analysis.service';
import { EmailDetail, EmailSummary, EmailPageResponse } from '../../models/email-summary.model';
import { EmailAnalysisResult, AnalysisState } from '../../models/email-analysis.model';
import { HttpErrorResponse } from '@angular/common/http';
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
  private readonly analysisService = inject(AnalysisService);

  readonly emails = signal<EmailSummary[]>([]);
  readonly selectedEmail = signal<EmailDetail | null>(null);
  readonly loading = signal(false);
  readonly showTrialExpiredModal = signal(false);
  readonly hasNextPage = signal(false);
  readonly lastKnownPage = signal<number | null>(null);
  readonly currentPage = signal(0);

  readonly analysisResult = signal<EmailAnalysisResult | null>(null);
  readonly analysisState = signal<AnalysisState>('idle');
  /**
   * Two-way bound to the viewer/analysis panel-toggle. Flipped to
   * {@code true} once an analysis completes so the panel auto-reveals
   * the result.
   */
  readonly analysisPanelOpen = signal(false);

  readonly isPremium = computed(() => this.authService.user()?.role === 'PREMIUM');
  readonly canMarkImportant = computed(() => this.authService.user()?.role !== 'TRIAL');
  readonly accessibilityMode = computed(() => this.authService.user()?.accessibilityMode ?? true);
  readonly userRole = computed(() => this.authService.user()?.role ?? null);

  readonly mobileEmailDetail = signal(false);

  hasEmails = () => this.emails().length > 0;

  private readonly onDestroy = new Subject<void>();
  private readonly pageCache = new Map<number, EmailPageResponse>();
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private pendingEmailId: number | null = null;
  /**
   * Tracks the email id currently being loaded/selected. Used to
   * ignore the {@code queryParamMap} echo that {@code router.navigate}
   * produces right after {@link selectEmail} (otherwise the navigation
   * would re-trigger the selection flow and loop).
   */
  private currentSelectionId: number | null = null;

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
        const id = Number(emailIdParam);
        if (this.currentSelectionId === id) {
          // We just navigated to this URL ourselves — skip the
          // re-entry to avoid a loop.
          return;
        }
        this.pendingEmailId = id;
        this.tryConsumePendingSelection();
      } else {
        // No emailId in URL → user navigated back to the list.
        this.clearSelection();
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
    this.clearSelection();
    this.router.navigate(['/home'], { queryParams: {} });
    this.currentPage.set(page);
    this.fetchEmails(page);
  }

  selectEmail(email: EmailSummary): void {
    this.mobileEmailDetail.set(true);
    if (this.currentSelectionId === email.id) return;
    this.currentSelectionId = email.id;
    // Optimistic: mark the row as read in both the signal and the
    // page cache so paginating back doesn't lose the change.
    this.mutateOnPage(email.id!, { isRead: true });
    this.selectedEmail.set(null);
    this.analysisResult.set(null);
    this.analysisState.set('idle');
    this.analysisPanelOpen.set(false);

    this.emailService.getEmailDetail(email.id!).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (detail) => {
        this.selectedEmail.set(detail);
        // The detail knows the canonical isImportant flag; sync the
        // list row + cache with it.
        this.mutateOnPage(email.id!, { isImportant: detail.isImportant });
        this.bootstrapAnalysis(detail, email);
        // Mirror the selection in the URL so navigating away and
        // back (or a hard reload) restores the open email.
        this.router.navigate(['/home'], {
          queryParams: { emailId: email.id },
          replaceUrl: true,
        });
      },
      error: () => {
        this.analysisState.set('error');
        this.toast.error('No se pudo cargar el correo. Intenta nuevamente.');
      },
    });
  }

  /**
   * Decides what to do with the analysis pane when an email is opened
   * from the list (where we already know the latest risk level from
   * the list endpoint's JOIN — see US 2.3 {@code EmailSummary}):
   *
   * <ul>
   *   <li>{@code TRIAL} — never auto-runs. Panel stays in
   *       {@code idle}; the user must press the in-body
   *       "Analizar este correo" button.</li>
   *   <li>{@code PREMIUM} / {@code ADMIN} — first checks the 24 h
   *       cache via {@code getLatest}; if a recent analysis exists
   *       it is shown immediately. If not, the panel stays in
   *       {@code idle} and the first toggle click triggers
   *       {@code analyze()} (see {@link onAnalysisRequest}).</li>
   * </ul>
   */
  private bootstrapAnalysis(detail: EmailDetail, summary: EmailSummary): void {
    const role = this.userRole();
    if (role === 'TRIAL') {
      this.analysisResult.set(null);
      this.analysisState.set('idle');
      return;
    }
    if (summary.riskLevel == null) {
      this.analysisResult.set(null);
      this.analysisState.set('idle');
      return;
    }
    this.analysisState.set('loading');
    this.analysisService.getLatest(detail.id).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (cached) => {
        if (cached) {
          this.analysisResult.set(cached);
          this.analysisState.set('ready');
        } else {
          this.analysisResult.set(null);
          this.analysisState.set('idle');
        }
      },
      error: () => {
        this.analysisState.set('idle');
      }
    });
  }

  /**
   * Navigates back to the inbox list (clears the {@code emailId}
   * query param and resets the in-memory selection). Bound to the
   * mobile "Volver" button.
   */
  goBackToList(): void {
    this.mobileEmailDetail.set(false);
    this.router.navigate(['/home'], { queryParams: {} });
  }

  private clearSelection(): void {
    this.currentSelectionId = null;
    this.selectedEmail.set(null);
    this.analysisResult.set(null);
    this.analysisState.set('idle');
    this.analysisPanelOpen.set(false);
    this.pendingEmailId = null;
  }

  /**
   * Applies an in-place patch to the email with the given id, both
   * in the list currently rendered by the signal AND in the cached
   * entry for the current page. The next visit to the current page
   * (e.g. after paginating forward and back) will see the fresh
   * state without a backend refetch.
   *
   * <p>This is a no-op when the email isn't in the list — e.g. a
   * deep link to an email on a different page. In that case the
   * backend already has the truth; the user just won't see the
   * change reflected in the list until the list itself is refetched.</p>
   */
  private mutateOnPage(emailId: number, patch: Partial<EmailSummary>): void {
    this.emails.update((list) =>
      list.map((e) => e.id === emailId ? { ...e, ...patch } : e)
    );
    const page = this.currentPage();
    const cached = this.pageCache.get(page);
    if (cached) {
      this.pageCache.set(page, {
        ...cached,
        emails: cached.emails.map((e) => e.id === emailId ? { ...e, ...patch } : e),
      });
    }
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

  /**
   * Loads an email without going through the list. Used both for
   * deep links ({@code /home?emailId=X}) and for restoring the
   * selection on browser back navigation. Always queries
   * {@code getLatest} so a previously-cached analysis is shown
   * immediately (no need for the summary-side JOIN).
   */
  private openStandaloneEmail(emailId: number): void {
    if (this.currentSelectionId === emailId) return;
    this.currentSelectionId = emailId;
    this.mobileEmailDetail.set(true);
    this.selectedEmail.set(null);
    this.analysisResult.set(null);
    this.analysisState.set('idle');
    this.analysisPanelOpen.set(false);
    this.emailService.getEmailDetail(emailId).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (detail) => {
        this.selectedEmail.set(detail);
        // Try to restore a previously-cached analysis. For TRIAL
        // users this also restores the result of a previous manual
        // run; for PREMIUM/ADMIN it short-circuits the 24 h cache.
        this.analysisState.set('loading');
        this.analysisService.getLatest(detail.id).pipe(
          takeUntil(this.onDestroy)
        ).subscribe({
          next: (cached) => {
            if (cached) {
              this.analysisResult.set(cached);
              this.analysisState.set('ready');
            } else {
              this.analysisResult.set(null);
              this.analysisState.set('idle');
            }
          },
          error: () => this.analysisState.set('idle'),
        });
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
        this.mutateOnPage(email.id, { isImportant: res.isImportant });
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

  /**
   * Bubbled from the analysis panel-toggle (PREMIUM/ADMIN first click)
   * or the in-body "Analizar este correo" button (TRIAL). Triggers the
   * real backend call and handles the three outcomes:
   *
   * <ul>
   *   <li>HTTP 200 → result + state {@code 'ready'} + auto-open panel.</li>
   *   <li>HTTP 403 (trial-limit) → state {@code 'unavailable-trial'}.</li>
   *   <li>Any other error → state {@code 'error'}.</li>
   * </ul>
   */
  onAnalysisRequest(): void {
    const email = this.selectedEmail();
    if (!email) return;
    this.runAnalysis(email.id);
  }

  /**
   * Same as {@link onAnalysisRequest} but exposes the imperative entry
   * point expected by the existing retry button. The actual HTTP call
   * is now real (replaces the previous 600 ms setTimeout mock).
   */
  onEmailAnalysisRetry(): void {
    const email = this.selectedEmail();
    if (!email) return;
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    this.runAnalysis(email.id);
  }

  private runAnalysis(emailId: number): void {
    this.analysisState.set('loading');
    this.analysisService.analyze(emailId).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (result) => {
        this.analysisResult.set(result);
        this.analysisState.set('ready');
        this.analysisPanelOpen.set(true);
        this.refreshListRiskCell(emailId, result);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.analysisState.set('unavailable-trial');
          this.toast.error('Has alcanzado el límite de análisis de tu prueba.');
        } else {
          this.analysisState.set('error');
          this.toast.error('No se pudo analizar el correo. Intenta nuevamente.');
        }
      }
    });
  }

  private refreshListRiskCell(emailId: number, result: EmailAnalysisResult): void {
    this.mutateOnPage(emailId, {
      riskPercentage: result.riskPercentage,
      riskLevel: result.riskLevel,
    });
  }
}
