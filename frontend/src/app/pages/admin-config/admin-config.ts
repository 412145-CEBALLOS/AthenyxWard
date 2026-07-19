import { Component, ChangeDetectionStrategy, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { ToastService } from '../../services/toast.service';
import { ConfigService } from '../../services/config.service';
import { AppConfigInitializerService } from '../../services/app-config-initializer.service';
import { ConfigCategory, ConfigEntry } from '../../models/config.model';

@Component({
  selector: 'app-admin-config',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './admin-config.html',
  styleUrl: './admin-config.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminConfigComponent implements OnInit, OnDestroy {
  private readonly configService = inject(ConfigService);
  private readonly appConfigInitializer = inject(AppConfigInitializerService);
  private readonly toast = inject(ToastService);
  private readonly onDestroy = new Subject<void>();

  private readonly VALID_KEYS = new Set<string>([
    'AUDIT_RETENTION_DAYS', 'EMAIL_RETENTION_DAYS',
    'AI_ENABLED', 'AI_MODEL',
    'TRIAL_ANALYSIS_LIMIT', 'REMINDER_MAX_PER_USER',
    'HEURISTIC_RISK_THRESHOLD_LOW', 'HEURISTIC_RISK_THRESHOLD_MEDIUM',
    'HEURISTIC_CACHE_HOURS',
    'NOTIFICATIONS_UPCOMING_WINDOW_HOURS', 'NOTIFICATIONS_POLL_INTERVAL_SECONDS',
    'RATELIMIT_EXPLAIN_PER_HOUR',
    'COPY_SUPPORT_EMAIL', 'OAUTH_ALLOWED_DOMAINS',
    'SECURITY_MAX_FAILED_LOGINS', 'SECURITY_IP_BLOCKLIST',
  ]);

  private readonly LABELS: Record<string, string> = {
    'AUDIT_RETENTION_DAYS':               'Retención de auditoría (días)',
    'EMAIL_RETENTION_DAYS':               'Retención de correos (días)',
    'AI_ENABLED':                         'IA activada',
    'AI_MODEL':                           'Modelo de IA',
    'TRIAL_ANALYSIS_LIMIT':              'Límite de análisis (TRIAL)',
    'REMINDER_MAX_PER_USER':              'Máximo de recordatorios por usuario',
    'HEURISTIC_RISK_THRESHOLD_LOW':       'Umbral de riesgo bajo',
    'HEURISTIC_RISK_THRESHOLD_MEDIUM':    'Umbral de riesgo medio',
    'HEURISTIC_CACHE_HOURS':              'Caché de análisis heurístico (horas)',
    'NOTIFICATIONS_UPCOMING_WINDOW_HOURS': 'Ventana de recordatorios próximos (h)',
    'NOTIFICATIONS_POLL_INTERVAL_SECONDS': 'Intervalo de polling (segundos)',
    'RATELIMIT_EXPLAIN_PER_HOUR':        'Límite de explicaciones IA por hora',
    'COPY_SUPPORT_EMAIL':                 'Email de soporte',
    'OAUTH_ALLOWED_DOMAINS':             'Dominios OAuth2 permitidos',
    'SECURITY_MAX_FAILED_LOGINS':        'Máximo de intentos de login fallidos',
    'SECURITY_IP_BLOCKLIST':             'Lista de IPs bloqueadas',
  };

  readonly categories = signal<ConfigCategory[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly editingKey = signal<string | null>(null);
  readonly editingValue = signal<string>('');
  readonly saving = signal<string | null>(null);
  readonly purging = signal<string | null>(null);
  readonly purgeConfirmKey = signal<string | null>(null);

  ngOnInit(): void {
    this.loadConfig();
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  loadConfig(): void {
    this.loading.set(true);
    this.error.set(false);
    this.configService.getAllAdmin()
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (cats) => {
          const filtered = this.filterValidKeys(cats);
          this.categories.set(filtered);
          this.loading.set(false);
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
        },
      });
  }

  private filterValidKeys(cats: ConfigCategory[]): ConfigCategory[] {
    const dropped: string[] = [];
    const filtered = cats.map(cat => ({
      ...cat,
      entries: cat.entries.filter(entry => {
        if (!this.VALID_KEYS.has(entry.key)) {
          dropped.push(entry.key);
          return false;
        }
        return true;
      }),
    })).filter(cat => cat.entries.length > 0);
    if (dropped.length > 0) {
      console.warn('[AdminConfig] Stale config rows filtered out:', dropped);
    }
    return filtered;
  }

  labelFor(key: string): string {
    return this.LABELS[key] ?? key;
  }

  formatValue(entry: ConfigEntry): string {
    if (entry.type === 'BOOLEAN') {
      return entry.value === 'true' ? 'Activado' : 'Desactivado';
    }
    const v = (entry.value ?? '').trim();
    if (!v) return '(vacío)';
    if (v.length > 60) return v.slice(0, 60) + '…';
    return v;
  }

  isValueEmpty(entry: ConfigEntry): boolean {
    if (entry.type === 'BOOLEAN') return false;
    return !(entry.value ?? '').trim();
  }

  isPurgable(key: string): boolean {
    return key === 'AUDIT_RETENTION_DAYS' || key === 'EMAIL_RETENTION_DAYS';
  }

  onEdit(entry: ConfigEntry): void {
    this.editingKey.set(entry.key);
    this.editingValue.set(entry.value);
  }

  onCancel(): void {
    this.editingKey.set(null);
    this.editingValue.set('');
  }

  onSave(key: string): void {
    const entry = this.findEntry(key);
    if (!entry) return;
    const newVal = this.editingValue();
    if (newVal === entry.value) {
      this.onCancel();
      return;
    }
    if (entry.type === 'INT') {
      if (isNaN(Number(newVal))) {
        this.toast.error('El valor debe ser un número entero.');
        return;
      }
    }
    this.saving.set(key);
    this.configService.updateEntry(key, newVal)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (updated) => {
          this.categories.update(cats =>
            cats.map(cat => ({
              ...cat,
              entries: cat.entries.map(e =>
                e.key === key ? { ...e, value: updated.value, updatedAt: updated.updatedAt, updatedBy: updated.updatedBy } : e
              )
            }))
          );
          this.saving.set(null);
          this.onCancel();
          this.toast.success('Configuración guardada correctamente.');
          this.appConfigInitializer.load();
        },
        error: (err) => {
          this.saving.set(null);
          const msg = err?.error?.error || 'No se pudo guardar la configuración.';
          this.toast.error(msg);
        },
      });
  }

  onPurgeRequest(key: string): void {
    this.purgeConfirmKey.set(key);
  }

  onPurgeCancel(): void {
    this.purgeConfirmKey.set(null);
  }

  onPurgeConfirm(key: string): void {
    this.purgeConfirmKey.set(null);
    this.purging.set(key);
    this.configService.purgeNow(key as 'AUDIT_RETENTION_DAYS' | 'EMAIL_RETENTION_DAYS')
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (result) => {
          this.purging.set(null);
          const msg = result.skippedDueToReminders !== undefined
            ? `Se eliminaron ${result.purgedCount} registros${result.skippedDueToReminders > 0 ? ` (${result.skippedDueToReminders} omitidos por recordatorios activos)` : ''}.`
            : `Se eliminaron ${result.purgedCount} registros.`;
          this.toast.success(msg);
        },
        error: (err) => {
          this.purging.set(null);
          const msg = err?.error?.error || 'No se pudo ejecutar el purgado.';
          this.toast.error(msg);
        },
      });
  }

  relativeTime(dateStr: string | null): string {
    if (!dateStr) return 'nunca';
    const date = new Date(dateStr);
    const diffMs = Date.now() - date.getTime();
    const diffSec = Math.floor(diffMs / 1000);
    const rtf = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });
    if (diffSec < 60) return rtf.format(-Math.floor(diffSec), 'second');
    if (diffSec < 3600) return rtf.format(-Math.floor(diffSec / 60), 'minute');
    if (diffSec < 86400) return rtf.format(-Math.floor(diffSec / 3600), 'hour');
    return rtf.format(-Math.floor(diffSec / 86400), 'day');
  }

  categoryIcon(cat: ConfigCategory): string {
    const icons: Record<string, string> = {
      'RETENTION': 'ti ti-eraser',
      'AI': 'ti ti-brain',
      'QUOTAS': 'ti ti-stack',
      'HEURISTIC': 'ti ti-shield-check',
      'NOTIFICATIONS': 'ti ti-bell',
      'RATE_LIMIT': 'ti ti-gauge',
      'COPY': 'ti ti-text-size',
      'SECURITY': 'ti ti-lock',
    };
    return icons[cat.category] || 'ti ti-settings';
  }

  canSave(key: string): boolean {
    if (this.saving() !== null) return false;
    const entry = this.findEntry(key);
    if (!entry) return false;
    const newVal = this.editingValue();
    if (newVal === entry.value) return false;
    if (entry.type === 'INT' && isNaN(Number(newVal))) return false;
    return true;
  }

  private findEntry(key: string): ConfigEntry | undefined {
    for (const cat of this.categories()) {
      const entry = cat.entries.find(e => e.key === key);
      if (entry) return entry;
    }
    return undefined;
  }
}
