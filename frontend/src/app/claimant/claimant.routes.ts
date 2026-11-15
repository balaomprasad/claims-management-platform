import { Routes } from '@angular/router';

export const CLAIMANT_ROUTES: Routes = [
  {
    path: '',
    title: 'My claims · ClaimSphere',
    loadComponent: () => import('./claimant-dashboard/claimant-dashboard').then((m) => m.ClaimantDashboard),
  },
  {
    path: 'report',
    title: 'Report an incident · ClaimSphere',
    loadComponent: () => import('./report-incident/report-incident').then((m) => m.ReportIncident),
  },
];
