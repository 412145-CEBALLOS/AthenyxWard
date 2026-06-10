import {
  ChangeDetectionStrategy,
  Component,
  signal,
} from '@angular/core';
import { PageShellComponent } from '../../components/page-shell/page-shell';

interface Reminder {
  id: number;
  title: string;
  emailSubject: string;
  dueAt: string;
  done: boolean;
}

@Component({
  selector: 'app-reminders',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './reminders.html',
  styleUrl: './reminders.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RemindersComponent {
  readonly reminders = signal<Reminder[]>([
    {
      id: 1,
      title: 'Responder a RRHH sobre oferta',
      emailSubject: 'Oferta laboral - Entrevista inicial',
      dueAt: '2026-06-10 10:00',
      done: false,
    },
    {
      id: 2,
      title: 'Revisar factura adjunta',
      emailSubject: 'Factura de mayo - Proveedor X',
      dueAt: '2026-06-09 18:00',
      done: false,
    },
    {
      id: 3,
      title: 'Confirmar asistencia al webinar',
      emailSubject: 'Invitación: Webinar de seguridad',
      dueAt: '2026-06-12 16:30',
      done: true,
    },
    {
      id: 4,
      title: 'Pagar suscripción del servicio',
      emailSubject: 'Tu suscripción vence pronto',
      dueAt: '2026-06-15 23:59',
      done: false,
    },
    {
      id: 5,
      title: 'Llamar al banco',
      emailSubject: 'Verificación de movimientos',
      dueAt: '2026-06-08 14:00',
      done: true,
    },
  ]);

  toggle(id: number): void {
    this.reminders.update((list) =>
      list.map((r) => (r.id === id ? { ...r, done: !r.done } : r)),
    );
  }

  remove(id: number): void {
    this.reminders.update((list) => list.filter((r) => r.id !== id));
  }
}
