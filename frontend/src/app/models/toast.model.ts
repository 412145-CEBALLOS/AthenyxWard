/** Visual category of a toast, mapped to a colour and default duration. */
export type ToastType = 'error' | 'warning' | 'info' | 'success';

/** A single visible toast as exposed by {@link ToastService.toasts}. */
export interface ToastMessage {
  id: number;
  type: ToastType;
  message: string;
  /** Increments each time the same message is pushed within the dedupe window. */
  count: number;
  /** Unix ms timestamp of creation or last refresh. */
  createdAt: number;
}

/** Optional overrides when pushing a toast. */
export interface ToastOptions {
  /** Custom duration in milliseconds (overrides the type default). */
  duration?: number;
}
