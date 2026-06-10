import {
  ChangeDetectionStrategy,
  Component,
  signal,
} from '@angular/core';
import { PageShellComponent } from '../../components/page-shell/page-shell';

type RiskLevel = 'GREEN' | 'YELLOW' | 'RED';

interface HistoryEntry {
  id: string;
  date: string;
  sender: string;
  subject: string;
  risk: number;
  level: RiskLevel;
  summary: string;
}

@Component({
  selector: 'app-analysis-history',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './analysis-history.html',
  styleUrl: './analysis-history.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalysisHistoryComponent {
  readonly entries = signal<HistoryEntry[]>([
    {
      id: 'h-001',
      date: '2026-06-08 09:14',
      sender: 'bancosantander@seguridad-cuenta.com',
      subject: 'Verifica tu identidad urgentemente',
      risk: 87,
      level: 'RED',
      summary: 'Dominio no oficial, mensaje urgente y enlace externo.',
    },
    {
      id: 'h-002',
      date: '2026-06-07 18:42',
      sender: 'soporte@athenyx.app',
      subject: 'Resumen semanal de actividad',
      risk: 12,
      level: 'GREEN',
      summary: 'Remitente verificado, sin indicadores de riesgo.',
    },
    {
      id: 'h-003',
      date: '2026-06-07 11:05',
      sender: 'Amazon <ofertas@amaz0n-promo.net>',
      subject: '¡Has ganado un iPhone 15 Pro!',
      risk: 64,
      level: 'YELLOW',
      summary: 'Suplantación de marca y奖品诱惑.',
    },
    {
      id: 'h-004',
      date: '2026-06-06 22:31',
      sender: 'no-reply@accounts.google.com',
      subject: 'Nuevo inicio de sesión detectado',
      risk: 18,
      level: 'GREEN',
      summary: 'Notificación legítima de Google.',
    },
    {
      id: 'h-005',
      date: '2026-06-06 14:18',
      sender: 'rectoria@univers1dad-edu.co',
      subject: 'Pago de matrícula pendiente',
      risk: 71,
      level: 'RED',
      summary: 'Dominio近似 y solicitud de pago a cuenta no oficial.',
    },
    {
      id: 'h-006',
      date: '2026-06-05 08:22',
      sender: 'newsletter@github.com',
      subject: 'Your weekly GitHub digest',
      risk: 5,
      level: 'GREEN',
      summary: 'Boletín legítimo, remitente verificado.',
    },
  ]);

  levelLabel(level: RiskLevel): string {
    switch (level) {
      case 'GREEN': return 'Seguro';
      case 'YELLOW': return 'Sospechoso';
      case 'RED': return 'Peligroso';
    }
  }
}
