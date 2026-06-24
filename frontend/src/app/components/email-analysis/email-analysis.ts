import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  model,
  output,
} from '@angular/core';
import {
  AnalysisState,
  EmailAnalysisResult,
  RiskLevel,
  THREAT_CATEGORY_LABELS,
  ThreatCategory,
} from '../../models/email-analysis.model';
import { riskLevelFromPercentage } from '../../utils/risk.util';

const STATUS_LABELS: Record<RiskLevel, string> = {
  GREEN: 'Seguro',
  YELLOW: 'Sospechoso',
  RED: 'Peligroso',
};

const STATE_LABELS: Record<AnalysisState, string> = {
  idle: 'Aún no se ha analizado este correo.',
  loading: 'Analizando correo con heurísticas e IA local…',
  ready: '',
  error: 'No fue posible analizar el correo. Intenta nuevamente.',
  'unavailable-trial':
    'Has alcanzado el límite de análisis del período de prueba.',
};

type UserRole = 'TRIAL' | 'PREMIUM' | 'ADMIN' | null;

@Component({
  selector: 'app-email-analysis',
  standalone: true,
  imports: [],
  templateUrl: './email-analysis.html',
  styleUrl: './email-analysis.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailAnalysisComponent {
  readonly analysis = input<EmailAnalysisResult | null>(null);
  readonly state = input<AnalysisState>('idle');
  readonly accessibilityMode = input<boolean>(true);
  readonly canMarkImportant = input<boolean>(false);
  readonly isImportant = input<boolean>(false);
  readonly trialRemaining = input<number | null>(null);
  /**
   * True when the email currently being viewed has a reminder
   * (any state). Used to switch the action-button label between
   * "Crear recordatorio", "Reactivar recordatorio" and
   * "Ver recordatorio" — and to disable the button entirely when
   * a pending reminder already exists.
   */
  readonly hasPendingReminder = input<boolean>(false);
  readonly hasReminder = input<boolean>(false);
  /**
   * Drives the trial-user flow: when the user is on a TRIAL plan the
   * panel never auto-runs the analysis — they must press the explicit
   * "Analizar este correo" button. Non-trial users (PREMIUM, ADMIN)
   * fire the {@link analyzeRequest} output on the first toggle click.
   */
  readonly userRole = input<UserRole>(null);

  readonly hide = output<void>();
  readonly delete = output<void>();
  readonly markPhishing = output<void>();
  readonly markImportant = output<void>();
  readonly createReminder = output<void>();
  readonly retry = output<void>();
  /**
   * Emitted when the user asks for a fresh analysis (PREMIUM/ADMIN
   * toggle on first click, or TRIAL via the in-body button). The
   * parent (email-viewer → home) is responsible for invoking
   * {@code AnalysisService.analyze()} and flipping {@link open} to
   * {@code true} when the result is ready.
   */
  readonly analyzeRequest = output<void>();

  /**
   * Two-way bound open-state. The parent (home.ts) writes
   * {@code true} when an analysis just finished so the panel
   * auto-reveals; the component writes back whenever the user
   * manually toggles the header.
   */
  readonly open = model<boolean>(false);

  readonly circumference = 2 * Math.PI * 46;

  readonly dashOffset = computed<number>(() => {
    const pct = this.analysis()?.riskPercentage ?? 0;
    return this.circumference * (1 - pct / 100);
  });

  readonly riskLevel = computed<RiskLevel>(() => {
    const a = this.analysis();
    return riskLevelFromPercentage(a?.riskPercentage);
  });

  readonly riskClass = computed<'risk-safe' | 'risk-suspicious' | 'risk-dangerous'>(
    () => {
      switch (this.riskLevel()) {
        case 'GREEN':
          return 'risk-safe';
        case 'YELLOW':
          return 'risk-suspicious';
        case 'RED':
          return 'risk-dangerous';
      }
    },
  );

  readonly statusLabel = computed<string>(() => STATUS_LABELS[this.riskLevel()]);

  readonly stateMessage = computed<string>(() => STATE_LABELS[this.state()]);

  readonly isTrial = computed<boolean>(() => this.userRole() === 'TRIAL');

  readonly threatLabel = (category: ThreatCategory): string =>
    THREAT_CATEGORY_LABELS[category];

  readonly showHeuristics = computed<boolean>(
    () => !this.accessibilityMode() && (this.analysis()?.heuristicFindings.length ?? 0) > 0,
  );

  readonly analyzedLabel = computed<string>(() => {
    const a = this.analysis();
    if (!a) return '';
    const date = new Date(a.analyzedAt);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleString('es-ES', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  });

  readonly visibleUrls = computed(() => {
    const urls = this.analysis()?.suspiciousUrls ?? [];
    return this.open() ? urls : urls.slice(0, 3);
  });

  readonly hasOverflowUrls = computed<boolean>(
    () => (this.analysis()?.suspiciousUrls.length ?? 0) > 3,
  );

  /**
   * Public hook invoked by the parent (home.ts) when the analysis has
   * just finished and the panel should auto-reveal. Kept for
   * backwards compatibility with the imperative call site; the
   * idiomatic path is to write {@link open} directly via the
   * two-way binding.
   */
  showAfterAnalysis(): void {
    this.open.set(true);
  }

  toggle(): void {
    const currentlyOpen = this.open();
    if (!currentlyOpen && this.state() === 'idle' && !this.isTrial()) {
      this.analyzeRequest.emit();
      return;
    }
    if (!currentlyOpen && this.state() === 'idle' && this.isTrial()) {
      this.open.set(true);
      return;
    }
    this.open.update((v) => !v);
  }

  onAnalyzeClick(): void {
    this.analyzeRequest.emit();
  }

  onHide(): void {
    this.hide.emit();
  }

  onDelete(): void {
    this.delete.emit();
  }

  onMarkPhishing(): void {
    this.markPhishing.emit();
  }

  onMarkImportant(): void {
    this.markImportant.emit();
  }

  onCreateReminder(): void {
    this.createReminder.emit();
  }

  onRetry(): void {
    this.retry.emit();
  }

  threatIcon(category: ThreatCategory): string {
    switch (category) {
      case 'PHISHING':
      case 'SPOOFING':
        return 'ti ti-fish-hook';
      case 'MALWARE':
        return 'ti ti-bug';
      case 'SOCIAL_ENGINEERING':
        return 'ti ti-mask';
      case 'DANGEROUS_LINK':
        return 'ti ti-link';
      case 'FRAUD':
      case 'ACCOUNT_THEFT':
        return 'ti ti-shield-x';
      case 'AI_GENERATED':
        return 'ti ti-robot';
    }
  }
}
