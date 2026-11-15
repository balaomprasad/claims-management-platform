import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · ClaimSphere',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Register · ClaimSphere',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'claims' },
      {
        path: 'claims',
        loadChildren: () => import('./features/claims/claims.routes').then((m) => m.CLAIMS_ROUTES),
      },
      {
        path: 'policies',
        title: 'Policies · ClaimSphere',
        loadComponent: () =>
          import('./features/policies/policy-list/policy-list').then((m) => m.PolicyList),
      },
      {
        path: 'notifications',
        title: 'Notifications · ClaimSphere',
        loadComponent: () =>
          import('./features/notifications/notification-list/notification-list').then(
            (m) => m.NotificationList,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
