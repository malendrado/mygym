import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const webRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'j/:slug',
    loadComponent: () => import('./pages/join/join').then((m) => m.Join),
  },
  {
    path: 'member',
    canActivate: [authGuard, roleGuard('MEMBER')],
    loadComponent: () => import('./pages/member/member').then((m) => m.MemberPage),
  },
  {
    path: 'gym-admin',
    canActivate: [authGuard, roleGuard('GYM_ADMIN', 'DEMO_ADMIN')],
    loadComponent: () => import('./pages/gym-admin/gym-admin').then((m) => m.GymAdmin),
  },
  {
    // "Ver como socio" de la demo comercial: es LA MISMA pantalla que ve un socio real
    // (/member), con la misma UI y el mismo flujo — solo cambia de dónde lee los datos y que
    // no puede escribir (ver isDemoPreview en member.ts). Tener una pantalla "parecida" hecha
    // aparte no servía: la demo tiene que mostrar el producto real, no una maqueta.
    path: 'gym-admin/demo-preview',
    canActivate: [authGuard, roleGuard('DEMO_ADMIN')],
    data: { demoPreview: true },
    loadComponent: () => import('./pages/member/member').then((m) => m.MemberPage),
  },
  {
    path: 'admin/gyms',
    canActivate: [authGuard, roleGuard('SUPER_ADMIN')],
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/admin/gyms/gym-list/gym-list').then((m) => m.GymList),
      },
      {
        path: 'new',
        loadComponent: () => import('./pages/admin/gyms/gym-form/gym-form').then((m) => m.GymForm),
      },
      {
        path: 'deletion-audits',
        loadComponent: () =>
          import('./pages/admin/gyms/deletion-audits/deletion-audits').then((m) => m.DeletionAudits),
      },
      {
        path: ':publicId',
        loadComponent: () => import('./pages/admin/gyms/gym-form/gym-form').then((m) => m.GymForm),
      },
    ],
  },
];
