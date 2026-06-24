/** Visual category of a toast, mapped to a colour and default duration. */
export type ToastType = 'error' | 'warning' | 'info' | 'success';

/**
 * Optional inline action rendered as a button on the right of the
 * toast. Clicking it invokes {@link ToastAction.onClick} and
 * dismisses the toast. Used (for example) to let the user mark a
 * reminder as done without leaving the current page.
 */
export interface ToastAction {
  label: string;
  onClick: () => void;
}

/** A single visible toast as exposed by {@link ToastService.toasts}. */
export interface ToastMessage {
  id: number;
  type: ToastType;
  message: string;
  /** Increments each time the same message is pushed within the dedupe window. */
  count: number;
  /** Unix ms timestamp of creation or last refresh. */
  createdAt: number;
  /** Optional inline action button. */
  action?: ToastAction;
}

/** Optional overrides when pushing a toast. */
export interface ToastOptions {
  /** Custom duration in milliseconds (overrides the type default). */
  duration?: number;
  /** Inline action button rendered inside the toast. */
  action?: ToastAction;
}
