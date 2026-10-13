import { DayOfWeek } from './gym.model';

/** Contadores de un período; la tasa (vinieron / reservaron) la calcula el frontend. */
export interface AttendanceTotals {
  booked: number;
  attended: number;
  missed: number;
}

export interface AttendanceClassStat {
  blockId: number;
  label: string;
  weekday: DayOfWeek;
  startTime: string;
  booked: number;
  attended: number;
}

export interface AttendanceWeekdayStat {
  weekday: DayOfWeek;
  booked: number;
  attended: number;
}

/** Asistencia de un socio en un período (solo clases ya terminadas). */
export interface MemberAttendanceRow {
  memberId: number;
  name: string;
  photoUrl: string | null;
  phone: string | null;
  booked: number;
  attended: number;
  missed: number;
  lastMissedDate: string | null;
}

export interface AttendanceSummary {
  totals: AttendanceTotals;
  previousTotals: AttendanceTotals;
  byClass: AttendanceClassStat[];
  byWeekday: AttendanceWeekdayStat[];
  topAbsentees: MemberAttendanceRow[];
  /** Los que más asistieron (mínimo 3 reservas), más asistencias primero. */
  topAttendees: MemberAttendanceRow[];
}

export interface AttendanceMembersPage {
  items: MemberAttendanceRow[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
}

/** Qué lista de socios abrir al tocar un número del resumen. */
export interface AttendanceMembersFilter {
  /** Título de la vista ("Camila no vino a GAP · lunes 19:00"). */
  title: string;
  blockId?: number;
  weekday?: DayOfWeek;
  onlyMissed?: boolean;
}

export interface MemberAttendanceDetailRow {
  reservationId: number;
  classDate: string;
  blockLabel: string;
  startTime: string;
  ended: boolean;
  attended: boolean;
  checkinMethod: 'QR' | 'MANUAL' | null;
  checkedInAt: string | null;
  /** Dentro de la ventana de marcado a mano (hasta 7 días después de la clase). */
  editable: boolean;
}

export interface MemberAttendanceDetail {
  member: MemberAttendanceRow;
  rows: MemberAttendanceDetailRow[];
}

/** Resultado de marcar o desmarcar a mano. */
export interface AttendanceMark {
  reservationId: number;
  checkedInAt: string | null;
  checkinMethod: 'QR' | 'MANUAL' | null;
}
