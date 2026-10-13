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
  /** Eje independiente de membershipStatus: null (se auto-registró) | 'PENDING' (el admin lo agregó a
   *  mano, todavía no entró con Google) | 'REGISTERED' (el admin lo agregó a mano y ya entró). */
  inviteStatus: InviteStatus;
  /** "Lun 19:00 GAP · Mié 19:00 GAP" — null si no tiene clases fijas. */
  fixedClassesSummary: string | null;
  /** Datos de contacto del socio — null mientras no los complete. El PROFESOR solo recibe el celular. */
  rut: string | null;
  address: string | null;
  /** "YYYY-MM-DD" */
  birthDate: string | null;
  /** Formato internacional ("+56912345678") — de acá sale el link de WhatsApp (ver whatsappUrl). */
  phone: string | null;
  /** Contacto de emergencia (obligatorio): nombre y celular internacional — null mientras no lo complete. */
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  /** false = todavía no completa sus datos: no puede reservar (el servidor lo hace cumplir). */
  profileComplete: boolean;
}

/** Los datos de contacto y el contacto de emergencia juntos — PUT /api/me/profile y los de admin/super-admin. */
export interface MemberProfile {
  rut: string;
  address: string;
  /** "YYYY-MM-DD" */
  birthDate: string;
  phone: string;
  emergencyContactName: string;
  emergencyContactPhone: string;
}

export type InviteStatus = 'PENDING' | 'REGISTERED' | null;

export interface CreateMemberRequest {
  name: string;
  email: string;
}

export interface MarkPaidRequest {
  planId: number;
  /** Banco con el que el socio le transfirió al gym (lista CHILE_BANKS; "Otro" = texto libre) —
   *  queda guardado para informes posteriores (ManualPayment en el backend). */
  bank: string;
  /** Desde cuándo vale el plan ("YYYY-MM-DD", nunca futura ni de hace más de un mes); omitido = ahora. */
  startDate?: string;
  /** Clases que el socio ya usó fuera de la app en ese período (solo planes con cupo). */
  usedClasses?: number;
}

/** Vista previa de activar un plan con esa fecha y esas clases (ver GymService.previewPlanPayment). */
export interface PaymentPreview {
  periodStart: string;
  periodEnd: string;
  monthlyClasses: number | null;
  usedOutside: number;
  bookedInApp: number;
  /** null = plan sin tope de clases. */
  remaining: number | null;
  expiringSoon: boolean;
}

/**
 * Una fila del Excel de importación masiva (ver ImportMembersModal). planId/amountClp/expiresAt van
 * los tres juntos o los tres vacíos — el backend revalida esto igual (MemberService.importRow).
 */
export interface MemberImportRow {
  name: string;
  email: string;
  planId: number | null;
  amountClp: number | null;
  /** ISO 8601 (instant) — el admin lo ve como "Fecha de vencimiento" en el Excel, ver ImportMembersModal. */
  expiresAt: string | null;
  /** Solo aplica si el plan tiene cupo limitado — clases que ya venía usando ese mes. */
  usedSessions: number | null;
  /** Opcionales en el Excel (el socio los completa al entrar si faltan); el servidor los valida igual. */
  rut: string | null;
  address: string | null;
  /** "YYYY-MM-DD" */
  birthDate: string | null;
  phone: string | null;
}

export interface MemberImportRowResult {
  email: string;
  success: boolean;
  error: string | null;
}

/**
 * Vista completa de un socio reservado en una clase — solo para admins.
 * reservationId identifica la reserva puntual (no el socio) — lo necesita el admin para
 * poder cancelarla (ver GymService.cancelReservation/cancelReservationForGym).
 */
export interface Attendee {
  id: number;
  name: string;
  email: string;
  photoUrl: string | null;
  reservationId: number;
  /** Instante en que marcó asistencia; null = sin marcar (si la clase ya terminó, no vino). */
  checkedInAt: string | null;
  checkinMethod: 'QR' | 'MANUAL' | null;
  phone: string | null;
}

/** Vista reducida para otros socios — nunca email ni apellido. */
export interface AttendeeSummary {
  firstName: string;
  photoUrl: string | null;
}

/** Conteos de las calugas de la pestaña Socios — del gym completo, no de la página visible. */
export interface MemberSummary {
  total: number;
  active: number;
  expiringSoon: number;
  expired: number;
  unpaid: number;
  invitedPending: number;
}

/** Parámetros de GET /members: búsqueda y filtros viven en el servidor. */
export interface MemberPageQuery {
  q: string;
  status: MembershipStatus | null;
  invite: Exclude<InviteStatus, null> | null;
  page: number;
  size: number;
}
