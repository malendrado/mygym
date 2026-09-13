export type Role = 'SUPER_ADMIN' | 'GYM_ADMIN' | 'MEMBER';

export interface AuthUser {
  userId: number;
  email: string;
  name: string;
  role: Role;
  gymId: number | null;
}

export interface LoginResponse {
  token: string;
  userId: number;
  email: string;
  name: string;
  role: Role;
  gymId: number | null;
  isNewMember: boolean;
}
