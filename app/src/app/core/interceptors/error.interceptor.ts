import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      // 410: el acceso a la demo (Role.DEMO_ADMIN) venció — ver DemoAccessService/
      // DemoAccessExpiryFilter en el backend. Pasa tanto en el primer login después de las 48h
      // como a mitad de una sesión ya abierta (el JWT en sí dura 30 días). Nunca dejamos que
      // esto siga como un error más: se limpia la sesión acá mismo y se manda al formulario de
      // contacto — swallow total (EMPTY) para que ningún subscriber downstream compita
      // mostrando su propio mensaje de error mientras ya estamos redirigiendo.
      if (err.status === 410) {
        void authService.logout().finally(() => {
          router.navigate(['/'], { queryParams: { contacto: 'demo-vencida' } });
        });
        return EMPTY;
      }
      // 403 del login mismo: la cuenta de Google es real, pero no hay ningún AppUser con ese
      // email (ni socio, ni admin) — UnauthorizedGoogleLoginException en el backend. Antes caía
      // en la regla genérica de abajo y mostraba "esta es una demo de solo lectura", un mensaje
      // sin sentido para alguien que nunca tuvo nada que ver con mygym (bug real encontrado en la
      // práctica). Mismo patrón que el 410 de arriba: se manda al formulario de contacto en vez
      // de dejarlo en una pantalla de login sin salida.
      if (err.status === 403 && req.url.endsWith('/api/auth/google')) {
        router.navigate(['/'], { queryParams: { contacto: 'cuenta-no-registrada' } });
        return EMPTY;
      }
      // 403 en un método que no es GET casi siempre es DEMO_ADMIN chocando contra el bloqueo
      // real de solo-lectura en SecurityConfig (no llega al @RestControllerAdvice, así que
      // err.error no trae detail/title) — mensaje específico en vez del genérico de abajo.
      if (err.status === 403 && req.method !== 'GET') {
        return throwError(() => new Error('Esta es una demo de solo lectura — esta acción no está disponible.'));
      }
      const message =
        err.error?.detail ?? err.error?.title ?? 'Ocurrió un error inesperado. Intenta nuevamente.';
      // Se preserva el status HTTP original en el Error que sí le llega al subscriber — sin esto,
      // cualquier código que revisara err.status más abajo (ej. tv-screen.ts para saber si una
      // pantalla se desvinculó, gym-admin.ts para un código de emparejamiento vencido, gym-form.ts
      // para el 409/502 de desvincular un gimnasio) nunca podía distinguir el caso específico y
      // caía siempre en su mensaje genérico — bug real encontrado en la práctica, no solo teórico.
      const wrapped = Object.assign(new Error(message), { status: err.status });
      return throwError(() => wrapped);
    }),
  );
};
