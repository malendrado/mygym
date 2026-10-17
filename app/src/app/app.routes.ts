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
    title: 'Estado · mygym',
    loadComponent: () => import('./pages/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    title: 'Ingresar · mygym',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'member',
    title: 'Mi cuenta · mygym',
    canActivate: [authGuard, roleGuard('MEMBER')],
    loadComponent: () => import('./pages/member/member').then((m) => m.MemberPage),
  },
  // Mismo fix que web.routes.ts — sin comodín, una URL sin match deja la app en blanco.
  {
    path: '**',
    title: 'Página no encontrada · mygym',
    loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFoundPage),
  },
];
