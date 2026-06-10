import {
  ChangeDetectionStrategy,
  Component,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageShellComponent } from '../../components/page-shell/page-shell';

type AdminSettingKind = 'toggle' | 'number';

interface AdminSetting {
  key: string;
  label: string;
  description: string;
  kind: AdminSettingKind;
  value: boolean | number;
}

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [PageShellComponent, FormsModule],
  templateUrl: './admin-settings.html',
  styleUrl: './admin-settings.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminSettingsComponent {
  readonly settings = signal<AdminSetting[]>([
    {
      key: 'enable_registration',
      label: 'Permitir nuevos registros',
      description: 'Habilita o deshabilita la creación de nuevas cuentas en la plataforma.',
      kind: 'toggle',
      value: true,
    },
    {
      key: 'force_2fa',
      label: 'Forzar verificación en dos pasos',
      description: 'Obliga a todos los usuarios administradores a usar 2FA.',
      kind: 'toggle',
      value: true,
    },
    {
      key: 'max_analyses_per_day',
      label: 'Máx. análisis por usuario / día',
      description: 'Número máximo de análisis automáticos que puede ejecutar un usuario al día.',
      kind: 'number',
      value: 500,
    },
    {
      key: 'retention_days',
      label: 'Días de retención de datos',
      description: 'Días que se conservan los correos analizados antes de purgar.',
      kind: 'number',
      value: 90,
    },
  ]);

  private readonly originalValues = new Map(
    this.settings().map((s) => [s.key, s.value] as const),
  );

  hasChanges(): boolean {
    return this.settings().some(
      (s) => this.originalValues.get(s.key) !== s.value,
    );
  }

  onToggle(key: string, value: boolean): void {
    this.update(key, value);
  }

  onNumber(key: string, value: string): void {
    const n = Number(value);
    if (Number.isFinite(n)) {
      this.update(key, n);
    }
  }

  save(): void {
    console.warn('TODO Sprint 3: persist global admin settings', this.settings());
    this.settings().forEach((s) => this.originalValues.set(s.key, s.value));
  }

  reset(): void {
    this.settings.update((list) =>
      list.map((s) => ({ ...s, value: this.originalValues.get(s.key) ?? s.value })),
    );
  }

  private update(key: string, value: boolean | number): void {
    this.settings.update((list) =>
      list.map((s) => (s.key === key ? { ...s, value } : s)),
    );
  }
}
