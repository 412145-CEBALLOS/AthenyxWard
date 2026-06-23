import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { EmailPaginatorComponent } from '../../components/email-paginator/email-paginator';
import { ImportantEmailDatePipe } from '../../pipes/important-email-date.pipe';
import { AnalysisService } from '../../services/analysis.service';
import {
  AnalysisHistoryItem,
  RiskLevel,
} from '../../models/email-analysis.model';

@Component({
  selector: 'app-analysis-history',
  standalone: true,
  imports: [PageShellComponent, EmailPaginatorComponent, ImportantEmailDatePipe],
  templateUrl: './analysis-history.html',
  styleUrl: './analysis-history.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalysisHistoryComponent implements OnInit, OnDestroy {
  private readonly analysisService = inject(AnalysisService);
  private readonly router = inject(Router);
  private readonly onDestroy = new Subject<void>();

  readonly items = signal<AnalysisHistoryItem[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly from = signal<string | null>(null);
  readonly to = signal<string | null>(null);

  readonly hasNextPage = computed(
    () => this.currentPage() < this.totalPages() - 1,
  );
  readonly hasFilters = computed(
    () => this.from() !== null || this.to() !== null,
  );
  readonly emptyState = computed(
    () =>
      !this.loading() &&
      !this.error() &&
      this.totalItems() === 0 &&
      !this.hasFilters(),
  );
  readonly emptyFilteredState = computed(
    () =>
      !this.loading() &&
      !this.error() &&
      this.totalItems() === 0 &&
      this.hasFilters(),
  );
  readonly outOfRange = computed(
    () =>
      !this.loading() &&
      !this.error() &&
      this.totalItems() > 0 &&
      this.items().length === 0,
  );

  readonly maxDate = new Date().toISOString().split('T')[0];

  ngOnInit(): void {
    this.loadHistory();
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  onFromChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.from.set(value || null);
  }

  onToChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.to.set(value || null);
  }

  applyFilters(): void {
    this.currentPage.set(0);
    this.loadHistory();
  }

  clearFilters(): void {
    this.from.set(null);
    this.to.set(null);
    this.currentPage.set(0);
    this.loadHistory();
  }

  onPageChange(page: number): void {
    const target = this.clampPage(page);
    if (target === this.currentPage()) {
      return;
    }
    this.currentPage.set(target);
    this.loadHistory();
  }

  openEmail(item: AnalysisHistoryItem): void {
    this.router.navigate(['/home'], {
      queryParams: { emailId: item.emailId },
    });
  }

  levelLabel(level: RiskLevel): string {
    switch (level) {
      case 'GREEN':
        return 'Seguro';
      case 'YELLOW':
        return 'Sospechoso';
      case 'RED':
        return 'Peligroso';
    }
  }

  private clampPage(page: number): number {
    const safe = Math.max(0, page);
    const max = this.totalPages();
    if (max <= 0) return 0;
    return Math.min(safe, max - 1);
  }

  private loadHistory(): void {
    this.loading.set(true);
    this.error.set(false);
    this.analysisService
      .getHistory({
        from: this.from() ?? undefined,
        to: this.to() ?? undefined,
        page: this.currentPage(),
      })
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (response) => {
          this.items.set(response.items);
          this.currentPage.set(response.currentPage);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }
}
