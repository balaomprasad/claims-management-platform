import { Routes } from '@angular/router';

export const CLAIMS_ROUTES: Routes = [
  {
    path: '',
    title: 'Claims · ClaimSphere',
    loadComponent: () => import('./claim-list/claim-list').then((m) => m.ClaimList),
  },
  {
    path: ':id',
    title: 'Claim · ClaimSphere',
    loadComponent: () => import('./claim-detail/claim-detail').then((m) => m.ClaimDetail),
  },
];
