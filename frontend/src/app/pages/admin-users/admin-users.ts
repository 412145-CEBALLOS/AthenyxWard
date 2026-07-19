import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  computed,
  inject,
  OnDestroy,
  OnInit,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { PageShellComponent } from '../../components/page-shell/page-shell';
import { EmailPaginatorComponent } from '../../components/email-paginator/email-paginator';
import { ChangeRoleDialogComponent } from '../../components/change-role-dialog/change-role-dialog';
import { UserDetailDrawerComponent } from '../../components/user-detail-drawer/user-detail-drawer';
import { ConfirmDialogComponent } from '../../components/confirm-dialog/confirm-dialog';
import { AdminUsersService } from '../../services/admin-users.service';
import { ToastService } from '../../services/toast.service';
import {
  AdminUser,
  AdminUserDetail,
  UserFilters,
  UserRole,
} from '../../models/admin-user.model';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    PageShellComponent,
    EmailPaginatorComponent,
    ChangeRoleDialogComponent,
    UserDetailDrawerComponent,
  ],
  templateUrl: './admin-users.html',
  styleUrl: './admin-users.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUsersComponent implements OnInit, OnDestroy {
  private readonly adminUsersService = inject(AdminUsersService);
  private readonly toastService = inject(ToastService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly onDestroy = new Subject<void>();
  private readonly querySubject = new Subject<string>();

  readonly users = signal<AdminUser[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly selectedUser = signal<AdminUserDetail | null>(null);
  readonly drawerOpen = signal(false);
  readonly openKebabId = signal<number | null>(null);

  readonly roleDialogUser = signal<AdminUser | null>(null);
  readonly deleteConfirmUser = signal<AdminUser | null>(null);
  readonly confirmDeleteText = signal('');

  readonly queryFilter = signal('');
  readonly roleFilter = signal<UserRole | ''>('');
  readonly activeFilter = signal<boolean | ''>('');

  readonly hasNextPage = computed(() => this.currentPage() < this.totalPages() - 1);
  readonly emptyState = computed(() => !this.loading() && !this.error() && this.totalItems() === 0);

  ngOnInit(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    this.querySubject
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.onDestroy))
      .subscribe((q) => {
        this.queryFilter.set(q);
        this.currentPage.set(0);
        this.loadUsers();
      });

    this.route.queryParamMap.pipe(takeUntil(this.onDestroy)).subscribe((params) => {
      const focus = params.get('focus');
      if (focus) {
        this.loadUserDetail(+focus);
      }
    });

    this.loadUsers();
  }

  ngOnDestroy(): void {
    this.onDestroy.next();
    this.onDestroy.complete();
  }

  onQueryInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.querySubject.next(value);
  }

  onRoleFilterChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as UserRole | '';
    this.roleFilter.set(value);
    this.currentPage.set(0);
    this.loadUsers();
  }

  onActiveFilterChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.activeFilter.set(value === 'true' ? true : value === 'false' ? false : '');
    this.currentPage.set(0);
    this.loadUsers();
  }

  onPageChange(page: number): void {
    const safe = Math.max(0, page);
    if (safe === this.currentPage()) return;
    this.currentPage.set(safe);
    this.loadUsers();
  }

  toggleKebab(id: number, event: MouseEvent): void {
    event.stopPropagation();
    this.openKebabId.update((current) => (current === id ? null : id));
  }

  closeKebab(): void {
    this.openKebabId.set(null);
  }

  openDetail(user: AdminUser): void {
    this.closeKebab();
    this.loadUserDetail(user.id);
  }

  openRoleDialog(userOrId: AdminUser | number): void {
    this.closeKebab();
    if (typeof userOrId === 'number') {
      const user = this.users().find((u) => u.id === userOrId);
      if (user) this.roleDialogUser.set(user);
    } else {
      this.roleDialogUser.set(userOrId);
    }
  }

  closeRoleDialog(): void {
    this.roleDialogUser.set(null);
  }

  onDrawerChangeRole(): void {
    const detail = this.selectedUser();
    if (!detail) return;
    const id = detail.id;
    this.drawerOpen.set(false);
    this.selectedUser.set(null);
    this.router.navigate([], { queryParams: { focus: null }, queryParamsHandling: 'merge' });
    this.openRoleDialog(id);
  }

  onRoleChange(newRole: UserRole): void {
    const user = this.roleDialogUser();
    if (!user) return;
    this.adminUsersService.updateRole(user.id, newRole).pipe(takeUntil(this.onDestroy)).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        const detail = this.selectedUser();
        if (detail && detail.id === updated.id) {
          this.selectedUser.set({ ...detail, role: updated.role });
        }
        this.closeRoleDialog();
        this.toastService.success(`Rol actualizado a ${this.roleLabel(newRole)}`);
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (err.error?.error === 'cannot_change_own_role') {
          this.toastService.error('No puedes cambiar tu propio rol');
        } else {
          this.toastService.error('Error al cambiar el rol');
        }
        this.closeRoleDialog();
      },
    });
  }

  onDeleteRequest(userId: number): void {
    this.closeKebab();
    const user = this.users().find((u) => u.id === userId);
    if (user) this.deleteConfirmUser.set(user);
    this.confirmDeleteText.set('');
  }

  closeDeleteConfirm(): void {
    this.deleteConfirmUser.set(null);
    this.confirmDeleteText.set('');
  }

  onDeleteConfirm(): void {
    const user = this.deleteConfirmUser();
    if (!user) return;
    if (this.confirmDeleteText().trim().toLowerCase() !== 'eliminar') return;
    this.adminUsersService.softDelete(user.id).pipe(takeUntil(this.onDestroy)).subscribe({
      next: () => {
        this.users.update((list) => list.filter((u) => u.id !== user.id));
        this.closeDeleteConfirm();
        this.closeDrawer();
        this.toastService.success('Usuario eliminado');
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (err.error?.error === 'cannot_delete_self') {
          this.toastService.error('No puedes eliminarte a ti mismo');
        } else {
          this.toastService.error('Error al eliminar el usuario');
        }
        this.closeDeleteConfirm();
      },
    });
  }

  onResetTrial(): void {
    const detail = this.selectedUser();
    if (!detail) return;
    this.adminUsersService.resetTrial(detail.id).pipe(takeUntil(this.onDestroy)).subscribe({
      next: () => {
        this.adminUsersService.getDetail(detail.id).pipe(takeUntil(this.onDestroy)).subscribe({
          next: (updated) => {
            this.selectedUser.set(updated);
            this.loadUsers();
            this.toastService.success('Trial restablecido');
            this.cdr.markForCheck();
          },
        });
      },
      error: () => {
        this.toastService.error('Error al restablecer el trial');
      },
    });
  }

  onViewAuditHistory(email: string): void {
    this.router.navigate(['/admin/audit'], { queryParams: { actor: email } });
  }

  private loadUserDetail(id: number): void {
    this.adminUsersService.getDetail(id).pipe(takeUntil(this.onDestroy)).subscribe({
      next: (detail) => {
        this.selectedUser.set(detail);
        this.drawerOpen.set(true);
        this.cdr.markForCheck();
      },
      error: () => {
        this.toastService.error('No se pudo cargar el detalle del usuario');
      },
    });
  }

  closeDrawer(): void {
    this.drawerOpen.set(false);
    this.router.navigate([], { queryParams: { focus: null }, queryParamsHandling: 'merge' });
  }

  toggleActive(user: AdminUser): void {
    this.closeKebab();
    this.adminUsersService.updateActive(user.id, !user.isActive).pipe(takeUntil(this.onDestroy)).subscribe({
      next: (updated) => {
        this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
        const detail = this.selectedUser();
        if (detail && detail.id === updated.id) {
          this.selectedUser.set({ ...detail, isActive: updated.isActive });
        }
        this.toastService.success(updated.isActive ? 'Usuario activado' : 'Usuario desactivado');
        this.cdr.markForCheck();
      },
      error: (err) => {
        if (err.error?.error === 'cannot_deactivate_self') {
          this.toastService.error('No puedes desactivarte a ti mismo');
        } else {
          this.toastService.error('Error al cambiar el estado del usuario');
        }
      },
    });
  }

  onUserUpdated(updated: AdminUser): void {
    this.users.update((list) => list.map((u) => (u.id === updated.id ? updated : u)));
    const detail = this.selectedUser();
    if (detail && detail.id === updated.id) {
      this.selectedUser.set({ ...detail, role: updated.role });
    }
    this.closeKebab();
    this.cdr.markForCheck();
  }

  roleLabel(role: UserRole): string {
    switch (role) {
      case 'ADMIN': return 'Admin';
      case 'PREMIUM': return 'Premium';
      case 'TRIAL': return 'Prueba';
    }
  }

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(false);
    const filters: UserFilters = {
      query: this.queryFilter() || undefined,
      role: this.roleFilter() || undefined,
      active: this.activeFilter() !== '' ? this.activeFilter() : undefined,
    };
    this.adminUsersService
      .list(filters, this.currentPage(), 20)
      .pipe(takeUntil(this.onDestroy))
      .subscribe({
        next: (response) => {
          this.users.set(response.items);
          this.currentPage.set(response.currentPage);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
          this.loading.set(false);
          this.error.set(false);
          this.cdr.markForCheck();
        },
        error: () => {
          this.error.set(true);
          this.loading.set(false);
          this.cdr.markForCheck();
        },
      });
  }
}
