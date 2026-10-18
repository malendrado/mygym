import { Component, DestroyRef, computed, effect, inject, signal, untracked } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInput,
  IonItem,
  IonLabel,
  IonSegment,
  IonSegmentButton,
  IonSkeletonText,
  IonModal,
  IonSpinner,
  IonTitle,
  IonToolbar,
  AlertController,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { InstallBanner } from './install-banner/install-banner';
import { GymPause } from '../../core/components/gym-pause/gym-pause';
import { GymPauseService } from '../../core/services/gym-pause.service';
import { PushBell } from '../../core/components/push-bell/push-bell';
import { WorkoutHistoryFilter } from '../../core/components/workout-history-filter/workout-history-filter';
import { PushPrompt } from '../../core/components/push-prompt/push-prompt';
import { WelcomeMission } from './welcome-mission/welcome-mission';
import { QrScanner } from '../../core/components/qr-scanner/qr-scanner';
import { extractCheckinCode } from '../../core/utils/qr-code';
import { TourOverlay } from '../../core/components/tour-overlay/tour-overlay';
import { SectionTour, TourService } from '../../core/services/tour.service';
import { TourSeenService } from '../../core/services/tour-seen.service';
import { toLogoImgSrc } from '../../core/utils/logo-src';
import {
  addCircleOutline,
  alertCircleOutline,
  checkmarkCircleOutline,
  banOutline,
  barbellOutline,
  calendarOutline,
  checkmarkDoneOutline,
  checkmarkOutline,
  chevronBackOutline,
  cloudOfflineOutline,
  idCardOutline,
  radioButtonOnOutline,
  refreshOutline,
  timeOutline,
  chevronForwardOutline,
  closeCircleOutline,
  createOutline,
  eyeOutline,
  flashOutline,
  informationCircleOutline,
  lockClosedOutline,
  logOutOutline,
  personCircleOutline,
  logoInstagram,
  logoWhatsapp,
  playOutline,
  qrCodeOutline,
  cameraOutline,
  sparklesOutline,
  trendingUpOutline,
} from 'ionicons/icons';
import { AuthService } from '../../core/services/auth.service';
import { DemoPreviewService } from '../../core/services/demo-preview.service';
import { GymService } from '../../core/services/gym.service';
import { ReservationService } from '../../core/services/reservation.service';
import { WorkoutService } from '../../core/services/workout.service';
import { GymBlockOccurrence, Reservation } from '../../core/models/reservation.model';
import { WaitlistEntry } from '../../core/models/waitlist.model';
import { BankTransferInfo, GymClosureNotice, GymPhoto, MemberPlan, PublicGym } from '../../core/models/gym.model';
import { AttendeeSummary, Member } from '../../core/models/member.model';
import { ExerciseLogEntry, MemberWorkoutLog, PendingWorkout, SaveWorkoutLogRequest } from '../../core/models/workout.model';
import {
  NO_WORKOUT_FILTER,
  WorkoutLogFilter,
  filterWorkoutLogs,
  highlightParts,
  isFiltering,
} from '../../core/utils/workout-history-filter';
import { deriveSurfaceTint, ensureMinContrastColor, syncThemeOverrides } from '../../core/utils/gym-theme';
import { registerClassCategoryIcons, resolveClassCategoryIcon } from '../../core/utils/class-category';
import { MemberProfileModal } from '../../core/components/member-profile-modal/member-profile-modal';
import { transferNoticeMessage } from '../../core/utils/whatsapp-messages';
import { MemberProfile } from '../../core/models/member.model';
import { MemberProfileService } from '../../core/services/member-profile.service';
import { errorText } from '../../core/utils/error-text';
import { santiagoNow } from '../../core/utils/attendance';
import { formatIsoDate } from '../../core/utils/date-format';
import { isOutsideCurrentWeek, nextWeekMonday } from '../../core/utils/fixed-week';
import { VersionTag } from '../../core/components/version-tag/version-tag';
import { ErrorState } from '../../core/components/error-state/error-state';

registerClassCategoryIcons();

const FALLBACK_HERO_PHOTO = 'https://images.unsplash.com/photo-1637430308606-86576d8fef3c?q=75&w=1600&h=900&fit=crop&auto=format';

// Comisión de Flow por link de pago (2,89% + IVA), traspasada al socio como recargo aparte
// sobre el precio del plan — mismo cálculo que FlowPaymentService.GATEWAY_COMMISSION_RATE en
// el backend, acá solo para mostrar el desglose antes de pagar (el backend es la fuente de
// verdad del monto realmente cobrado).
const GATEWAY_COMMISSION_RATE = 0.0289 * 1.19;

addIcons({
  'checkmark-circle-outline': checkmarkCircleOutline,
  'eye-outline': eyeOutline,
  'flash-outline': flashOutline,
  'calendar-outline': calendarOutline,
  'trending-up-outline': trendingUpOutline,
  'sparkles-outline': sparklesOutline,
  'log-out-outline': logOutOutline,
  'person-circle-outline': personCircleOutline,
  'logo-instagram': logoInstagram,
  'logo-whatsapp': logoWhatsapp,
  'checkmark-done-outline': checkmarkDoneOutline,
  'lock-closed-outline': lockClosedOutline,
  'alert-circle-outline': alertCircleOutline,
  'checkmark-outline': checkmarkOutline,
  'chevron-back-outline': chevronBackOutline,
  'chevron-forward-outline': chevronForwardOutline,
  'barbell-outline': barbellOutline,
  'add-circle-outline': addCircleOutline,
  'close-circle-outline': closeCircleOutline,
  'information-circle-outline': informationCircleOutline,
  'create-outline': createOutline,
  'qr-code-outline': qrCodeOutline,
  'camera-outline': cameraOutline,
  'ban-outline': banOutline,
  'play-outline': playOutline,
  'time-outline': timeOutline,
  'radio-button-on-outline': radioButtonOnOutline,
  'cloud-offline-outline': cloudOfflineOutline,
  'refresh-outline': refreshOutline,
  'id-card-outline': idCardOutline,
});

type Status = 'idle' | 'loading' | 'error';

// ---------------------------------------------------------------------------
// Sección Membresía. Los planes ya son reales (GET /api/me/plans, configurados
// por el admin del gym en gym-admin → Planes). Lo que sigue siendo MOCK es
// el estado de la membresía en sí (pending/active/past_due) y selectPlan(),
// que simula el checkout — todavía no existe el backend de pagos (Parte B
// del plan, Flow.cl). Cuando exista, reemplazar el signal `membership` por
// datos reales y selectPlan() por el checkout real de Flow.cl.
// ---------------------------------------------------------------------------
type MembershipStatus = 'none' | 'pending' | 'active' | 'past_due';

interface MembershipPlan {
  id: number;
  name: string;
  priceClp: number;
  monthlyClasses: number | null; // null = ilimitado
  highlight?: boolean;
  /** true si el destacado viene de la elección del visitante, no de la heurística. */
  chosen?: boolean;
}

interface Membership {
  status: MembershipStatus;
  plan: MembershipPlan | null;
  classesUsed: number;
  // Fecha (YYYY-MM-DD, hora de Chile) del último "pago" — la membresía dura
  // exactamente 1 mes desde acá (pagó el 3 de enero → vence el 3 de
  // febrero, haya usado o no todas sus clases). null = nunca pagó.
  paidAt: string | null;
  // Renovación anticipada (todas YYYY-MM-DD, hora de Chile; opcionales: la copia sin conexión las omite).
  /** Fin del período en curso (si falta, paidAt + 1 mes). */
  currentPeriodEnd?: string | null;
  /** Vencimiento FINAL cuando ya pagó por adelantado; null = sin cola. */
  queuedPeriodEnd?: string | null;
  /** Clases que le quedan en el período ya pagado por adelantado; null = plan libre o sin cola. */
  queuedSessionsRemaining?: number | null;
  /** Plan vigente y sin cola: puede renovar por adelantado. */
  canRenewEarly?: boolean;
  /** Primer día del período nuevo (= vencimiento actual). */
  earlyRenewalStartsOn?: string | null;
  /** Nombre del plan ya pagado que parte al terminar el actual; null = sin cambio programado. */
  pendingPlanName?: string | null;
}

/** El plan de precio intermedio se marca "Recomendado" — heurística visual, no una señal del admin.
 *  Si el visitante tocó un plan en la página de alta (/j/:slug), ese plan pasa a ser el destacado
 *  ("Tu elección") en vez del intermedio. */
function withHighlight(plans: MemberPlan[], preferredPlanId: number | null = null): MembershipPlan[] {
  const sorted = [...plans].sort((a, b) => a.priceClp - b.priceClp);
  const preferred = preferredPlanId !== null && sorted.some((plan) => plan.id === preferredPlanId);
  const highlightIndex = preferred
    ? sorted.findIndex((plan) => plan.id === preferredPlanId)
    : sorted.length >= 3
      ? Math.floor(sorted.length / 2)
      : -1;
  return sorted.map((plan, index) => ({
    id: plan.id,
    name: plan.name,
    priceClp: plan.priceClp,
    monthlyClasses: plan.monthlyClasses,
    highlight: index === highlightIndex,
    chosen: preferred && index === highlightIndex,
  }));
}

/** Plan que el visitante marcó en la página de alta de ESTE gimnasio (ver join.ts), si lo hay. */
function readPreferredPlanId(slug: string | undefined): number | null {
  try {
    const raw = localStorage.getItem('mygym.preferredPlan');
    const saved = raw ? (JSON.parse(raw) as { slug?: string; planId?: number }) : null;
    return saved && saved.slug === slug && typeof saved.planId === 'number' ? saved.planId : null;
  } catch {
    return null;
  }
}

interface Benefit {
  icon: string;
  title: string;
  body: string;
}

const BENEFITS: Benefit[] = [
  {
    icon: 'flash-outline',
    title: 'Reserva en 10 segundos',
    body: 'Mira los cupos libres al instante y confirma la clase sin escribir por WhatsApp.',
  },
  {
    icon: 'calendar-outline',
    title: 'Sin filas ni sorpresas',
    body: 'Cada horario muestra cuántos cupos quedan, antes de salir de casa.',
  },
  {
    icon: 'trending-up-outline',
    title: 'Tu constancia, a la vista',
    body: 'Cada clase reservada es un paso más cerca de la meta — el historial queda aquí.',
  },
];

const MOTIVATIONAL_QUOTES = [
  'Nadie se arrepiente de la clase a la que fue. Reserva la tuya.',
  'La constancia gana. Un cupo reservado es un compromiso con el resultado.',
  'El mejor momento para entrenar fue ayer. El segundo mejor es hoy.',
];

const WEEKDAY_LABELS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

interface MonthDayCell {
  iso: string;
  dayNumber: number;
  isToday: boolean;
  isPast: boolean;
  hasClasses: boolean;
}

function pad2(n: number): string {
  return n.toString().padStart(2, '0');
}

/** Saca filas de 7 celdas completamente vacías del INICIO de la grilla. Pasan cuando el período
 *  pagado (`from`) empieza a mitad de mes: el cálculo arma el mes calendario completo y deja los
 *  días previos a `from` como `null` (fuera del período), lo que puede generar varias semanas
 *  enteras en blanco antes del primer día real — nada que mostrar, solo espacio muerto. */
function trimLeadingEmptyRows<T>(cells: (T | null)[]): (T | null)[] {
  let start = 0;
  while (start + 7 <= cells.length && cells.slice(start, start + 7).every((cell) => cell === null)) {
    start += 7;
  }
  return cells.slice(start);
}

/** Instant ISO del backend ("2026-09-16T17:35:38Z") → "YYYY-MM-DD" en hora de Chile, mismo formato que `todayIso`. */
function toChileIsoDate(instantIso: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date(instantIso));
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "YYYY-MM-DD" + N días (puede ser negativo) → "YYYY-MM-DD" — puro cálculo de calendario con
 *  y/m/d explícitos, mismo criterio que el resto de las fechas de este archivo (nunca
 *  `new Date(isoString)`, que interpreta UTC y puede correr un día cerca de medianoche). */
function addDaysIso(dateIso: string, days: number): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  const date = new Date(y, m - 1, d + days);
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/** "YYYY-MM-DD" → "7 de octubre", mismo formato que ya usa `renewsOnLabel`. */
function dayMonthLabel(dateIso: string): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  return new Intl.DateTimeFormat('es-CL', { day: 'numeric', month: 'long' }).format(new Date(y, m - 1, d));
}

function monthYearLabel(year: number, month: number): string {
  return capitalize(
    new Intl.DateTimeFormat('es-CL', { month: 'long', year: 'numeric' }).format(new Date(year, month - 1, 1)),
  );
}

/** "YYYY-MM-DD" → "mié 23 sep" — construido a partir de y/m/d explícitos, nunca `new Date(isoString)` (ver el bug de huso horario ya encontrado con nextOccurrenceDate). */
function formatShortDate(dateIso: string): string {
  const [y, m, d] = dateIso.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  return capitalize(new Intl.DateTimeFormat('es-CL', { weekday: 'short', day: 'numeric', month: 'short' }).format(date));
}

/** "06:00:00" → "06:00" — pedido explícito: la fecha/hora de cada clase debe notarse más, sin los segundos redundantes. */
function shortTime(time: string): string {
  return time.slice(0, 5);
}

