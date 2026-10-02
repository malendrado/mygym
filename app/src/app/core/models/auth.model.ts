export type Role = 'SUPER_ADMIN' | 'GYM_ADMIN' | 'MEMBER' | 'DEMO_ADMIN' | 'PROFESOR';

// Compartido por login.ts (fresh login) y roleGuard (sesión ya persistida que abre directo en
// una ruta que no le corresponde — la PWA instalada siempre abre en /member, start_url fijo del
// manifest — bug real confirmado en Android: un SUPER_ADMIN con token todavía válido caía en la
// landing pública sin ningún indicio de que seguía logueado, se sentía como "no persiste la
// sesión" aunque el token sí estaba guardado bien).
export function homeRouteForRole(role: Role): string {
  switch (role) {
    case 'SUPER_ADMIN':
      return '/admin/gyms';
    case 'GYM_ADMIN':
    case 'DEMO_ADMIN':
    case 'PROFESOR':
      return '/gym-admin';
    case 'MEMBER':
      return '/member';
  }
}

export interface AuthUser {
  userId: number;
  email: string;
  name: string;
  role: Role;
  gymId: number | null;
  /** Foto de perfil de Google — null si nunca se logueó con Google. */
  photoUrl: string | null;
}

export interface LoginResponse {
  token: string;
  userId: number;
  email: string;
  name: string;
  role: Role;
  gymId: number | null;
  isNewMember: boolean;
  photoUrl: string | null;
}
