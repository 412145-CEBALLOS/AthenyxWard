import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

export interface MenuItem {
  id: string;
  label: string;
  icon?: string;
  variant?: 'default' | 'destructive';
  active?: boolean;
  disabled?: boolean;
  disabledTooltip?: string;
  ariaLabel?: string;
}

@Component({
  selector: 'app-kebab-menu',
  standalone: true,
  imports: [],
  templateUrl: './kebab-menu.html',
  styleUrl: './kebab-menu.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KebabMenuComponent {
  readonly items = input.required<MenuItem[]>();
  readonly label = input<string>('Más acciones');
  readonly buttonClass = input<string>('kebab-trigger');

  readonly action = output<string>();

  private readonly host = viewChild<ElementRef<HTMLElement>>('host');
  readonly triggerBtn = viewChild<ElementRef<HTMLButtonElement>>('triggerBtn');
  readonly menuEl = viewChild<ElementRef<HTMLUListElement>>('menuEl');

  readonly isOpen = signal(false);
  private readonly activeIndex = signal(0);

  readonly enabledItems = computed(() =>
    this.items().map((item, i) => ({ item, index: i })).filter(({ item }) => !item.disabled),
  );

  toggle(): void {
    if (this.isOpen()) {
      this.close();
    } else {
      this.open();
    }
  }

  private open(): void {
    this.isOpen.set(true);
    const first = this.firstEnabledIndex();
    this.activeIndex.set(first >= 0 ? first : 0);
    setTimeout(() => {
      const menu = this.menuEl();
      if (menu) {
        const items = menu.nativeElement.querySelectorAll<HTMLElement>('[role="menuitem"]:not([aria-disabled="true"])');
        const firstEnabled = Array.from(items).find(
          (el) => !el.hasAttribute('aria-disabled'),
        );
        firstEnabled?.focus();
      }
    });
  }

  close(): void {
    this.isOpen.set(false);
    this.triggerBtn()?.nativeElement.focus();
  }

  selectItem(item: MenuItem): void {
    if (item.disabled) return;
    this.action.emit(item.id);
    this.close();
  }

  private firstEnabledIndex(): number {
    const idx = this.items().findIndex((item) => !item.disabled);
    return idx >= 0 ? idx : 0;
  }

  private lastEnabledIndex(): number {
    const items = this.items();
    for (let i = items.length - 1; i >= 0; i--) {
      if (!items[i].disabled) return i;
    }
    return items.length - 1;
  }

  moveFocus(delta: 1 | -1): void {
    const enabled = this.enabledItems();
    if (enabled.length === 0) return;
    const current = this.activeIndex();
    const currentEnabledPos = enabled.findIndex(({ index }) => index === current);
    const nextPos = Math.max(0, Math.min(enabled.length - 1, currentEnabledPos + delta));
    this.activeIndex.set(enabled[nextPos].index);
    this.focusActiveItem();
  }

  goToFirst(): void {
    const first = this.firstEnabledIndex();
    this.activeIndex.set(first);
    this.focusActiveItem();
  }

  goToLast(): void {
    const last = this.lastEnabledIndex();
    this.activeIndex.set(last);
    this.focusActiveItem();
  }

  private focusActiveItem(): void {
    const menu = this.menuEl();
    if (!menu) return;
    const el = menu.nativeElement.querySelector<HTMLElement>(
      `[role="menuitem"][data-index="${this.activeIndex()}"]`,
    );
    el?.focus();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const hostEl = this.host()?.nativeElement;
    if (!hostEl || hostEl.contains(event.target as Node)) return;
    this.isOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen()) {
      this.close();
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!this.isOpen()) return;
    const menu = this.menuEl();
    if (!menu?.nativeElement.contains(document.activeElement)) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.moveFocus(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.moveFocus(-1);
        break;
      case 'Home':
        event.preventDefault();
        this.goToFirst();
        break;
      case 'End':
        event.preventDefault();
        this.goToLast();
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        this.activateActive();
        break;
    }
  }

  private activateActive(): void {
    const idx = this.activeIndex();
    const item = this.items()[idx];
    if (item && !item.disabled) {
      this.selectItem(item);
    }
  }

  isActive(index: number): boolean {
    return this.activeIndex() === index;
  }
}
