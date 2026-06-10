import {
  ChangeDetectionStrategy,
  Component,
  signal,
} from '@angular/core';
import { PageShellComponent } from '../../components/page-shell/page-shell';

interface AuditEntry {
  id: number;
  actor: string;
  action: string;
  target: string;
  at: string;
}

@Component({
  selector: 'app-admin-audit',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './admin-audit.html',
  styleUrl: './admin-audit.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminAuditComponent {
  readonly entries = signal<AuditEntry[]>([
    { id: 1, actor: 'maria.g@example.com', action: 'ROLE_CHANGE', target: 'carlos.perez@example.com', at: '2026-06-08 09:24' },
    { id: 2, actor: 'sistema', action: 'AUTO_ANALYSIS', target: 'gmail:18f4...a1c2', at: '2026-06-08 09:14' },
    { id: 3, actor: 'javier.l@example.com', action: 'LOGIN', target: 'javier.l@example.com', at: '2026-06-08 08:55' },
    { id: 4, actor: 'maria.g@example.com', action: 'CONFIG_UPDATE', target: 'global.max_analyses_per_day', at: '2026-06-08 08:11' },
    { id: 5, actor: 'sistema', action: 'PHISHING_DETECTED', target: 'gmail:7bc3...d91e', at: '2026-06-08 07:42' },
    { id: 6, actor: 'carlos.perez@example.com', action: 'EMAIL_MARKED_IMPORTANT', target: 'gmail:c2a1...88f0', at: '2026-06-07 22:45' },
    { id: 7, actor: 'sistema', action: 'TOKEN_REFRESH_FAILED', target: 'ana.torres@example.com', at: '2026-06-07 21:10' },
    { id: 8, actor: 'maria.g@example.com', action: 'USER_DEACTIVATED', target: 'ana.torres@example.com', at: '2026-06-07 19:01' },
  ]);
}
