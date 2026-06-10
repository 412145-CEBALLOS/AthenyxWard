import {
  ChangeDetectionStrategy,
  Component,
  signal,
} from '@angular/core';
import { PageShellComponent } from '../../components/page-shell/page-shell';

type UserRole = 'ADMIN' | 'PREMIUM' | 'TRIAL';

interface AdminUser {
  id: number;
  name: string;
  email: string;
  role: UserRole;
  lastLogin: string;
  active: boolean;
}

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [PageShellComponent],
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUsersComponent {
  readonly users = signal<AdminUser[]>([
    { id: 1, name: 'María González', email: 'maria.g@example.com', role: 'ADMIN', lastLogin: '2026-06-08 09:12', active: true },
    { id: 2, name: 'Carlos Pérez', email: 'carlos.perez@example.com', role: 'PREMIUM', lastLogin: '2026-06-07 22:45', active: true },
    { id: 3, name: 'Lucía Rodríguez', email: 'lucia.r@example.com', role: 'PREMIUM', lastLogin: '2026-06-08 08:01', active: true },
    { id: 4, name: 'Pedro Ramírez', email: 'pedro.r@example.com', role: 'TRIAL', lastLogin: '2026-06-06 14:33', active: true },
    { id: 5, name: 'Ana Torres', email: 'ana.torres@example.com', role: 'PREMIUM', lastLogin: '2026-06-05 19:21', active: false },
    { id: 6, name: 'Javier López', email: 'javier.l@example.com', role: 'TRIAL', lastLogin: '2026-06-08 07:55', active: true },
  ]);

  toggleActive(id: number): void {
    this.users.update((list) =>
      list.map((u) => (u.id === id ? { ...u, active: !u.active } : u)),
    );
  }

  roleLabel(role: UserRole): string {
    switch (role) {
      case 'ADMIN': return 'Admin';
      case 'PREMIUM': return 'Premium';
      case 'TRIAL': return 'Prueba';
    }
  }
}
