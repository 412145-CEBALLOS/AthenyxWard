import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Subject, takeUntil } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';

type SettingKind = 'toggle' | 'select';

interface Setting {
  key: string;
  label: string;
  description: string;
  kind: SettingKind;
  value: boolean | string;
  options?: string[];
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [PageShellComponent, FormsModule],
  templateUrl: './settings.html',
  styleUrl: './settings.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent implements OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly onDestroy = new Subject<void>();

  private readonly otherSettings = signal<Setting[]>([
    {
      key: 'notifications',
      label: 'Notificaciones por correo',
      description: 'Recibe avisos cuando se detecten amenazas de alto riesgo.',
      kind: 'toggle',
      value: true,
    },
    {
      key: 'heuristicDetail',
      label: 'Mostrar análisis heurístico detallado',
      description: 'Habilita la sección de reglas heurísticas en el panel de análisis.',
      kind: 'toggle',
      value: false,
    },
    {
      key: 'language',
      label: 'Idioma de la interfaz',
      description: 'Cambia el idioma de los menús y mensajes.',
      kind: 'select',
      value: 'es',
      options: ['es', 'en'],
    },
  ]);

  private readonly accessibilityValue = signal<boolean>(
    this.authService.user()?.accessibilityMode ?? true,
  );

  readonly settings = computed<Setting[]>(() => [
    {
      key: 'accessibility',
      label: 'Modo accesibilidad',
      description: 'Texto grande, alto contraste e interfaz simplificada.',
      kind: 'toggle',
      value: this.accessibilityValue(),
    },
    ...this.otherSettings(),
  ]);

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  onToggle(key: string, value: boolean): void {
    if (key === 'accessibility') {
      const previous = this.accessibilityValue();
      this.accessibilityValue.set(value);
      this.authService
        .updateAccessibilityMode(value)
        .pipe(takeUntil(this.onDestroy))
        .subscribe({
          error: () => {
            this.accessibilityValue.set(previous);
            this.toast.error('No se pudo guardar el ajuste de accesibilidad');
          },
        });
      return;
    }

    this.update(key, value);
    console.warn('TODO Sprint 3: persist setting', key, value);
  }

  onSelect(key: string, value: string): void {
    this.update(key, value);
    console.warn('TODO Sprint 3: persist setting', key, value);
  }

  private update(key: string, value: boolean | string): void {
    this.otherSettings.update((list) =>
      list.map((s) => (s.key === key ? { ...s, value } : s)),
    );
  }
}
