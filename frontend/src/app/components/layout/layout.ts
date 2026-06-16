import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HeaderComponent } from "../header/header";
import { SidebarComponent } from "../sidebar/sidebar";
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-layout',
  imports: [HeaderComponent, SidebarComponent, RouterOutlet],
  templateUrl: './layout.html',
  styleUrl: './layout.css',
})
export class LayoutComponent implements OnInit, OnDestroy {
  private readonly elementRef = inject(ElementRef);
  private readonly platformId = inject(PLATFORM_ID);
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
  }

  ngOnDestroy(): void {
    if (isPlatformBrowser(this.platformId)) {
      document.removeEventListener('click', this.onDocClick);
    }
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
