import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';

export const CLAIMS_ROUTES: Routes = [
  {
    path: '',
    title: 'Claims · ClaimSphere',
    loadComponent: () => import('./claim-list/claim-list').then((m) => m.ClaimList),
  },
  {
    path: 'new',
    title: 'New claim · ClaimSphere',
    canActivate: [roleGuard],
    data: { roles: ['CLAIMANT'] },
    loadComponent: () => import('./claim-form/claim-form').then((m) => m.ClaimForm),
  },
  {
    path: ':id',
    title: 'Claim · ClaimSphere',
    loadComponent: () => import('./claim-detail/claim-detail').then((m) => m.ClaimDetail),
  },
];
