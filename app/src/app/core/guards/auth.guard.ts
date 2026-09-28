import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/auth.model';

// returnUrl: sin esto, alguien sin sesión que escanea el QR de asistencia de la TV (ver
// tv-screen.ts/checkin.ts) hace login y cae en /member por defecto — perdiendo silenciosamente
// la clase que quería marcar, incluso si el código todavía no venció.
export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  if (authService.currentUser() !== null) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);
    const user = authService.currentUser();
    if (user === null) {
      return router.parseUrl('/login');
    }
    return roles.includes(user.role) || router.parseUrl('/');
  };
}
