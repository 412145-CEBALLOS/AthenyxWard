import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { AuthService } from '../../services/auth.service';
import { StatsService } from '../../services/stats.service';
import {
  AdminStatsResponse,
  KpiMetric,
  StatsPeriod,
  UserStatsResponse,
} from '../../models/stats.model';
import { Subject, takeUntil } from 'rxjs';

const RATE_LABELS = new Set([
  'Tasa de phishing',
  'Riesgo medio',
  'Media análisis / usuario',
]);

@Component({
  selector: 'app-stats',
  standalone: true,
  imports: [PageShellComponent, DecimalPipe],
  templateUrl: './stats.html',
  styleUrl: './stats.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatsComponent implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly statsService = inject(StatsService);
  private readonly onDestroy = new Subject<void>();

  readonly period = signal<StatsPeriod>('week');
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly userStats = signal<UserStatsResponse | null>(null);
  readonly adminStats = signal<AdminStatsResponse | null>(null);

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

  readonly periodLabel = computed(() => {
    switch (this.period()) {
      case 'week':
        return 'Última semana';
      case 'month':
        return 'Último mes';
      case 'year':
        return 'Último año';
    }
  });

  readonly userKpis = computed(() => this.userStats()?.kpis ?? []);
  readonly userDailyThreats = computed(() => this.userStats()?.dailyThreats ?? []);
  readonly userRiskDistribution = computed(() => this.userStats()?.riskDistribution ?? []);
  readonly userTopCategories = computed(() => this.userStats()?.topCategories ?? []);
  readonly userRecentActivity = computed(() => this.userStats()?.recentActivity ?? []);
  readonly userTrialUsage = computed(() => this.userStats()?.trialUsage ?? null);
  readonly lastThreatAt = computed(() => this.userStats()?.lastThreatAt ?? null);

  readonly timeSinceLastThreat = computed(() => {
    const last = this.lastThreatAt();
    if (!last) return 'Sin amenazas registradas';
    const then = new Date(last).getTime();
    const diffMs = Date.now() - then;
    if (diffMs < 0) return 'hace menos de 1 minuto';
    const minutes = Math.floor(diffMs / 60_000);
    if (minutes < 1) return 'hace menos de 1 minuto';
    if (minutes < 60) return `hace ${minutes} min`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `hace ${hours} h`;
    const days = Math.floor(hours / 24);
    return `hace ${days} día${days === 1 ? '' : 's'}`;
  });

  readonly adminKpis = computed(() => this.adminStats()?.kpis ?? []);
  readonly adminGlobalDailyThreats = computed(() => this.adminStats()?.dailyThreats ?? []);
  readonly adminGlobalRiskDistribution = computed(() => this.adminStats()?.riskDistribution ?? []);
  readonly adminUserSplit = computed(() => this.adminStats()?.userSplit ?? []);
  readonly adminTopCategories = computed(() => this.adminStats()?.topCategories ?? []);
  readonly adminAnalysisSourceSplit = computed(() => this.adminStats()?.analysisSourceSplit ?? []);
  readonly adminEngagement = computed(() => this.adminStats()?.engagement ?? null);
  readonly adminConversionRate = computed(() => this.adminStats()?.conversionRate ?? null);
  readonly adminNewSignups = computed(() => this.adminStats()?.signups ?? []);
  readonly adminThreatsByHour = computed(() => this.adminStats()?.threatsByHour ?? []);

  readonly userTotalRisk = computed(() =>
    this.userRiskDistribution().reduce((sum, b) => sum + b.count, 0),
  );
  readonly adminTotalRisk = computed(() =>
    this.adminGlobalRiskDistribution().reduce((sum, b) => sum + b.count, 0),
  );
  readonly adminTotalUsers = computed(() =>
    this.adminUserSplit().reduce((sum, b) => sum + b.count, 0),
  );

  readonly maxUserThreat = computed(() =>
    Math.max(1, ...this.userDailyThreats().map((d) => d.count)),
  );
  readonly maxAdminThreat = computed(() =>
    Math.max(1, ...this.adminGlobalDailyThreats().map((d) => d.count)),
  );
  readonly maxSignup = computed(() =>
    Math.max(1, ...this.adminNewSignups().map((d) => d.count)),
  );
  readonly maxHourCount = computed(() =>
    Math.max(1, ...this.adminThreatsByHour().map((d) => d.count)),
  );

  readonly userTopMax = computed(() =>
    Math.max(1, ...this.userTopCategories().map((t) => t.count)),
  );
  readonly adminTopMax = computed(() =>
    Math.max(1, ...this.adminTopCategories().map((t) => t.count)),
  );
  readonly adminSourceMax = computed(() =>
    Math.max(1, ...this.adminAnalysisSourceSplit().map((s) => s.count)),
  );

  readonly userTrialPercent = computed(() => {
    const t = this.userTrialUsage();
    return t ? Math.round((t.used / t.total) * 100) : 0;
  });

  readonly hasUserData = computed(() =>
    (this.userStats()?.kpis?.length ?? 0) > 0,
  );
  readonly hasAdminData = computed(() =>
    (this.adminStats()?.kpis?.length ?? 0) > 0,
  );
  readonly isEmpty = computed(() => {
    if (this.loading()) return false;
    if (this.isAdmin()) return !this.hasAdminData();
    return !this.hasUserData();
  });

  constructor() {
    effect(() => {
      const p = this.period();
      const user = this.auth.user();
      if (user) {
        this.loadStats();
      }
    });
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  setPeriod(p: StatsPeriod): void {
    if (this.period() === p) return;
    this.period.set(p);
    this.loadStats();
  }

  retry(): void {
    this.error.set(null);
    this.loadStats();
  }

  private loadStats(): void {
    this.loading.set(true);
    this.error.set(null);

    if (this.isAdmin()) {
      this.statsService
        .getAdminStats(this.period())
        .pipe(takeUntil(this.onDestroy))
        .subscribe({
          next: (stats) => {
            this.adminStats.set(stats);
            this.loading.set(false);
          },
          error: () => {
            this.error.set('No se pudieron cargar las estadísticas.');
            this.loading.set(false);
          },
        });
    } else {
      this.statsService
        .getUserStats(this.period())
        .pipe(takeUntil(this.onDestroy))
        .subscribe({
          next: (stats) => {
            this.userStats.set(stats);
            this.loading.set(false);
          },
          error: () => {
            this.error.set('No se pudieron cargar las estadísticas.');
            this.loading.set(false);
          },
        });
    }
  }

  formatKpiValue(kpi: KpiMetric): string {
    if (RATE_LABELS.has(kpi.label)) {
      return `${kpi.value.toFixed(1).replace('.', ',')}%`;
    }
    if (kpi.value >= 1000) {
      return kpi.value.toLocaleString('es-ES', { maximumFractionDigits: 0 });
    }
    return kpi.value.toLocaleString('es-ES', { maximumFractionDigits: 1 });
  }

  formatTrend(kpi: KpiMetric): string {
    const delta = kpi.value - kpi.previousValue;
    const sign = delta >= 0 ? '+' : '';
    if (RATE_LABELS.has(kpi.label)) {
      return `${sign}${delta.toFixed(1).replace('.', ',')} pp`;
    }
    return `${sign}${Math.round(delta)}`;
  }

  formatConversionValue(value: number): string {
    return `${value.toFixed(1).replace('.', ',')}%`;
  }

  formatConversionTrend(value: number, previous: number): string {
    const delta = value - previous;
    const sign = delta >= 0 ? '+' : '';
    return `${sign}${delta.toFixed(1).replace('.', ',')} pp`;
  }

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

  levelLabel(level: 'GREEN' | 'YELLOW' | 'RED'): string {
    switch (level) {
      case 'GREEN':
        return 'Seguro';
      case 'YELLOW':
        return 'Sospechoso';
      case 'RED':
        return 'Peligroso';
    }
  }

  roleLabel(role: 'ADMIN' | 'PREMIUM' | 'TRIAL'): string {
    switch (role) {
      case 'ADMIN':
        return 'Admin';
      case 'PREMIUM':
        return 'Premium';
      case 'TRIAL':
        return 'Prueba';
    }
  }

}
