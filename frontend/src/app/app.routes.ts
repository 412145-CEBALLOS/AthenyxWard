import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { adminGuard } from './guards/admin.guard';
import { LayoutComponent } from './components/layout/layout';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.LoginComponent),
  },
  {
    path: '',
    component: LayoutComponent,
    children: [
      {
        path: '',
        redirectTo: 'home',
        pathMatch: 'full',
      },
      {
        path: 'home',
        loadComponent: () => import('./pages/home/home').then((m) => m.HomeComponent),
        canActivate: [authGuard],
      },
      {
        path: 'history',
        loadComponent: () =>
          import('./pages/analysis-history/analysis-history').then((m) => m.AnalysisHistoryComponent),
        canActivate: [authGuard],
      },
      {
        path: 'stats',
        loadComponent: () =>
          import('./pages/stats/stats').then((m) => m.StatsComponent),
        canActivate: [authGuard],
      },
      {
        path: 'plan',
        loadComponent: () =>
          import('./pages/plan/plan').then((m) => m.PlanComponent),
        canActivate: [authGuard],
      },
      {
        path: 'reminders',
        loadComponent: () =>
          import('./pages/reminders/reminders').then((m) => m.RemindersComponent),
        canActivate: [authGuard],
      },
      {
        path: 'important',
        loadComponent: () =>
          import('./pages/important-emails/important-emails').then((m) => m.ImportantEmailsComponent),
        canActivate: [authGuard],
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./pages/settings/settings').then((m) => m.SettingsComponent),
        canActivate: [authGuard],
      },
      {
        path: 'help',
        loadComponent: () =>
          import('./pages/help-center/help-center').then((m) => m.HelpCenterComponent),
        canActivate: [authGuard],
      },
      {
        path: 'admin',
        canActivate: [authGuard, adminGuard],
        children: [
          {
            path: 'users',
            loadComponent: () =>
              import('./pages/admin-users/admin-users').then((m) => m.AdminUsersComponent),
          },
          {
            path: 'audit',
            loadComponent: () =>
              import('./pages/admin-audit/admin-audit').then((m) => m.AdminAuditComponent),
          },
          {
            path: 'settings',
            loadComponent: () =>
              import('./pages/admin-settings/admin-settings').then((m) => m.AdminSettingsComponent),
          },
        ],
      },
    ]
  },
  {
    path: '**',
    redirectTo: 'home',
  },
];
