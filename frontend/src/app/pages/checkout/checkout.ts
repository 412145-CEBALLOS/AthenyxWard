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
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { CheckoutService } from '../../services/checkout.service';
import { ToastService } from '../../services/toast.service';
import { AuthService } from '../../services/auth.service';
import { CheckoutStatusResponse } from '../../models/plan.model';

type CheckoutStep = 'loading' | 'redirecting' | 'returning' | 'form' | 'processing' | 'success' | 'failed' | 'expired' | 'error';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [],
  templateUrl: './checkout.html',
  styleUrl: './checkout.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckoutComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly checkoutService = inject(CheckoutService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly onDestroy = new Subject<void>();

  readonly step = signal<CheckoutStep>('loading');
  readonly paymentId = signal<number>(0);
  readonly checkout = signal<CheckoutStatusResponse | null>(null);
  readonly provider = signal<string>('');
  readonly simulateFail = signal(false);

  readonly cardNumber = signal('');
  readonly cardHolder = signal('');
  readonly cardExpiry = signal('');
  readonly cardCvv = signal('');
  readonly paypalEmail = signal('');

  private redirectTimer: ReturnType<typeof setTimeout> | null = null;
  private returnTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isDev = signal(false);

  readonly luhnValid = computed(() => {
    const num = this.cardNumber().replace(/\s/g, '');
    if (!num || num.length < 13 || num.length > 19) return false;
    let sum = 0;
    let alternate = false;
    for (let i = num.length - 1; i >= 0; i--) {
      const c = num.charAt(i);
      if (!/\d/.test(c)) return false;
      let n = parseInt(c, 10);
      if (alternate) {
        n *= 2;
        if (n > 9) n -= 9;
      }
      sum += n;
      alternate = !alternate;
    }
    return sum % 10 === 0;
  });

  readonly formValid = computed(() => {
    if (this.provider() === 'CARD') {
      const expiry = this.cardExpiry();
      const [mm, yy] = expiry.split('/');
      const month = parseInt(mm, 10);
      const year = parseInt('20' + yy, 10);
      const now = new Date();
      const expValid = month >= 1 && month <= 12 &&
        (year > now.getFullYear() || (year === now.getFullYear() && month >= now.getMonth() + 1));
      return this.luhnValid() &&
        this.cardHolder().trim().length >= 3 &&
        expiry.length === 5 &&
        expiry.includes('/') &&
        expValid &&
        this.cardCvv().length >= 3;
    }
    if (this.provider() === 'PAYPAL') {
      const email = this.paypalEmail().trim();
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      return emailRegex.test(email);
    }
    return true;
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('paymentId'));
    if (!id) {
      this.router.navigate(['/plan']);
      return;
    }
    this.paymentId.set(id);
    this.loadAndBranch();
  }

  ngOnDestroy(): void {
    this.clearTimers();
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  private loadAndBranch(): void {
    this.step.set('loading');
    const id = this.paymentId();
    const storedRedirect = (id && isPlatformBrowser(this.platformId))
      ? sessionStorage.getItem('mp_redirect_url_' + id)
      : null;

    this.checkoutService.getStatus(id)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (status) => {
          this.checkout.set(status);
          this.provider.set(status.provider);
          const p = status.provider;

          if (p === 'MERCADOPAGO' && storedRedirect && storedRedirect.startsWith('http')) {
            this.clearSessionRedirect();
            this.doExternalRedirect(storedRedirect);
            return;
          }

          if (p === 'PAYPAL' || p === 'MERCADOPAGO') {
            this.startRedirectAnimation();
          } else {
            this.step.set('form');
          }
        },
        error: (err) => {
          if (err.status === 404) {
            this.step.set('error');
          } else {
            this.step.set('error');
          }
        },
      });
  }

  private doExternalRedirect(url: string): void {
    window.location.href = url;
  }

  private clearSessionRedirect(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const id = this.paymentId();
    if (id) {
      sessionStorage.removeItem('mp_redirect_url_' + id);
    }
  }

  private startRedirectAnimation(): void {
    this.step.set('redirecting');
    this.redirectTimer = setTimeout(() => {
      this.step.set('returning');
      this.returnTimer = setTimeout(() => {
        this.step.set('form');
      }, 1500);
    }, 1500);
  }

  private clearTimers(): void {
    if (this.redirectTimer) clearTimeout(this.redirectTimer);
    if (this.returnTimer) clearTimeout(this.returnTimer);
  }

  toggleSimulateFail(): void {
    this.simulateFail.update((v) => !v);
  }

  submitPayment(): void {
    this.step.set('processing');
    const token = this.simulateFail()
      ? 'FAIL'
      : (this.provider() === 'CARD' ? this.cardNumber().replace(/\s/g, '') : this.paypalEmail().trim());

    this.checkoutService.confirmPayment(this.paymentId(), token)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: () => {
          this.step.set('success');
          this.auth.checkAuth().pipe(takeUntil(this.onDestroy)).subscribe();
          this.toast.success('¡Pago completado! Ya sos usuario Premium.');
        },
        error: (err) => {
          if (err.status === 409) {
            const msg: string = err.error?.error ?? '';
            if (msg.includes('ya fue completado')) {
              this.step.set('success');
              this.toast.success('¡Pago completado! Ya sos usuario Premium.');
              this.auth.checkAuth().pipe(takeUntil(this.onDestroy)).subscribe();
            } else {
              this.step.set('failed');
              this.toast.error('Tu pago anterior no se completó. Iniciá uno nuevo.');
              setTimeout(() => this.router.navigate(['/plan']), 1500);
            }
          } else if (err.status === 410) {
            this.step.set('expired');
          } else if (err.status === 402) {
            this.step.set('failed');
          } else if (err.status === 502) {
            this.step.set('error');
            this.toast.error('No se pudo conectar con MercadoPago. Verificá la configuración.');
          } else {
            this.step.set('error');
          }
        },
      });
  }

  goToPlan(): void {
    this.router.navigate(['/plan']);
  }

  retry(): void {
    this.router.navigate(['/plan']);
  }

  formatAmount(amount: number, currency: string): string {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: currency,
    }).format(amount);
  }

  providerLabel(): string {
    const labels: Record<string, string> = {
      PAYPAL: 'PayPal',
      MERCADOPAGO: 'MercadoPago',
      CARD: 'Tarjeta',
    };
    return labels[this.provider()] ?? this.provider();
  }

  providerIcon(): string {
    const icons: Record<string, string> = {
      PAYPAL: 'ti ti-brand-paypal',
      MERCADOPAGO: 'ti ti-currency-dollar',
      CARD: 'ti ti-credit-card',
    };
    return icons[this.provider()] ?? 'ti ti-credit-card';
  }

  providerColor(): string {
    const colors: Record<string, string> = {
      PAYPAL: 'paypal',
      MERCADOPAGO: 'mercadopago',
      CARD: 'card',
    };
    return colors[this.provider()] ?? 'card';
  }

  formatCardNumber(value: string): string {
    const digits = value.replace(/\D/g, '').substring(0, 16);
    return digits.replace(/(\d{4})(?=\d)/g, '$1 ');
  }

  onCardNumberInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    const formatted = this.formatCardNumber(input.value);
    this.cardNumber.set(formatted);
    input.value = formatted;
  }

  onCardExpiryInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    let val = input.value.replace(/\D/g, '').substring(0, 4);
    if (val.length >= 2) val = val.substring(0, 2) + '/' + val.substring(2);
    this.cardExpiry.set(val);
    input.value = val;
  }

  onCardCvvInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.cardCvv.set(input.value.replace(/\D/g, '').substring(0, 4));
  }
}
