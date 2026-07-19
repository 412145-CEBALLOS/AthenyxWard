import { Component, inject, OnInit, PLATFORM_ID, signal, computed } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class LoginComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly route = inject(ActivatedRoute);

  readonly termsAccepted = signal(false);
  readonly privacyAccepted = signal(false);
  readonly canLogin = computed(() => this.termsAccepted() && this.privacyAccepted());

  ngOnInit(): void {
    if (isPlatformBrowser(this.platformId)) {
      if (this.authService.refreshFailed()) {
        this.authService.refreshFailed.set(false);
        return;
      }
      if (this.route.snapshot.queryParamMap.get('error') === 'account_disabled') {
        this.router.navigate(['/account-disabled']);
        return;
      }
      this.authService.checkAuth().subscribe((user) => {
        if (user) {
          this.router.navigate(['/home']);
        }
      });
    }
  }

  loginWithGoogle(): void {
    if (isPlatformBrowser(this.platformId)) {
      window.location.href = '/oauth2/authorization/google';
    }
  }
}
