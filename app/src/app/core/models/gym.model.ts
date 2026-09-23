import { Attendee } from './member.model';

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface Gym {
  id: number;
  /** UUID opaco usado en la URL del super-admin — el id de arriba nunca se expone en una ruta. */
  publicId: string;
  name: string;
  slug: string;
  active: boolean;
  maxUsers: number;
  googleLoginEnabled: boolean;
  themeColor: string | null;
  logoSvg: string | null;
  tagline: string | null;
  description: string | null;
  instagramUrl: string | null;
  whatsappNumber: string | null;
  cancellationWindowHours: number;
  /** 'DARK' (acento libre) o 'LIGHT' (limitado a las 4 paletas curadas — ver LIGHT_PALETTES). */
  themeMode: 'DARK' | 'LIGHT';
  bankName: string | null;
  bankAccountType: string | null;
  bankAccountNumber: string | null;
  bankHolderRut: string | null;
  bankHolderName: string | null;
  bankConfirmationEmail: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Branding-only view for the public join page (mygym.cl/j/{slug}) — no auth required to fetch this. */
export interface PublicGym {
  name: string;
  slug: string;
  themeColor: string;
  themeContrast: string;
  logoSvg: string | null;
  googleLoginEnabled: boolean;
  tagline: string | null;
  description: string | null;
  instagramUrl: string | null;
  whatsappNumber: string | null;
  cancellationWindowHours: number;
  themeMode: 'DARK' | 'LIGHT';
}

/** PUT .../theme (gym-admin y super-admin comparten el mismo shape). */
export interface ThemeUpdateRequest {
  themeColor: string;
  themeMode: 'DARK' | 'LIGHT';
}

export interface GymIdentityUpdateRequest {
  tagline: string | null;
  description: string | null;
  instagramUrl: string | null;
  whatsappNumber: string | null;
  cancellationWindowHours: number;
}

/** PUT .../bank-transfer (gym-admin y super-admin comparten el mismo shape). */
export interface BankTransferUpdateRequest {
  bankName: string | null;
  accountType: string | null;
  accountNumber: string | null;
  holderRut: string | null;
  holderName: string | null;
  confirmationEmail: string | null;
}

/** Bancos que operan en Chile — "Otro" abre un campo de texto libre en el formulario. */
export const CHILE_BANKS = [
  'Banco de Chile',
  'Banco Estado',
  'Banco Santander',
  'Banco de Crédito e Inversiones (BCI)',
  'Scotiabank Chile',
  'Banco Itaú Chile',
  'Banco Falabella',
  'Banco Security',
  'Banco BICE',
  'Banco Consorcio',
  'HSBC Bank Chile',
  'Banco Ripley',
  'Banco Internacional',
  'Coopeuch',
  'Otro',
] as const;

export const BANK_ACCOUNT_TYPES = ['Cuenta Corriente', 'Cuenta Vista', 'Cuenta de Ahorro', 'Cuenta RUT'] as const;

/** Vista del socio de los datos bancarios de SU gym (GET /api/me/gym/bank-transfer) — si
 *  `configured` es false, el resto de los campos viene en null y la opción de transferencia
 *  debe ocultarse (el admin todavía no cargó los 4 campos clave). */
export interface BankTransferInfo {
  configured: boolean;
  bankName: string | null;
  accountType: string | null;
  accountNumber: string | null;
  holderRut: string | null;
  holderName: string | null;
  confirmationEmail: string | null;
}

export interface GymPhoto {
  id: number;
  data: string;
  caption: string | null;
}

export interface CreateGymPhotoRequest {
  data: string;
  caption: string | null;
}

export interface GymBlock {
  id: number;
  gymId: number;
  label: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  capacity: number;
  /** Etiqueta libre (ej. "spinning", "yoga") — el frontend le asigna un ícono por palabra clave. */
  category: string | null;
  instructorName: string | null;
  instructorPhoto: string | null;
  active: boolean;
}

const DAY_ORDER: Record<DayOfWeek, number> = {
  MONDAY: 0,
  TUESDAY: 1,
  WEDNESDAY: 2,
  THURSDAY: 3,
  FRIDAY: 4,
  SATURDAY: 5,
  SUNDAY: 6,
};

/** Weekly-schedule order: day of week first (Mon→Sun), then start time within each day. */
export function sortBlocksBySchedule(blocks: GymBlock[]): GymBlock[] {
  return [...blocks].sort((a, b) => {
    const dayDiff = DAY_ORDER[a.dayOfWeek] - DAY_ORDER[b.dayOfWeek];
    return dayDiff !== 0 ? dayDiff : a.startTime.localeCompare(b.startTime);
  });
}

export interface Admin {
  id: number;
  name: string;
  email: string;
  active: boolean;
  /** Foto de perfil de Google — null si nunca se logueó con Google. */
  photoUrl: string | null;
}

export interface CreateGymRequest {
  name: string;
  slug: string;
  maxUsers: number;
  ownerName: string;
  ownerEmail: string;
  themeColor?: string | null;
  logoSvg?: string | null;
}

export interface BrandingSuggestion {
  themeColor: string;
  logoSvg: string;
}

export interface CreateAdminRequest {
  name: string;
  email: string;
}

export interface GymDisconnectRequest {
  confirmGymName: string;
}

/** Resumen permanente de una desvinculación — deliberadamente sin datos personales de socios,
 *  ver GymDisconnectionService en el backend. */
export interface GymDeletionAudit {
  id: number;
  gymName: string;
  gymSlug: string;
  adminEmails: string;
  memberCount: number;
  reservationCount: number;
  paymentCount: number;
  blockCount: number;
  planCount: number;
  photoCount: number;
  executedBy: string;
  executedAt: string;
}

export interface GymConfigUpdateRequest {
  active: boolean;
  maxUsers: number;
  googleLoginEnabled: boolean;
  logoSvg: string | null;
}

export interface CreateGymBlockRequest {
  label: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  capacity: number;
  category: string | null;
  instructorName: string | null;
  instructorPhoto: string | null;
}

export interface UpdateGymBlockRequest extends CreateGymBlockRequest {
  active: boolean;
}

export interface GymPlan {
  id: number;
  gymId: number;
  name: string;
  description: string | null;
  /** Etiqueta libre (ej. "personal", "libre", "estudiante") — solo para mostrar, no una categoría cerrada. */
  category: string | null;
  priceClp: number;
  /** Null = plan libre (reserva ilimitada en los cupos disponibles). */
  monthlyClasses: number | null;
  active: boolean;
}

export interface CreateGymPlanRequest {
  name: string;
  description: string | null;
  category: string | null;
  priceClp: number;
  monthlyClasses: number | null;
}

export interface UpdateGymPlanRequest extends CreateGymPlanRequest {
  active: boolean;
}

/** Vista del socio de un plan activo de su gym — sin gymId ni active (GET /api/me/plans). */
export interface MemberPlan {
  id: number;
  name: string;
  description: string | null;
  category: string | null;
  priceClp: number;
  monthlyClasses: number | null;
}

/** Superficie pública instrumentada con el beacon de visitas — ver GymService.recordVisit. */
export type TrackedPage = 'BROCHURE' | 'LANDING' | 'JOIN';

export interface PageStats {
  total: number;
  last7d: number;
  last30d: number;
}

export interface GymVisitStats {
  /** null en la fila "Otros" — visitas a /j/:slug cuyo slug no coincidió con ningún gimnasio. */
  gymId: number | null;
  gymName: string;
  gymSlug: string | null;
  total: number;
  last30d: number;
}

/** Respuesta de POST /api/me/plans/{id}/checkout — URL de Flow a la que redirigir el navegador. */
export interface CheckoutResponse {
  redirectUrl: string;
}

/** Panel de Visitas del super-admin (GET /api/gyms/analytics/summary). */
export interface AnalyticsSummary {
  brochure: PageStats;
  landing: PageStats;
  joinTotal: PageStats;
  /** Ordenada de mayor a menor por total. */
  byGym: GymVisitStats[];
}

/** Una entrada por cada ocurrencia de bloque con al menos un asistente — respuesta del
 *  endpoint batch de Historial (.../history-attendees?from=&to=), reemplaza el fan-out de
 *  una llamada por cada bloque×día de la semana. */
export interface BlockOccurrenceAttendees {
  gymBlockId: number;
  classDate: string;
  attendees: Attendee[];
}
