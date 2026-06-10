import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { AuthService } from '../../services/auth.service';

type Period = 'week' | 'month' | 'year';
type RiskLevel = 'GREEN' | 'YELLOW' | 'RED';
type UserRole = 'ADMIN' | 'PREMIUM' | 'TRIAL';

interface Kpi {
  label: string;
  value: string;
  trend: string;
  trendUp: boolean;
}

interface DailyThreat {
  day: string;
  threats: number;
}

interface RiskBucket {
  level: RiskLevel;
  count: number;
}

interface UserBucket {
  role: UserRole;
  count: number;
}

interface ThreatCategory {
  category: string;
  count: number;
}

interface ImpersonatedBrand {
  brand: string;
  count: number;
}

interface AnalysisSource {
  source: string;
  count: number;
}

interface EngagementMetric {
  label: string;
  value: string;
}

interface RecentAnalysis {
  date: string;
  sender: string;
  risk: number;
  level: RiskLevel;
}

interface SourceSignup {
  day: string;
  signups: number;
}

interface HourBucket {
  hour: number;
  count: number;
}

interface TrialUsage {
  used: number;
  total: number;
}

@Component({
  selector: 'app-stats',
  standalone: true,
  imports: [PageShellComponent, DecimalPipe],
  templateUrl: './stats.html',
  styleUrl: './stats.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatsComponent {
  private readonly auth = inject(AuthService);

  readonly isAdmin = computed(() => this.auth.user()?.role === 'ADMIN');
  readonly isTrial = computed(() => this.auth.user()?.role === 'TRIAL');

  readonly title = computed(() =>
    this.isAdmin() ? 'Estadísticas (panel global)' : 'Mis estadísticas',
  );
  readonly subtitle = computed(() =>
    this.isAdmin()
      ? 'Métricas de plataforma, suscripciones y amenazas agregadas.'
      : 'Resumen de tu actividad y amenazas detectadas.',
  );
  readonly icon = computed(() =>
    this.isAdmin() ? 'ti ti-shield-cog' : 'ti ti-chart-bar',
  );

  readonly period = signal<Period>('week');
  readonly periodLabel = computed(() => {
    switch (this.period()) {
      case 'week': return 'Última semana';
      case 'month': return 'Último mes';
      case 'year': return 'Último año';
    }
  });
  setPeriod(p: Period): void {
    this.period.set(p);
  }

  readonly lastThreatAt = signal<string>('2026-06-08T09:14:00');
  readonly timeSinceLastThreat = computed(() => {
    const then = new Date(this.lastThreatAt()).getTime();
    const diffMs = Date.now() - then;
    const minutes = Math.floor(diffMs / 60_000);
    if (minutes < 1) return 'hace menos de 1 minuto';
    if (minutes < 60) return `hace ${minutes} min`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `hace ${hours} h`;
    const days = Math.floor(hours / 24);
    return `hace ${days} día${days === 1 ? '' : 's'}`;
  });

  readonly userKpis = signal<Kpi[]>([
    { label: 'Correos analizados', value: '1.284', trend: '+12%', trendUp: true },
    { label: 'Amenazas bloqueadas', value: '37', trend: '+4', trendUp: true },
    { label: 'Tasa de phishing', value: '2,9%', trend: '-0,4%', trendUp: false },
    { label: 'Tiempo medio de análisis', value: '0,8s', trend: '-0,2s', trendUp: false },
  ]);

  readonly userWeeklyThreats = signal<DailyThreat[]>([
    { day: 'Lun', threats: 4 },
    { day: 'Mar', threats: 7 },
    { day: 'Mié', threats: 3 },
    { day: 'Jue', threats: 9 },
    { day: 'Vie', threats: 6 },
    { day: 'Sáb', threats: 2 },
    { day: 'Dom', threats: 1 },
  ]);

  readonly userRiskDistribution = signal<RiskBucket[]>([
    { level: 'GREEN', count: 1180 },
    { level: 'YELLOW', count: 71 },
    { level: 'RED', count: 33 },
  ]);

  readonly userTopThreats = signal<ThreatCategory[]>([
    { category: 'PHISHING', count: 18 },
    { category: 'SUPLANTACIÓN', count: 9 },
    { category: 'ENLACE PELIGROSO', count: 6 },
    { category: 'INGENIERÍA SOCIAL', count: 4 },
  ]);

  readonly userRecentActivity = signal<RecentAnalysis[]>([
    { date: '2026-06-08 09:14', sender: 'bancosantander@seguridad-cuenta.com', risk: 87, level: 'RED' },
    { date: '2026-06-07 18:42', sender: 'soporte@athenyx.app', risk: 12, level: 'GREEN' },
    { date: '2026-06-07 11:05', sender: 'Amazon <ofertas@amaz0n-promo.net>', risk: 64, level: 'YELLOW' },
    { date: '2026-06-06 22:31', sender: 'no-reply@accounts.google.com', risk: 18, level: 'GREEN' },
    { date: '2026-06-06 14:18', sender: 'rectoria@univers1dad-edu.co', risk: 71, level: 'RED' },
  ]);

  readonly userTrialUsage = computed<TrialUsage | null>(() => {
    if (this.auth.user()?.role !== 'TRIAL') return null;
    return { used: 8, total: 20 };
  });

  readonly adminKpis = signal<Kpi[]>([
    { label: 'Usuarios totales', value: '1.842', trend: '+47', trendUp: true },
    { label: 'Suscripciones activas', value: '1.103', trend: '+31', trendUp: true },
    { label: 'Suscripciones canceladas', value: '214', trend: '+8', trendUp: true },
    { label: 'Análisis totales', value: '287.451', trend: '+12,4%', trendUp: true },
    { label: 'Media análisis / usuario', value: '156', trend: '+3', trendUp: true },
    { label: 'Amenazas globales (mes)', value: '4.812', trend: '-5%', trendUp: false },
  ]);

  readonly adminGlobalDailyThreats = signal<DailyThreat[]>([
    { day: 'Lun', threats: 612 },
    { day: 'Mar', threats: 743 },
    { day: 'Mié', threats: 588 },
    { day: 'Jue', threats: 902 },
    { day: 'Vie', threats: 711 },
    { day: 'Sáb', threats: 304 },
    { day: 'Dom', threats: 198 },
  ]);

  readonly adminUserSplit = signal<UserBucket[]>([
    { role: 'PREMIUM', count: 1103 },
    { role: 'TRIAL', count: 738 },
    { role: 'ADMIN', count: 1 },
  ]);

  readonly adminGlobalRiskDistribution = signal<RiskBucket[]>([
    { level: 'GREEN', count: 268210 },
    { level: 'YELLOW', count: 14283 },
    { level: 'RED', count: 4958 },
  ]);

  readonly adminTopThreatCategories = signal<ThreatCategory[]>([
    { category: 'PHISHING', count: 1980 },
    { category: 'SUPLANTACIÓN DE MARCA', count: 1142 },
    { category: 'ENLACES PELIGROSOS', count: 802 },
    { category: 'INGENIERÍA SOCIAL', count: 511 },
    { category: 'FACTURAS FALSAS', count: 377 },
  ]);

  readonly adminImpersonatedBrands = signal<ImpersonatedBrand[]>([
    { brand: 'Amazon', count: 412 },
    { brand: 'Microsoft', count: 287 },
    { brand: 'Santander', count: 201 },
    { brand: 'Google', count: 188 },
    { brand: 'WhatsApp', count: 154 },
  ]);

  readonly adminAnalysisSourceSplit = signal<AnalysisSource[]>([
    { source: 'Heurística', count: 198342 },
    { source: 'IA (Llama 3)', count: 89109 },
  ]);

  readonly adminEngagement = signal<EngagementMetric[]>([
    { label: 'DAU (usuarios activos hoy)', value: '621' },
    { label: 'WAU (última semana)', value: '1.288' },
    { label: 'MAU (último mes)', value: '1.704' },
  ]);

  readonly adminAnalysisLatency = signal({ p50: '0,6s', p95: '2,1s', p99: '4,7s' });

  readonly adminNewSignups = signal<SourceSignup[]>([
    { day: 'Lun', signups: 12 },
    { day: 'Mar', signups: 18 },
    { day: 'Mié', signups: 9 },
    { day: 'Jue', signups: 22 },
    { day: 'Vie', signups: 15 },
    { day: 'Sáb', signups: 4 },
    { day: 'Dom', signups: 3 },
  ]);

  readonly adminConversionRate = signal({ value: '14,2%', trend: '+1,1 pp', trendUp: true });

  readonly adminThreatsByHour = signal<HourBucket[]>(
    Array.from({ length: 24 }, (_, h) => ({
      hour: h,
      count: Math.round(40 + 60 * Math.sin(((h - 8) / 24) * Math.PI * 2) + (h % 3) * 12),
    })),
  );

  readonly maxUserThreat = computed(() =>
    Math.max(1, ...this.userWeeklyThreats().map((d) => d.threats)),
  );
  readonly maxAdminThreat = computed(() =>
    Math.max(1, ...this.adminGlobalDailyThreats().map((d) => d.threats)),
  );
  readonly maxSignup = computed(() =>
    Math.max(1, ...this.adminNewSignups().map((d) => d.signups)),
  );
  readonly maxHourCount = computed(() =>
    Math.max(1, ...this.adminThreatsByHour().map((d) => d.count)),
  );

  readonly userTotalRisk = computed(() =>
    this.userRiskDistribution().reduce((sum, b) => sum + b.count, 0),
  );
  readonly adminTotalRisk = computed(() =>
    this.adminGlobalRiskDistribution().reduce((sum, b) => sum + b.count, 0),
  );

  readonly adminTotalUsers = computed(() =>
    this.adminUserSplit().reduce((sum, b) => sum + b.count, 0),
  );

  readonly userTopMax = computed(() =>
    Math.max(1, ...this.userTopThreats().map((t) => t.count)),
  );
  readonly adminTopMax = computed(() =>
    Math.max(1, ...this.adminTopThreatCategories().map((t) => t.count)),
  );
  readonly adminBrandsMax = computed(() =>
    Math.max(1, ...this.adminImpersonatedBrands().map((b) => b.count)),
  );
  readonly adminSourceMax = computed(() =>
    Math.max(1, ...this.adminAnalysisSourceSplit().map((s) => s.count)),
  );

  readonly userTrialPercent = computed(() => {
    const t = this.userTrialUsage();
    return t ? Math.round((t.used / t.total) * 100) : 0;
  });

  barHeight(value: number, max: number): number {
    return Math.max(2, Math.round((value / max) * 100));
  }

  widthPercent(value: number, total: number): number {
    if (total === 0) return 0;
    return Math.max(2, Math.round((value / total) * 100));
  }

  riskPercent(count: number, total: number): string {
    if (total === 0) return '0%';
    return ((count / total) * 100).toFixed(1).replace('.', ',') + '%';
  }

  heatmapIntensity(count: number, max: number): number {
    if (max === 0) return 0;
    return Math.max(0.08, count / max);
  }

  formatHour(h: number): string {
    return `${h.toString().padStart(2, '0')}:00`;
  }

  levelLabel(level: RiskLevel): string {
    switch (level) {
      case 'GREEN': return 'Seguro';
      case 'YELLOW': return 'Sospechoso';
      case 'RED': return 'Peligroso';
    }
  }

  roleLabel(role: UserRole): string {
    switch (role) {
      case 'ADMIN': return 'Admin';
      case 'PREMIUM': return 'Premium';
      case 'TRIAL': return 'Prueba';
    }
  }
}
