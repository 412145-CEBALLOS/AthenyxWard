import {
  ChangeDetectionStrategy,
  Component,
  signal,
} from '@angular/core';
import { PageShellComponent } from '../../components/page-shell/page-shell';

interface ImportantEmail {
  id: string;
  sender: string;
  subject: string;
  snippet: string;
  receivedAt: string;
  risk: number;
}

@Component({
  selector: 'app-important-emails',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './important-emails.html',
  styleUrl: './important-emails.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportantEmailsComponent {
  readonly emails = signal<ImportantEmail[]>([
    {
      id: 'ie-1',
      sender: 'rectoria@universidad.edu.co',
      subject: 'Resultado de tu solicitud de beca',
      snippet: 'Nos complace informarte que tu solicitud ha sido aprobada…',
      receivedAt: '2026-06-07',
      risk: 8,
    },
    {
      id: 'ie-2',
      sender: 'no-reply@accounts.google.com',
      subject: 'Nuevo inicio de sesión en tu cuenta',
      snippet: 'Se ha detectado un nuevo acceso desde Chrome en Windows…',
      receivedAt: '2026-06-06',
      risk: 12,
    },
    {
      id: 'ie-3',
      sender: 'rrhh@empresa.com',
      subject: 'Tu oferta laboral ha sido actualizada',
      snippet: 'Hemos actualizado las condiciones de tu oferta. Por favor…',
      receivedAt: '2026-06-05',
      risk: 4,
    },
    {
      id: 'ie-4',
      sender: 'soporte@banco.com',
      subject: 'Confirmación de transferencia realizada',
      snippet: 'Tu transferencia por valor de $1.250.000 ha sido procesada…',
      receivedAt: '2026-06-04',
      risk: 22,
    },
    {
      id: 'ie-5',
      sender: 'secretaria@colegio.edu',
      subject: 'Circular importante - Calendario académico',
      snippet: 'Adjuntamos el calendario académico actualizado para el…',
      receivedAt: '2026-06-03',
      risk: 6,
    },
  ]);

  unmark(id: string): void {
    this.emails.update((list) => list.filter((e) => e.id !== id));
  }
}
