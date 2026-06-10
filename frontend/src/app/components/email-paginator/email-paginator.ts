import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';

@Component({
  selector: 'app-email-paginator',
  standalone: true,
  imports: [],
  templateUrl: './email-paginator.html',
  styleUrl: './email-paginator.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailPaginatorComponent {
  readonly currentPage = input.required<number>();
  readonly hasNextPage = input.required<boolean>();
  readonly lastKnownPage = input<number | null>(null);
  readonly page = output<number>();

  readonly canJumpFirst = computed(() => this.currentPage() > 0);
  readonly canJumpPrevFive = computed(() => this.currentPage() > 0);
  readonly canJumpPrev = computed(() => this.currentPage() > 0);
  readonly canJumpNext = computed(() => this.hasNextPage());
  readonly canJumpNextFive = computed(() => {
    const last = this.lastKnownPage();
    if (last !== null) return this.currentPage() + 5 <= last;
    return this.hasNextPage();
  });

  jumpFirst(): void { this.page.emit(0); }
  jumpPrevFive(): void { this.page.emit(Math.max(0, this.currentPage() - 5)); }
  jumpPrev(): void { this.page.emit(Math.max(0, this.currentPage() - 1)); }
  jumpNext(): void { this.page.emit(this.currentPage() + 1); }
  jumpNextFive(): void { this.page.emit(this.currentPage() + 5); }
}
