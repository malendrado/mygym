import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: HttpErrorResponse) => {
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
