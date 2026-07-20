import { Injectable, inject, signal, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type Theme = 'auto' | 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'athenyx.theme';
  private readonly platformId = inject(PLATFORM_ID);

  readonly theme = signal<Theme>('auto');

  init(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const stored = this.getStoredTheme();
    this.applyToDom(stored);
    this.theme.set(stored);
  }

  setTheme(value: Theme): void {
    if (!isPlatformBrowser(this.platformId)) {
      this.theme.set(value);
      return;
    }
    if (value === 'auto') {
      localStorage.removeItem(this.STORAGE_KEY);
    } else {
      localStorage.setItem(this.STORAGE_KEY, value);
    }
    this.applyToDom(value);
    this.theme.set(value);
  }

  private getStoredTheme(): Theme {
    const raw = localStorage.getItem(this.STORAGE_KEY);
    if (raw === 'light' || raw === 'dark') return raw;
    return 'auto';
  }

  private applyToDom(value: Theme): void {
    const el = document.documentElement;
    if (value === 'auto') {
      el.removeAttribute('data-theme');
    } else {
      el.setAttribute('data-theme', value);
    }
  }
}
