import { Component, ElementRef, OnDestroy, OnInit, inject, signal, output } from '@angular/core';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-header',
  standalone: true,
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class HeaderComponent implements OnInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly elementRef = inject(ElementRef);
  readonly toggleSidebar = output<void>();
  readonly user = this.authService.user;
  readonly notificationsOpen = signal(false);

  private readonly onDocClick = (event: MouseEvent): void => {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.closeNotifications();
    }
  };

  ngOnInit(): void {
    document.addEventListener('click', this.onDocClick);
  }

  ngOnDestroy(): void {
    document.removeEventListener('click', this.onDocClick);
  }

  toggleSidebarButton() {
    this.toggleSidebar.emit();
  }

  toggleNotifications(): void {
    this.notificationsOpen.update((v) => !v);
  }

  closeNotifications(): void {
    this.notificationsOpen.set(false);
  }

  logout(): void {
    this.authService.logout().subscribe();
  }

  logoutAll(): void {
    this.authService.logoutAll().subscribe();
  }
}
