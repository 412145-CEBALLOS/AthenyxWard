import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    path: 'login',
    renderMode: RenderMode.Prerender,
  },
  {
    path: 'account-disabled',
    renderMode: RenderMode.Prerender,
  },
  {
    path: 'legal/terms',
    renderMode: RenderMode.Prerender,
  },
  {
    path: 'legal/privacy',
    renderMode: RenderMode.Prerender,
  },
  {
    path: 'home',
    renderMode: RenderMode.Server,
  },
  {
    path: '**',
    renderMode: RenderMode.Server,
  },
];
