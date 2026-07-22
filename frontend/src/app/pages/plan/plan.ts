import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
  computed,
  OnInit,
  OnDestroy,
  PLATFORM_ID,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog';
import { AuthService } from '../../services/auth.service';
import { SubscriptionService } from '../../services/subscription.service';
import { CheckoutService } from '../../services/checkout.service';
import { ToastService } from '../../services/toast.service';
import { PopupService } from '../../services/popup.service';
import {
  SubscriptionResponse,
  BillingCycle,
  PaymentHistoryResponse,
  PublicPricingResponse,
} from '../../models/plan.model';
import { UserInfo } from '../../models/user-info.model';

type PlanView = 'loading' | 'error' | 'trial' | 'premium';

@Component({
  selector: 'app-plan',
  standalone: true,
  imports: [PageShellComponent, ConfirmDialogComponent],
  templateUrl: './plan.html',
  styleUrl: './plan.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly subscriptionService = inject(SubscriptionService);
  private readonly checkoutService = inject(CheckoutService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly popupService = inject(PopupService);
  private readonly onDestroy = new Subject<void>();

  readonly view = signal<PlanView>('loading');
  readonly subscription = signal<SubscriptionResponse | null>(null);
  readonly paymentHistory = signal<PaymentHistoryResponse | null>(null);
  readonly loadingHistory = signal(false);
  readonly pricing = signal<PublicPricingResponse | null>(null);

  readonly selectedCycle = signal<BillingCycle>('MONTHLY');
  readonly selectedProvider = signal<string>('PAYPAL');
  readonly processing = signal(false);
  readonly pendingCheckoutId = signal<number | null>(null);

  readonly showCancelDialog = signal(false);

  readonly currentUser = computed<UserInfo | null>(() => this.auth.user());

  readonly isTrial = computed(() => this.currentUser()?.role === 'TRIAL');
  readonly isPremium = computed(() => this.currentUser()?.role === 'PREMIUM');

  readonly trialDaysLeft = computed(() => {
    const endDate = this.currentUser()?.trialEndDate;
    if (!endDate) return 0;
    const diff = new Date(endDate).getTime() - Date.now();
    return Math.max(0, Math.ceil(diff / (1000 * 60 * 60 * 24)));
  });

  readonly gracePeriodEnd = computed(() => {
    const sub = this.subscription();
    if (sub && !sub.autoRenew && sub.canceledAt) {
      const canceled = new Date(sub.canceledAt);
      canceled.setDate(canceled.getDate() + 30);
      return canceled.toISOString();
    }
    return null;
  });

  readonly enabledProviders = computed<string[]>(() => {
    const csv = this.subscription()?.enabledProviders ?? '';
    return csv ? csv.split(',') : [];
  });

  readonly providers: { id: string; name: string; icon: string }[] = [
    { id: 'PAYPAL', name: 'PayPal', icon: 'ti ti-brand-paypal' },
    { id: 'MERCADOPAGO', name: 'MercadoPago', icon: 'ti ti-currency-dollar' },
    { id: 'CARD', name: 'Tarjeta', icon: 'ti ti-credit-card' },
  ];

  ngOnInit(): void {
    this.loadSubscription();
    this.loadPricing();
  }

  ngOnDestroy(): void {
    this.popupService.closePopup();
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  loadSubscription(): void {
    this.view.set('loading');
    this.subscriptionService.getCurrent()
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (sub) => {
          this.subscription.set(sub);
          this.pendingCheckoutId.set(null);
          const role = this.currentUser()?.role;
          if (role === 'ADMIN' || sub.planTier === 'PREMIUM') {
            this.view.set('premium');
          } else {
            this.view.set('trial');
          }
        },
        error: () => {
          this.view.set('error');
          this.toast.error('No se pudo cargar la información de tu suscripción');
        },
      });
  }

  loadPricing(): void {
    this.checkoutService.getPricing()
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (info) => {
          this.pricing.set(info);
        },
        error: () => {},
      });
  }

  selectCycle(cycle: BillingCycle): void {
    this.selectedCycle.set(cycle);
  }

  selectProvider(provider: string): void {
    this.selectedProvider.set(provider);
  }

  isProviderEnabled(providerId: string): boolean {
    return this.enabledProviders().includes(providerId);
  }

  startCheckout(): void {
    this.processing.set(true);
    this.checkoutService.createCheckout({
      billingCycle: this.selectedCycle(),
      provider: this.selectedProvider(),
      planTier: 'PREMIUM',
    }).pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (response) => {
          this.processing.set(false);
          this.pendingCheckoutId.set(response.paymentId);
          if (response.redirectUrl && isPlatformBrowser(this.platformId)) {
            sessionStorage.setItem('mp_redirect_url_' + response.paymentId, response.redirectUrl);
          }
          if (this.selectedProvider() === 'MERCADOPAGO' && response.redirectUrl && isPlatformBrowser(this.platformId)) {
            const popup = this.checkoutService.openInPopup(response.redirectUrl);
            if (popup) {
              this.popupService.setPopup(popup);
            } else {
              this.toast.warning('Permití los popups para este sitio, o hacé clic en el botón de pago nuevamente.');
            }
          } else {
            this.router.navigate(['/checkout', response.paymentId]);
          }
        },
        error: (err) => {
          this.processing.set(false);
          if (err.status === 409) {
            const msg: string = err.error?.error ?? '';
            if (msg.includes('ya fue completado')) {
              this.toast.error('Este pago ya fue completado.');
              this.router.navigate(['/home']);
            } else if (msg.includes('ya no está pendiente')) {
              this.toast.error('Tu pago anterior expiró. Iniciá uno nuevo.');
              this.loadSubscription();
            } else {
              this.toast.error('Ya tienes una suscripción activa');
            }
          } else if (err.status === 502) {
            this.toast.error('No se pudo conectar con MercadoPago. Verificá la configuración o reintentá en unos minutos.');
          } else {
            this.toast.error('No se pudo iniciar el pago. Intenta de nuevo.');
          }
        },
      });
  }

  loadHistory(): void {
    this.loadingHistory.set(true);
    this.subscriptionService.getHistory(0, 10)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (history) => {
          this.paymentHistory.set(history);
          this.loadingHistory.set(false);
        },
        error: () => {
          this.loadingHistory.set(false);
          this.toast.error('No se pudo cargar el historial de pagos');
        },
      });
  }

  requestCancel(): void {
    this.showCancelDialog.set(true);
  }

  confirmCancel(): void {
    this.showCancelDialog.set(false);
    this.subscriptionService.cancel()
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: () => {
          this.toast.warning('Tu suscripción se cancelará al final del período de facturación');
          this.pendingCheckoutId.set(null);
          this.loadSubscription();
        },
        error: () => {
          this.toast.error('No se pudo cancelar la suscripción');
        },
      });
  }

  cancelDialogDismiss(): void {
    this.showCancelDialog.set(false);
  }

  formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) return '-';
    return new Date(dateStr).toLocaleDateString('es-AR', {
      day: '2-digit',
      month: 'long',
      year: 'numeric',
    });
  }

  formatAmount(amount: number, currency: string): string {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: currency,
    }).format(amount);
  }

  providerName(p: string): string {
    return this.providers.find((x) => x.id === p)?.name ?? p;
  }

  statusLabel(status: string): string {
    const labels: Record<string, string> = {
      COMPLETED: 'Completado',
      PENDING: 'Pendiente',
      FAILED: 'Fallido',
      EXPIRED: 'Expirado',
      REFUNDED: 'Reembolsado',
    };
    return labels[status] ?? status;
  }
}
