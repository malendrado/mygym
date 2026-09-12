import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const message =
        err.error?.detail ?? err.error?.title ?? 'Ocurrió un error inesperado. Intenta nuevamente.';
      return throwError(() => new Error(message));
    }),
  );
