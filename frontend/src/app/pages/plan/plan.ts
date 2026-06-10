import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
  computed,
} from '@angular/core';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { AuthService } from '../../services/auth.service';

type PlanName = 'TRIAL' | 'PREMIUM' | 'FAMILY';

interface PlanCard {
  name: PlanName;
  displayName: string;
  price: string;
  period: string;
  features: string[];
  highlight: boolean;
}

@Component({
  selector: 'app-plan',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './plan.html',
  styleUrl: './plan.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanComponent {
  private readonly auth = inject(AuthService);

  readonly plans = signal<PlanCard[]>([
    {
      name: 'TRIAL',
      displayName: 'Prueba',
      price: '0 €',
      period: '14 días',
      features: [
        '50 análisis de correos',
        'Detección heurística',
        'Historial limitado (7 días)',
      ],
      highlight: false,
    },
    {
      name: 'PREMIUM',
      displayName: 'Premium',
      price: '4,99 €',
      period: 'mes',
      features: [
        'Análisis ilimitados',
        'Modelo IA (Llama 3) por correo',
        'Recordatorios y marcas importantes',
        'Historial completo',
        'Soporte prioritario',
      ],
      highlight: true,
    },
    {
      name: 'FAMILY',
      displayName: 'Familia',
      price: '9,99 €',
      period: 'mes',
      features: [
        'Hasta 5 cuentas',
        'Todas las ventajas Premium',
        'Panel de control familiar',
        'Detector de suplantación avanzado',
      ],
      highlight: false,
    },
  ]);

  readonly currentRole = computed(() => this.auth.user()?.role ?? 'TRIAL');

  isCurrent(plan: PlanCard): boolean {
    return this.currentRole() === plan.name;
  }

  onSelect(plan: PlanName): void {
    console.warn('TODO Sprint 3: change plan to', plan);
  }
}
