import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, EMPTY, Observable } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { takeUntil } from 'rxjs';
import { EmailSearchService } from '../../services/email-search.service';
import { EmailService } from '../../services/email.service';
import { EmailSummary, EmailPageResponse } from '../../models/email-summary.model';
import { SenderAvatarComponent } from '../sender-avatar/sender-avatar';
import { EmailDatePipe } from '../../pipes/email-date.pipe';

/**
 * Gmail-style search dropdown. Mounted inside the header, rendered
 * when the search input has focus and the term is non-empty. Fetches
 * its own top results (page 0, size 20) and re-fetches whenever the
 * debounced term changes — cancellation happens via
 * {@code switchMap} so a new term discards the in-flight request.
 *
 * <p>This component is hidden via CSS on viewports ≤ 720 px (the
 * home page filters the inbox live on mobile instead).</p>
 */
@Component({
  selector: 'app-email-search-results',
  standalone: true,
  imports: [SenderAvatarComponent, EmailDatePipe],
  templateUrl: './email-search-results.html',
  styleUrl: './email-search-results.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailSearchResultsComponent implements OnDestroy {
  private readonly emailSearchService = inject(EmailSearchService);
  private readonly emailService = inject(EmailService);
  private readonly router = inject(Router);

  /** Top results for the current debounced term. */
  readonly results = signal<EmailSummary[]>([]);
  /** True while a fetch is in flight and we have no results yet. */
  readonly loading = signal(false);
  /** True when the dropdown is rendered (focus + non-empty term). */
  readonly visible = computed(
    () => this.emailSearchService.isOpen() && this.emailSearchService.term().trim().length > 0,
  );
  /** Current term — read in the template to render the input value
   *  in the "no results" / "view all" copy. */
  readonly term = this.emailSearchService.term;

  private readonly onDestroy = new Subject<void>();
  /** Monotonic request id so late responses can't overwrite fresh ones. */
  private requestId = 0;

  constructor() {
    this.emailSearchService.debouncedTerm$.pipe(
      takeUntil(this.onDestroy),
      switchMap((trimmed) => this.fetchResults$(trimmed)),
    ).subscribe();
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  private fetchResults$(term: string): Observable<EmailPageResponse> {
    if (!term) {
      this.results.set([]);
      this.loading.set(false);
      return EMPTY;
    }
    const id = ++this.requestId;
    this.loading.set(true);
    return this.emailService.fetchEmails(0, term, 20).pipe(
      tap((response) => {
        if (id !== this.requestId) return; // superseded
        this.results.set(response.emails);
        this.loading.set(false);
      }),
      catchError(() => {
        if (id === this.requestId) this.loading.set(false);
        return EMPTY;
      }),
    );
  }

  /**
   * Bound to each result row. Closes the dropdown and navigates to
   * the email viewer. We do NOT clear the search term — Gmail keeps
   * it so the user can refine the query without losing context.
   */
  onResultClick(email: EmailSummary): void {
    this.emailSearchService.close();
    this.router.navigate(['/home'], { queryParams: { emailId: email.id } });
  }

  /**
   * Bound to the "Ver todos los resultados" footer. Tells the home
   * page to apply the search to the inbox (immediate, no debounce)
   * and closes the dropdown.
   */
  onViewAll(): void {
    const term = this.emailSearchService.term().trim();
    this.emailSearchService.close();
    this.emailSearchService.applyToInbox(term);
  }
}
