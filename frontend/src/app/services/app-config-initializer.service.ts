import { Injectable, signal, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ConfigService } from './config.service';
import { RiskThresholds } from '../models/config.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AppConfigInitializerService {
  readonly supportEmail = signal<string>(environment.supportEmail);
  readonly pollIntervalSeconds = signal<number>(120);
  readonly riskThresholds = signal<RiskThresholds>({ low: 40, medium: 70 });
  readonly aiEnabled = signal<boolean>(true);
  readonly loading = signal<boolean>(true);

  private readonly configService = inject(ConfigService);

  async load(): Promise<void> {
    try {
      const categories = await firstValueFrom(this.configService.getPublicConfig());
      for (const cat of categories) {
        for (const entry of cat.entries) {
          if (entry.key === 'COPY_SUPPORT_EMAIL') {
            this.supportEmail.set(entry.value);
          } else if (entry.key === 'NOTIFICATIONS_POLL_INTERVAL_SECONDS') {
            this.pollIntervalSeconds.set(Number(entry.value));
          } else if (entry.key === 'HEURISTIC_RISK_THRESHOLD_LOW') {
            const n = Number(entry.value);
            if (!Number.isNaN(n)) this.riskThresholds.update(t => ({ ...t, low: n }));
          } else if (entry.key === 'HEURISTIC_RISK_THRESHOLD_MEDIUM') {
            const n = Number(entry.value);
            if (!Number.isNaN(n)) this.riskThresholds.update(t => ({ ...t, medium: n }));
          } else if (entry.key === 'AI_ENABLED') {
            this.aiEnabled.set(entry.value === 'true');
          }
        }
      }
    } catch (e) {
      console.warn('AppConfigInitializer: failed to load public config, using defaults', e);
    } finally {
      this.loading.set(false);
    }
  }
}
