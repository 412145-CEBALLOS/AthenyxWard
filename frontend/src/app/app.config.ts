import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection, inject, provideEnvironmentInitializer } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';
import { refreshInterceptor } from './interceptors/refresh.interceptor';
import { errorToastInterceptor } from './interceptors/error-toast.interceptor';
import { ThemeService } from './services/theme.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideClientHydration(withEventReplay()),
    provideHttpClient(
      withFetch(),
      withInterceptors([errorToastInterceptor, refreshInterceptor]),
    ),
    provideEnvironmentInitializer(() => {
      const t = inject(ThemeService);
      t.init();
    }),
  ]
};
