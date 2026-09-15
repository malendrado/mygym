import { Role } from './auth.model';

/** Calculado en el servidor a partir de paidAt (ver MemberService.membershipStatus en el backend). */
export type MembershipStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'UNPAID';

export interface Member {
  id: number;
  name: string;
  email: string;
  role: Role;
  gymId: number;
  active: boolean;
  createdAt: string;
  planId: number | null;
  paidAt: string | null;
  membershipStatus: MembershipStatus;
}

export interface CreateMemberRequest {
  name: string;
  email: string;
}

export interface MarkPaidRequest {
  planId: number;
}
