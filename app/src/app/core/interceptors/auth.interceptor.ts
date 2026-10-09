import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';

// Nunca adjuntar el JWT a una request que no vaya a nuestra propia API — hoy no hay ninguna
// llamada a un dominio externo con HttpClient, pero sin este chequeo, el día que se agregue una
// (analítica, un CDN, etc.) el token se filtraría a ese tercero sin que nadie lo note (auditoría
// de seguridad 2026-10-09).
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthService).token;
  if (!token || !req.url.startsWith(environment.apiUrl)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
