import { Component, output, input, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.css',
})
export class SidebarComponent {

  private readonly authService = inject(AuthService);
  readonly user = this.authService.user;

  isOpen = input<boolean>(false);
  isClosing = input<boolean>(false);
  toggleSidebar = output<void>();

  toggle() {
    this.toggleSidebar.emit();
  }

}
