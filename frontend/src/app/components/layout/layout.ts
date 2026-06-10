import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
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
    document.addEventListener('click', this.onDocClick);
  }

  ngOnDestroy(): void {
    document.removeEventListener('click', this.onDocClick);
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
