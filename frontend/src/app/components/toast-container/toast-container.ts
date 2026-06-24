import { ChangeDetectionStrategy, Component, OnDestroy, inject } from '@angular/core';
import { Subject } from 'rxjs';
import { ToastService } from '../../services/toast.service';
import { ToastAction, ToastMessage } from '../../models/toast.model';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  templateUrl: './toast-container.html',
  styleUrl: './toast-container.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ToastContainerComponent implements OnDestroy {
  private readonly toastService = inject(ToastService);

  readonly toasts = this.toastService.toasts;

  private readonly onDestroy = new Subject<void>();

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  dismiss(id: number): void {
    this.toastService.dismiss(id);
  }

  /**
   * Fires the inline action associated with a toast and dismisses
   * the toast immediately. Errors thrown by the action are caught
   * by Angular's default error handler so a misbehaving callback
   * never breaks the container.
   */
  invokeAction(id: number, action: ToastAction): void {
    try {
      action.onClick();
    } finally {
      this.toastService.dismiss(id);
    }
  }

  trackById(_index: number, toast: ToastMessage): number {
    return toast.id;
  }
}
