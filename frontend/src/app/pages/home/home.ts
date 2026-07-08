import { Component, effect, inject, signal, OnInit, OnDestroy, computed, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
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
import { NotificationService } from '../../services/notification.service';
import { EmailSearchService } from '../../services/email-search.service';
import { AiExplanationService } from '../../services/ai-explanation.service';
import { EmailDetail, EmailSummary, EmailPageResponse } from '../../models/email-summary.model';
import { EmailAction } from '../../models/email-action.model';
import { EmailAnalysisResult, AnalysisState } from '../../models/email-analysis.model';
import { AiExplanation, AiState } from '../../models/ai-explanation.model';
import { Reminder, ReminderSummary } from '../../models/reminder.model';
import { HttpErrorResponse } from '@angular/common/http';
import { EMPTY, Observable, Subject, forkJoin, of, takeUntil } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { ReminderAction } from '../../components/reminder-indicator/reminder-indicator';

const MOBILE_QUERY = '(max-width: 720px)';

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
  private readonly notificationService = inject(NotificationService);
  private readonly emailSearchService = inject(EmailSearchService);
  private readonly aiExplanationService = inject(AiExplanationService);
  private readonly platformId = inject(PLATFORM_ID);

  readonly emails = signal<EmailSummary[]>([]);
  readonly selectedEmail = signal<EmailDetail | null>(null);
  readonly isHidden = signal(false);
  readonly loading = signal(false);
  readonly showTrialExpiredModal = signal(false);
  readonly hasNextPage = signal(false);
  readonly lastKnownPage = signal<number | null>(null);
  readonly currentPage = signal(0);

  /**
   * Trimmed value of the active search. Mirrors the {@code q} URL
   * param and the {@link EmailSearchService#term} signal. Used by
   * {@link fetchEmails$}, {@link cachePage} and
   * {@link prefetchNextPage} as the per-query cache key prefix and
   * as the echo guard for the {@code debouncedTerm$} subscription.
   * Exposed as a signal so the template can reactively render the
   * "no se encontraron correos para X" empty state.
   */
  readonly currentQuery = signal('');

  /**
   * True when the viewport is ≤ 720 px wide. On mobile the home page
   * filters the inbox live as the user types; on desktop the search
   * input drives a dropdown of top results and the inbox only
   * updates when the user explicitly opts in (Enter / "Ver todos").
   *
   * <p>Re-evaluated on every matchMedia {@code change} event so a
   * window resize flips the behaviour without a page reload.</p>
   */
  readonly isMobile = signal(false);

  readonly analysisResult = signal<EmailAnalysisResult | null>(null);
  readonly analysisState = signal<AnalysisState>('idle');
  /**
   * Two-way bound to the viewer/analysis panel-toggle. Flipped to
   * {@code true} once an analysis completes so the panel auto-reveals
   * the result.
   */
  readonly analysisPanelOpen = signal(false);

  readonly aiExplanation = signal<AiExplanation | null>(null);
  readonly aiState = signal<AiState>('idle');

  readonly isPremium = computed(() => this.authService.user()?.role === 'PREMIUM');
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
  readonly confirmEmailDeleteOpen = signal(false);

  readonly mobileEmailDetail = signal(false);

  hasEmails = () => this.emails().length > 0;

  private readonly onDestroy = new Subject<void>();
  private readonly pageCache = new Map<string, EmailPageResponse>();
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private pendingEmailId: number | null = null;
  /**
   * Tracks the email id currently being loaded/selected. Used to
   * ignore the {@code queryParamMap} echo that {@code router.navigate}
   * produces right after {@link selectEmail} (otherwise the navigation
   * would re-trigger the selection flow and loop).
   */
  private currentSelectionId: number | null = null;
  /**
   * Monotonic counter for the most recent fetch kick-off. Used by
   * {@link fetchEmails$} to discard responses that belong to a
   * superseded request — e.g. when the user clears the search box
   * (firing a fetch with {@code q=""}) and immediately types a new
   * value (firing another fetch with {@code q="foo"}). The
   * older, slower response is dropped even if it lands after the
   * newer one, preventing the stale results from overwriting the
   * fresh ones.
   *
   * <p>Complements the {@code switchMap} cancellation in the
   * debounced subscription: {@code switchMap} cancels the inner
   * observable when a NEW search term arrives; this counter
   * additionally drops late responses from imperative callers
   * (e.g. fast paginator clicks that race with the debounce).</p>
   */
  private fetchRequestId = 0;

  constructor() {
    // Viewport detection (US 3.7). The matchMedia subscription only
    // runs in the browser; on the server we default to "desktop" so
    // SSR markup matches the most common case.
    if (isPlatformBrowser(this.platformId)) {
      const mql = window.matchMedia(MOBILE_QUERY);
      this.isMobile.set(mql.matches);
      const onChange = (e: MediaQueryListEvent): void => this.isMobile.set(e.matches);
      mql.addEventListener('change', onChange);
      this.onDestroy.subscribe(() => mql.removeEventListener('change', onChange));
    }

    // Debounced search pipe (US 3.7). The header pushes the raw
    // input value into EmailSearchService, which debounces it 300 ms
    // and trims + dedupes. Here we react to the debounced term with
    // switchMap so a new term cancels the previous in-flight fetch
    // — this is the fix for the "clear then type" race where the
    // older (slower) response was overwriting the newer one.
    this.emailSearchService.debouncedTerm$.pipe(
      takeUntil(this.onDestroy),
      switchMap((trimmed) => {
        // Echo guard: the queryParamMap subscription below may have
        // already applied this value (deep link, back/forward). Skip
        // when the value didn't actually change from the URL's POV.
        if (trimmed === this.currentQuery()) {
          return EMPTY;
        }
        this.currentQuery.set(trimmed);
        this.pageCache.clear();
        this.currentPage.set(0);
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { q: trimmed || null, page: null, emailId: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
        // Desktop: the dropdown handles its own fetching. Skip the
        // inbox fetch so the user keeps their unfiltered inbox in
        // view. They can opt in via Enter or "Ver todos resultados".
        if (!this.isMobile()) {
          return EMPTY;
        }
        return this.fetchEmails$(0, trimmed);
      }),
    ).subscribe();

    // "Apply to inbox" (US 3.7 desktop). The user pressed Enter on
    // the search input, or clicked the "Ver todos los resultados"
    // link in the dropdown. Fetch the inbox for the current term
    // immediately (no debounce) — the caller has already committed
    // to the action.
    this.emailSearchService.inboxApply$.pipe(
      takeUntil(this.onDestroy),
    ).subscribe((term) => {
      this.currentQuery.set(term);
      this.pageCache.clear();
      this.currentPage.set(0);
      this.fetchEmails(0, term);
    });

    // Listen to the notification service's `done$` stream. The bell
    // fires it after a successful PATCH so we can refresh the
    // banner / chip on this side without a signal-effect cascade
    // (which was hanging the render thread).
    this.notificationService.done$
      .pipe(takeUntil(this.onDestroy))
      .subscribe((reminderId) => {
        // The bell may have marked a reminder done that the user
        // is currently viewing. If the open email is the one tied
        // to that reminder, refetch the email detail so the
        // banner updates.
        const current = this.currentReminder();
        if (current && current.id === reminderId && !current.done) {
          const email = this.selectedEmail();
          if (email) {
            this.emailService.getEmailDetail(email.id)
              .pipe(takeUntil(this.onDestroy))
              .subscribe({
                next: (detail) => {
                  this.selectedEmail.set(detail);
                  this.loadCurrentReminder(detail.id);
                },
              });
          }
        }
        // Always patch the list chip so it shows the done state.
        this.remindersByEmail.update((map) => {
          const next = new Map(map);
          for (const [emailId, summary] of next.entries()) {
            if (summary.id === reminderId) {
              next.set(emailId, { ...summary, done: true });
            }
          }
          return next;
        });
      });
  }

  ngOnInit(): void {
    const user = this.authService.user();
    if (user?.role === 'TRIAL' && user.trialExpired) {
      this.showTrialExpiredModal.set(true);
    } else {
      this.fetchEmails(0);
    }
    this.route.queryParamMap.pipe(takeUntil(this.onDestroy)).subscribe((params) => {
      const emailIdParam = params.get('emailId');
      const qParam = params.get('q') ?? '';

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

      // q sync: independent of emailId so toggling the search box
      // never re-triggers clearSelection(). The echo guard skips
      // the case where the URL change came from us (the debounced
      // subscription is the one that wrote it).
      if (qParam !== this.currentQuery()) {
        this.currentQuery.set(qParam);
        // Push the URL value into the search service WITHOUT firing
        // the debounce — we already triggered (or are about to
        // trigger) a fetch for this term, and re-running it 300 ms
        // later would be a duplicate request.
        this.emailSearchService.setTerm(qParam);
        this.pageCache.clear();
        this.fetchEmails(this.currentPage(), qParam);
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

  /**
   * Imperative entry point for callers that don't want the Rx pipeline
   * (initial load, paginator clicks, goToPage). Delegates to
   * {@link fetchEmails$} and subscribes synchronously; the inner
   * requestId / switchMap guards still apply, so this is safe to
   * call from multiple places concurrently.
   */
  fetchEmails(page: number = 0, q?: string): void {
    this.fetchEmails$(page, (q ?? this.currentQuery()).trim()).subscribe();
  }

  /**
   * Returns the observable that drives a single page fetch. Used by
   * {@link fetchEmails} (imperative callers) and directly by the
   * debounced search subscription via {@code switchMap} — the latter
   * is what gives us automatic cancellation when the user types
   * a new value before the previous response lands.
   *
   * <p>Cache hits are served synchronously through {@code of()};
   * cache misses issue an HTTP request whose response is filtered
   * through the {@link fetchRequestId} counter so a stale response
   * (e.g. from a superseded paginator click) cannot overwrite the
   * current view.</p>
   */
  private fetchEmails$(page: number, q: string): Observable<EmailPageResponse> {
    const key = this.cacheKey(page, q);
    const cached = this.pageCache.get(key);
    if (cached) {
      this.emails.set(cached.emails);
      this.hasNextPage.set(cached.hasNextPage);
      this.avatars.precompute(cached.emails.map((e) => e.sender));
      this.refreshRemindersForPage(cached.emails);
      this.prefetchNextPage(page + 1);
      this.tryConsumePendingSelection();
      return of(cached);
    }

    const requestId = ++this.fetchRequestId;
    this.loading.set(true);
    return this.emailService.fetchEmails(page, q).pipe(
      tap((response) => {
        // Drop the response if a newer fetch has been kicked off
        // (paginator race, debounce emission, etc.). Without this
        // guard, the older request could land after the newer one
        // and silently overwrite the visible results.
        if (requestId !== this.fetchRequestId) {
          return;
        }
        this.cachePage(page, response, q);
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
      }),
      catchError(() => {
        if (requestId === this.fetchRequestId) {
          this.loading.set(false);
        }
        return EMPTY;
      }),
    );
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
    const q = this.currentQuery();
    const key = this.cacheKey(page, q);
    if (this.pageCache.has(key)) return;
    this.emailService.fetchEmails(page, q).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (response) => {
        this.cachePage(page, response, q);
        this.avatars.precompute(response.emails.map((e) => e.sender));
      },
      error: () => {}
    });
  }

  /**
   * Composite cache key that scopes a page to the current query.
   * Without this, paginated pages from a previous search would
   * leak into a fresh unfiltered session and vice versa.
   */
  private cacheKey(page: number, q: string): string {
    return `${q}::${page}`;
  }

  private cachePage(page: number, response: EmailPageResponse, q: string): void {
    this.pageCache.set(this.cacheKey(page, q), response);
    if (this.pageCache.size <= MAX_PAGE_CACHE) return;
    const current = this.currentPage();
    const protectedKeys = new Set([
      this.cacheKey(current - 1, q),
      this.cacheKey(current, q),
      this.cacheKey(current + 1, q),
    ]);
    let oldestKey: string | null = null;
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
    this.router.navigate(['/home'], {
      queryParams: {
        q: this.currentQuery() || null,
        emailId: null,
        page: null,
      },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.currentPage.set(page);
    this.fetchEmails(page);
  }

  selectEmail(email: EmailSummary): void {
    this.mobileEmailDetail.set(true);
    if (this.currentSelectionId === email.id) return;
    this.currentSelectionId = email.id;
    this.isHidden.set(email.isHidden ?? false);
    // Optimistic: mark the row as read in both the signal and the
    // page cache so paginating back doesn't lose the change.
    this.mutateOnPage(email.id!, { isRead: true });
    this.selectedEmail.set(null);
    this.analysisResult.set(null);
    this.analysisState.set('idle');
    this.analysisPanelOpen.set(false);
    this.currentReminder.set(null);
    this.aiExplanation.set(null);
    this.aiState.set('idle');

    this.emailService.getEmailDetail(email.id!).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (detail) => {
        this.selectedEmail.set(detail);
        // The detail knows the canonical isImportant flag; sync the
        // list row + cache with it.
        this.mutateOnPage(email.id!, { isImportant: detail.isImportant });
        this.isHidden.set(detail.isHidden);
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
    this.isHidden.set(false);
    this.pendingEmailId = null;
    this.aiExplanation.set(null);
    this.aiState.set('idle');
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
    const cached = this.pageCache.get(this.cacheKey(page, this.currentQuery()));
    if (cached) {
      this.pageCache.set(this.cacheKey(page, this.currentQuery()), {
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
    this.aiExplanation.set(null);
    this.aiState.set('idle');
    this.emailService.getEmailDetail(emailId).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (detail) => {
        this.selectedEmail.set(detail);
        this.isHidden.set(detail.isHidden);
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

  onEmailAction(action: EmailAction): void {
    switch (action) {
      case 'explain-ai':
        if (this.analysisState() === 'idle') {
          this.runAnalysis(this.selectedEmail()!.id);
        } else {
          this.analysisPanelOpen.set(true);
        }
        return;
      case 'mark-important':
        this.onMarkImportant();
        return;
      case 'create-reminder':
        this.onCreateReminder();
        return;
      case 'hide':
        this.onHideEmail();
        return;
      case 'delete':
        this.confirmEmailDeleteOpen.set(true);
        return;
    }
  }

  private onMarkImportant(): void {
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

  private onCreateReminder(): void {
    const email = this.selectedEmail();
    if (!email) return;
    if (this.userRole() === 'TRIAL') {
      this.toast.warning('Los recordatorios están disponibles en el plan Premium.');
      this.router.navigate(['/plan']);
      return;
    }
    const existing = this.currentReminder();
    if (existing) {
      this.openEditReminderDialog(email.id, existing);
      return;
    }
    this.openCreateReminderDialog(email.id, email.subject);
  }

  private onHideEmail(): void {
    const email = this.selectedEmail();
    if (!email) return;
    const currentlyHidden = this.isHidden();
    const action$ = currentlyHidden
      ? this.emailService.unhide(email.id)
      : this.emailService.hide(email.id);
    this.isHidden.set(!currentlyHidden);
    action$.pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (res) => {
        this.isHidden.set(res.isHidden);
        if (res.isHidden) {
          this.emails.update((list) => list.filter((e) => e.id !== email.id));
          this.mutateOnPage(email.id, { isHidden: res.isHidden });
          this.clearSelection();
          this.toast.success('Correo ocultado.');
        } else {
          this.fetchEmails(this.currentPage(), this.currentQuery());
          this.clearSelection();
          this.toast.success('Correo visible de nuevo.');
        }
      },
      error: () => {
        this.isHidden.set(currentlyHidden);
        this.toast.error('No se pudo cambiar la visibilidad del correo. Intenta de nuevo.');
      },
    });
  }

  // --- Reminder flow --------------------------------------------

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
    // List chip click — always open the dialog in edit mode so the
    // user can tweak the date/message and (since edit reactivates)
    // bring a done reminder back to the active list.
    const emailId = action.reminder.emailId || this.resolveEmailIdForReminder(action.reminder);
    if (emailId == null || emailId === 0) {
      // Fallback: if we couldn't resolve the emailId (rare race),
      // delegate to the generic action handler.
      this.onReminderAction({ ...action, type: 'edit' });
      return;
    }
    this.openEditReminderDialog(emailId, action.reminder);
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

  onConfirmEmailDeleteCancelled(): void {
    this.confirmEmailDeleteOpen.set(false);
  }

  onConfirmEmailDeleteAccepted(): void {
    const email = this.selectedEmail();
    if (!email) return;
    this.confirmEmailDeleteOpen.set(false);
    this.emailService.softDelete(email.id).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (res) => {
        this.emails.update((list) => list.filter((e) => e.id !== email.id));
        this.mutateOnPage(email.id, { isDeleted: res.isDeleted });
        this.clearSelection();
        this.emailService.refreshDeletedCount();
        this.toast.success('Correo eliminado.');
      },
      error: () => {
        this.toast.error('No se pudo eliminar el correo. Intenta de nuevo.');
      },
    });
  }

  private markReminderDone(reminder: Reminder | ReminderSummary, emailId: number): void {
    // The PATCH runs synchronously inside the click handler — any
    // deferral (e.g. setTimeout(0)) runs the HTTP call outside
    // Angular's zone, which combined with the banner re-render
    // inside the same tick was hanging the event loop and locking
    // the page. Route through the notification service so the
    // bell panel and the in-flight dedupe stay in sync —
    // otherwise the just-done reminder would keep showing in the
    // notification list for the rest of the polling cycle.
    this.notificationService.markDoneById(reminder.id)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: () => {
          this.toast.success('Recordatorio marcado como hecho.');
          this.applyReminderUpdate(
            { ...(reminder as Reminder), done: true },
            this.findEmailById(emailId)
          );
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

  onExplainRequest(): void {
    const email = this.selectedEmail();
    if (!email) return;
    this.aiState.set('loading');
    this.aiExplanationService.explain(email.id).pipe(
      takeUntil(this.onDestroy)
    ).subscribe({
      next: (result) => {
        this.aiExplanation.set(result);
        this.aiState.set('ready');
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 403) {
          this.aiState.set('unavailable-trial');
          this.toast.error('Has alcanzado el límite de análisis de tu prueba (incluye explicaciones con IA).');
        } else {
          this.aiState.set('error');
          this.toast.error('IA no disponible', {
            action: {
              label: 'Reintentar',
              onClick: () => this.onExplainRequest(),
            },
          });
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