// Un solo Intl.DateTimeFormat para toda la página: canCancel() lo invoca desde el template por
// cada tarjeta de clase en cada cambio de detección, y construir uno nuevo cada vez es lo más
// caro de esa función.
const GYM_ZONE_FORMAT = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Santiago',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** "YYYY-MM-DDTHH:mm:ss" en hora de Chile — comparable lexicográficamente contra `classDate + 'T' + endTime`. */
function nowInGymZoneIso(): string {
  const parts = GYM_ZONE_FORMAT.formatToParts(new Date());
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}:${get('second')}`;
}

const OCCURRENCES_LOADING_MESSAGES = [
  'Buscando la clase perfecta para ti...',
  'Revisando los cupos libres del mes...',
  'Armando tu calendario de entrenamiento...',
];

// Copia mínima para mostrar algo sin conexión: solo plan, vencimiento, clases usadas y reservas próximas
// (clase, fecha, hora). Nada de RUT, dirección ni tokens.
interface MemberSnapshot {
  savedAt: number;
  membership: {
    status: 'active' | 'past_due';
    planName: string;
    monthlyClasses: number | null;
    classesUsed: number;
    paidAt: string;
  };
  reservations: { id: number; gymBlockId: number; blockLabel: string; classDate: string; startTime: string; endTime: string }[];
  // Clases de los próximos 7 días, solo lectura: sin fotos ni asistentes, con un tope de tamaño.
  classes?: SnapshotClass[];
}

interface SnapshotClass {
  gymBlockId: number;
  label: string;
  classDate: string;
  dayOfWeek: GymBlockOccurrence['dayOfWeek'];
  startTime: string;
  endTime: string;
  capacity: number;
  category: string | null;
  instructorName: string | null;
  taken: number;
  closed: boolean;
  myReservationId: number | null;
  waitlisted: boolean;
  myReservationFixed: boolean;
}

const SNAPSHOT_KEY_PREFIX = 'mygym.snapshot.v1.';
// Días de clases que se guardan para verlas sin conexión, y tope de filas (evita llenar el almacenamiento).
const SNAPSHOT_CLASS_DAYS = 7;
const SNAPSHOT_CLASS_LIMIT = 120;

const SECTION_VALUES = ['reservar', 'reservas', 'rutina'] as const;
type MemberSection = (typeof SECTION_VALUES)[number];
const OFFLINE_WRITE_MESSAGE = 'Sin conexión: no se pudo hacer esto. Inténtalo cuando vuelvas a tener internet.';

/** "HH:mm" en hora de Chile de un instante en milisegundos. */
function clockLabel(ms: number): string {
  return new Intl.DateTimeFormat('es-CL', { timeZone: 'America/Santiago', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(
    new Date(ms),
  );
}

@Component({
  selector: 'app-member',
  imports: [
    WorkoutHistoryFilter,
    WelcomeMission,
    VersionTag,
    ErrorState,
    MemberProfileModal,
    IonModal,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonIcon,
    IonBadge,
    IonSpinner,
    IonSegment,
    IonSegmentButton,
    IonSkeletonText,
    IonLabel,
    IonInput,
    IonItem,
    InstallBanner,
    GymPause,
    PushBell,
    PushPrompt,
    QrScanner,
    NgTemplateOutlet,
    TourOverlay,
  ],
  templateUrl: './member.html',
  styleUrl: './member.scss',
})
export class MemberPage {
  private readonly authService = inject(AuthService);
  private readonly gymService = inject(GymService);
  private readonly gymPause = inject(GymPauseService);
  private readonly reservationService = inject(ReservationService);
  private readonly workoutService = inject(WorkoutService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastController = inject(ToastController);
  private readonly memberProfileService = inject(MemberProfileService);
  private readonly alertController = inject(AlertController);
  private readonly destroyRef = inject(DestroyRef);
  private readonly demoPreviewService = inject(DemoPreviewService);
  private readonly tourService = inject(TourService);
  protected readonly tourSeen = inject(TourSeenService);

  // Modo "ver como socio" de la demo comercial (ruta /gym-admin/demo-preview, ver web.routes.ts):
  // ESTA misma pantalla, con los mismos datos y la misma UI que ve un socio real, pero leyendo
  // del socio de muestra del gym demo (/api/gym-admin/demo-preview/**) y sin poder escribir nada.
  // Se reusa la página real a propósito: una pantalla "parecida" hecha aparte no sirve para
  // mostrarle a un prospecto cómo se ve de verdad el producto.
  protected readonly isDemoPreview = this.route.snapshot.data['demoPreview'] === true;
  protected readonly demoMemberName = signal<string | null>(null);

  protected readonly section = signal<'reservar' | 'reservas' | 'rutina'>('reservar');

  protected readonly status = signal<Status>('idle');
  protected readonly gym = signal<PublicGym | null>(null);
  protected readonly gymLoaded = signal(false);
  protected readonly logoSrc = computed(() => toLogoImgSrc(this.gym()?.logoSvg));
  protected readonly occurrences = signal<GymBlockOccurrence[]>([]);
  protected readonly myReservations = signal<Reservation[]>([]);
  // Misión de bienvenida: se alimenta del dashboard y se actualiza al reservar / marcar asistencia.
  protected readonly missionReady = signal(false);
  protected readonly missionBooked = signal(false);
  protected readonly missionCheckedIn = signal(false);
  protected readonly bookingId = signal<number | null>(null);
  protected readonly cancelingId = signal<number | null>(null);
  protected readonly waitlistingId = signal<number | null>(null);
  // Mis listas de espera (GET /api/me/waitlist). Si el endpoint falla o aún no existe, queda vacío y la sección no se muestra.
  protected readonly myWaitlist = signal<WaitlistEntry[]>([]);
  // Esqueleto único de la pestaña "Reserva tu clase" (respeta reduced-motion).
  protected readonly skeletonAnimated = !(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false);
  protected readonly skeletonCells = [0, 1, 2, 3, 4, 5, 6];

  // "Ahora" con hora incluida, en la zona horaria del gym (America/Santiago), no en la del
  // navegador del socio — un `new Date().toISOString()` corta a UTC y puede quedar un día
  // desalineado cerca de medianoche en Chile. Es un signal que se refresca solo (ver startClock):
  // cada 30 s y al volver a primer plano, así la pantalla abierta no queda con la hora congelada.
  private readonly now = signal(nowInGymZoneIso());
  // "Hoy" (YYYY-MM-DD) y "ahora" (YYYY-MM-DDTHH:mm:ss) derivan del mismo signal; se comparan
  // lexicográficamente contra `classDate` + `endTime` para separar reservas pasadas de próximas.
  private readonly todayIso = computed(() => this.now().slice(0, 10));
  private readonly nowChileIso = computed(() => this.now());
  private readonly monthRange = computed(() => this.currentMonthRange());
  protected readonly weekdayLabels = WEEKDAY_LABELS;
  protected readonly selectedDate = signal(this.todayIso());

  protected readonly benefits = BENEFITS;
  // Cargar el mes completo puede tardar unos segundos en gyms con muchos
  // bloques — mejor un mensaje con onda que una grilla vacía y quieta.
  protected readonly occurrencesLoadingMessage =
    OCCURRENCES_LOADING_MESSAGES[Math.floor(Math.random() * OCCURRENCES_LOADING_MESSAGES.length)];
  private readonly fallbackQuote = MOTIVATIONAL_QUOTES[Math.floor(Math.random() * MOTIVATIONAL_QUOTES.length)];
  // El gym puede escribir su propia frase (Gym.tagline) — si no lo hizo, cae a una genérica motivacional.
  protected readonly quote = computed(() => this.gym()?.tagline || this.fallbackQuote);

  // Cierre de emergencia (ver GymClosureService) — banner con el motivo + clases marcadas
  // "Cerrado" (occurrence.closed, ver member.html). Nunca aplica en demo preview.
  protected readonly closureNotice = signal<GymClosureNotice | null>(null);

  /** El motivo lo escribe el admin: si ya trae punto final, el template no debe duplicarlo. */
  protected withoutTrailingDot(text: string): string {
    return text.trim().replace(/[.\s]+$/, '');
  }
  // El backend manda startDate/endDate en ISO — formateados igual que renewsOnLabel() para no
  // mostrar fechas crudas tipo "2026-10-07" en el banner (detectado en auditoría UX 2026-10-04).
  protected readonly closureDatesLabel = computed(() => {
    const notice = this.closureNotice();
    if (!notice) {
      return '';
    }
    const start = dayMonthLabel(notice.startDate);
    return notice.startDate === notice.endDate ? `el ${start}` : `del ${start} al ${dayMonthLabel(notice.endDate)}`;
  });

  // Transferencia: confirmación solo de esta sesión (no se guarda nada en el servidor) tras tocar "Ya transferí".
  protected readonly transferNotified = signal(false);
  protected readonly transferWhatsappUrl = computed(() => {
    const link = this.whatsappLink();
    if (!link) {
      return null;
    }
    const plan = this.paymentChoicePlan();
    const message = transferNoticeMessage(this.firstName(), this.gym()?.name ?? 'el gimnasio', plan?.name ?? null);
    return `${link}?text=${encodeURIComponent(message)}`;
  });

  protected notifyTransfer(): void {
    this.transferNotified.set(true);
    this.showToast('Listo, avisa al gimnasio por WhatsApp.');
  }

  // "Mis datos": el socio edita sus datos de contacto cuando quiera (el modal obligatorio es otro).
  protected readonly profileEditOpen = signal(false);

  protected openProfileEdit(): void {
    this.profileError.set(null);
    if (this.profileIncomplete()) {
      // Faltan datos: se abre el formulario completo (todo obligatorio), no el de "cambiar solo lo que quiero".
      this.pendingBooking.set(null);
      this.profileModalOpen.set(true);
      return;
    }
    this.profileEditOpen.set(true);
  }

  protected readonly photos = signal<GymPhoto[]>([]);
  // Tira de fotos del gimnasio en el header — antes el header no tenía nada
  // más que el nombre, pedido explícito de hacerlo más notorio sin
  // recargarlo. Duplicada para que el scroll infinito (CSS puro, ver
  // member.scss) cierre el loop sin salto visible; con 1 sola foto igual
  // se duplica para que la animación tenga sentido (dos copias moviéndose).
  protected readonly photoStrip = computed(() => {
    const list = this.photos();
    return list.length ? [...list, ...list] : [];
  });
  protected readonly heroPhotoUrl = computed(() => this.photos()[0]?.data ?? FALLBACK_HERO_PHOTO);
  protected readonly galleryPhotos = computed(() => this.photos().slice(1));

  protected readonly instagramUrl = computed(() => this.gym()?.instagramUrl || null);
  protected readonly whatsappLink = computed(() => {
    const number = this.gym()?.whatsappNumber;
    return number ? `https://wa.me/${number.replace(/[^\d]/g, '')}` : null;
  });

  protected readonly plans = signal<MembershipPlan[]>([]);
  protected readonly plansLoaded = signal(false);
  // Plan elegido, esperando a que el socio decida CÓMO pagar (Flow o
  // transferencia) — null mientras se muestra la grilla de planes.
  protected readonly paymentChoicePlan = signal<MembershipPlan | null>(null);
  protected readonly bankTransfer = signal<BankTransferInfo | null>(null);
  // false hasta que el admin del gym cargue los 4 campos clave — sin eso, la
  // opción de transferencia queda oculta en vez de mostrar un desglose vacío.
  protected readonly bankTransferConfigured = computed(() => this.bankTransfer()?.configured ?? false);
  protected readonly membership = signal<Membership>({
    status: 'none',
    plan: null,
    classesUsed: 0,
    paidAt: null,
  });
  // Bug real reportado por el usuario: al reabrir la app ya logueada (PWA o pestaña con sesión
  // guardada), la pantalla "parpadeaba" mostrando primero "Elige tu plan" (el estado por defecto
  // de `membership` de arriba, antes de que responda GET /api/me/membership) y recién después
  // el plan real ya activo — el socio alcanzaba a leer "elige tu plan" durante una fracción de
  // segundo aunque ya tuviera uno. Gate explícito: mientras esto siga en false, el template
  // muestra un spinner neutro en vez de asumir "sin plan" (ver member.html, sección .membership).
  protected readonly membershipLoaded = signal(false);
  // La carga de la membresía falló y aún no hay un dato real: en vez de inventar "sin plan" (y
  // mostrarle los precios a un socio que sí tiene uno), la plantilla muestra un error con "Reintentar".
  protected readonly membershipError = signal(false);
  // true una vez que llegó al menos una vez la membresía real; un refresco posterior que falle no la pisa.
  private membershipResolved = false;
  // true al volver de Flow.cl (?checkout=return) mientras esperamos que el
  // webhook confirme — la redirección del navegador siempre llega antes que
  // la confirmación server-to-server, así que no alcanza con un solo
  // refresco inmediato.
  protected readonly checkoutPending = signal(false);
  protected readonly quotaTotal = computed(() => this.membership().plan?.monthlyClasses ?? null);
  protected readonly classesRemaining = computed(() => {
    const total = this.quotaTotal();
    return total === null ? null : Math.max(total - this.membership().classesUsed, 0);
  });
  protected readonly quotaExhausted = computed(() => this.classesRemaining() === 0);
  // Sin esto, un socio sin plan (o con el pago atrasado) veía el mismo botón
  // "Reservar" que alguien con plan activo — tocarlo solo llevaba a un error
  // del backend sin explicación. `quotaExhausted()` no cubre este caso: sin
  // plan, `quotaTotal()` es null, así que da `false` (no "agotado").
  protected readonly canBook = computed(() => this.membership().status === 'active');

  // Socios con clases fijas: si el gym encendió "solo la semana en curso", reservar y cancelar se limita a lunes-domingo
  // de esta semana (el servidor lo hace cumplir igual: FixedWeekRule). Acá solo se evita el error deshabilitando botones.
  private readonly hasFixedClasses = signal(false);
  protected readonly fixedWeekOnly = computed(() => !!this.gym()?.fixedMembersCurrentWeekOnly && this.hasFixedClasses());

  protected fixedWeekLocked(classDate: string): boolean {
    return this.fixedWeekOnly() && isOutsideCurrentWeek(classDate, santiagoNow().date);
  }

  protected fixedWeekOpensOn(): string {
    return formatIsoDate(nextWeekMonday(santiagoNow().date));
  }
  // "Sin cupo" antes se mostraba también cuando la clase seguía con lugar libre pero ya
  // estaba fuera del límite de reserva del gym (bookingWindowMinutes) — el
  // socio leía "no queda lugar" cuando el problema real era otro (la clase está por
  // empezar). `!occurrence.bookable` cubre ambos casos; esto distingue cuál es cuál sin
  // tocar el backend (capacity/taken ya venían en la respuesta).
  protected isCapacityFull(occurrence: GymBlockOccurrence): boolean {
    return occurrence.taken >= occurrence.capacity;
  }
  // Un punto por clase del plan — los primeros `classesUsed` salen marcados.
  // Reemplaza la barra horizontal (probada antes): con pocas clases al mes
  // (8-12, lo típico) se lee más directo como tarjeta de sellos que como
  // porcentaje de una barra.
  protected readonly quotaDots = computed<boolean[]>(() => {
    const total = this.quotaTotal();
    if (total === null) {
      return [];
    }
    const used = this.membership().classesUsed;
    return Array.from({ length: total }, (_, i) => i < used);
  });

  // Vencimiento del período pagado: exactamente 1 mes desde `paidAt`, sin
  // importar cuántas clases haya usado — las que no reservó dentro de ese
  // mes se pierden, no se acumulan al período siguiente.
  // Con renovación anticipada, `paidAt` es el pago real (puede caer a mitad del período), así que el
  // fin del período en curso lo manda el servidor (currentPeriodEnd); paidAt + 1 mes es solo el respaldo.
  protected readonly membershipPeriodEnd = computed(() => {
    const m = this.membership();
    if (m.currentPeriodEnd) {
      return m.currentPeriodEnd;
    }
    const paidAt = m.paidAt;
    if (!paidAt) {
      return null;
    }
    const [y, mo, d] = paidAt.split('-').map(Number);
    const end = new Date(y, mo - 1, d);
    end.setMonth(end.getMonth() + 1);
    return `${end.getFullYear()}-${pad2(end.getMonth() + 1)}-${pad2(end.getDate())}`;
  });
  // Vencimiento FINAL: con un período ya pagado por adelantado es el de la cola; si no, el del período en curso.
  protected readonly membershipFinalEnd = computed(() => this.membership().queuedPeriodEnd ?? this.membershipPeriodEnd());
  protected readonly hasQueuedPeriod = computed(() => !!this.membership().queuedPeriodEnd);
  private daysUntil(end: string | null): number | null {
    if (!end) {
      return null;
    }
    const [ey, em, ed] = end.split('-').map(Number);
    const [ty, tm, td] = this.todayIso().split('-').map(Number);
    const msPerDay = 24 * 60 * 60 * 1000;
    return Math.round((Date.UTC(ey, em - 1, ed) - Date.UTC(ty, tm - 1, td)) / msPerDay);
  }
  // Días hasta el vencimiento final (lo que decide "por vencer" y "vencido").
  protected readonly daysRemaining = computed(() => this.daysUntil(this.membershipFinalEnd()));
  // Días que le quedan del período en curso: son los que conserva si renueva el mismo plan por adelantado.
  protected readonly currentPeriodDaysRemaining = computed(() => this.daysUntil(this.membershipPeriodEnd()));
  protected readonly membershipExpired = computed(() => {
    const days = this.daysRemaining();
    return days !== null && days <= 0;
  });
  // Debajo de este umbral se avisa con texto explícito además del color —
  // "no transmitir información solo con color" (auditoría UX de la skill).
  // Con un período ya pagado por adelantado no corresponde alertar "por vencer" (aunque el vencimiento final esté cerca).
  protected readonly expirySoon = computed(() => {
    const days = this.daysRemaining();
    return days !== null && days > 0 && days <= 3 && !this.hasQueuedPeriod();
  });
  protected readonly expiryLabel = computed(() => {
    const days = this.daysRemaining();
    if (days === null) {
      return '';
    }
    if (days <= 0) {
      return 'Venció hoy';
    }
    if (days === 1) {
      return 'Vence mañana';
    }
    return `Vence el ${this.renewsOnLabel()} (en ${days} días)`;
  });
  protected readonly renewsOnLabel = computed(() => {
    const end = this.membershipFinalEnd();
    if (!end) {
      return '';
    }
    const [y, m, d] = end.split('-').map(Number);
    return new Intl.DateTimeFormat('es-CL', { day: 'numeric', month: 'long' }).format(new Date(y, m - 1, d));
  });

  // Renovación por adelantado: el socio con plan vigente despliega las tarjetas de plan dentro de su tarjeta de membresía.
  protected readonly renewingEarly = signal(false);
  protected readonly earlyRenewalAvailable = computed(() => {
    const m = this.membership();
    return m.status === 'active' && m.canRenewEarly === true && !m.queuedPeriodEnd && !this.expiredOrNoPlan();
  });
  private readonly expiredOrNoPlan = computed(() => this.membershipExpired() || !this.membership().plan);
  protected readonly earlyStartLabel = computed(() => {
    const start = this.membership().earlyRenewalStartsOn ?? this.membershipPeriodEnd();
    return start ? dayMonthLabel(start) : '';
  });
  protected readonly queuedEndLabel = computed(() => {
    const end = this.membership().queuedPeriodEnd;
    return end ? dayMonthLabel(end) : '';
  });
  // Mostrar las tarjetas de plan como renovación por adelantado (plan vigente) y no como alta/vencido.
  protected readonly earlyMode = computed(() => this.earlyRenewalAvailable() && this.renewingEarly());
  protected isCurrentPlan(plan: MembershipPlan): boolean {
    return plan.id === this.membership().plan?.id;
  }
  protected toggleEarlyRenewal(): void {
    this.cancelPaymentChoice();
    this.renewingEarly.update((open) => !open);
  }
  // 'none' (nunca eligió plan) y 'past_due' (venció, sin renovar) bloquean
  // por igual el calendario — mismo candado borroso para los dos casos.
  // checkoutPending también bloquea: recién volviendo de Flow, loadMembership()
  // puede traer un estado 'active' viejo (de un pago anterior, ya sea de este socio o
  // de datos de prueba) mientras el webhook del pago nuevo todavía no confirma — sin
  // esto, el socio veía el calendario completo (reservas de otros, cupos reales) por
  // debajo del cartel "Confirmando tu pago...", bug real reportado probando el flujo.
  // 'pending' también bloquea — es el estado optimista que selectPlan() setea al
  // toque de "Elegir plan", ANTES de siquiera terminar de armar la URL de Flow (no
  // depende de checkoutPending, que solo se activa al volver de Flow) — sin esto, el
  // calendario se veía sin blur durante esa ventana, bug real reportado probando el flujo.
  protected readonly bookingLocked = computed(() => {
    const status = this.membership().status;
    return this.checkoutPending() || status === 'none' || status === 'past_due' || status === 'pending';
  });

  // En la demo el saludo es el del socio de muestra, no el del prospecto logueado (que es un
  // DEMO_ADMIN, no un socio) — si no, diría "Hola, <nombre del prospecto>" sobre datos ajenos.
  protected readonly firstName = computed(() => {
    const name = this.isDemoPreview ? this.demoMemberName() : this.authService.currentUser()?.name;
    return name?.split(' ')[0] ?? 'socio';
  });
  // Only derive a surface tint from a real gym color — falling back to the lime
  // *accent* here (like the header does) would treat lime's hue as a background
  // color source and paint an off-brand olive surface. No gym color yet means
  // no override: the template's --member-surface binding stays unset and CSS
  // falls through to the plain --brand-ink default instead.
  protected readonly themeSurface = computed(() => {
    const color = this.gym()?.themeColor;
    return color ? deriveSurfaceTint(color, this.gym()?.themeMode) : null;
  });
  // El acento libre a veces no llega a 4.5:1 como texto plano (ej. el índigo
  // real de Fortis, ~4.07:1) — solo se usa donde el acento pinta TEXTO
  // (eyebrow del hero, "Tu membresía", "Ilimitado"), nunca fondos sólidos.
  protected readonly accentTextSafe = computed(() => {
    const color = this.gym()?.themeColor;
    const surface = this.themeSurface();
    return color && surface ? ensureMinContrastColor(color, surface.card) : null;
  });

  // El calendario reservable se ata al período PAGADO (paidAt → vencimiento), no al mes
  // calendario — pedido explícito del usuario tras notar que el 30 de septiembre no se podía
  // reservar una clase para el 1 de octubre, un límite técnico (mes calendario fijo) sin
  // relación con la regla de negocio real (se puede reservar mientras el plan esté activo).
  // Solo aplica con plan ACTIVO y paidAt real; sin plan activo (none/pending/past_due) se
  // mantiene el comportamiento viejo (mes calendario actual) sin tocarlo — decisión explícita.
  protected readonly bookableRange = computed<{ from: string; to: string } | null>(() => {
    const m = this.membership();
    if (m.status !== 'active' || !m.paidAt) {
      return null;
    }
    // Con un período ya pagado por adelantado se puede reservar hasta el vencimiento FINAL. El servidor
    // acepta rangos de hasta 62 días (listOccurrences), así que el rango nunca pasa de 61 días desde `from`.
    const end = this.membershipFinalEnd();
    if (!end) {
      return null;
    }
    const cap = addDaysIso(m.paidAt, 61);
    return { from: m.paidAt, to: end < cap ? end : cap };
  });

  // Grilla de días a mostrar — rectangular (7 columnas, Lun a Dom), con celdas `null` para
  // completar filas. Dos modos:
  // 1) Sin período pagado conocido: el mes calendario actual (comportamiento de siempre).
  // 2) Con período pagado: el rango [from, to) completo, que casi siempre cruza un fin de mes
  //    (paidAt no suele ser el día 1) — se arma concatenando los meses calendario que toca el
  //    rango, uno atrás del otro (los días son contiguos, la alineación semanal sigue sola, sin
  //    necesidad de realinear nada entre un mes y el siguiente). Un día real que cae FUERA de
  //    [from, to) (antes de pagar, o ya vencido) se deja como celda `null` — mismo mecanismo que
  //    ya usaban los días fuera del mes calendario, cero estado nuevo de "deshabilitado".
  protected readonly monthGrid = computed<(MonthDayCell | null)[]>(() => {
    const range = this.bookableRange();
    const occurrenceDates = new Set(this.occurrences().map((o) => o.classDate));
    const toCell = (iso: string, day: number): MonthDayCell => ({
      iso,
      dayNumber: day,
      isToday: iso === this.todayIso(),
      isPast: iso < this.todayIso(),
      hasClasses: occurrenceDates.has(iso),
    });

    if (!range) {
      const { year, month, daysInMonth } = this.monthRange();
      const cells: (MonthDayCell | null)[] = Array(this.dayOfWeekMonFirst(year, month, 1)).fill(null);
      for (let day = 1; day <= daysInMonth; day++) {
        cells.push(toCell(`${year}-${pad2(month)}-${pad2(day)}`, day));
      }
      while (cells.length % 7 !== 0) {
        cells.push(null);
      }
      return trimLeadingEmptyRows(cells);
    }

    const { from, to } = range;
    const [fy, fm] = from.split('-').map(Number);
    const [ly, lm] = addDaysIso(to, -1).split('-').map(Number);
    const cells: (MonthDayCell | null)[] = [];
    let y = fy;
    let m = fm;
    while (y < ly || (y === ly && m <= lm)) {
      const daysInMonth = new Date(y, m, 0).getDate();
      if (cells.length === 0) {
        cells.push(...Array(this.dayOfWeekMonFirst(y, m, 1)).fill(null));
      }
      for (let day = 1; day <= daysInMonth; day++) {
        const iso = `${y}-${pad2(m)}-${pad2(day)}`;
        cells.push(iso < from || iso >= to ? null : toCell(iso, day));
      }
      m++;
      if (m > 12) {
        m = 1;
        y++;
      }
    }
    while (cells.length % 7 !== 0) {
      cells.push(null);
    }
    return trimLeadingEmptyRows(cells);
  });

  // La grilla completa (hasta 2 meses parciales) empuja demasiado abajo la lista de clases del
  // día — por defecto se ve solo la semana de la fecha seleccionada; "Ver todo" expande al resto.
  protected readonly monthExpanded = signal(false);
  protected readonly visibleGrid = computed(() => {
    const grid = this.monthGrid();
    if (this.monthExpanded()) {
      return grid;
    }
    const index = grid.findIndex((cell) => cell?.iso === this.selectedDate());
    const rowStart = index === -1 ? 0 : Math.floor(index / 7) * 7;
    return grid.slice(rowStart, rowStart + 7);
  });

  // Reemplaza el título fijo de antes (un solo mes calendario) — ahora puede abarcar 1 o 2
  // meses según lo que entre en la porción visible de la grilla (semana suelta o todo el rango).
  protected readonly monthLabel = computed(() => {
    const seen = new Set<string>();
    const labels: string[] = [];
    for (const cell of this.visibleGrid()) {
      if (!cell) {
        continue;
      }
      const [y, m] = cell.iso.split('-').map(Number);
      const key = `${y}-${m}`;
      if (!seen.has(key)) {
        seen.add(key);
        labels.push(monthYearLabel(y, m));
      }
    }
    return labels.join(' – ');
  });

  // Flechas para pasar de semana sin tener que tocar un día específico —
  // pedido explícito del usuario. Acotadas al mes calendario en curso (mismo
  // límite que ya regía `monthGrid`, no navegan a meses futuros/pasados):
  // se deshabilitan solas cuando la semana actual es la primera/última fila
  // real de esa grilla.
  private readonly selectedWeekRowStart = computed(() => {
    const grid = this.monthGrid();
    const index = grid.findIndex((cell) => cell?.iso === this.selectedDate());
    return index === -1 ? 0 : Math.floor(index / 7) * 7;
  });
  protected readonly canGoPreviousWeek = computed(() => this.selectedWeekRowStart() > 0);
  protected readonly canGoNextWeek = computed(() => this.selectedWeekRowStart() + 7 < this.monthGrid().length);

  protected previousWeek(): void {
    this.shiftWeek(-1);
  }

  protected nextWeek(): void {
    this.shiftWeek(1);
  }

  private shiftWeek(direction: -1 | 1): void {
    const grid = this.monthGrid();
    const currentIndex = grid.findIndex((cell) => cell?.iso === this.selectedDate());
    const column = currentIndex === -1 ? 0 : currentIndex % 7;
    const targetRowStart = this.selectedWeekRowStart() + direction * 7;
    if (targetRowStart < 0 || targetRowStart >= grid.length) {
      return;
    }
    const targetRow = grid.slice(targetRowStart, targetRowStart + 7);
    const target = targetRow[column] ?? targetRow.find((cell) => cell !== null);
    if (target) {
      this.selectedDate.set(target.iso);
    }
  }

  protected readonly dayOccurrences = computed(() =>
    this.occurrences().filter((o) => o.classDate === this.selectedDate()),
  );

  // Pedido explícito del usuario: en el día de HOY, las clases que ya terminaron quedaban
  // arriba de la lista (orden cronológico) empujando hacia abajo la que está en curso o las que
  // vienen más tarde ese mismo día — se obliga a scrollear para ver lo que realmente importa en
  // el momento. Solo aplica a HOY (otro día del pasado no tiene "la que está en curso" que
  // proteger; un día futuro no tiene clases pasadas que colapsar). Colapsado por defecto,
  // `occurrence.past` ya lo calcula el backend — no hace falta duplicar esa lógica acá.
  protected readonly isViewingToday = computed(() => this.selectedDate() === this.todayIso());
  protected readonly pastOccurrencesCollapsed = signal(true);
  protected readonly collapsedPastCount = computed(() =>
    this.isViewingToday() ? this.dayOccurrences().filter((o) => o.past).length : 0,
  );
  protected readonly visibleDayOccurrences = computed(() => {
    const all = this.dayOccurrences();
    if (!this.isViewingToday() || !this.pastOccurrencesCollapsed()) {
      return all;
    }
    return all.filter((o) => !o.past);
  });

  protected togglePastOccurrences(): void {
    this.pastOccurrencesCollapsed.update((collapsed) => !collapsed);
  }

  // Quiénes más reservaron una clase — pedido explícito del usuario. Vista
  // acordeón (una clase expandida a la vez) para no inundar la lista de
  // clases del día; se pide al backend recién al expandir, nunca por
  // adelantado para todas las clases visibles. Nunca se guarda/expone el
  // email de otro socio acá — el propio endpoint (`/api/me/.../attendees`)
  // ya devuelve solo nombre de pila + foto.
  protected readonly expandedAttendeesKey = signal<string | null>(null);
  protected readonly attendeesByOccurrence = signal<Record<string, AttendeeSummary[]>>({});
  protected readonly loadingAttendeesKey = signal<string | null>(null);

  protected occurrenceKey(occurrence: GymBlockOccurrence): string {
    return `${occurrence.gymBlockId}_${occurrence.classDate}`;
  }

  protected attendeesFor(occurrence: GymBlockOccurrence): AttendeeSummary[] {
    return this.attendeesByOccurrence()[this.occurrenceKey(occurrence)] ?? [];
  }

  protected toggleAttendees(occurrence: GymBlockOccurrence): void {
    const key = this.occurrenceKey(occurrence);
    if (this.expandedAttendeesKey() === key) {
      this.expandedAttendeesKey.set(null);
      return;
    }
    this.expandedAttendeesKey.set(key);
    if (key in this.attendeesByOccurrence()) {
      return;
    }
    this.loadingAttendeesKey.set(key);
    (this.isDemoPreview
      ? this.demoPreviewService.getBlockAttendees(occurrence.gymBlockId, occurrence.classDate)
      : this.gymService.getMyBlockAttendees(occurrence.gymBlockId, occurrence.classDate)
    ).subscribe({
      next: (attendees) => {
        this.attendeesByOccurrence.update((map) => ({ ...map, [key]: attendees }));
        this.loadingAttendeesKey.set(null);
      },
      error: () => this.loadingAttendeesKey.set(null),
    });
  }

  // "Mis reservas" agrupado en Próximas/Pasadas — antes era una sola lista
  // larga sin distinción, poco útil apenas se acumula historial (reportado
  // por el usuario). Pasadas se muestran de más reciente a más antigua.
  protected readonly upcomingReservations = computed(() =>
    this.myReservations().filter((r) => !this.isReservationPast(r)),
  );
  // Tira "Próxima: hoy 19:00 · Funcional" arriba de la pestaña Reservar: la reserva en curso o la más cercana.
  // Solo con plan activo (sin plan o vencido, la tarjeta de membresía ya ocupa ese lugar).
  protected readonly nextStrip = computed<Reservation | null>(() => {
    if (this.membership().status !== 'active' || !this.membershipLoaded() || this.membershipError() || this.checkoutPending()) {
      return null;
    }
    const sorted = [...this.upcomingReservations()].sort((a, b) =>
      `${a.classDate}T${a.startTime}`.localeCompare(`${b.classDate}T${b.startTime}`),
    );
    return sorted[0] ?? null;
  });

  // Un solo esqueleto mientras llegan la membresía y las clases (antes: un spinner por bloque y la página saltaba).
  protected readonly reservarLoading = computed(
    () => (!this.membershipLoaded() || this.status() === 'loading') && !this.checkoutPending(),
  );

  /** "hoy", "mañana" o "mié 23 sep". */
  protected relativeDayLabel(classDate: string): string {
    const today = this.todayIso();
    if (classDate === today) {
      return 'hoy';
    }
    return classDate === addDaysIso(today, 1) ? 'mañana' : formatShortDate(classDate).toLowerCase();
  }

  // Pasadas = las de HOY que ya terminaron (vienen en myReservations, que es de hoy en adelante)
  // + las de días anteriores, que el backend entrega paginadas (loadPastReservations) — el
  // historial crece sin límite, así que nunca se pide completo. Los dos grupos no se solapan
  // (hoy vs. antes de hoy), y hoy es siempre más reciente que cualquier página.
  protected readonly pastReservations = computed(() => [
    ...this.myReservations()
      .filter((r) => this.isReservationPast(r))
      .slice()
      .reverse(),
    ...this.pastPageItems(),
  ]);
  private readonly pastPageItems = signal<Reservation[]>([]);
  protected readonly pastHasNext = signal(false);
  protected readonly pastLoadingMore = signal(false);
  protected readonly pastLoadError = signal(false);
  protected readonly pastAnnouncement = signal('');
  private pastNextPage = 0;
  private static readonly PAST_PAGE_SIZE = 10;

  protected readonly selectedDateLabel = computed(() => {
    const [y, m, d] = this.selectedDate().split('-').map(Number);
    const formatted = new Intl.DateTimeFormat('es-CL', { weekday: 'long', day: 'numeric', month: 'long' }).format(
      new Date(y, m - 1, d),
    );
    return capitalize(formatted);
  });

  // Apenas se cumple el mes desde el pago, bloquea (mismo candado que "sin
  // plan") — el email de aviso de vencimiento ya no lo dispara el cliente
  // (antes simulateMyPlanExpiry); con pago real, ese aviso quedaría mejor
  // como un job del backend que sepa que de verdad pasó un mes, pendiente.
  // `expiryNotified` sigue evitando repetir esta transición de estado en
  // cada re-render mientras el effect sigue vivo.
  private expiryNotified = false;
  private readonly notifyExpiry = effect(() => {
    if (!this.membershipExpired() || this.expiryNotified) {
      return;
    }
    const current = this.membership();
    if (current.status !== 'active') {
      return;
    }
    this.expiryNotified = true;
    this.membership.update((m) => ({ ...m, status: 'past_due' }));
    this.startBookingDemo();
  });

  constructor() {
    // La sección activa vive en ?s= (reservar | reservas | rutina): se lee al abrir y cada vez que cambia la URL.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => this.readSectionFromUrl(params.get('s')));
    this.startNetworkWatch();
    // Aviso al abrir /member: plan por vencer (≤3 días), plan vencido o clases agotadas. El email
    // llega aparte (MembershipReminderJob / ReservationService.book); esto es el mismo mensaje
    // dentro de la app, para quien no revisa el correo.
    effect(() => {
      if (this.membershipLoaded() && this.reservationsLoaded() && !this.membershipAlertChecked) {
        this.membershipAlertChecked = true;
        // Fuera del ciclo reactivo: crear el alert toca el DOM de Ionic.
        setTimeout(() => void this.showMembershipAlert());
      }
    });
    // Otro dispositivo creó, editó o levantó un cierre con esta pantalla abierta: recarga aviso, clases y reservas
    // (un cierre cancela reservas) sin esperar a que el socio toque algo. Ver GymPauseService.closuresChanged.
    effect(() => {
      if (this.gymPause.closuresChanged() === 0) {
        return;
      }
      untracked(() => {
        this.loadClosureNotice();
        this.loadOccurrences();
        this.loadMyReservations();
      });
    });
    // Alguien reservó o canceló con esta pantalla abierta: recarga los cupos (X/Y) y las reservas, para no intentar reservar
    // un cupo que ya se llenó ni perderse uno que se liberó. Ver GymPauseService.reservationsChanged.
    effect(() => {
      if (this.gymPause.reservationsChanged() === 0) {
        return;
      }
      untracked(() => {
        this.loadOccurrences();
        this.loadMyReservations();
      });
    });
    this.loadDashboard();
    this.loadPhotos();
    this.loadOccurrences();
    this.loadMyReservations();
    this.loadTodayRoutine();
    this.loadPastReservations(true);
    this.startBookingDemo();
    this.startClock();

    // Modo claro necesita más que --member-bg/--member-card (bindeadas inline abajo) —
    // también los tokens base globales (--brand-ink, --ion-color-danger, el step-ramp de
    // Ionic) que hoy asumen "siempre oscuro" en styles.scss. Ver gym-theme.ts:syncThemeOverrides.
    effect(() => {
      const currentGym = this.gym();
      syncThemeOverrides(currentGym?.themeMode, currentGym?.themeColor);
    });
    this.destroyRef.onDestroy(() => syncThemeOverrides('DARK', null));

    // Volvimos de Flow.cl con el navegador — el webhook que confirma de
    // verdad puede tardar unos segundos más que ese redirect, así que
    // reintentamos el refresco de membresía en vez de confiar en el único
    // loadMembership() del arranque de arriba.
    if (this.route.snapshot.queryParamMap.get('checkout') === 'return') {
      this.checkoutPending.set(true);
      this.router.navigate([], { queryParams: {}, replaceUrl: true });
      let attempts = 0;
      // Capturado en una variable de instancia (y limpiado en destroy) — antes, si el socio
      // navegaba fuera de /member dentro de esta ventana de ~17s, el callback seguía corriendo
      // contra un componente ya destruido (auditoría de performance 2026-10-09).
      const poll = () => {
        attempts += 1;
        this.gymService.getMyMembership().subscribe({
          next: (member) => {
            if (member.planId && member.paidAt && member.membershipStatus !== 'EXPIRED') {
              this.checkoutPending.set(false);
              this.loadMembership();
              this.showToast('¡Pago confirmado! Ya puedes reservar tus clases.');
            } else if (attempts < 5) {
              this.checkoutPollTimer = setTimeout(poll, 3000);
            } else {
              this.checkoutPending.set(false);
              this.showToast('Todavía estamos confirmando tu pago — recarga en un minuto.', 'danger');
            }
          },
          error: () => {
            if (attempts < 5) {
              this.checkoutPollTimer = setTimeout(poll, 3000);
            } else {
              this.checkoutPending.set(false);
            }
          },
        });
      };
      this.checkoutPollTimer = setTimeout(poll, 2000);
      this.destroyRef.onDestroy(() => clearTimeout(this.checkoutPollTimer));
    }
  }

  private checkoutPollTimer: ReturnType<typeof setTimeout> | undefined;

  // ---- Reloj vivo: refresca `now` cada 30 s (solo con la pantalla visible) y al volver a primer plano ----
  private static readonly CLOCK_TICK_MS = 30_000;
  // Si pasó más que esto sin sincronizar (segundo plano, equipo dormido) los datos cargados ya no son confiables.
  private static readonly STALE_AFTER_MS = 5 * 60_000;
  private lastClockSyncAt = Date.now();

  private startClock(): void {
    const intervalId = setInterval(() => {
      if (document.visibilityState === 'visible') {
        this.syncClock();
      }
    }, MemberPage.CLOCK_TICK_MS);
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        this.syncClock();
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);
    this.destroyRef.onDestroy(() => {
      clearInterval(intervalId);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    });
  }

  private syncClock(): void {
    const nowMs = Date.now();
    const idleMs = nowMs - this.lastClockSyncAt;
    this.lastClockSyncAt = nowMs;
    const previousToday = this.todayIso();
    this.now.set(nowInGymZoneIso());
    const today = this.todayIso();
    const dayChanged = today !== previousToday;
    if (dayChanged && this.selectedDate() === previousToday) {
      this.selectedDate.set(today);
    }
    if (dayChanged || idleMs > MemberPage.STALE_AFTER_MS) {
      this.refreshAll();
    }
  }

  // Misma carga que al abrir la pantalla, para cuando cambió el día o la app estuvo mucho rato dormida.
  private refreshAll(): void {
    this.loadDashboard();
    this.loadOccurrences();
    this.loadMyReservations();
    this.loadTodayRoutine();
    this.loadPastReservations(true);
  }

  protected categoryIcon(category: string | null): string {
    return resolveClassCategoryIcon(category);
  }

  /** Nombre accesible único de los botones repetidos de las tarjetas: acción + clase + fecha y hora. */
  protected tileLabel(action: string, label: string, classDate: string, startTime: string): string {
    return `${action} ${label}, ${formatShortDate(classDate)} a las ${shortTime(startTime)}`;
  }

  protected formatShortDate(dateIso: string): string {
    return formatShortDate(dateIso);
  }

  // Nombre accesible del día del calendario: el número solo no dice mes ni si hay clases (el punto es decorativo).
  protected calendarDayLabel(cell: MonthDayCell): string {
    return `${dayMonthLabel(cell.iso)}${cell.hasClasses ? ', hay clases' : ''}`;
  }

  // Copia un dato de la transferencia (cuenta, RUT, correo) y avisa con un toast corto.
  protected async copyBankDetail(value: string | null | undefined): Promise<void> {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      const toast = await this.toastController.create({ message: 'Copiado', duration: 1500, position: 'bottom', color: 'success' });
      await toast.present();
    } catch {
      this.showToast('No pudimos copiar. Cópialo a mano.', 'danger');
    }
  }

  protected shortTime(time: string): string {
    return shortTime(time);
  }

  protected selectDay(iso: string): void {
    this.selectedDate.set(iso);
    this.pastOccurrencesCollapsed.set(true);
    this.revealDayList();
  }

  // En móvil (columnas apiladas, bajo 860px) la lista del día queda bajo el calendario: si el título
  // no se ve tras elegir el día, se lleva a la vista para que el toque tenga respuesta visible.
  private revealDayList(): void {
    if (!window.matchMedia?.('(max-width: 859px)').matches) {
      return;
    }
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    setTimeout(() => {
      const title = document.querySelector('.member-columns__agenda h2');
      if (!title) {
        return;
      }
      const rect = title.getBoundingClientRect();
      if (rect.top >= 0 && rect.bottom <= window.innerHeight) {
        return;
      }
      title.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
    });
  }

  protected toggleMonthExpanded(): void {
    this.monthExpanded.update((expanded) => !expanded);
  }

  // Toque del socio en una pestaña: cambia la sección y la deja en la URL (?s=) como una entrada nueva del
  // historial, para que Atrás/Adelante recorran las pestañas y se pueda enlazar a una.
  protected setSection(section: MemberSection): void {
    if (section !== this.section()) {
      this.applySection(section);
    }
    this.syncSectionToUrl(section);
  }

  // Cambia la sección y carga lo suyo, sin tocar la URL (la usan el toque y la lectura de la URL).
  private applySection(section: MemberSection): void {
    this.section.set(section);
    if (section === 'reservas') {
      this.loadWaitlist();
    }
    if (section === 'rutina') {
      // Volver a la pestaña no debe borrar lo que el socio ya estaba anotando (ver loadPendingWorkout).
      this.loadPendingWorkout(false, true);
    }
  }

  private static sectionFromParam(value: string | null): MemberSection {
    return (SECTION_VALUES as readonly string[]).includes(value ?? '') ? (value as MemberSection) : 'reservar';
  }

  // Sin ?s= (o con un valor desconocido) la sección es "reservar", así que la entrada inicial no escribe nada
  // en la URL ni agrega historial; solo cambiar de pestaña agrega una entrada.
  private syncSectionToUrl(section: MemberSection): void {
    const current = MemberPage.sectionFromParam(this.route.snapshot.queryParamMap.get('s'));
    if (current === section) {
      return;
    }
    void this.router.navigate([], { queryParams: { s: section }, queryParamsHandling: 'merge', replaceUrl: false });
  }

  // Atrás/Adelante, enlaces directos y recarga: la URL manda sobre la sección mostrada.
  private readSectionFromUrl(param: string | null = this.route.snapshot.queryParamMap.get('s')): void {
    const target = MemberPage.sectionFromParam(param);
    if (target !== this.section()) {
      this.applySection(target);
    }
  }

  // Ionic reutiliza la página al volver: se relee la URL por si cambió mientras estaba en segundo plano.
  ionViewWillEnter(): void {
    this.readSectionFromUrl();
  }

  // Tour guiado por sección de la vista de socio (modo demo) — antes un solo recorrido de 5
  // pasos que saltaba entre "Reservar" y "Rutina"; ahora cada pestaña tiene su propio mini-tour
  // acotado (mismo criterio que gym-admin.ts, ver comentario ahí). El paso de check-in QR sigue
  // siendo deliberadamente sin interacción real: hoy un DEMO_ADMIN no puede llegar a
  // /checkin/:code (roleGuard exige MEMBER) ni DemoPreviewService tiene un método de check-in,
  // así que solo explica el aviso que ya existe en una clase en curso, sin simular el escaneo.
  // Los códigos ('M_RESERVAR'/'M_RUTINA') deben coincidir con DemoTourService.ALLOWED_TOURS.
  private readonly sectionTours: Partial<Record<'reservar' | 'reservas' | 'rutina', SectionTour>> = {
    reservas: {
      tour: 'M_RESERVAS',
      label: 'Mis Reservas',
      steps: [
        {
          title: 'Sus próximas clases, siempre a la vista',
          body: 'Si una está en curso, ve una barra de progreso en vivo y el aviso de marcar asistencia por QR. Las clases fijas que le dejaste aparecen reservadas solas cada semana, y le llega un aviso al celular antes de que empiecen — nunca más te escribe para saber si ya reservó.',
          targetSelector: '[data-tour="reservas-upcoming"]',
        },
        {
          title: 'Cancela cuando quiera',
          body: 'Tu socio gestiona sus propias reservas solo, sin llamar al gimnasio. El gimnasio define hasta cuándo se puede cancelar: pasado ese límite el botón se oculta y se le explica por qué.',
          targetSelector: '[data-tour="reservas-cancel"]',
        },
        {
          title: 'Su historial completo',
          body: 'Todas las clases a las que ya fue, ordenadas — útil para que vea su propio progreso.',
          targetSelector: '[data-tour="reservas-past"]',
        },
        {
          title: 'Instala la app en el celular',
          body: 'Si tu socio abre la app desde el navegador, arriba a la derecha tiene el botón para instalarla: en Android se instala con un toque y en iPhone le muestra los 2 pasos. Queda con su ícono, a pantalla completa. (En esta demo no aparece.)',
          targetSelector: 'ion-header ion-buttons[slot="end"]',
        },
        {
          title: 'Avisos en el celular',
          body: 'Con la app ya instalada, en ese mismo lugar aparece una campana: la activa una vez y le avisamos cuando se libera un cupo, antes de su clase, si cambias un horario o si hay un cierre. Solo se ofrece dentro de la app instalada, no desde el navegador. (En esta demo no aparece.)',
          targetSelector: 'ion-header ion-buttons[slot="end"]',
        },
      ],
    },
    reservar: {
      tour: 'M_RESERVAR',
      label: 'Reservar',
      steps: [
        {
          title: 'Navega cualquier semana o mes',
          body: 'Calendario completo con indicador de qué días tienen clases — tu socio planifica su semana de un vistazo.',
          targetSelector: '[data-tour="calendar-nav"]',
        },
        {
          title: 'Reserva en segundos',
          body: 'Tu socio ve los cupos libres de cada clase y reserva con un toque.',
          targetSelector: '[data-tour="reserve-book"]',
          // "Hoy" puede no tener nada reservable (la clase de hoy ya pasó, o cae en un día sin
          // bloques) según a qué hora real se abra la demo — en vez de depender de que "hoy"
          // tenga algo, se navega al día más próximo que sí tenga una clase reservable real.
          beforeShow: () => this.goToNearestBookableDate(),
        },
        {
          title: 'Ve quién más va',
          body: 'Factor social: tu socio ve a sus compañeros anotados en la misma clase — refuerza que vuelva. Cada gimnasio decide si mostrarlo.',
          targetSelector: '[data-tour="class-attendees"]',
          // Este paso necesita una clase que ya tenga al menos un anotado (si no, el link "Ver
          // quién va" ni se renderiza) — no alcanza con "reservable a secas" como el paso anterior.
          beforeShow: () => this.goToNearestBookableDateWithAttendees(),
        },
        {
          title: 'Si está llena, se anota',
          body: 'Cuando una clase no tiene cupo, se anota en la lista de espera, le avisamos por email y al celular apenas se libera uno (con unos minutos de ventaja si es el primero), y puede salir de la lista cuando quiera.',
          targetSelector: '[data-tour="waitlist-join"]',
          // Este paso necesita específicamente una clase LLENA, no solo reservable.
          beforeShow: () => this.goToNearestFullDate(),
        },
        {
          title: 'Elige su plan y paga, con todo claro',
          body: 'Ve el precio y cupo de cada plan. Al pagar, ve el desglose exacto (plan + comisión de Flow, o los datos para transferir) — cero sorpresas.',
          targetSelector: '[data-tour="plans-grid"]',
        },
        {
          title: 'Check-in con QR en la TV',
          body: 'Mientras una clase está en curso, tu socio escanea el QR de la pantalla para marcar que llegó.',
          targetSelector: '[data-tour="checkin-hint"]',
        },
      ],
    },
    rutina: {
      tour: 'M_RUTINA',
      label: 'Rutina',
      steps: [
        {
          title: 'Memoria Viva: su rutina',
          body: 'Después de marcar asistencia, anota qué hizo en la clase — su profesor lo ve antes de hablar con él.',
          targetSelector: '[data-tour="workout-days"]',
        },
        {
          title: 'Registro libre si hizo otra cosa',
          body: 'Si ese día entrenó algo distinto al plan sugerido, puede anotarlo igual con "Otro" — el sistema lo marca como una desviación, sin forzar nada.',
          targetSelector: '[data-tour="workout-free-text"]',
        },
        {
          title: 'Detalle por ejercicio, y corrige si se equivocó',
          body: 'Anota notas de cada ejercicio por separado, y puede corregir un registro ya guardado si cambió algo.',
          targetSelector: '[data-tour="workout-exercise-rows"]',
        },
      ],
    },
  };

  protected readonly currentSectionTour = computed(() => this.sectionTours[this.section()] ?? null);

  protected startSectionTour(): void {
    const sectionTour = this.currentSectionTour();
    if (!sectionTour) {
      return;
    }
    this.tourSeen.markMemberTourSeen();
    this.tourService.start(sectionTour.tour, sectionTour.steps);
  }

  // Usados solo por el tour guiado (ver sectionTours arriba) — navegan el calendario a un día
  // real con el tipo de clase que ese paso necesita mostrar, en vez de asumir que "hoy" sirve
  // (la clase de hoy puede ya haber pasado, o caer un día sin bloques, según la hora real en que
  // se abra la demo). Si no encuentran nada, no tocan la fecha — el overlay ya sabe mostrar la
  // tarjeta centrada sin spotlight cuando el target no existe.
  private goToNearestBookableDate(): void {
    const match = this.occurrences()
      .filter((o) => !o.past && !this.isCapacityFull(o))
      .sort((a, b) => a.classDate.localeCompare(b.classDate))[0];
    if (match) {
      this.selectedDate.set(match.classDate);
    }
  }

  private goToNearestBookableDateWithAttendees(): void {
    const withAttendees = this.occurrences()
      .filter((o) => !o.past && !this.isCapacityFull(o) && o.taken > 0)
      .sort((a, b) => a.classDate.localeCompare(b.classDate))[0];
    if (withAttendees) {
      this.selectedDate.set(withAttendees.classDate);
    } else {
      this.goToNearestBookableDate();
    }
  }

  private goToNearestFullDate(): void {
    const match = this.occurrences()
      .filter((o) => !o.past && this.isCapacityFull(o))
      .sort((a, b) => a.classDate.localeCompare(b.classDate))[0];
    if (match) {
      this.selectedDate.set(match.classDate);
    }
  }

  // ---- "Memoria Viva": pestaña Rutina — ver WorkoutService (backend) para los 3 estados
  // vacíos (NO_PLAN/NO_PENDING/READY) y el diseño completo. Sin historial: solo se puede ver/
  // anotar/corregir la última reserva con check-in pendiente de registrar a la vez.

  protected readonly pendingWorkout = signal<PendingWorkout | null>(null);
  protected readonly loadingWorkout = signal(false);
  protected readonly savingWorkout = signal(false);

  // El log recién guardado se muestra inline (con opción de corregirlo) aunque `pendingWorkout`
  // ya haya pasado a NO_PENDING tras guardarlo — si no, el socio no tendría forma de ver/editar
  // lo que acaba de anotar en esta misma visita.
  protected readonly justSavedLog = signal<MemberWorkoutLog | null>(null);
  protected readonly editingSavedLog = signal(false);

  protected readonly selectedPlanDayId = signal<number | null>(null);
  protected readonly useFreeText = signal(false);
  protected readonly freeTextLabel = signal('');
  protected readonly exerciseRows = signal<ExerciseLogEntry[]>([{ exercise: '', notes: '' }]);

  protected readonly canSaveWorkout = computed(() =>
    this.useFreeText() ? this.freeTextLabel().trim().length > 0 : this.selectedPlanDayId() !== null,
  );

  // Los registros del propio socio, de a 7 días: arranca con la última semana y "Cargar semana anterior" trae más.
  // Antes no había dónde volver a ver lo anotado: el registro recién guardado se mostraba un instante y desaparecía.
  protected readonly workoutHistory = signal<MemberWorkoutLog[]>([]);
  protected readonly workoutHistoryNext = signal<string | null>(null);
  // Buscador y chips de "Mis registros": actúan solo sobre lo ya cargado.
  private readonly workoutFilter = signal<WorkoutLogFilter>(NO_WORKOUT_FILTER);
  protected readonly workoutFiltering = computed(() => isFiltering(this.workoutFilter()));
  protected readonly visibleWorkoutHistory = computed(() => filterWorkoutLogs(this.workoutHistory(), this.workoutFilter()));
  protected setWorkoutFilter(filter: WorkoutLogFilter): void {
    this.workoutFilter.set(filter);
  }
  protected workoutParts(text: string | null) {
    return highlightParts(text ?? '', this.workoutFilter().query);
  }
  protected readonly loadingMoreWorkoutHistory = signal(false);
  protected readonly workoutHistoryError = signal(false);
  protected readonly pendingWorkoutError = signal(false);

  // Qué día de su rutina le toca hoy ("Día 3 · Piernas"), para mostrarlo en su reserva de hoy y que llegue a la clase
  // sabiendo qué va a hacer. null si no tiene rutina (o es la demo).
  protected readonly todayRoutine = signal<string | null>(null);

  private loadTodayRoutine(): void {
    if (this.isDemoPreview) {
      return;
    }
    this.workoutService.getPending().subscribe({
      next: (pending) => {
        const day = pending.plan?.days.find((d) => d.id === pending.suggestedPlanDayId);
        this.todayRoutine.set(day ? day.title : null);
      },
      error: () => this.todayRoutine.set(null),
    });
  }

  protected isTodayDate(classDate: string): boolean {
    return classDate === this.todayIso();
  }

  private workoutHistoryGeneration = 0;

  protected loadWorkoutHistory(): void {
    if (this.isDemoPreview) {
      return;
    }
    this.workoutHistoryError.set(false);
    // Una recarga de la primera página invalida cualquier "Cargar semana anterior" que siga en vuelo.
    const generation = ++this.workoutHistoryGeneration;
    this.loadingMoreWorkoutHistory.set(false);
    this.workoutService.getMyHistory().subscribe({
      next: (page) => {
        if (generation !== this.workoutHistoryGeneration) {
          return;
        }
        this.workoutHistory.set(page.logs);
        this.workoutHistoryNext.set(page.nextBefore);
      },
      error: () => this.workoutHistoryError.set(true),
    });
  }

  protected loadMoreWorkoutHistory(): void {
    const before = this.workoutHistoryNext();
    if (!before || this.loadingMoreWorkoutHistory()) {
      return;
    }
    this.loadingMoreWorkoutHistory.set(true);
    const generation = this.workoutHistoryGeneration;
    this.workoutService.getMyHistory(before).subscribe({
      next: (page) => {
        if (generation !== this.workoutHistoryGeneration) {
          return;
        }
        // Sin ids repetidos, por si la primera página se solapó con la ventana pedida.
        this.workoutHistory.update((logs) => {
          const seen = new Set(logs.map((l) => l.id));
          return [...logs, ...page.logs.filter((l) => !seen.has(l.id))];
        });
        this.workoutHistoryNext.set(page.nextBefore);
        this.loadingMoreWorkoutHistory.set(false);
      },
      error: () => {
        if (generation !== this.workoutHistoryGeneration) {
          return;
        }
        this.loadingMoreWorkoutHistory.set(false);
        this.showToast('No pudimos cargar la semana anterior. Intenta nuevamente.', 'danger');
      },
    });
  }

  protected workoutDeviated(log: MemberWorkoutLog): boolean {
    return log.planDayId === null || log.planDayId !== log.suggestedPlanDayId;
  }

  // Borrador de la rutina: se conserva mientras siga la misma reserva pendiente y el socio ya haya escrito algo.
  private draftReservationId: number | null = null;

  private hasWorkoutDraft(): boolean {
    return (
      this.exerciseRows().some((row) => row.exercise.trim() || row.notes?.trim()) || this.freeTextLabel().trim().length > 0
    );
  }

  protected loadPendingWorkout(keepSavedLog = false, keepDraft = false): void {
    this.loadingWorkout.set(true);
    this.pendingWorkoutError.set(false);
    if (!keepSavedLog) {
      this.justSavedLog.set(null);
    }
    this.loadWorkoutHistory();
    (this.isDemoPreview ? this.demoPreviewService.getPendingWorkout() : this.workoutService.getPending()).subscribe({
      next: (pending) => {
        this.pendingWorkout.set(pending);
        this.loadingWorkout.set(false);
        if (pending.status === 'READY') {
          const sameReservation = pending.reservationId !== null && pending.reservationId === this.draftReservationId;
          if (!(keepDraft && sameReservation && this.hasWorkoutDraft())) {
            this.resetWorkoutForm(pending.suggestedPlanDayId);
          }
          this.draftReservationId = pending.reservationId;
        }
      },
      error: () => {
        this.loadingWorkout.set(false);
        this.pendingWorkoutError.set(true);
      },
    });
  }

  private resetWorkoutForm(suggestedPlanDayId: number | null): void {
    this.selectedPlanDayId.set(suggestedPlanDayId);
    this.useFreeText.set(false);
    this.freeTextLabel.set('');
    this.exerciseRows.set([{ exercise: '', notes: '' }]);
  }

  protected selectPlanDay(id: number): void {
    this.selectedPlanDayId.set(id);
    this.useFreeText.set(false);
  }

  protected selectFreeText(): void {
    this.useFreeText.set(true);
    this.selectedPlanDayId.set(null);
  }

  protected setFreeTextLabel(value: string): void {
    this.freeTextLabel.set(value);
  }

  protected setExerciseField(index: number, field: 'exercise' | 'notes', value: string): void {
    const rows = [...this.exerciseRows()];
    rows[index] = { ...rows[index], [field]: value };
    this.exerciseRows.set(rows);
  }

  protected addExerciseRow(): void {
    this.exerciseRows.set([...this.exerciseRows(), { exercise: '', notes: '' }]);
  }

  protected removeExerciseRow(index: number): void {
    const rows = this.exerciseRows().filter((_, i) => i !== index);
    this.exerciseRows.set(rows.length > 0 ? rows : [{ exercise: '', notes: '' }]);
  }

  private buildWorkoutPayload(): SaveWorkoutLogRequest {
    return {
      planDayId: this.useFreeText() ? null : this.selectedPlanDayId(),
      freeTextLabel: this.useFreeText() ? this.freeTextLabel().trim() : null,
      exercises: this.exerciseRows().filter((row) => row.exercise.trim().length > 0),
    };
  }

  protected saveWorkoutLog(): void {
    const pending = this.pendingWorkout();
    if (!pending || pending.status !== 'READY' || pending.reservationId === null || !this.canSaveWorkout()) {
      return;
    }
    if (this.blockedInDemo()) {
      return;
    }
    this.savingWorkout.set(true);
    this.workoutService.createLog(pending.reservationId, this.buildWorkoutPayload()).subscribe({
      next: (log) => {
        this.savingWorkout.set(false);
        this.justSavedLog.set(log);
        this.editingSavedLog.set(false);
        this.showToast('Registro guardado.');
        this.draftReservationId = null;
        this.loadPendingWorkout(true);
      },
      error: () => {
        this.savingWorkout.set(false);
        this.showToast('No pudimos guardar el registro. Intenta nuevamente.', 'danger');
      },
    });
  }

  protected editSavedLog(): void {
    const log = this.justSavedLog();
    if (!log) {
      return;
    }
    this.selectedPlanDayId.set(log.planDayId);
    this.useFreeText.set(log.planDayId === null);
    this.freeTextLabel.set(log.freeTextLabel ?? '');
    this.exerciseRows.set(log.exercises && log.exercises.length > 0 ? log.exercises : [{ exercise: '', notes: '' }]);
    this.editingSavedLog.set(true);
  }

  protected cancelEditSavedLog(): void {
    this.editingSavedLog.set(false);
  }

  protected updateSavedLog(): void {
    const log = this.justSavedLog();
    if (!log || !this.canSaveWorkout()) {
      return;
    }
    if (this.blockedInDemo()) {
      return;
    }
    this.savingWorkout.set(true);
    this.workoutService.updateLog(log.id, this.buildWorkoutPayload()).subscribe({
      next: (updated) => {
        this.savingWorkout.set(false);
        this.justSavedLog.set(updated);
        this.editingSavedLog.set(false);
        this.showToast('Registro actualizado.');
        this.loadWorkoutHistory();
      },
      error: () => {
        this.savingWorkout.set(false);
        this.showToast('No pudimos actualizar el registro. Intenta nuevamente.', 'danger');
      },
    });
  }

  protected book(occurrence: GymBlockOccurrence): void {
    if (this.blockedInDemo() || this.guardOffline()) {
      return;
    }
    // Sin datos de contacto el servidor no deja reservar: se piden ahora (no al entrar) y se sigue con esta reserva al guardar.
    if (this.profileIncomplete()) {
      this.askProfileToBook(occurrence);
      return;
    }
    this.bookingId.set(occurrence.gymBlockId);
    this.reservationService
      .book({ gymBlockId: occurrence.gymBlockId, classDate: occurrence.classDate })
      .subscribe({
        next: (reservation) => {
          this.bookingId.set(null);
          this.missionBooked.set(true);
          this.membership.update((m) => ({ ...m, classesUsed: m.classesUsed + 1 }));
          // Tarjeta al instante; loadOccurrences() reconcilia en silencio con el servidor.
          this.patchOccurrences(
            (o) => o.gymBlockId === reservation.gymBlockId && o.classDate === reservation.classDate,
            (o) => ({
              ...o,
              myReservationId: reservation.id,
              myReservationFixed: reservation.fixed,
              waitlisted: false,
              taken: o.taken + 1,
            }),
          );
          this.loadOccurrences();
          this.loadMyReservations();
          this.showToast('¡Reserva confirmada! Te esperamos en la clase.');
        },
        error: (err: Error & { status?: number; code?: string }) => {
          this.bookingId.set(null);
          // 409 "Perfil incompleto": el servidor sabe algo que esta pantalla aún no (datos borrados o sesión vieja).
          // El código PROFILE_INCOMPLETE es lo seguro; el texto queda solo como respaldo por si el código no llegara.
          if (err.code === 'PROFILE_INCOMPLETE' || (err.status === 409 && /datos/i.test(err.message))) {
            this.profileIncomplete.set(true);
            this.askProfileToBook(occurrence);
            return;
          }
          this.showToast(
            this.isNetworkError(err) ? OFFLINE_WRITE_MESSAGE : err.message || 'No pudimos reservar esa clase. Intenta nuevamente.',
            'danger',
          );
        },
      });
  }

  // Pide confirmación antes de cancelar: el botón rojo estaba a un toque de perder el cupo.
  protected async confirmCancel(
    reservationId: number,
    label: string,
    classDate: string,
    startTime: string,
    fixed = false,
  ): Promise<void> {
    if (this.blockedInDemo() || this.cancelingId() !== null) {
      return;
    }
    if (this.guardOffline()) {
      return;
    }
    const today = this.todayIso();
    const day =
      classDate === today
        ? 'hoy'
        : classDate === addDaysIso(today, 1)
          ? 'mañana'
          : formatShortDate(classDate);
    const alert = await this.alertController.create({
      header: '¿Cancelar tu reserva?',
      message:
        `${label} · ${day} ${shortTime(startTime)}.` +
        (fixed ? ' Es una clase fija: solo se cancela esta fecha, las próximas semanas siguen reservadas.' : ''),
      buttons: [
        { text: 'Volver', role: 'cancel' },
        {
          text: 'Cancelar clase',
          role: 'destructive',
          handler: () => {
            this.cancel(reservationId, fixed);
          },
        },
      ],
    });
    await alert.present();
  }

  private cancel(reservationId: number, fixed = false): void {
    if (this.blockedInDemo() || this.guardOffline()) {
      return;
    }
    this.cancelingId.set(reservationId);
    this.reservationService.cancel(reservationId).subscribe({
      next: () => {
        this.cancelingId.set(null);
        this.membership.update((m) => ({ ...m, classesUsed: Math.max(m.classesUsed - 1, 0) }));
        // Cupo liberado y reserva fuera de la lista al instante; los loads de abajo reconcilian en silencio.
        this.patchOccurrences(
          (o) => o.myReservationId === reservationId,
          (o) => ({ ...o, myReservationId: null, myReservationFixed: false, myCheckedInAt: null, taken: Math.max(o.taken - 1, 0) }),
        );
        this.myReservations.update((list) => list.filter((r) => r.id !== reservationId));
        this.loadOccurrences();
        this.loadMyReservations();
        this.showToast(
          fixed
            ? 'Cancelaste solo esta fecha. Tu clase fija sigue para las próximas semanas.'
            : 'Reserva cancelada.',
        );
      },
      error: (err: Error) => {
        this.cancelingId.set(null);
        this.showToast(this.isNetworkError(err) ? OFFLINE_WRITE_MESSAGE : err.message || 'No pudimos cancelar esa reserva.', 'danger');
      },
    });
  }

  // Bloque lleno: en vez de quedarse sin enterarse, el socio pide que le avisen si se
  // libera un cupo (ver WaitlistService en el backend — orden de llegada, con ventaja para el
  // primero de la lista antes de avisarle al resto).
  protected joinWaitlist(occurrence: GymBlockOccurrence): void {
    if (this.blockedInDemo() || this.guardOffline()) {
      return;
    }
    this.waitlistingId.set(occurrence.gymBlockId);
    this.reservationService
      .joinWaitlist({ gymBlockId: occurrence.gymBlockId, classDate: occurrence.classDate })
      .subscribe({
        next: () => {
          this.waitlistingId.set(null);
          this.patchOccurrences(
            (o) => o.gymBlockId === occurrence.gymBlockId && o.classDate === occurrence.classDate,
            (o) => ({ ...o, waitlisted: true }),
          );
          this.loadOccurrences();
          this.loadWaitlist();
          this.showToast('Listo, te avisamos si se libera un cupo.');
        },
        error: (err: Error) => {
          this.waitlistingId.set(null);
          this.showToast(
            this.isNetworkError(err) ? OFFLINE_WRITE_MESSAGE : err.message || 'No pudimos anotarte en la lista de espera.',
            'danger',
          );
        },
      });
  }

  protected leaveWaitlist(occurrence: Pick<GymBlockOccurrence, 'gymBlockId' | 'classDate'>): void {
    if (this.blockedInDemo() || this.guardOffline()) {
      return;
    }
    this.waitlistingId.set(occurrence.gymBlockId);
    this.reservationService.leaveWaitlist(occurrence.gymBlockId, occurrence.classDate).subscribe({
      next: () => {
        this.waitlistingId.set(null);
        this.patchOccurrences(
          (o) => o.gymBlockId === occurrence.gymBlockId && o.classDate === occurrence.classDate,
          (o) => ({ ...o, waitlisted: false }),
        );
        this.loadOccurrences();
        this.loadWaitlist();
        this.showToast('Te sacamos de la lista de espera.');
      },
      error: (err: Error) => {
        this.waitlistingId.set(null);
        this.showToast(
          this.isNetworkError(err) ? OFFLINE_WRITE_MESSAGE : err.message || 'No pudimos sacarte de la lista de espera.',
          'danger',
        );
      },
    });
  }

  // "Salir de la lista": pide confirmación desde Mis Reservas y desde el calendario (el puesto en la fila no se recupera).
  protected async confirmLeaveWaitlist(
    entry: Pick<WaitlistEntry, 'gymBlockId' | 'classDate' | 'label' | 'startTime'>,
  ): Promise<void> {
    if (this.blockedInDemo() || this.waitlistingId() !== null || this.guardOffline()) {
      return;
    }
    const alert = await this.alertController.create({
      header: '¿Salir de la lista de espera?',
      message: `${entry.label} · ${this.relativeDayLabel(entry.classDate)} ${shortTime(entry.startTime)}. Perderás tu puesto en la fila.`,
      buttons: [
        { text: 'Volver', role: 'cancel' },
        { text: 'Salir de la lista', role: 'destructive', handler: () => this.leaveWaitlist(entry) },
      ],
    });
    await alert.present();
  }

  protected loadWaitlist(): void {
    if (this.isDemoPreview) {
      return;
    }
    this.reservationService.myWaitlist().subscribe({
      next: (entries) => this.myWaitlist.set(entries),
      // Sin toast: mientras el endpoint no esté desplegado (404) la sección simplemente no aparece.
      error: () => this.myWaitlist.set([]),
    });
  }

  // Primer paso al elegir un plan: no se paga todavía, solo se muestra cómo
  // pagar (Flow o transferencia) — el plan queda "guardado" en
  // paymentChoicePlan hasta que el socio confirma un método.
  protected choosePaymentMethod(plan: MembershipPlan): void {
    this.transferNotified.set(false);
    this.paymentChoicePlan.set(plan);
  }

  protected cancelPaymentChoice(): void {
    this.transferNotified.set(false);
    this.paymentChoicePlan.set(null);
  }

  // Comisión de Flow por link de pago, traspasada al socio — ver
  // GATEWAY_COMMISSION_RATE arriba. Solo para mostrar el desglose: el monto
  // que realmente se cobra lo calcula FlowPaymentService en el backend.
  protected commissionFor(plan: MembershipPlan): number {
    return Math.round(plan.priceClp * GATEWAY_COMMISSION_RATE);
  }

  protected totalWithCommission(plan: MembershipPlan): number {
    return plan.priceClp + this.commissionFor(plan);
  }

  // Pago real con Flow.cl — el navegador sale del dominio de mygym por
  // completo (primer uso de este patrón en la app) y vuelve recién cuando
  // Flow termina el registro de tarjeta, a /member?checkout=return (ver
  // constructor). El estado "pending" acá es solo mientras se arma la URL
  // de checkout, no simula ningún pago.
  protected payWithFlow(plan: MembershipPlan): void {
    if (this.blockedInDemo()) {
      return;
    }
    // En renovación por adelantado el plan sigue vigente: si falla, vuelve a 'active', no a las tarjetas de alta.
    const previousStatus: MembershipStatus = this.earlyMode() ? 'active' : 'none';
    this.membership.update((m) => ({ ...m, status: 'pending' }));
    this.gymService.startCheckout(plan.id).subscribe({
      next: (res) => {
        window.location.href = res.redirectUrl;
      },
      error: (err: Error) => {
        this.membership.update((m) => ({ ...m, status: previousStatus }));
        this.showToast(errorText(err, 'No pudimos iniciar el pago. Intenta nuevamente.'), 'danger');
      },
    });
  }

  // Desde la tarjeta de "membresía vencida" — vuelve a mostrar las tarjetas
  // de plan (mismo estado que un socio que nunca pagó) para "renovar".
  protected renewPlan(): void {
    this.membership.update((m) => ({ ...m, status: 'none' }));
  }

  // Desde el aviso "Tu plan vence en N días": abre las tarjetas de renovación por adelantado y baja hasta
  // ellas. Si ya no aplica (venció, o ya renovó), cae al comportamiento de siempre.
  protected startEarlyRenewal(): void {
    if (this.earlyRenewalAvailable()) {
      this.cancelPaymentChoice();
      this.renewingEarly.set(true);
      this.goToPlans();
    } else {
      this.scrollToPlans();
    }
  }

  protected scrollToPlans(): void {
    document.querySelector('.membership')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  // Igual que scrollToPlans(), pero desde la pestaña "Mis Reservas" — ahí .membership
  // todavía no está en el DOM (vive en la pestaña "Reserva tu Bloque"), hay que cambiar
  // de pestaña primero y recién ahí buscar el elemento, una vez que Angular lo pintó.
  protected goToPlans(): void {
    this.setSection('reservar');
    setTimeout(() => this.scrollToPlans());
  }

  protected formatClp(amount: number): string {
    return amount.toLocaleString('es-CL');
  }

  // En la demo no hay nada que escribir (los endpoints de /demo-preview son solo GET, y
  // SecurityConfig igual bloquearía cualquier POST de un DEMO_ADMIN) — se corta acá con un
  // aviso claro en vez de dejar que el botón falle con un error genérico.
  private blockedInDemo(): boolean {
    if (!this.isDemoPreview) {
      return false;
    }
    this.showToast('Esta es una demostración: así se vería la acción, pero no se guarda nada.');
    return true;
  }

  // Mismo criterio que gym-admin.ts: un socio que sale vuelve a la página
  // propia de SU gimnasio, no al /login genérico. En la demo no se cierra sesión:
  // "salir" es volver al panel de administración que el prospecto estaba mirando.
  protected async logout(): Promise<void> {
    if (this.isDemoPreview) {
      this.router.navigate(['/gym-admin']);
      return;
    }
    const alert = await this.alertController.create({
      header: '¿Cerrar sesión?',
      message: 'Vas a salir de tu cuenta en este dispositivo.',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Salir',
          role: 'destructive',
          handler: () => {
            void this.doLogout();
          },
        },
      ],
    });
    await alert.present();
  }

  private async doLogout(): Promise<void> {
    this.clearSnapshot();
    await this.authService.logout();
    const slug = this.gym()?.slug;
    this.router.navigate([slug ? `/j/${slug}` : '/login']);
  }

  // Escáner de QR dentro de la app: el QR de la TV es un link normal que el teléfono suele abrir con el
  // navegador; con la cámara propia el socio marca su asistencia sin salir de la app. Reusa el mismo
  // endpoint que la página /checkin/:code (ReservationService.checkIn).
  protected readonly scannerOpen = signal(false);
  protected readonly checkinBusy = signal(false);

  protected openQrScanner(): void {
    if (this.blockedInDemo() || this.checkinBusy()) {
      return;
    }
    this.scannerOpen.set(true);
  }

  // Paso "reservar" de la misión: lleva al calendario, que es donde se elige la clase.
  protected onMissionBook(): void {
    this.setSection('reservar');
    setTimeout(() => document.querySelector('.member-columns__calendar')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  protected closeQrScanner(): void {
    this.scannerOpen.set(false);
  }

  protected onQrScanned(text: string): void {
    this.scannerOpen.set(false);
    const code = extractCheckinCode(text);
    if (!code) {
      this.showToast('Ese QR no es de asistencia de mygym. Escanea el que muestra la pantalla de tu gimnasio.', 'danger');
      return;
    }
    this.checkinBusy.set(true);
    this.reservationService.checkIn(code).subscribe({
      next: (res) => {
        this.checkinBusy.set(false);
        this.missionCheckedIn.set(true);
        this.showCheckinToast(`¡Asistencia marcada${res.classLabels.length ? ' en ' + res.classLabels.join(' y ') : ''}!`);
        this.loadMyReservations();
        this.loadTodayRoutine();
        this.loadOccurrences();
      },
      error: (err: Error) => {
        this.checkinBusy.set(false);
        this.showToast(err.message || 'No pudimos confirmar tu asistencia. Intenta nuevamente.', 'danger');
      },
    });
  }

  // Tras escanear el QR: si tiene rutina, el mismo aviso lleva directo a anotar lo que hizo (antes tenía que ir a
  // buscar la pestaña "Rutina").
  private async showCheckinToast(message: string): Promise<void> {
    const hasRoutine = this.todayRoutine() !== null;
    const toast = await this.toastController.create({
      message,
      duration: hasRoutine ? 9000 : 4000,
      position: 'bottom',
      color: 'success',
      buttons: hasRoutine
        ? [{ text: 'Anotar lo que hice', handler: () => this.setSection('rutina') }, { text: 'Cerrar', role: 'cancel' }]
        : [],
    });
    await toast.present();
  }

  private async showToast(message: string, color: 'success' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 4000, position: 'bottom', color });
    await toast.present();
  }

  private loadPlans(): void {
    (this.isDemoPreview ? this.demoPreviewService.getPlans() : this.gymService.getMyMemberPlans()).subscribe({
      next: (plans) => {
        this.plans.set(withHighlight(plans, readPreferredPlanId(this.gym()?.slug)));
        this.plansLoaded.set(true);
      },
      error: () => this.plansLoaded.set(true),
    });
  }

  // El pago/plan YA se persiste de verdad en el backend (app_user.plan_id/
  // paid_at, marcado por el admin o por selectPlan() más abajo) — antes esta
  // pantalla nunca lo leía de vuelta y el signal `membership` arrancaba
  // siempre en "none", mostrando "Elige tu plan" a un socio que un admin ya
  // había marcado como Activo. Reportado por el usuario probando con una
  // cuenta real.
  // "Reintentar" del error de membresía: vuelve al spinner neutro y repite la carga inicial.
  protected retryDashboard(): void {
    this.membershipError.set(false);
    this.membershipLoaded.set(false);
    this.loadDashboard();
  }

  private loadMembership(): void {
    (this.isDemoPreview ? this.demoPreviewService.getMembership() : this.gymService.getMyMembership()).subscribe({
      next: (member) => this.applyMembership(member),
      error: () => {
        // Sin dato real NO se asume "sin plan": se avisa con un error y "Reintentar". Si ya habíamos
        // cargado la membresía antes, se conserva (un refresco fallido no la borra).
        if (!this.membershipResolved) {
          this.membershipError.set(true);
        }
        this.membershipLoaded.set(true);
      },
    });
  }

  // Compartido por loadMembership() (demo preview, y el re-poll tras volver de Flow.cl) y
  // loadDashboard() (carga inicial real) — mismo procesamiento, solo cambia de dónde sale el dato.
  private applyMembership(member: Member): void {
    this.membershipResolved = true;
    this.membershipError.set(false);
    this.offline.set(null);
    this.hasFixedClasses.set(!!member.fixedClassesSummary);
    if (this.isDemoPreview) {
      this.demoMemberName.set(member.name);
    } else {
      // RUT, dirección, fecha de nacimiento y celular son obligatorios solo para RESERVAR: si faltan, se piden al
      // intentar reservar (ver book) y se deja un aviso discreto — el servidor no deja reservar hasta que estén.
      this.profileIncomplete.set(!member.profileComplete);
      this.memberProfile.set({
        rut: member.rut ?? '',
        address: member.address ?? '',
        birthDate: member.birthDate ?? '',
        phone: member.phone ?? '',
        emergencyContactName: member.emergencyContactName ?? '',
        emergencyContactPhone: member.emergencyContactPhone ?? '',
      });
    }
    if (!member.planId || !member.paidAt) {
      this.membershipLoaded.set(true);
      return;
    }
    const status = member.membershipStatus === 'EXPIRED' ? 'past_due' : 'active';
    const monthlyClasses = member.monthlyClasses;
    const classesUsed = monthlyClasses !== null ? monthlyClasses - (member.sessionsRemaining ?? monthlyClasses) : 0;
    this.membership.set({
      status,
      plan: {
        id: member.planId,
        name: member.planName ?? 'Plan',
        priceClp: 0,
        monthlyClasses,
      },
      classesUsed,
      paidAt: toChileIsoDate(member.paidAt),
      currentPeriodEnd: member.currentPeriodEnd ? toChileIsoDate(member.currentPeriodEnd) : null,
      queuedPeriodEnd: member.queuedPeriodEnd ? toChileIsoDate(member.queuedPeriodEnd) : null,
      queuedSessionsRemaining: member.queuedSessionsRemaining ?? null,
      canRenewEarly: member.canRenewEarly === true && status === 'active',
      earlyRenewalStartsOn: member.earlyRenewalStartsOn ?? null,
      pendingPlanName: member.pendingPlanName ?? null,
    });
    if (!member.canRenewEarly) {
      this.renewingEarly.set(false);
    }
    this.expiryNotified = false;
    this.stopBookingDemo();
    // El fetch inicial de ocurrencias (constructor) salió con el mes calendario de
    // respaldo, antes de saber si hay plan activo — con status==='active' ya conocido acá,
    // bookableRange() pasa a ser el rango real del período pagado: hay que volver a pedir
    // las ocurrencias para ESE rango (puede no coincidir con el mes calendario de respaldo).
    if (status === 'active') {
      this.loadOccurrences();
    }
    this.membershipLoaded.set(true);
    this.saveSnapshot();
  }

  private loadBankTransfer(): void {
    (this.isDemoPreview
      ? this.demoPreviewService.getBankTransferInfo()
      : this.gymService.getMyBankTransferInfo()
    ).subscribe({
      next: (info) => this.bankTransfer.set(info),
      error: () => {
        // Best-effort: sin datos, la opción de transferencia se queda oculta.
      },
    });
  }

  private loadPhotos(): void {
    (this.isDemoPreview ? this.demoPreviewService.getPhotos() : this.gymService.getMyMemberPhotos()).subscribe({
      next: (photos) => this.photos.set(photos),
      error: () => {
        // Best-effort: sin fotos, el hero cae al fondo genérico.
      },
    });
  }

  private loadGym(): void {
    (this.isDemoPreview ? this.demoPreviewService.getGym() : this.gymService.getMyMemberGym()).subscribe({
      next: (gym) => {
        this.gym.set(gym);
        this.gymLoaded.set(true);
      },
      error: () => this.gymLoaded.set(true),
    });
  }

  // Carga inicial real: combina gym+membresía+planes+datos bancarios+aviso de cierre en 1 sola
  // request en vez de 5 sueltas — eran, junto con fotos/ocurrencias/reservas, hasta 7-8 conexiones
  // simultáneas contra un pool Hikari de solo 5 en la pantalla de mayor concurrencia real de la
  // app (auditoría de performance 2026-10-09). El modo demo sigue con las 4 llamadas sueltas de
  // siempre (bajo tráfico, servidas por un backend de demo separado).
  private loadDashboard(): void {
    if (this.isDemoPreview) {
      this.loadGym();
      this.loadPlans();
      this.loadMembership();
      this.loadBankTransfer();
      this.loadClosureNotice();
      return;
    }
    this.gymService.getMyDashboard().subscribe({
      next: (dashboard) => {
        this.gym.set(dashboard.gym);
        this.gymLoaded.set(true);
        this.plans.set(withHighlight(dashboard.plans, readPreferredPlanId(dashboard.gym.slug)));
        this.plansLoaded.set(true);
        this.bankTransfer.set(dashboard.bankTransfer);
        this.closureNotice.set(dashboard.closureNotice);
        this.missionBooked.set(dashboard.hasBookedClass);
        this.missionCheckedIn.set(dashboard.hasCheckedIn);
        this.missionReady.set(true);
        this.applyMembership(dashboard.membership);
      },
      error: (err: unknown) => {
        this.gymLoaded.set(true);
        this.plansLoaded.set(true);
        // Sin red: si hay una copia guardada, se muestra con su hora en vez del error.
        if (!this.membershipResolved && this.restoreSnapshot(err)) {
          return;
        }
        // Mismo criterio que loadMembership(): sin dato real, error con "Reintentar" en vez de "Elige tu plan".
        if (!this.membershipResolved) {
          this.membershipError.set(true);
        }
        this.membershipLoaded.set(true);
      },
    });
  }

  // Spinner solo en la primera carga (o al reintentar tras un error sin datos). Si ya hay datos, refresca
  // en silencio: la grilla, la altura y el scroll se quedan como están y solo cambian los números.
  protected loadOccurrences(): void {
    if (!this.occurrencesLoaded && this.occurrences().length === 0) {
      this.status.set('loading');
    }
    const request = ++this.occurrencesRequest;
    const monthRange = this.monthRange();
    const range = this.bookableRange() ?? { from: monthRange.from, to: monthRange.to };
    (this.isDemoPreview
      ? this.demoPreviewService.listOccurrences(range.from, range.to)
      : this.reservationService.listOccurrences(range.from, range.to)
    ).subscribe({
      next: (occurrences) => {
        if (request !== this.occurrencesRequest) {
          return; // llegó una respuesta más nueva, esta quedó vieja
        }
        this.occurrencesLoaded = true;
        this.classesNoNetwork.set(false);
        this.occurrences.set(occurrences);
        this.status.set('idle');
        this.cacheClasses(occurrences);
      },
      error: (err: unknown) => {
        if (request !== this.occurrencesRequest) {
          return;
        }
        if (!this.occurrencesLoaded) {
          // Sin red: se muestran las clases guardadas (solo lectura); sin copia, un aviso claro en vez del error genérico.
          if (this.isNetworkError(err) && this.restoreClasses()) {
            return;
          }
          this.classesNoNetwork.set(this.isNetworkError(err));
          this.status.set('error');
          return;
        }
        // Con datos ya cargados no se borran: se deja lo anterior y se avisa discretamente.
        this.showToast('No pudimos actualizar las clases. Estás viendo la última información cargada.', 'danger');
      },
    });
  }

  private occurrencesLoaded = false;
  private occurrencesRequest = 0;

  // Cambia solo las ocurrencias que cumplen `match`, sin vaciar la lista (actualización optimista).
  private patchOccurrences(
    match: (o: GymBlockOccurrence) => boolean,
    patch: (o: GymBlockOccurrence) => GymBlockOccurrence,
  ): void {
    this.occurrences.update((list) => list.map((o) => (match(o) ? patch(o) : o)));
  }

  /** Primera página del historial (reset) o la siguiente ("Cargar más"). */
  protected loadPastReservations(reset = false): void {
    if (this.pastLoadingMore()) {
      return;
    }
    const page = reset ? 0 : this.pastNextPage;
    this.pastLoadingMore.set(true);
    this.pastLoadError.set(false);
    const size = MemberPage.PAST_PAGE_SIZE;
    (this.isDemoPreview
      ? this.demoPreviewService.listPastReservations(page, size)
      : this.reservationService.myPastReservations(page, size)
    ).subscribe({
      next: (result) => {
        this.pastPageItems.update((current) => (reset ? result.items : [...current, ...result.items]));
        this.pastHasNext.set(result.hasNext);
        this.pastNextPage = result.page + 1;
        this.pastLoadingMore.set(false);
        if (!reset && result.items.length > 0) {
          this.pastAnnouncement.set(`${result.items.length} reservas más cargadas`);
        }
      },
      error: () => {
        this.pastLoadingMore.set(false);
        this.pastLoadError.set(true);
      },
    });
  }

  protected readonly reservationsError = signal(false);
  private reservationsFetched = false;

  // ---- Sin conexión: copia mínima en localStorage (ver MemberSnapshot) ----
  // Instante (ms) de la copia que se está mostrando por falta de red; null = datos en vivo.
  protected readonly offline = signal<number | null>(null);
  protected readonly offlineLabel = computed(() => {
    const at = this.offline();
    return at === null ? 'Sin conexión · lo que ves puede estar desactualizado' : `Última actualización ${clockLabel(at)} · sin conexión`;
  });

  // Estado de la red según el navegador (eventos online/offline) y bloqueo de escrituras: con copia local
  // mostrada o sin red, reservar/cancelar/lista de espera no salen a internet (el banner explica el motivo).
  private readonly networkUp = signal(typeof navigator === 'undefined' ? true : navigator.onLine);
  protected readonly writesBlocked = computed(() => !this.isDemoPreview && (this.offline() !== null || !this.networkUp()));
  // true cuando la última carga de clases falló por falta de red y no había copia que mostrar.
  protected readonly classesNoNetwork = signal(false);
  private wentOffline = false;

  private startNetworkWatch(): void {
    const onOffline = () => {
      this.wentOffline = true;
      this.networkUp.set(false);
    };
    const onOnline = () => {
      this.networkUp.set(true);
      const stale =
        this.wentOffline ||
        this.offline() !== null ||
        this.status() === 'error' ||
        this.membershipError() ||
        this.reservationsError();
      this.wentOffline = false;
      if (!stale) {
        return;
      }
      this.classesNoNetwork.set(false);
      this.retryOffline();
      this.showToast('Volvió la conexión');
    };
    window.addEventListener('offline', onOffline);
    window.addEventListener('online', onOnline);
    this.destroyRef.onDestroy(() => {
      window.removeEventListener('offline', onOffline);
      window.removeEventListener('online', onOnline);
    });
  }

  // Un solo punto para las acciones que escriben (reservar, cancelar, lista de espera): sin red avisa y corta.
  private guardOffline(): boolean {
    if (!this.writesBlocked()) {
      return false;
    }
    this.showToast(OFFLINE_WRITE_MESSAGE, 'danger');
    return true;
  }

  private isNetworkError(err: unknown): boolean {
    return (err as { status?: number } | null)?.status === 0;
  }

  private snapshotKey(): string | null {
    const userId = this.authService.currentUser()?.userId;
    return userId == null ? null : `${SNAPSHOT_KEY_PREFIX}${userId}`;
  }

  private saveSnapshot(): void {
    const key = this.snapshotKey();
    const membership = this.membership();
    if (
      !key ||
      this.isDemoPreview ||
      this.offline() !== null ||
      !this.membershipResolved ||
      !this.reservationsFetched ||
      !membership.plan ||
      !membership.paidAt ||
      (membership.status !== 'active' && membership.status !== 'past_due')
    ) {
      return;
    }
    const snapshot: MemberSnapshot = {
      savedAt: Date.now(),
      membership: {
        status: membership.status,
        planName: membership.plan.name,
        monthlyClasses: membership.plan.monthlyClasses,
        classesUsed: membership.classesUsed,
        paidAt: membership.paidAt,
      },
      reservations: this.upcomingReservations()
        .slice(0, 20)
        .map((r) => ({
          id: r.id,
          gymBlockId: r.gymBlockId,
          blockLabel: r.blockLabel,
          classDate: r.classDate,
          startTime: r.startTime,
          endTime: r.endTime,
        })),
      ...(this.classesCache.length > 0 ? { classes: this.classesCache } : {}),
    };
    try {
      localStorage.setItem(key, JSON.stringify(snapshot));
    } catch {
      // Sin almacenamiento (modo privado, cuota llena): simplemente no hay copia sin conexión.
    }
  }

  private clearSnapshot(): void {
    const key = this.snapshotKey();
    try {
      if (key) {
        localStorage.removeItem(key);
      }
    } catch {
      // Nada que limpiar.
    }
  }

  private readSnapshot(): MemberSnapshot | null {
    const key = this.snapshotKey();
    try {
      const raw = key ? localStorage.getItem(key) : null;
      const parsed = raw ? (JSON.parse(raw) as MemberSnapshot) : null;
      return parsed && typeof parsed.savedAt === 'number' && parsed.membership && Array.isArray(parsed.reservations) ? parsed : null;
    } catch {
      return null;
    }
  }

  /** Falla por red (status 0) y hay copia: la muestra con su hora. Devuelve true si la usó. */
  private restoreSnapshot(err: unknown): boolean {
    if (this.isDemoPreview || (err as { status?: number } | null)?.status !== 0) {
      return false;
    }
    const snapshot = this.readSnapshot();
    if (!snapshot) {
      return false;
    }
    const m = snapshot.membership;
    this.membership.set({
      status: m.status,
      plan: { id: 0, name: m.planName, priceClp: 0, monthlyClasses: m.monthlyClasses },
      classesUsed: m.classesUsed,
      paidAt: m.paidAt,
    });
    this.myReservations.set(
      snapshot.reservations.map((r) => ({ ...r, status: 'BOOKED', createdAt: '', checkedInAt: null, fixed: false })),
    );
    this.membershipError.set(false);
    this.membershipLoaded.set(true);
    this.reservationsError.set(false);
    this.reservationsLoaded.set(true);
    this.offline.set(snapshot.savedAt);
    return true;
  }

  // Clases de los próximos días en memoria, para incluirlas en la copia local (ver saveSnapshot).
  private classesCache: SnapshotClass[] = [];

  private cacheClasses(occurrences: GymBlockOccurrence[]): void {
    if (this.isDemoPreview) {
      return;
    }
    const today = this.todayIso();
    const last = addDaysIso(today, SNAPSHOT_CLASS_DAYS - 1);
    this.classesCache = occurrences
      .filter((o) => o.classDate >= today && o.classDate <= last)
      .slice(0, SNAPSHOT_CLASS_LIMIT)
      .map((o) => ({
        gymBlockId: o.gymBlockId,
        label: o.label,
        classDate: o.classDate,
        dayOfWeek: o.dayOfWeek,
        startTime: o.startTime,
        endTime: o.endTime,
        capacity: o.capacity,
        category: o.category,
        instructorName: o.instructorName,
        taken: o.taken,
        closed: o.closed,
        myReservationId: o.myReservationId,
        waitlisted: o.waitlisted,
        myReservationFixed: o.myReservationFixed,
      }));
    this.saveSnapshot();
  }

  /** Sin red: muestra las clases guardadas (solo lectura, sin fotos ni asistentes). Devuelve true si las usó. */
  private restoreClasses(): boolean {
    if (this.isDemoPreview) {
      return false;
    }
    const snapshot = this.readSnapshot();
    const today = this.todayIso();
    const savedClasses = snapshot?.classes;
    const saved = (Array.isArray(savedClasses) ? savedClasses : []).filter((c) => c.classDate >= today);
    if (!snapshot || saved.length === 0) {
      return false;
    }
    const nowIso = this.nowChileIso();
    this.occurrences.set(
      saved.map((c) => {
        const past = `${c.classDate}T${c.endTime}` < nowIso;
        return {
          ...c,
          instructorPhoto: null,
          bookable: !past && !c.closed,
          past,
          myCheckedInAt: null,
        };
      }),
    );
    this.classesNoNetwork.set(false);
    this.status.set('idle');
    if (this.offline() === null) {
      this.offline.set(snapshot.savedAt);
    }
    return true;
  }

  protected retryOffline(): void {
    this.loadDashboard();
    this.loadMyReservations();
    this.loadOccurrences();
  }

  protected loadMyReservations(): void {
    (this.isDemoPreview
      ? this.demoPreviewService.listReservations()
      : this.reservationService.myReservations()
    ).subscribe({
      next: (reservations) => {
        this.myReservations.set(reservations);
        this.reservationsError.set(false);
        this.reservationsLoaded.set(true);
        this.reservationsFetched = true;
        this.saveSnapshot();
      },
      error: (err: unknown) => {
        this.reservationsLoaded.set(true);
        if (!this.reservationsFetched && this.restoreSnapshot(err)) {
          return;
        }
        this.reservationsError.set(true);
      },
    });
  }

  private loadClosureNotice(): void {
    if (this.isDemoPreview) {
      return;
    }
    this.gymService.getMyClosureNotice().subscribe({
      next: (notice) => this.closureNotice.set(notice),
      error: () => this.closureNotice.set(null),
    });
  }

  private currentMonthRange(): { year: number; month: number; from: string; to: string; daysInMonth: number } {
    const [y, m] = this.todayIso().split('-').map(Number);
    // new Date(year, month, 0) da el último día del mes `month` (1-indexado) —
    // es puro cálculo de calendario, no de instante, así que da lo mismo la
    // zona horaria del navegador.
    const daysInMonth = new Date(y, m, 0).getDate();
    return {
      year: y,
      month: m,
      from: `${y}-${pad2(m)}-01`,
      to: `${y}-${pad2(m)}-${pad2(daysInMonth)}`,
      daysInMonth,
    };
  }

  /** 0=lunes .. 6=domingo, para alinear la grilla con encabezados Lun..Dom. */
  private dayOfWeekMonFirst(year: number, month: number, day: number): number {
    const jsDay = new Date(year, month - 1, day).getDay(); // 0=domingo..6=sábado
    return (jsDay + 6) % 7;
  }

  private isReservationPast(reservation: Reservation): boolean {
    return `${reservation.classDate}T${reservation.endTime}` < this.nowChileIso();
  }

  // Resalta la clase que está pasando AHORA — pedido explícito del usuario para que se note (y
  // de paso empuje a marcar asistencia escaneando el QR de la TV, sin el cual Memoria Viva ni
  // Historial cuentan la clase como asistida). Compartido por "Mis reservas" (Reservation) y
  // "Reserva tu bloque" (GymBlockOccurrence) — mismos 3 campos de fecha/hora en los dos.
  private isLiveNow(classDate: string, startTime: string, endTime: string): boolean {
    const start = `${classDate}T${startTime}`;
    const end = `${classDate}T${endTime}`;
    const now = this.nowChileIso();
    return start <= now && now <= end;
  }

  // Misma regla que ReservationService.cancel: solo se puede cancelar si faltan MÁS de
  // `cancellationWindowMinutes` minutos para el inicio (el admin del gym la define, y es un dato
  // DISTINTO al límite para reservar). Antes el botón salía siempre y el backend rechazaba
  // después con un error. Se calcula con "ahora" fresco (no el snapshot de la página) porque
  // ésta puede quedar abierta horas. La lectura de `now` es solo para que la plantilla reevalúe
  // el botón en cada refresco del reloj.
  protected canCancel(classDate: string, startTime: string): boolean {
    void this.now();
    const windowMinutes = this.gym()?.cancellationWindowMinutes ?? 0;
    const toMs = (iso: string) => {
      const [date, time = '00:00:00'] = iso.split('T');
      const [y, mo, d] = date.split('-').map(Number);
      const [h, mi, s = 0] = time.split(':').map(Number);
      return Date.UTC(y, mo - 1, d, h, mi, s);
    };
    return toMs(nowInGymZoneIso()) + windowMinutes * 60_000 < toMs(`${classDate}T${startTime}`);
  }

  // "2 h", "1 h 30 min", "45 min" — mismo formato que los mensajes de error del backend.
  protected minutesLabel(minutes: number | undefined): string {
    const total = minutes ?? 0;
    const hours = Math.floor(total / 60);
    const rest = total % 60;
    if (hours === 0) {
      return `${rest} min`;
    }
    return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
  }

  // ---- Aviso de membresía al abrir la app (plan por vencer / vencido / clases agotadas) ----

  private membershipAlertChecked = false;

  // ---- Datos de contacto obligatorios (RUT, dirección, fecha de nacimiento, celular) ----
  // `profileIncomplete` = al servidor le faltan datos (no reserva). El formulario ya NO se abre al entrar:
  // se abre al intentar reservar (askProfileToBook) o desde el aviso "Completa tus datos para reservar".
  protected readonly profileIncomplete = signal(false);
  protected readonly profileModalOpen = signal(false);
  // Reserva que el socio intentaba cuando le pedimos los datos: se retoma al guardar.
  protected readonly pendingBooking = signal<GymBlockOccurrence | null>(null);

  private askProfileToBook(occurrence: GymBlockOccurrence): void {
    this.profileError.set(null);
    this.pendingBooking.set(occurrence);
    this.profileModalOpen.set(true);
  }

  protected closeProfileModal(): void {
    this.profileModalOpen.set(false);
    this.pendingBooking.set(null);
  }
  protected readonly memberProfile = signal<Partial<MemberProfile> | null>(null);
  protected readonly savingProfile = signal(false);
  protected readonly profileError = signal<string | null>(null);

  protected saveProfile(profile: MemberProfile): void {
    this.savingProfile.set(true);
    this.profileError.set(null);
    // "Mis datos" parcial: solo se envía lo que tiene valor (el servidor deja igual lo que no llega).
    const payload: Partial<MemberProfile> = Object.fromEntries(
      Object.entries(profile).filter(([, value]) => typeof value === 'string' && value.trim() !== ''),
    );
    this.memberProfileService.updateOwn(payload).subscribe({
      next: (member) => {
        this.savingProfile.set(false);
        this.profileIncomplete.set(member.profileComplete === false);
        this.profileEditOpen.set(false);
        const pending = this.pendingBooking();
        this.profileModalOpen.set(false);
        this.pendingBooking.set(null);
        this.memberProfile.set({ ...this.memberProfile(), ...payload });
        if (pending && !this.profileIncomplete()) {
          // Tras cerrar el modal, sigue con la reserva que intentaba (con el cupo al día si cambió).
          const fresh = this.occurrences().find((o) => o.gymBlockId === pending.gymBlockId && o.classDate === pending.classDate);
          setTimeout(() => this.book(fresh ?? pending), 250);
          this.showToast('Listo, guardamos tus datos. Reservando tu clase…');
        } else {
          this.showToast('Listo, tus datos quedaron guardados.');
        }
      },
      error: (err: Error) => {
        this.savingProfile.set(false);
        this.profileError.set(err.message || 'No pudimos guardar tus datos. Intenta nuevamente.');
      },
    });
  }
  // El aviso de "clases agotadas" distingue si las clases están reservadas como clases fijas, así que
  // espera a que lleguen las reservas (también si fallan: no se queda esperando para siempre).
  private readonly reservationsLoaded = signal(false);

  // "2026-02-03" → "3 de febrero"
  private longDateLabel(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('es-CL', { day: 'numeric', month: 'long' });
  }

  private async showMembershipAlert(): Promise<void> {
    // Nunca en la vista demo del admin ("Ver como socio") ni mientras corre el tour guiado.
    // Mientras el formulario de datos está abierto (o sin conexión, con datos viejos) no se apila otro aviso.
    if (this.isDemoPreview || this.tourService.isOpen() || this.profileModalOpen() || this.offline()) {
      return;
    }
    const membership = this.membership();
    const planName = membership.plan?.name;
    // Vencimiento FINAL: con un período ya pagado por adelantado no hay aviso de "por vencer".
    const periodEnd = this.membershipFinalEnd();
    const days = this.daysRemaining();
    if (!planName || !periodEnd || days === null) {
      return; // sin plan pagado nunca hubo nada que avisar
    }
    const expired = this.membershipExpired();
    const expiring = this.expirySoon();
    const exhausted = this.quotaExhausted() && !expired;

    let key: string;
    let header: string;
    let message: string;
    let renew = true;
    if (expired) {
      // sin cambios: el caso vencido no se toca
      key = 'expired';
      header = 'Tu plan venció';
      message = `Tu plan ${planName} se cumplió. Mientras no lo renueves no podrás reservar clases nuevas.`;
    } else if (expiring) {
      key = 'expiring';
      header = days === 1 ? 'Tu plan vence mañana' : `Tu plan vence en ${days} días`;
      // Renovar el mismo plan por adelantado conserva los días: se ofrece solo si el servidor lo permite.
      renew = this.earlyRenewalAvailable();
      message = renew
        ? `Tu plan ${planName} vence el ${this.longDateLabel(periodEnd)}. Si lo renuevas antes, conservas los días que te quedan.`
        : `Tu plan ${planName} vence el ${this.longDateLabel(periodEnd)}.`;
      message += exhausted ? ' Además, ya usaste todas tus clases de este período.' : '';
    } else if (exhausted) {
      // Con clases fijas el socio no "usó" nada: ya están reservadas por el gimnasio.
      const hasFixed = this.myReservations().some((r) => r.fixed && !this.isReservationPast(r));
      if (hasFixed) {
        key = 'exhausted-fixed';
        header = 'Tus clases ya están reservadas';
        message = this.hasQueuedPeriod()
          ? `Las clases de tu plan ${planName} de este período ya están reservadas como clases fijas. El período termina el ${this.longDateLabel(this.membershipPeriodEnd() ?? periodEnd)} y desde ahí tienes el cupo completo del período que ya pagaste.`
          : `Las clases de tu plan ${planName} de este período ya están reservadas como clases fijas. Tu plan sigue vigente hasta el ${this.longDateLabel(periodEnd)}; si cancelas una a tiempo, esa clase vuelve a tu cupo.`;
      } else {
        key = 'exhausted';
        header = 'Usaste todas tus clases';
        message = this.hasQueuedPeriod()
          ? `Ya reservaste las clases de tu plan ${planName} de este período, que termina el ${this.longDateLabel(this.membershipPeriodEnd() ?? periodEnd)}. Desde esa fecha tienes el cupo completo del período que ya pagaste; si cancelas una reserva a tiempo, esa clase se libera.`
          : `Ya reservaste las clases de tu plan ${planName} de este período. Tu plan sigue vigente hasta el ${this.longDateLabel(periodEnd)}; si cancelas una reserva a tiempo, esa clase se libera.`;
      }
      renew = false;
    } else {
      return;
    }

    // Una vez por día por situación y período: si lo cierra, no se le insiste hasta mañana.
    const storageKey = `mygym.alert.${key}.${periodEnd}`;
    try {
      if (localStorage.getItem(storageKey) === this.todayIso()) {
        return;
      }
      localStorage.setItem(storageKey, this.todayIso());
    } catch {
      // Sin almacenamiento (modo privado): se muestra, y vuelve a salir en la próxima apertura.
    }

    const alert = await this.alertController.create({
      header,
      message,
      cssClass: 'membership-alert',
      buttons: renew
        ? [
            { text: 'Después', role: 'cancel' },
            { text: 'Renovar plan', role: 'confirm', handler: () => setTimeout(() => this.startEarlyRenewal(), 150) },
          ]
        : [{ text: 'Entendido', role: 'cancel' }],
    });
    await alert.present();
  }

  protected isReservationLive(reservation: Reservation): boolean {
    return this.isLiveNow(reservation.classDate, reservation.startTime, reservation.endTime);
  }

  protected isOccurrenceLive(occurrence: GymBlockOccurrence): boolean {
    return !!occurrence.myReservationId && this.isLiveNow(occurrence.classDate, occurrence.startTime, occurrence.endTime);
  }

  // Barra calculada contra el signal `now` de esta pantalla: avanza con cada refresco del reloj
  // (30 s), sin timer propio.
  protected liveProgressPercent(startTime: string, endTime: string): number {
    const toMinutes = (hms: string) => {
      const [h, m] = hms.split(':').map(Number);
      return h * 60 + m;
    };
    const total = toMinutes(endTime) - toMinutes(startTime);
    if (total <= 0) {
      return 0;
    }
    const nowTime = this.nowChileIso().split('T')[1];
    const elapsed = toMinutes(nowTime) - toMinutes(startTime);
    return Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
  }

  // Mientras el socio no tiene plan, el calendario borroso va cambiando solo
  // de día seleccionado (entre los que sí tienen clases) — la idea es que
  // se note que el sistema responde de verdad, no solo una foto estática
  // detrás del candado. Se corta apenas elige un plan (selectPlan) o si el
  // componente se destruye. Respeta prefers-reduced-motion: en ese caso deja
  // fijo el primer día con clases, sin loop.
  private demoIntervalId: ReturnType<typeof setInterval> | null = null;

  private startBookingDemo(): void {
    const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    // Con reduced-motion: se sigue "esperando" (poll corto) a que carguen las
    // ocurrencias, pero apenas hay datos fija un día y para — sin loop.
    this.demoIntervalId = setInterval(() => this.advanceBookingDemo(reduceMotion), reduceMotion ? 400 : 2200);
    this.destroyRef.onDestroy(() => this.stopBookingDemo());
  }

  private stopBookingDemo(): void {
    if (this.demoIntervalId !== null) {
      clearInterval(this.demoIntervalId);
      this.demoIntervalId = null;
    }
  }

  private advanceBookingDemo(reduceMotion: boolean): void {
    if (!this.bookingLocked()) {
      this.stopBookingDemo();
      return;
    }
    const daysWithClasses = this.visibleGrid().filter(
      (cell): cell is MonthDayCell => !!cell && cell.hasClasses,
    );
    if (daysWithClasses.length === 0) {
      return;
    }
    if (reduceMotion) {
      this.selectedDate.set(daysWithClasses[0].iso);
      this.stopBookingDemo();
      return;
    }
    const currentIndex = daysWithClasses.findIndex((cell) => cell.iso === this.selectedDate());
    const next = daysWithClasses[(currentIndex + 1) % daysWithClasses.length];
    this.selectedDate.set(next.iso);
  }
}
