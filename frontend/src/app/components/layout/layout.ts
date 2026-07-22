import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { HeaderComponent } from "../header/header";
import { SidebarComponent } from "../sidebar/sidebar";
import { AppConfigInitializerService } from '../../services/app-config-initializer.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../services/toast.service';
import { PopupService } from '../../services/popup.service';

@Component({
  selector: 'app-layout',
  imports: [HeaderComponent, SidebarComponent, RouterOutlet, RouterLink],
  templateUrl: './layout.html',
  styleUrl: './layout.css',
})
export class LayoutComponent implements OnInit, OnDestroy {
  private readonly elementRef = inject(ElementRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly popupService = inject(PopupService);
  private readonly onDestroy = new Subject<void>();

  readonly appConfig = inject(AppConfigInitializerService);
  @ViewChild('sidebarRef', { read: ElementRef }) private sidebarEl?: ElementRef;

  sidebarOpen = false;
  sidebarClosing = false;
  private openedAt = 0;

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
    this.appConfig.load();
  }

  @HostListener('window:message', ['$event'])
  onMessage(event: MessageEvent): void {
    const data = event.data;
    if (!data || typeof data !== 'object') return;
    if (data.type !== 'mp-return') return;

    const expectedOrigin = isPlatformBrowser(this.platformId)
      ? window.location.origin
      : null;
    if (expectedOrigin !== null && event.origin !== expectedOrigin && event.origin !== data.origin) {
      return;
    }

    const status = data.status as string | undefined;
    if (status === 'approved' || status === 'in_process') {
      this.auth.checkAuth().pipe(takeUntil(this.onDestroy)).subscribe({
        next: () => {
          if (status === 'approved') {
            this.toast.success('Pago completado. ¡Bienvenido a Premium!');
          } else {
            this.toast.info('Tu pago está siendo procesado.');
          }
        },
        error: () => {
          this.toast.error('Pago procesado. Iniciá sesión para ver tu cuenta Premium.');
        },
      });
      if (isPlatformBrowser(this.platformId)) {
        this.popupService.closePopup();
        window.location.assign('/home');
      }
    } else if (status === 'rejected' || status === 'failure') {
      this.toast.error('El pago no se completó. Intentá nuevamente.');
      if (isPlatformBrowser(this.platformId)) {
        this.popupService.closePopup();
        window.location.assign('/home');
      }
    }
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.removeEventListener('click', this.onDocClick);
    }
    this.onDestroy.next();
    this.onDestroy.complete();
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
}
