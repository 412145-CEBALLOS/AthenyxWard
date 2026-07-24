import {
  ChangeDetectionStrategy,
  Component,
  inject,
  OnInit,
  OnDestroy,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { CheckoutService } from '../../services/checkout.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { CheckoutStatusResponse } from '../../models/plan.model';

type ReturnStep = 'loading' | 'pending' | 'success' | 'failed';

@Component({
  selector: 'app-checkout-return',
  standalone: true,
  imports: [],
  templateUrl: './checkout-return.html',
  styleUrl: './checkout-return.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutReturnComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly checkoutService = inject(CheckoutService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly onDestroy = new Subject<void>();

  readonly step = signal<ReturnStep>('loading');
  readonly paymentId = signal<number | null>(null);
  readonly mpStatus = signal<string | null>(null);

  private pollCount = 0;
  private maxPolls = 30;
  private extendedPolls = 0;
  private maxExtendedPolls = 12;
  private pollInterval: ReturnType<typeof setInterval> | null = null;

  ngOnInit(): void {
    const idParam = this.route.snapshot.queryParamMap.get('payment_id');
    const statusParam = this.route.snapshot.queryParamMap.get('status');
    const externalRefParam = this.route.snapshot.queryParamMap.get('external_reference');

    if (idParam) {
      this.paymentId.set(Number(idParam));
    }
    if (statusParam) {
      this.mpStatus.set(statusParam);
    }

    if (!this.paymentId() && externalRefParam) {
      this.step.set('pending');
      this.startPolling();
    } else if (this.paymentId()) {
      this.startPolling();
    } else {
      this.step.set('pending');
    }
  }

  ngOnDestroy(): void {
    this.clearPoll();
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  private startPolling(): void {
    this.clearPoll();
    this.pollCount = 0;
    this.extendedPolls = 0;
    this.pollInterval = setInterval(() => {
      this.pollStatus();
    }, 2000);
    this.pollStatus();
  }

  private clearPoll(): void {
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  private pollStatus(): void {
    const id = this.paymentId();
    if (!id) {
      this.step.set('pending');
      return;
    }

    this.checkoutService.getStatus(id)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (status) => {
          if (status.status === 'COMPLETED') {
            this.clearPoll();
            this.step.set('success');
            this.auth.checkAuth().pipe(takeUntil(this.onDestroy)).subscribe();
            this.toast.success('Pago completado. Bienvenido a Premium!');
            this.router.navigate(['/home']);
          } else if (status.status === 'FAILED') {
            this.clearPoll();
            this.step.set('failed');
          } else if (status.status === 'PENDING') {
            this.pollCount++;
            if (this.pollCount >= this.maxPolls) {
              this.clearPoll();
              this.step.set('pending');
              this.startExtendedPolling();
            }
          }
        },
        error: () => {
          this.pollCount++;
          if (this.pollCount >= this.maxPolls) {
            this.clearPoll();
            this.step.set('pending');
            this.startExtendedPolling();
          }
        },
      });
  }

  private startExtendedPolling(): void {
    this.pollInterval = setInterval(() => {
      this.pollStatusExtended();
    }, 5000);
    this.pollStatusExtended();
  }

  private pollStatusExtended(): void {
    const id = this.paymentId();
    if (!id) {
      this.clearPoll();
      return;
    }

    this.checkoutService.getStatus(id)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (status) => {
          if (status.status === 'COMPLETED') {
            this.clearPoll();
            this.step.set('success');
            this.auth.checkAuth().pipe(takeUntil(this.onDestroy)).subscribe();
            this.toast.success('Pago completado. Bienvenido a Premium!');
            this.router.navigate(['/home']);
          } else if (status.status === 'FAILED') {
            this.clearPoll();
            this.step.set('failed');
          } else {
            this.extendedPolls++;
            if (this.extendedPolls >= this.maxExtendedPolls) {
              this.clearPoll();
            }
          }
        },
        error: () => {
          this.extendedPolls++;
          if (this.extendedPolls >= this.maxExtendedPolls) {
            this.clearPoll();
          }
        },
      });
  }

  goToPlan(): void {
      this.router.navigate(['/home']);
  }

  retry(): void {
      this.router.navigate(['/home']);
  }

  formatAmount(amount: number, currency: string): string {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: currency,
    }).format(amount);
  }
}
