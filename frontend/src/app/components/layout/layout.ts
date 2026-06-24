import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HeaderComponent } from "../header/header";
import { SidebarComponent } from "../sidebar/sidebar";
import { RouterOutlet } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-layout',
  imports: [HeaderComponent, SidebarComponent, RouterOutlet],
  templateUrl: './layout.html',
  styleUrl: './layout.css',
})
export class LayoutComponent implements OnInit, OnDestroy {
  private readonly elementRef = inject(ElementRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  @ViewChild('sidebarRef', { read: ElementRef }) private sidebarEl?: ElementRef;

  sidebarOpen = false;
  sidebarClosing = false;
  private openedAt = 0;
  private pollingStarted = false;

  private readonly onDocClick = (event: MouseEvent): void => {
    if (!this.sidebarOpen) return;
    if (window.innerWidth > 700) return;
    if (this.sidebarEl?.nativeElement.contains(event.target)) return;
    if (Date.now() - this.openedAt < 150) return;
    this.toggleSidebar();
  };

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.addEventListener('click', this.onDocClick);
    }
    this.maybeStartPolling();
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.removeEventListener('click', this.onDocClick);
    }
    this.notificationService.stopPolling();
  }

  toggleSidebar() {
    if (this.sidebarOpen) {
      this.sidebarClosing = true;
      setTimeout(() => {
        this.sidebarOpen = false;
        this.sidebarClosing = false;
      }, 400);
    } else {
      this.openedAt = Date.now();
      this.sidebarOpen = true;
    }
  }

  /**
   * Starts the notification polling loop if the user is on a plan
   * that supports reminders (PREMIUM or ADMIN). TRIAL users get
   * an empty list on the server side, so there's no point
   * polling them every two minutes. Idempotent.
   */
  private maybeStartPolling(): void {
    if (this.pollingStarted) return;
    const role = this.authService.user()?.role;
    if (role === 'PREMIUM' || role === 'ADMIN') {
      this.notificationService.startPolling(120_000);
      this.pollingStarted = true;
    }
  }
}
