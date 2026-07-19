import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { HelpCenterService } from '../../services/help-center.service';
import { FaqCategory } from '../../models/faq.model';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-help-center',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './help-center.html',
  styleUrl: './help-center.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HelpCenterComponent implements OnInit, OnDestroy {
  private readonly helpCenterService = inject(HelpCenterService);
  private readonly onDestroy = new Subject<void>();

  readonly supportEmail = environment.supportEmail;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly query = signal('');

  private readonly allCategories = signal<FaqCategory[]>([]);

  readonly filteredCategories = computed(() => {
    const q = this.normalize(this.query());
    const cats = this.allCategories();
    if (!q) {
      return cats;
    }
    return cats
      .map((c) => ({
        ...c,
        items: c.items.filter(
          (item) =>
            this.normalize(item.q).includes(q) ||
            this.normalize(item.a).includes(q),
        ),
      }))
      .filter((c) => c.items.length > 0);
  });

  private normalize(str: string): string {
    return str.toLowerCase().trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }

  readonly noResults = computed(
    () =>
      !this.loading() &&
      !this.error() &&
      this.query().length > 0 &&
      this.filteredCategories().length === 0,
  );

  ngOnInit(): void {
    this.loadFaqs();
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  onQueryInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
  }

  loadFaqs(): void {
    this.loading.set(true);
    this.error.set(false);
    this.helpCenterService
      .getFaqs()
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (data) => {
          this.allCategories.set(data.categories);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }
}
