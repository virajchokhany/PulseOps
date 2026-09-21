import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard',
    title: 'PulseOps · Dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.Dashboard)
  },
  {
    path: 'incidents',
    title: 'PulseOps · Incidents',
    loadComponent: () => import('./pages/incidents/incidents').then(m => m.Incidents)
  },
  {
    path: 'incidents/:key',
    title: 'PulseOps · Incident',
    loadComponent: () => import('./pages/incident-detail/incident-detail').then(m => m.IncidentDetailPage)
  },
  {
    path: 'services',
    title: 'PulseOps · Services',
    loadComponent: () => import('./pages/services/services').then(m => m.Services)
  },
  { path: '**', redirectTo: 'dashboard' }
];
