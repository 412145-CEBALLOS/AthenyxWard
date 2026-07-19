import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  HostListener,
  OnDestroy,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { AdminUserDetail } from '../../models/admin-user.model';
import { ConfigService } from '../../services/config.service';

@Component({
  selector: 'app-user-detail-drawer',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './user-detail-drawer.html',
  styleUrl: './user-detail-drawer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDetailDrawerComponent implements OnDestroy {
  readonly user = input<AdminUserDetail | null>(null);
  readonly open = input(false);

  readonly close = output<void>();
  readonly resetTrial = output<void>();
  readonly deleteUser = output<void>();
  readonly changeRole = output<void>();
  readonly viewAuditHistory = output<string>();

  private readonly configService = inject(ConfigService);
  private readonly onDestroy = new Subject<void>();
  readonly trialAnalysisLimit = signal<number>(20);

  constructor() {
    effect(() => {
      if (this.open() && this.user()) {
        this.loadTrialLimit();
      }
    });
  }

  private loadTrialLimit(): void {
    this.configService.getEntry('TRIAL_ANALYSIS_LIMIT')
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (entry) => {
          const n = Number(entry.value);
          if (!Number.isNaN(n) && n > 0) {
            this.trialAnalysisLimit.set(n);
          }
        },
        error: () => { /* keep default 20 */ },
      });
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.close.emit();
    }
  }

  onOverlayClick(): void {
    this.close.emit();
  }

  onPanelClick(event: MouseEvent): void {
    event.stopPropagation();
  }

  roleLabel(role: string): string {
    switch (role) {
      case 'ADMIN': return 'Admin';
      case 'PREMIUM': return 'Premium';
      case 'TRIAL': return 'Prueba';
      default: return role;
    }
  }

  isTrialExpiringSoon(): boolean {
    const td = this.user()?.trialEndDate;
    if (!td) return false;
    const days = (new Date(td).getTime() - Date.now()) / 86400000;
    return days > 0 && days < 3;
  }

  isTrialExpired(): boolean {
    const td = this.user()?.trialEndDate;
    if (!td) return false;
    return new Date(td) < new Date();
  }
}
