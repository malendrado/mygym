import { Role } from './auth.model';

export interface Member {
  id: number;
  name: string;
  email: string;
  role: Role;
  gymId: number;
  active: boolean;
  createdAt: string;
}

export interface CreateMemberRequest {
  name: string;
  email: string;
}
