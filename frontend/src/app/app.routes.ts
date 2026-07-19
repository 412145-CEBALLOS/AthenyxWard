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
    path: 'account-disabled',
    loadComponent: () => import('./pages/account-disabled/account-disabled').then((m) => m.AccountDisabledComponent),
  },
  {
    path: 'legal/terms',
    loadComponent: () => import('./pages/legal/legal').then((m) => m.LegalComponent),
  },
  {
    path: 'legal/privacy',
    loadComponent: () => import('./pages/legal/legal').then((m) => m.LegalComponent),
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
        path: 'hidden',
        loadComponent: () =>
          import('./pages/hidden-emails/hidden-emails').then((m) => m.HiddenEmailsComponent),
        canActivate: [authGuard],
      },
      {
        path: 'deleted',
        loadComponent: () =>
          import('./pages/deleted-emails/deleted-emails').then((m) => m.DeletedEmailsComponent),
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
            path: 'config',
            loadComponent: () =>
              import('./pages/admin-config/admin-config').then((m) => m.AdminConfigComponent),
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
