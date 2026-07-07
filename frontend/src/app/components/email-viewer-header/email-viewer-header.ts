import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import { EmailDetail } from '../../models/email-summary.model';
import { MenuItem } from '../kebab-menu/kebab-menu';
import { EmailAction } from '../../models/email-action.model';
import { SenderAvatarComponent } from '../sender-avatar/sender-avatar';
import { EmailDatePipe } from '../../pipes/email-date.pipe';
import { KebabMenuComponent } from '../kebab-menu/kebab-menu';

type UserRole = 'TRIAL' | 'PREMIUM' | 'ADMIN' | null;

@Component({
  selector: 'app-email-viewer-header',
  standalone: true,
  imports: [SenderAvatarComponent, EmailDatePipe, KebabMenuComponent],
  templateUrl: './email-viewer-header.html',
  styleUrl: './email-viewer-header.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailViewerHeaderComponent {
  readonly email = input.required<EmailDetail>();
  readonly userRole = input<UserRole>(null);
  readonly canMarkImportant = input<boolean>(false);
  readonly isImportant = input<boolean>(false);
  readonly hasReminder = input<boolean>(false);
  readonly hasPendingReminder = input<boolean>(false);
  readonly isHidden = input<boolean>(false);
  readonly isDeleted = input<boolean>(false);

  readonly action = output<EmailAction>();

  readonly isTrial = computed(() => this.userRole() === 'TRIAL');

  readonly importantLabel = computed(() =>
    this.isImportant() ? 'Quitar importante' : 'Marcar importante',
  );

  readonly reminderLabel = computed(() => {
    if (this.hasPendingReminder()) return 'Ver recordatorio';
    if (this.hasReminder()) return 'Reactivar recordatorio';
    return 'Crear recordatorio';
  });

  readonly hideLabel = computed(() =>
    this.isHidden() ? 'Mostrar correo' : 'Ocultar correo',
  );

  readonly items = computed<MenuItem[]>(() => {
    const isTrial = this.isTrial();
    const deleted = this.isDeleted();
    return [
      {
        id: 'explain-ai',
        label: 'Explicar con IA',
        icon: 'ti ti-robot',
        disabled: false,
      },
      {
        id: 'mark-important',
        label: this.importantLabel(),
        icon: 'ti ti-flag-3',
        disabled: isTrial || deleted,
        active: this.isImportant(),
        disabledTooltip: deleted
          ? 'No disponible para correos eliminados'
          : 'Disponible en plan Premium',
      },
      {
        id: 'create-reminder',
        label: this.reminderLabel(),
        icon: 'ti ti-bell',
        disabled: isTrial || this.hasPendingReminder() || deleted,
        disabledTooltip: deleted
          ? 'No disponible para correos eliminados'
          : isTrial
            ? 'Disponible en plan Premium'
            : this.hasPendingReminder()
              ? 'Ya tienes un recordatorio pendiente para este correo'
              : undefined,
      },
      {
        id: 'hide',
        label: this.hideLabel(),
        icon: 'ti ti-eye-off',
        disabled: deleted,
        disabledTooltip: deleted ? 'No disponible para correos eliminados' : undefined,
      },
      {
        id: 'delete',
        label: 'Eliminar correo',
        icon: 'ti ti-trash',
        variant: 'destructive',
        disabled: deleted,
        disabledTooltip: deleted ? 'Este correo ya fue eliminado' : undefined,
      },
    ];
  });

  onAction(id: string): void {
    this.action.emit(id as EmailAction);
  }
}
