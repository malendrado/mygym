import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const webRoutes: Routes = [
  {
    path: '',
    title: 'mygym — Gestiona tu gimnasio sin la planilla de Excel',
    loadComponent: () => import('./pages/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    title: 'Ingresar · mygym',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'j/:slug',
    title: 'Únete · mygym',
    loadComponent: () => import('./pages/join/join').then((m) => m.Join),
  },
  {
    // Convergencia de las 3 variantes de email+contraseña (invitado por un admin, auto-registro,
    // "olvidé mi contraseña") — ver PasswordAuthService en el backend. Pública a propósito: el
    // token en sí es la autenticación.
    path: 'activar/:token',
    title: 'Activar cuenta · mygym',
    loadComponent: () => import('./pages/activate/activate').then((m) => m.Activate),
  },
  {
    // Guía del socio en formato historias — el token firmado del correo de bienvenida es la
    // credencial (ver PublicGuideController). Sin link desde la landing ni indexable.
    path: 'guia/:token',
    title: 'Guía del socio · mygym',
    loadComponent: () => import('./pages/guide/guide').then((m) => m.GuidePage),
  },
  {
    // Públicas, sin link desde la landing ni el brochure todavía — pedido explícito del
    // usuario (2026-09-23): subirlas a una ruta real para poder compartirlas, sin agregarlas
    // a la navegación hasta que decida publicarlas.
    path: 'faq',
    title: 'Preguntas frecuentes · mygym',
    loadComponent: () => import('./pages/legal/faq/faq').then((m) => m.LegalFaq),
  },
  {
    path: 'privacidad',
    title: 'Privacidad · mygym',
    loadComponent: () => import('./pages/legal/privacidad/privacidad').then((m) => m.LegalPrivacidad),
  },
  {
    // Pantalla de TV del gym — pública a propósito, sin login: una tele no puede loguearse con
    // Google cómodo. Se autentica sola con un token de emparejamiento guardado en localStorage
    // (ver TvScreen, tv-screen.ts) — nunca con el JWT de auth normal de la app.
    path: 'tv',
    title: 'Pantalla TV · mygym',
    loadComponent: () => import('./pages/tv-screen/tv-screen').then((m) => m.TvScreenPage),
  },
  {
    path: 'member',
    title: 'Mi cuenta · mygym',
    canActivate: [authGuard, roleGuard('MEMBER')],
    loadComponent: () => import('./pages/member/member').then((m) => m.MemberPage),
  },
  {
    // A donde llega el socio al escanear el QR de asistencia de la TV (ver tv-screen.ts) —
    // requiere estar logueado como MEMBER, igual que /member.
    path: 'checkin/:code',
    title: 'Check-in · mygym',
    canActivate: [authGuard, roleGuard('MEMBER')],
    loadComponent: () => import('./pages/checkin/checkin').then((m) => m.CheckinPage),
  },
  {
    path: 'gym-admin',
    title: 'Panel · mygym',
    canActivate: [authGuard, roleGuard('GYM_ADMIN', 'DEMO_ADMIN', 'PROFESOR')],
    loadComponent: () => import('./pages/gym-admin/gym-admin').then((m) => m.GymAdmin),
  },
  {
    // "Ver como socio" de la demo comercial: es LA MISMA pantalla que ve un socio real
    // (/member), con la misma UI y el mismo flujo — solo cambia de dónde lee los datos y que
    // no puede escribir (ver isDemoPreview en member.ts). Tener una pantalla "parecida" hecha
    // aparte no servía: la demo tiene que mostrar el producto real, no una maqueta.
    path: 'gym-admin/demo-preview',
    title: 'Vista de socio · mygym',
    canActivate: [authGuard, roleGuard('DEMO_ADMIN')],
    data: { demoPreview: true },
    loadComponent: () => import('./pages/member/member').then((m) => m.MemberPage),
  },
  {
    path: 'admin/gyms',
    title: 'Gimnasios · mygym',
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
        path: 'plans',
        title: 'Planes mygym · mygym',
        loadComponent: () => import('./pages/admin/gyms/mygym-plans/mygym-plans').then((m) => m.MygymPlans),
      },
      {
        path: 'admins',
        loadComponent: () => import('./pages/admin/gyms/admins-list/admins-list').then((m) => m.AdminsList),
      },
      {
        path: 'avisos',
        title: 'Avisos push · mygym',
        loadComponent: () => import('./pages/admin/gyms/avisos-push/avisos-push').then((m) => m.AvisosPush),
      },
      {
        path: ':publicId',
        loadComponent: () => import('./pages/admin/gyms/gym-form/gym-form').then((m) => m.GymForm),
      },
    ],
  },
  // Comodín — sin esto, cualquier URL que no matchee ninguna ruta de arriba (ej. una escrita
  // mal a mano) tira NG04002 sin capturar y deja la app entera en blanco (bug real, 2026-09-23).
  // Antes redirigía en silencio a la landing; ahora muestra una página 404 con salidas claras.
  {
    path: '**',
    title: 'Página no encontrada · mygym',
    loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFoundPage),
  },
];
