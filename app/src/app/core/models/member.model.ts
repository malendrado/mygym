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
  /** Nombre del plan contratado — null si nunca se marcó un pago. */
  planName: string | null;
  /** paidAt + 1 mes calendario — null si nunca se marcó un pago. */
  planEndDate: string | null;
  /** Cupo mensual del plan — null si el plan es libre (ilimitado) o no hay plan. */
  monthlyClasses: number | null;
  /** monthlyClasses menos reservas activas en el período actual — null si el plan es ilimitado o no hay plan. */
  sessionsRemaining: number | null;
  /** Foto de perfil de Google — null si el socio nunca se logueó con Google. */
  photoUrl: string | null;
}

export interface CreateMemberRequest {
  name: string;
  email: string;
}

export interface MarkPaidRequest {
  planId: number;
}
