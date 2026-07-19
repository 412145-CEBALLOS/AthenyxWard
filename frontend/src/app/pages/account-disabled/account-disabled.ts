import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-account-disabled',
  standalone: true,
  templateUrl: './account-disabled.html',
  styleUrl: './account-disabled.css',
})
export class AccountDisabledComponent {
  private readonly router = inject(Router);

  readonly supportEmail = environment.supportEmail;

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
