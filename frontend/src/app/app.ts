import { Component, OnInit, PLATFORM_ID, effect, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { ToastContainerComponent } from './components/toast-container/toast-container';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';

/**
 * Root component of the Athenyx Ward SPA.
 *
 * Hosts the {@link RouterOutlet} (so all routed pages render inside
 * this host) and the global {@link ToastContainerComponent}.
 *
 * <p>Also keeps the global `accessibility-mode` CSS class on
 * {@code document.documentElement} in sync with the authenticated user's
 * preference. This avoids a layout flash because the same class is set
 * eagerly by the inline script in {@code index.html}.</p>
 *
 * <p>When the user enables accessibility mode, the theme is forced to
 * light so the high-contrast light palette is guaranteed to render.</p>
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastContainerComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly platformId = inject(PLATFORM_ID);

  private readonly cleanupAccessibilityEffect = effect(() => {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }
    const user = this.auth.user();
    if (user == null) {
      return;
    }
    this.applyAccessibilityClass(user.accessibilityMode);
    if (user.accessibilityMode) {
      this.theme.setTheme('light');
    }
  });

  ngOnInit(): void {
    // No defaults are applied here. The index.html anti-flash script already
    // sets the initial theme and accessibility-mode class from localStorage.
    // Once the authenticated user loads, the effect above keeps both in sync.
  }

  private applyAccessibilityClass(enabled: boolean): void {
    document.documentElement.classList.toggle('accessibility-mode', enabled);
  }
}
