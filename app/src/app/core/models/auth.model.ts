export type Role = 'SUPER_ADMIN' | 'GYM_ADMIN' | 'MEMBER' | 'DEMO_ADMIN';

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
