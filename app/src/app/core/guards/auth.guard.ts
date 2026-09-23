import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/auth.model';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  return authService.currentUser() !== null || router.parseUrl('/login');
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
