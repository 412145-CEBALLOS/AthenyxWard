import { Component, inject, signal, OnInit, OnDestroy, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { EmailListComponent } from '../../components/email-list/email-list';
import { EmailPaginatorComponent } from '../../components/email-paginator/email-paginator';
import { EmailViewerComponent } from '../../components/email-viewer/email-viewer';
import { ReminderFormDialogComponent } from '../../components/reminder-form-dialog/reminder-form-dialog';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog';
import { EmailService } from '../../services/email.service';
import { AuthService } from '../../services/auth.service';
import { AvatarService } from '../../services/avatar.service';
import { ToastService } from '../../services/toast.service';
import { AnalysisService } from '../../services/analysis.service';
import { ReminderService } from '../../services/reminder.service';
import { EmailDetail, EmailSummary, EmailPageResponse } from '../../models/email-summary.model';
import { EmailAnalysisResult, AnalysisState } from '../../models/email-analysis.model';
import { Reminder, ReminderSummary } from '../../models/reminder.model';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, forkJoin, of, takeUntil } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ReminderAction } from '../../components/reminder-indicator/reminder-indicator';

const MAX_PAGE_CACHE = 10;

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    EmailListComponent,
    EmailPaginatorComponent,
    EmailViewerComponent,
    ReminderFormDialogComponent,
    ConfirmDialogComponent,
  ],
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
  private readonly reminderService = inject(ReminderService);

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
  readonly canCreateReminder = computed(() => this.authService.user()?.role !== 'TRIAL');
  readonly accessibilityMode = computed(() => this.authService.user()?.accessibilityMode ?? true);
  readonly userRole = computed(() => this.authService.user()?.role ?? null);

  /**
   * Sparse map of email id → reminder summary used by the list
   * rows to render the bell chip. Rebuilt every time the email
   * list is refreshed.
   */
  readonly remindersByEmail = signal<ReadonlyMap<number, ReminderSummary>>(new Map());
  /** Full reminder for the email currently open in the viewer. */
  readonly currentReminder = signal<Reminder | ReminderSummary | null>(null);

  /** Form dialog state. */
  readonly reminderFormOpen = signal(false);
  readonly reminderFormEditing = signal<Reminder | null>(null);
  readonly reminderFormEmailId = signal<number | null>(null);

  /** Delete confirmation state. */
  readonly confirmDeleteOpen = signal(false);
  readonly confirmDeleteReminder = signal<Reminder | ReminderSummary | null>(null);

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
      this.refreshRemindersForPage(cached.emails);
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
        this.refreshRemindersForPage(response.emails);
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

  /**
   * Fetches the reminder summary for every email on the current
   * page in a single batch (using a fan-out of
   * {@code GET /reminders/by-email/{id}} calls). The list endpoint
   * already returns reminder data inline, but it can be missing
   * for deep links or paginated transitions; this method
   * guarantees the bell chips stay in sync.
   */
  private refreshRemindersForPage(emails: ReadonlyArray<EmailSummary>): void {
    const ids = emails
      .map((e) => e.id)
      .filter((id): id is number => id != null);
    if (ids.length === 0) return;
    const calls = ids.map((id) =>
      this.reminderService.getByEmail(id).pipe(
        catchError(() => of(null as ReminderSummary | null))
      )
    );
    forkJoin(calls).pipe(takeUntil(this.onDestroy)).subscribe((results) => {
      const next = new Map<number, ReminderSummary>();
      ids.forEach((id, i) => {
        const r = results[i];
        if (r) next.set(id, r);
      });
      // Merge with whatever is already in the map so we don't drop
      // reminders from other pages.
      const merged = new Map(this.remindersByEmail());
      next.forEach((v, k) => merged.set(k, v));
      this.remindersByEmail.set(merged);
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
    this.currentReminder.set(null);

    this.emailService.getEmailDetail(email.id!).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (detail) => {
        this.selectedEmail.set(detail);
        // The detail knows the canonical isImportant flag; sync the
        // list row + cache with it.
        this.mutateOnPage(email.id!, { isImportant: detail.isImportant });
        this.bootstrapAnalysis(detail, email);
        this.loadCurrentReminder(detail.id);
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
   * Fetches the full reminder (with message) for the email
   * currently being viewed. The summary returned here is good
   * enough for the banner — the parent flow can upgrade to a full
   * {@link Reminder} when the user opens the edit dialog
   * (it then hits {@code GET /reminders/{id}}? — actually we
   * re-fetch the summary because the controller doesn't expose a
   * single-reminder endpoint; the dialog uses the summary plus
   * the email subject as the message fallback).
   */
  private loadCurrentReminder(emailId: number): void {
    this.reminderService.getByEmail(emailId)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (summary) => {
          this.currentReminder.set(summary);
          if (summary) {
            const next = new Map(this.remindersByEmail());
            next.set(emailId, summary);
            this.remindersByEmail.set(next);
          }
        },
        error: () => this.currentReminder.set(null),
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
    this.currentReminder.set(null);
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
    this.currentReminder.set(null);
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
        this.loadCurrentReminder(detail.id);
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
  onEmailCreateReminder(): void {
    const email = this.selectedEmail();
    if (!email) return;
    if (!this.canCreateReminder()) {
      this.toast.warning('Los recordatorios están disponibles en el plan Premium.');
      this.router.navigate(['/plan']);
      return;
    }
    this.openCreateReminderDialog(email.id, email.subject);
  }

  // --- Reminder flow ----------------------------------------------

  /**
   * Entry point for the email-list bell chip click. Resolves the
   * full {@link Reminder} for the email and routes to the right
   * action (always "view" from the chip — the dialog itself offers
   * the edit/delete actions).
   */
  onEmailListReminderAction(action: ReminderAction): void {
    if (action.type !== 'view') {
      this.onReminderAction(action);
      return;
    }
    this.openEditReminderDialog(action.reminder.emailId || action.reminder.id);
  }

  /**
   * Single entry point for every reminder action (list chip and
   * banner). The {@link ReminderAction.reminder.emailId} field is
   * meaningful only when the source is the banner; the list chip
   * uses the summary coercion which leaves it as {@code 0} — in
   * that case we look it up from the email list.
   */
  onReminderAction(action: ReminderAction): void {
    const emailId = this.resolveEmailIdForReminder(action.reminder);
    if (emailId == null) {
      this.toast.error('No se encontró el correo asociado al recordatorio.');
      return;
    }
    switch (action.type) {
      case 'view':
      case 'edit':
        this.openEditReminderDialog(emailId, action.reminder);
        return;
      case 'markDone':
        this.markReminderDone(action.reminder, emailId);
        return;
      case 'delete':
        this.confirmDeleteReminder.set(action.reminder);
        this.confirmDeleteOpen.set(true);
        return;
    }
  }

  /** Opens the create-mode dialog for the given email. */
  openCreateReminderDialog(emailId: number, emailSubject?: string): void {
    this.reminderFormEditing.set(null);
    this.reminderFormEmailId.set(emailId);
    this.reminderFormOpen.set(true);
  }

  /**
   * Opens the edit-mode dialog. When the user has clicked the
   * list chip we may only have a {@link ReminderSummary} (no
   * message); in that case we still open the dialog with the
   * known date and let the user edit the message inline.
   */
  openEditReminderDialog(emailId: number, summary?: Reminder | ReminderSummary): void {
    this.reminderFormEditing.set(null);
    this.reminderFormEmailId.set(emailId);
    this.reminderFormOpen.set(true);
    if (summary) {
      this.reminderFormEditing.set(
        this.ensureFullReminder(summary, emailId)
      );
    }
  }

  onReminderFormSaved(saved: Reminder): void {
    this.reminderFormOpen.set(false);
    this.reminderFormEditing.set(null);
    this.reminderFormEmailId.set(null);
    this.applyReminderUpdate(saved, this.findEmailById(saved.emailId));
  }

  onReminderFormCancelled(): void {
    this.reminderFormOpen.set(false);
    this.reminderFormEditing.set(null);
    this.reminderFormEmailId.set(null);
  }

  onConfirmDeleteCancelled(): void {
    this.confirmDeleteOpen.set(false);
    this.confirmDeleteReminder.set(null);
  }

  onConfirmDeleteAccepted(): void {
    const target = this.confirmDeleteReminder();
    this.confirmDeleteOpen.set(false);
    this.confirmDeleteReminder.set(null);
    if (!target) return;
    const emailId = this.resolveEmailIdForReminder(target) ?? target.id;
    this.reminderService.delete(target.id)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: () => {
          this.toast.success('Recordatorio eliminado.');
          this.afterReminderRemoved(emailId);
        },
        error: () => this.toast.error('No se pudo eliminar el recordatorio.'),
      });
  }

  private markReminderDone(reminder: Reminder | ReminderSummary, emailId: number): void {
    this.reminderService.update(reminder.id, { done: true })
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (updated) => {
          this.toast.success('Recordatorio marcado como hecho.');
          this.applyReminderUpdate(updated, this.findEmailById(emailId));
        },
        error: () => this.toast.error('No se pudo actualizar el recordatorio.'),
      });
  }

  /**
   * Updates the {@code remindersByEmail} map and the currently
   * selected email's reminder. Also refreshes the cached
   * {@link EmailSummary} entry so paginating back keeps the chip.
   */
  private applyReminderUpdate(saved: Reminder, _email: EmailSummary | undefined): void {
    const summary: ReminderSummary = {
      id: saved.id,
      reminderDate: saved.reminderDate,
      done: saved.done,
    };
    const next = new Map(this.remindersByEmail());
    next.set(saved.emailId, summary);
    this.remindersByEmail.set(next);

    const selected = this.selectedEmail();
    if (selected && selected.id === saved.emailId) {
      this.currentReminder.set(saved);
    }
    this.mutateOnPage(saved.emailId, {});
  }

  private afterReminderRemoved(emailId: number): void {
    const next = new Map(this.remindersByEmail());
    next.delete(emailId);
    this.remindersByEmail.set(next);
    const selected = this.selectedEmail();
    if (selected && selected.id === emailId) {
      this.currentReminder.set(null);
    }
    this.mutateOnPage(emailId, {});
  }

  private resolveEmailIdForReminder(reminder: Reminder | ReminderSummary): number | null {
    if ('emailId' in reminder && reminder.emailId > 0) {
      return reminder.emailId;
    }
    const bySummary = this.remindersByEmail();
    for (const [emailId, summary] of bySummary.entries()) {
      if (summary.id === reminder.id) return emailId;
    }
    const current = this.currentReminder();
    if (current && current.id === reminder.id && 'emailId' in current && current.emailId > 0) {
      return current.emailId;
    }
    return null;
  }

  private findEmailById(emailId: number): EmailSummary | undefined {
    return this.emails().find((e) => e.id === emailId);
  }

  private ensureFullReminder(
    summary: Reminder | ReminderSummary,
    emailId: number
  ): Reminder {
    if ('message' in summary) {
      return summary;
    }
    return {
      id: summary.id,
      emailId,
      reminderDate: summary.reminderDate,
      message: null,
      done: summary.done,
      createdAt: '',
      updatedAt: '',
    };
  }

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
