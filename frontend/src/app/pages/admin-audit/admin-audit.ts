import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { EmailPaginatorComponent } from '../../components/email-paginator/email-paginator';
import { AuditDetailDrawerComponent } from '../../components/audit-detail-drawer/audit-detail-drawer';
import { AuditService } from '../../services/audit.service';
import {
  AuditActionType,
  AuditEntryResponse,
  AuditFilters,
  AuditSeverity,
} from '../../models/audit.model';
import { environment } from '../../../environments/environment';

type Period = '1w' | '1m' | '1y' | 'all';

@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [PageShellComponent, EmailPaginatorComponent, AuditDetailDrawerComponent, DatePipe],
  templateUrl: './admin-audit.html',
  styleUrl: './admin-audit.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminAuditComponent implements OnInit, OnDestroy {
  private readonly auditService = inject(AuditService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly onDestroy = new Subject<void>();
  private readonly querySubject = new Subject<string>();
  private readonly platformId = inject(PLATFORM_ID);

  readonly entries = signal<AuditEntryResponse[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly selectedEntry = signal<AuditEntryResponse | null>(null);
  readonly drawerOpen = signal(false);

  readonly period = signal<Period>('all');
  readonly actorFilter = signal('');
  readonly actionFilter = signal<AuditActionType | ''>('');
  readonly severityFilter = signal<AuditSeverity | ''>('');
  readonly query = signal('');

  readonly hasNextPage = computed(() => this.currentPage() < this.totalPages() - 1);
  readonly emptyState = computed(() => !this.loading() && !this.error() && this.totalItems() === 0);
  readonly maxDate = new Date().toISOString().split('T')[0];

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.querySubject
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.onDestroy))
      .subscribe((q) => {
        this.query.set(q);
        this.currentPage.set(0);
        this.loadEntries();
      });

    this.loadEntries();
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  setPeriod(p: Period): void {
    this.period.set(p);
    this.currentPage.set(0);
    this.loadEntries();
  }

  onActorChange(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.actorFilter.set(value);
    this.currentPage.set(0);
    this.loadEntries();
  }

  onActionChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as AuditActionType | '';
    this.actionFilter.set(value);
    this.currentPage.set(0);
    this.loadEntries();
  }

  onSeverityChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as AuditSeverity | '';
    this.severityFilter.set(value);
    this.currentPage.set(0);
    this.loadEntries();
  }

  onQueryInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.querySubject.next(value);
  }

  onPageChange(page: number): void {
    const safe = Math.max(0, page);
    if (safe === this.currentPage()) return;
    this.currentPage.set(safe);
    this.loadEntries();
  }

  openDrawer(entry: AuditEntryResponse): void {
    this.selectedEntry.set(entry);
    this.drawerOpen.set(true);
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  onCorrelationClick(correlationId: string): void {
    this.actorFilter.set('');
    this.query.set(correlationId);
    this.querySubject.next(correlationId);
    this.currentPage.set(0);
    this.loadEntries();
  }

  exportCsv(): void {
    const filters: AuditFilters = this.buildFilters(true);
    const url = this.auditService.getExportUrl(filters);
    const a = document.createElement('a');
    a.href = url;
    a.download = `audit-${new Date().toISOString().split('T')[0]}.csv`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }

  private fromDate(): string | undefined {
    const now = new Date();
    const p = this.period();
    if (p === 'all') return undefined;
    const d = new Date(now);
    if (p === '1w') d.setDate(d.getDate() - 7);
    if (p === '1m') d.setMonth(d.getMonth() - 1);
    if (p === '1y') d.setFullYear(d.getFullYear() - 1);
    return d.toISOString().split('T')[0];
  }

  private toDate(): string | undefined {
    if (this.period() === 'all') return undefined;
    return new Date().toISOString().split('T')[0];
  }

  private buildFilters(isExport = false): AuditFilters {
    const f: AuditFilters = {
      from: this.fromDate(),
      to: this.toDate(),
      actor: this.actorFilter() || undefined,
      action: this.actionFilter() || undefined,
      severity: this.severityFilter() || undefined,
      page: isExport ? undefined : this.currentPage(),
      size: isExport ? undefined : 20,
    };
    if (isExport) {
      f.query = undefined;
    } else {
      f.query = this.query() || undefined;
    }
    return f;
  }

  loadEntries(): void {
    this.loading.set(true);
    this.error.set(false);
    this.auditService
      .getEntries(this.buildFilters())
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (response) => {
          this.entries.set(response.items);
          this.currentPage.set(response.currentPage);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
          this.loading.set(false);
          this.error.set(false);
          this.cdr.markForCheck();
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
          this.cdr.markForCheck();
        },
      });
  }
}
