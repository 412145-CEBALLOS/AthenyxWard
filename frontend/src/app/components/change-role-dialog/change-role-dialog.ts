import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import { UserRole } from '../../models/admin-user.model';

@Component({
  selector: 'app-change-role-dialog',
  standalone: true,
  templateUrl: './change-role-dialog.html',
  styleUrl: './change-role-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangeRoleDialogComponent {
  readonly userId = input.required<number>();
  readonly userName = input.required<string>();
  readonly currentRole = input.required<UserRole>();

  readonly changeRole = output<UserRole>();
  readonly cancel = output<void>();

  readonly roles: UserRole[] = ['TRIAL', 'PREMIUM', 'ADMIN'];
  readonly confirmText = signal('');
  readonly selectedRole = signal<UserRole | null>(null);

  readonly canConfirm = computed(
    () => this.confirmText().trim().toLowerCase() === 'confirmar' && this.selectedRole() !== null,
  );

  roleLabel(role: UserRole): string {
    switch (role) {
      case 'ADMIN': return 'Admin';
      case 'PREMIUM': return 'Premium';
      case 'TRIAL': return 'Prueba';
    }
  }

  onConfirm(): void {
    const role = this.selectedRole();
    if (!this.canConfirm() || role === null) return;
    this.changeRole.emit(role);
  }

  onCancel(): void {
    this.cancel.emit();
  }
}
