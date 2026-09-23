import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'status',
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'member',
    canActivate: [authGuard, roleGuard('MEMBER')],
    loadComponent: () => import('./pages/member/member').then((m) => m.MemberPage),
  },
  // Mismo fix que web.routes.ts — sin comodín, una URL sin match deja la app en blanco.
  {
    path: '**',
    redirectTo: 'login',
  },
];
