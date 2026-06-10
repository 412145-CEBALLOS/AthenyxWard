import {
  ChangeDetectionStrategy,
  Component,
  signal,
} from '@angular/core';
import { PageShellComponent } from '../../components/page-shell/page-shell';

interface FaqItem {
  q: string;
  a: string;
}

interface FaqCategory {
  category: string;
  items: FaqItem[];
}

@Component({
  selector: 'app-help-center',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './help-center.html',
  styleUrl: './help-center.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HelpCenterComponent {
  readonly faqs = signal<FaqCategory[]>([
    {
      category: 'Primeros pasos',
      items: [
        {
          q: '¿Cómo conecto mi cuenta de Gmail?',
          a: 'Al iniciar sesión por primera vez se te solicitará permiso para acceder a tu correo. Athenyx Ward solo lee los metadatos y el contenido necesario para el análisis; nunca almacena tu contraseña.',
        },
        {
          q: '¿Cuánto tarda un análisis?',
          a: 'El análisis heurístico tarda menos de un segundo. El análisis con IA (Llama 3) puede tomar entre 2 y 6 segundos dependiendo del tamaño del correo.',
        },
      ],
    },
    {
      category: 'Seguridad y privacidad',
      items: [
        {
          q: '¿Qué datos almacenáis?',
          a: 'Solo guardamos los datos relevantes para el análisis: remitente, fecha, contenido (limitado a lo necesario), URLs, porcentaje de riesgo y clasificación. Nunca almacenamos los correos completos de forma permanente.',
        },
        {
          q: '¿La IA se ejecuta en la nube?',
          a: 'No. Usamos Ollama + Llama 3 ejecutándose en local. Ningún correo sale de tu entorno hacia servicios externos de IA.',
        },
      ],
    },
    {
      category: 'Planes y facturación',
      items: [
        {
          q: '¿Puedo cancelar en cualquier momento?',
          a: 'Sí. Desde la sección Mi plan puedes cambiar o cancelar tu suscripción cuando quieras, sin permanencia.',
        },
        {
          q: '¿Qué incluye el plan Familia?',
          a: 'Hasta 5 cuentas con todas las ventajas Premium y un panel de control familiar compartido.',
        },
      ],
    },
  ]);
}
