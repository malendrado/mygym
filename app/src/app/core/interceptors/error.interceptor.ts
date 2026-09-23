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
      // 403 en un método que no es GET casi siempre es DEMO_ADMIN chocando contra el bloqueo
      // real de solo-lectura en SecurityConfig (no llega al @RestControllerAdvice, así que
      // err.error no trae detail/title) — mensaje específico en vez del genérico de abajo.
      if (err.status === 403 && req.method !== 'GET') {
        return throwError(() => new Error('Esta es una demo de solo lectura — esta acción no está disponible.'));
      }
      const message =
        err.error?.detail ?? err.error?.title ?? 'Ocurrió un error inesperado. Intenta nuevamente.';
      return throwError(() => new Error(message));
    }),
  );
};
