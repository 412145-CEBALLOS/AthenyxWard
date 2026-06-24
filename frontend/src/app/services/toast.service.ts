import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ToastMessage, ToastOptions, ToastType } from '../models/toast.model';

const DEFAULT_DURATION_MS: Record<ToastType, number> = {
  error: 6000,
  warning: 5000,
  info: 4000,
  success: 4000,
};

const DEDUPE_WINDOW_MS = 3000;
const MAX_STACK = 5;

/**
 * Lightweight toast queue.
 *
 * <p>Backed by a single signal so the {@link ToastContainerComponent}
 * re-renders automatically on change. Identical messages fired within
 * a 3-second window are deduplicated (the duplicate increments the
 * {@link ToastMessage.count} and pushes the dismiss timer out).
 * The visible stack is capped at {@code MAX_STACK} (5) — older
 * toasts are evicted FIFO.</p>
 *
 * <p>Dismiss timers are skipped during SSR (no DOM, no
 * {@code setTimeout}).</p>
 */
@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private readonly platformId = inject(PLATFORM_ID);

  /** Signal of currently-visible toasts (newest at the end). */
  readonly toasts = signal<ToastMessage[]>([]);

  private nextId = 1;
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  /** Pushes an error toast (default duration: 6 s). */
  error(message: string, opts?: ToastOptions): void {
    this.push('error', message, opts);
  }

  /** Pushes a warning toast (default duration: 5 s). */
  warning(message: string, opts?: ToastOptions): void {
    this.push('warning', message, opts);
  }

  /** Pushes an info toast (default duration: 4 s). */
  info(message: string, opts?: ToastOptions): void {
    this.push('info', message, opts);
  }

  /** Pushes a success toast (default duration: 4 s). */
  success(message: string, opts?: ToastOptions): void {
    this.push('success', message, opts);
  }

  /**
   * Manually dismisses a single toast.
   *
   * @param id toast identifier returned by the service
   */
  dismiss(id: number): void {
    this.clearTimer(id);
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  /** Dismisses every visible toast and clears pending timers. */
  clear(): void {
    for (const id of this.timers.keys()) {
      this.clearTimer(id);
    }
    this.toasts.set([]);
  }

  private push(type: ToastType, message: string, opts?: ToastOptions): void {
    const now = Date.now();
    const duration = opts?.duration ?? DEFAULT_DURATION_MS[type];

    const existing = this.toasts().find(
      (t) => t.type === type && t.message === message && now - t.createdAt < DEDUPE_WINDOW_MS,
    );

    if (existing) {
      this.toasts.update((list) =>
        list.map((t) =>
          t.id === existing.id ? { ...t, count: t.count + 1, createdAt: now } : t,
        ),
      );
      this.scheduleDismiss(existing.id, duration);
      return;
    }

    const toast: ToastMessage = {
      id: this.nextId++,
      type,
      message,
      count: 1,
      createdAt: now,
      action: opts?.action,
    };

    this.toasts.update((list) => {
      const next = [...list, toast];
      if (next.length > MAX_STACK) {
        const dropped = next[0];
        this.clearTimer(dropped.id);
        return next.slice(1);
      }
      return next;
    });

    this.scheduleDismiss(toast.id, duration);
  }

  private scheduleDismiss(id: number, duration: number): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    this.clearTimer(id);
    const handle = setTimeout(() => this.dismiss(id), duration);
    this.timers.set(id, handle);
  }

  private clearTimer(id: number): void {
    const handle = this.timers.get(id);
    if (handle !== undefined) {
      clearTimeout(handle);
      this.timers.delete(id);
    }
  }
}
