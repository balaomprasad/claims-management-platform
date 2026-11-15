import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { roleHomeGuard } from './core/guards/role-home.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · ClaimSphere',
    loadComponent: () => import('./auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Register · ClaimSphere',
    loadComponent: () => import('./auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', canActivate: [roleHomeGuard], children: [] },
      {
        path: 'claimant',
        canActivate: [roleGuard],
        data: { roles: ['CLAIMANT'] },
        loadChildren: () => import('./claimant/claimant.routes').then((m) => m.CLAIMANT_ROUTES),
      },
      {
        path: 'officer',
        title: 'Officer workspace · ClaimSphere',
        canActivate: [roleGuard],
        data: { roles: ['CLAIMS_OFFICER'] },
        loadComponent: () => import('./officer/officer-dashboard/officer-dashboard').then((m) => m.OfficerDashboard),
      },
      {
        path: 'manager',
        title: 'Manager dashboard · ClaimSphere',
        canActivate: [roleGuard],
        data: { roles: ['MANAGER'] },
        loadComponent: () => import('./manager/manager-dashboard/manager-dashboard').then((m) => m.ManagerDashboard),
      },
      {
        path: 'claims',
        loadChildren: () => import('./claims/claims.routes').then((m) => m.CLAIMS_ROUTES),
      },
      {
        path: 'policies',
        title: 'Policies · ClaimSphere',
        loadComponent: () => import('./policies/policy-list/policy-list').then((m) => m.PolicyList),
      },
      {
        path: 'notifications',
        title: 'Notifications · ClaimSphere',
        loadComponent: () =>
          import('./notifications/notification-list/notification-list').then((m) => m.NotificationList),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
