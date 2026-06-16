import { Component, output, input, inject, afterNextRender } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { EmailService } from '../../services/email.service';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class SidebarComponent {

  private readonly authService = inject(AuthService);
  private readonly emailService = inject(EmailService);
  readonly user = this.authService.user;
  readonly importantCount = this.emailService.importantCount;

  isOpen = input<boolean>(false);
  isClosing = input<boolean>(false);
  toggleSidebar = output<void>();

  constructor() {
    afterNextRender(() => {
      if (this.user()?.role !== 'TRIAL') {
        this.emailService.refreshImportantCount();
      }
    });
  }

  toggle() {
    this.toggleSidebar.emit();
  }

}
