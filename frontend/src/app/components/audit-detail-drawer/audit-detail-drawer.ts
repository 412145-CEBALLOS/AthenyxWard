import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  HostListener,
  input,
  output,
} from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { AuditEntryResponse } from '../../models/audit.model';

@Component({
  selector: 'app-audit-detail-drawer',
  standalone: true,
  imports: [JsonPipe, DatePipe],
  templateUrl: './audit-detail-drawer.html',
  styleUrl: './audit-detail-drawer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditDetailDrawerComponent {
  readonly entry = input<AuditEntryResponse | null>(null);
  readonly open = input(false);
  readonly closeDrawer = output<void>();
  readonly correlationClick = output<string>();

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.closeDrawer.emit();
    }
  }

  onOverlayClick(): void {
    this.closeDrawer.emit();
  }

  onPanelClick(event: MouseEvent): void {
    event.stopPropagation();
  }

  onCorrelationClick(correlationId: string): void {
    if (correlationId) {
      this.correlationClick.emit(correlationId);
    }
  }

  parsePayload(payload: string | null): Record<string, unknown> {
    if (!payload) return {};
    try {
      return JSON.parse(payload);
    } catch {
      return {};
    }
  }
}
