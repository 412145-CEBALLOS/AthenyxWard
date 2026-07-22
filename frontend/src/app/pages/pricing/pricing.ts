import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
  computed,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { CheckoutService } from '../../services/checkout.service';
import { AuthService } from '../../services/auth.service';
import { PublicPricingResponse } from '../../models/plan.model';

@Component({
  selector: 'app-pricing',
  standalone: true,
  imports: [],
  templateUrl: './pricing.html',
  styleUrl: './pricing.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PricingComponent implements OnInit, OnDestroy {
  private readonly checkoutService = inject(CheckoutService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly onDestroy = new Subject<void>();

  readonly pricing = signal<PublicPricingResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);

  readonly isLoggedIn = computed(() => this.auth.user() !== null);

  ngOnInit(): void {
    this.checkoutService.getPricing()
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (info) => {
          this.pricing.set(info);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(true);
        },
      });
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  goToCheckout(): void {
    if (this.isLoggedIn()) {
      this.router.navigate(['/plan']);
    } else {
      this.router.navigate(['/login']);
    }
  }

  formatPrice(amount: number): string {
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: this.pricing()?.currency ?? 'ARS',
    }).format(amount);
  }

  monthlyEquivalent(annual: number): string {
    return this.formatPrice(Math.round(annual / 12));
  }
}
