import { Attendee, Member } from './member.model';

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

/** Vista liviana de /admin/gyms (lista del super-admin, GET /api/gyms) — solo los campos que
 *  esa pantalla pinta (tarjeta + 3 stats agregados). El detalle de un gym puntual (gym-form)
 *  sigue usando `Gym` completo vía GymService.get(id). */
export interface GymSummary {
  id: number;
  publicId: string;
  name: string;
  slug: string;
  active: boolean;
  maxUsers: number;
  themeColor: string | null;
  logoSvg: string | null;
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
  /** true si el gym tiene su propia cuenta Flow configurada — si es false, /member oculta
   *  "Pagar con Flow" y solo ofrece transferencia bancaria. */
  flowConfigured: boolean;
}

/** GET /api/me/dashboard — combina los 5 datos "de encabezado" de /member en una sola request
 *  (antes eran 5 llamadas sueltas en paralelo; ver auditoría de performance 2026-10-09). */
export interface MemberDashboard {
  gym: PublicGym;
  membership: Member;
  plans: MemberPlan[];
  bankTransfer: BankTransferInfo;
  closureNotice: GymClosureNotice | null;
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

/** Vista del super-admin de la cuenta "Pago Online" (Flow.cl) de un gym
 *  (GET/PUT .../flow-account) — apiKeyMasked/secretKeyMasked nunca traen el valor real, solo
 *  para mostrar "ya hay una cargada" (ver hasApiKey/hasSecretKey). Reenviar apiKey/secretKey
 *  vacíos en el PUT significa "no cambiar lo ya guardado". */
export interface FlowAccount {
  configured: boolean;
  companyRut: string | null;
  companyName: string | null;
  businessActivity: string | null;
  companyAddress: string | null;
  vatCondition: string | null;
  legalRepName: string | null;
  legalRepRut: string | null;
  legalRepPhone: string | null;
  contactEmail: string | null;
  contactName: string | null;
  contactPhone: string | null;
  hasApiKey: boolean;
  hasSecretKey: boolean;
  apiKeyMasked: string | null;
  secretKeyMasked: string | null;
}

/** Lo que puede editar el propio GYM_ADMIN de su cuenta Pago Online — todo lo de
 *  FlowAccountUpdateRequest MENOS apiKey/secretKey (esas dos las carga solo el super-admin). */
export interface FlowAccountDetailsUpdateRequest {
  companyRut: string | null;
  companyName: string | null;
  businessActivity: string | null;
  companyAddress: string | null;
  vatCondition: string | null;
  legalRepName: string | null;
  legalRepRut: string | null;
  legalRepPhone: string | null;
  contactEmail: string | null;
  contactName: string | null;
  contactPhone: string | null;
}

export interface FlowAccountUpdateRequest {
  companyRut: string | null;
  companyName: string | null;
  businessActivity: string | null;
  companyAddress: string | null;
  vatCondition: string | null;
  legalRepName: string | null;
  legalRepRut: string | null;
  legalRepPhone: string | null;
  contactEmail: string | null;
  contactName: string | null;
  contactPhone: string | null;
  /** Vacío/null = no cambiar el valor ya guardado. */
  apiKey: string | null;
  secretKey: string | null;
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
  /** Cuándo se creó este acceso — para un demo admin, también "cuándo se invitó". El vencimiento
   *  a 7 días de la demo se calcula acá en el frontend a partir de este campo. */
  createdAt: string;
  /** Null si nunca entró con Google. */
  lastLoginAt: string | null;
  /** Paso más alto alcanzado en cada tour guiado de la demo — null si todavía no lo abrió.
   *  Solo aplica a un DEMO_ADMIN, siempre null para un admin real. */
  adminTourStep: number | null;
  memberTourStep: number | null;
}

/** Fila de la vista "Administradores" del super-admin (GET /api/gyms/admins) — a diferencia de
 *  Admin (scoped a un solo gym), cada fila trae su propio gymName/gymSlug porque mezcla
 *  gym-admins de gimnasios distintos. */
export interface GymAdminListItem {
  id: number;
  name: string;
  email: string;
  active: boolean;
  photoUrl: string | null;
  createdAt: string;
  lastLoginAt: string | null;
  gymId: number | null;
  gymName: string | null;
  gymSlug: string | null;
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

/** Resultado de un bloque procesado por POST .../blocks/batch — un lote entero en un solo
 *  request (reemplaza el fan-out de un POST por bloque que hacía la creación de series antes).
 *  Cada bloque es independiente: un horario inválido en uno no tumba el resto del lote, por eso
 *  la respuesta es un array de resultados, no un solo GymBlock[]. */
export interface BlockCreateResult {
  success: boolean;
  block: GymBlock | null;
  error: string | null;
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

/** Cierre de emergencia de gimnasio (ver GymClosureService) — rango de fechas completo
 *  (wholeDays=true) o clases puntuales (wholeDays=false, blockIds con los bloques elegidos). */
export interface GymClosureCreateRequest {
  startDate: string;
  endDate: string;
  wholeDays: boolean;
  blockIds: number[];
  reason: string;
}

/** Editar un cierre ya creado — a propósito solo endDate+reason (ver GymClosureService.update):
 *  el alcance (wholeDays/blockIds) y startDate quedan fijos desde la creación. */
export interface GymClosureUpdateRequest {
  endDate: string;
  reason: string;
}

export interface GymClosurePreview {
  cancelledReservationsCount: number;
  affectedMembersCount: number;
}

export interface GymClosure {
  id: number;
  startDate: string;
  endDate: string;
  wholeDays: boolean;
  blockIds: number[];
  reason: string;
  createdByEmail: string;
  createdByRole: string;
  createdAt: string;
  liftedAt: string | null;
  active: boolean;
  /** Mientras no se haya levantado y su rango no haya terminado del todo — habilita "Editar". */
  editable: boolean;
  cancelledReservationsCount: number;
  affectedMembersCount: number;
  emailsSent: number;
  emailsFailed: number;
}

/** Banner de /member cuando el gym del socio tiene un cierre vigente o próximo. */
export interface GymClosureNotice {
  startDate: string;
  endDate: string;
  reason: string;
  wholeDays: boolean;
}

/** Conteos de las calugas de /admin/gyms — de todos los gimnasios, no de la página visible. */
export interface GymStats {
  total: number;
  active: number;
  totalCapacity: number;
  branded: number;
}

/** Respuesta del buscador de reservas futuras: `truncated` = true cuando el texto coincidió con más
 *  socios que el tope del servidor y solo se devolvieron las reservas de los primeros. */
export interface ReservationSearchResponse {
  results: BlockOccurrenceAttendees[];
  truncated: boolean;
}
