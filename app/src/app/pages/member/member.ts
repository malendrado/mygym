import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
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
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
  AlertController,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { InstallBanner } from './install-banner/install-banner';
import { QrScanner } from '../../core/components/qr-scanner/qr-scanner';
import { extractCheckinCode } from '../../core/utils/qr-code';
import { TourOverlay } from '../../core/components/tour-overlay/tour-overlay';
import { SectionTour, TourService } from '../../core/services/tour.service';
import { TourSeenService } from '../../core/services/tour-seen.service';
import { toLogoImgSrc } from '../../core/utils/logo-src';
import {
  addCircleOutline,
  alertCircleOutline,
  banOutline,
  barbellOutline,
  calendarOutline,
  checkmarkDoneOutline,
  checkmarkOutline,
  chevronBackOutline,
  chevronForwardOutline,
  closeCircleOutline,
  createOutline,
  eyeOutline,
  flashOutline,
  informationCircleOutline,
  lockClosedOutline,
  logOutOutline,
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
import { BankTransferInfo, GymClosureNotice, GymPhoto, MemberPlan, PublicGym } from '../../core/models/gym.model';
import { AttendeeSummary, Member } from '../../core/models/member.model';
import { ExerciseLogEntry, MemberWorkoutLog, PendingWorkout, SaveWorkoutLogRequest } from '../../core/models/workout.model';
import { deriveSurfaceTint, ensureMinContrastColor, syncThemeOverrides } from '../../core/utils/gym-theme';
import { registerClassCategoryIcons, resolveClassCategoryIcon } from '../../core/utils/class-category';
import { VersionTag } from '../../core/components/version-tag/version-tag';

registerClassCategoryIcons();

const FALLBACK_HERO_PHOTO = 'https://images.unsplash.com/photo-1637430308606-86576d8fef3c?q=75&w=1600&h=900&fit=crop&auto=format';

// Comisión de Flow por link de pago (2,89% + IVA), traspasada al socio como recargo aparte
// sobre el precio del plan — mismo cálculo que FlowPaymentService.GATEWAY_COMMISSION_RATE en
// el backend, acá solo para mostrar el desglose antes de pagar (el backend es la fuente de
// verdad del monto realmente cobrado).
const GATEWAY_COMMISSION_RATE = 0.0289 * 1.19;

addIcons({
  'eye-outline': eyeOutline,
  'flash-outline': flashOutline,
  'calendar-outline': calendarOutline,
  'trending-up-outline': trendingUpOutline,
  'sparkles-outline': sparklesOutline,
  'log-out-outline': logOutOutline,
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
    body: 'Cada clase reservada es un paso más cerca de la meta — el historial queda acá.',
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

@Component({
  selector: 'app-member',
  imports: [
    VersionTag,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonContent,
    IonIcon,
    IonBadge,
    IonText,
    IonSpinner,
    IonSegment,
    IonSegmentButton,
    IonLabel,
    IonInput,
    IonItem,
    InstallBanner,
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
  private readonly reservationService = inject(ReservationService);
  private readonly workoutService = inject(WorkoutService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastController = inject(ToastController);
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
  protected readonly bookingId = signal<number | null>(null);
  protected readonly cancelingId = signal<number | null>(null);
  protected readonly waitlistingId = signal<number | null>(null);

  // "Hoy" se calcula en la zona horaria del gym (America/Santiago), no en la
  // del navegador del socio — un `new Date().toISOString()` corta a UTC y
  // puede quedar un día desalineado cerca de medianoche en Chile. Se computa
  // una sola vez al abrir la página: no hace falta que la grilla se
  // actualice sola si el socio la deja abierta pasando la medianoche.
  private readonly todayIso = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date());
  // "Ahora" con hora incluida, misma zona — para separar reservas pasadas de
  // próximas en "Mis reservas" comparando contra `classDate` + `endTime`.
  private readonly nowChileIso = nowInGymZoneIso();
  private readonly monthRange = this.currentMonthRange();
  protected readonly weekdayLabels = WEEKDAY_LABELS;
  protected readonly selectedDate = signal(this.todayIso);

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
  protected readonly membershipPeriodEnd = computed(() => {
    const paidAt = this.membership().paidAt;
    if (!paidAt) {
      return null;
    }
    const [y, m, d] = paidAt.split('-').map(Number);
    const end = new Date(y, m - 1, d);
    end.setMonth(end.getMonth() + 1);
    return `${end.getFullYear()}-${pad2(end.getMonth() + 1)}-${pad2(end.getDate())}`;
  });
  protected readonly daysRemaining = computed(() => {
    const end = this.membershipPeriodEnd();
    if (!end) {
      return null;
    }
    const [ey, em, ed] = end.split('-').map(Number);
    const [ty, tm, td] = this.todayIso.split('-').map(Number);
    const msPerDay = 24 * 60 * 60 * 1000;
    return Math.round((Date.UTC(ey, em - 1, ed) - Date.UTC(ty, tm - 1, td)) / msPerDay);
  });
  protected readonly membershipExpired = computed(() => {
    const days = this.daysRemaining();
    return days !== null && days <= 0;
  });
  // Debajo de este umbral se avisa con texto explícito además del color —
  // "no transmitir información solo con color" (auditoría UX de la skill).
  protected readonly expirySoon = computed(() => {
    const days = this.daysRemaining();
    return days !== null && days > 0 && days <= 3;
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
    return `Vence en ${days} días`;
  });
  protected readonly renewsOnLabel = computed(() => {
    const end = this.membershipPeriodEnd();
    if (!end) {
      return '';
    }
    const [y, m, d] = end.split('-').map(Number);
    return new Intl.DateTimeFormat('es-CL', { day: 'numeric', month: 'long' }).format(new Date(y, m - 1, d));
  });
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
    const to = this.membershipPeriodEnd();
    return to ? { from: m.paidAt, to } : null;
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
      isToday: iso === this.todayIso,
      isPast: iso < this.todayIso,
      hasClasses: occurrenceDates.has(iso),
    });

    if (!range) {
      const { year, month, daysInMonth } = this.monthRange;
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
  protected readonly isViewingToday = computed(() => this.selectedDate() === this.todayIso);
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
    // Aviso al abrir /member: plan por vencer (≤3 días), plan vencido o clases agotadas. El email
    // llega aparte (MembershipReminderJob / ReservationService.book); esto es el mismo mensaje
    // dentro de la app, para quien no revisa el correo.
    effect(() => {
      if (this.membershipLoaded() && !this.membershipAlertChecked) {
        this.membershipAlertChecked = true;
        // Fuera del ciclo reactivo: crear el alert toca el DOM de Ionic.
        setTimeout(() => void this.showMembershipAlert());
      }
    });
    this.loadDashboard();
    this.loadPhotos();
    this.loadOccurrences();
    this.loadMyReservations();
    this.loadPastReservations(true);
    this.startBookingDemo();

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

  protected categoryIcon(category: string | null): string {
    return resolveClassCategoryIcon(category);
  }

  protected formatShortDate(dateIso: string): string {
    return formatShortDate(dateIso);
  }

  protected shortTime(time: string): string {
    return shortTime(time);
  }

  protected selectDay(iso: string): void {
    this.selectedDate.set(iso);
    this.pastOccurrencesCollapsed.set(true);
  }

  protected toggleMonthExpanded(): void {
    this.monthExpanded.update((expanded) => !expanded);
  }

  protected setSection(section: 'reservar' | 'reservas' | 'rutina'): void {
    this.section.set(section);
    if (section === 'rutina') {
      this.loadPendingWorkout();
    }
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
          body: 'Si una está en curso, ve una barra de progreso en vivo y el aviso de marcar asistencia por QR — nunca más te escribe para saber si ya reservó.',
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
          body: 'Cuando una clase no tiene cupo, se anota en la lista de espera, le avisamos apenas se libera uno, y puede salir de la lista cuando quiera.',
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

  private loadPendingWorkout(): void {
    this.loadingWorkout.set(true);
    this.justSavedLog.set(null);
    (this.isDemoPreview ? this.demoPreviewService.getPendingWorkout() : this.workoutService.getPending()).subscribe({
      next: (pending) => {
        this.pendingWorkout.set(pending);
        this.loadingWorkout.set(false);
        if (pending.status === 'READY') {
          this.resetWorkoutForm(pending.suggestedPlanDayId);
        }
      },
      error: () => this.loadingWorkout.set(false),
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
        this.loadPendingWorkout();
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
      },
      error: () => {
        this.savingWorkout.set(false);
        this.showToast('No pudimos actualizar el registro. Intenta nuevamente.', 'danger');
      },
    });
  }

  protected book(occurrence: GymBlockOccurrence): void {
    if (this.blockedInDemo()) {
      return;
    }
    this.bookingId.set(occurrence.gymBlockId);
    this.reservationService
      .book({ gymBlockId: occurrence.gymBlockId, classDate: occurrence.classDate })
      .subscribe({
        next: () => {
          this.bookingId.set(null);
          this.membership.update((m) => ({ ...m, classesUsed: m.classesUsed + 1 }));
          this.loadOccurrences();
          this.loadMyReservations();
          this.showToast('¡Reserva confirmada! Te esperamos en la clase.');
        },
        error: (err: Error) => {
          this.bookingId.set(null);
          this.showToast(err.message || 'No pudimos reservar esa clase. Intenta nuevamente.', 'danger');
        },
      });
  }

  protected cancel(reservationId: number): void {
    if (this.blockedInDemo()) {
      return;
    }
    this.cancelingId.set(reservationId);
    this.reservationService.cancel(reservationId).subscribe({
      next: () => {
        this.cancelingId.set(null);
        this.membership.update((m) => ({ ...m, classesUsed: Math.max(m.classesUsed - 1, 0) }));
        this.loadOccurrences();
        this.loadMyReservations();
        this.showToast('Reserva cancelada.');
      },
      error: (err: Error) => {
        this.cancelingId.set(null);
        this.showToast(err.message || 'No pudimos cancelar esa reserva.', 'danger');
      },
    });
  }

  // Bloque lleno: en vez de quedarse sin enterarse, el socio pide que le avisen por mail si se
  // libera un cupo (ver WaitlistService en el backend — orden de llegada, con ventaja para el
  // primero de la lista antes de avisarle al resto).
  protected joinWaitlist(occurrence: GymBlockOccurrence): void {
    if (this.blockedInDemo()) {
      return;
    }
    this.waitlistingId.set(occurrence.gymBlockId);
    this.reservationService
      .joinWaitlist({ gymBlockId: occurrence.gymBlockId, classDate: occurrence.classDate })
      .subscribe({
        next: () => {
          this.waitlistingId.set(null);
          this.loadOccurrences();
          this.showToast('Listo, te avisamos por mail si se libera un cupo.');
        },
        error: (err: Error) => {
          this.waitlistingId.set(null);
          this.showToast(err.message || 'No pudimos anotarte en la lista de espera.', 'danger');
        },
      });
  }

  protected leaveWaitlist(occurrence: GymBlockOccurrence): void {
    if (this.blockedInDemo()) {
      return;
    }
    this.waitlistingId.set(occurrence.gymBlockId);
    this.reservationService.leaveWaitlist(occurrence.gymBlockId, occurrence.classDate).subscribe({
      next: () => {
        this.waitlistingId.set(null);
        this.loadOccurrences();
        this.showToast('Te sacamos de la lista de espera.');
      },
      error: (err: Error) => {
        this.waitlistingId.set(null);
        this.showToast(err.message || 'No pudimos sacarte de la lista de espera.', 'danger');
      },
    });
  }

  // Primer paso al elegir un plan: no se paga todavía, solo se muestra cómo
  // pagar (Flow o transferencia) — el plan queda "guardado" en
  // paymentChoicePlan hasta que el socio confirma un método.
  protected choosePaymentMethod(plan: MembershipPlan): void {
    this.paymentChoicePlan.set(plan);
  }

  protected cancelPaymentChoice(): void {
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
    this.membership.update((m) => ({ ...m, status: 'pending' }));
    this.gymService.startCheckout(plan.id).subscribe({
      next: (res) => {
        window.location.href = res.redirectUrl;
      },
      error: () => {
        this.membership.update((m) => ({ ...m, status: 'none' }));
        this.showToast('No pudimos iniciar el pago. Intenta nuevamente.', 'danger');
      },
    });
  }

  // Desde la tarjeta de "membresía vencida" — vuelve a mostrar las tarjetas
  // de plan (mismo estado que un socio que nunca pagó) para "renovar".
  protected renewPlan(): void {
    this.membership.update((m) => ({ ...m, status: 'none' }));
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
        this.showToast(`¡Asistencia marcada${res.classLabels.length ? ' en ' + res.classLabels.join(' y ') : ''}!`);
        this.loadMyReservations();
        this.loadOccurrences();
      },
      error: (err: Error) => {
        this.checkinBusy.set(false);
        this.showToast(err.message || 'No pudimos confirmar tu asistencia. Intenta nuevamente.', 'danger');
      },
    });
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
  private loadMembership(): void {
    (this.isDemoPreview ? this.demoPreviewService.getMembership() : this.gymService.getMyMembership()).subscribe({
      next: (member) => this.applyMembership(member),
      error: () => {
        // Best-effort: sin datos reales, se queda en "none" (el estado por
        // defecto) — el socio puede seguir viendo la pantalla de elegir plan.
        this.membershipLoaded.set(true);
      },
    });
  }

  // Compartido por loadMembership() (demo preview, y el re-poll tras volver de Flow.cl) y
  // loadDashboard() (carga inicial real) — mismo procesamiento, solo cambia de dónde sale el dato.
  private applyMembership(member: Member): void {
    if (this.isDemoPreview) {
      this.demoMemberName.set(member.name);
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
    });
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
        this.applyMembership(dashboard.membership);
      },
      error: () => {
        this.gymLoaded.set(true);
        this.plansLoaded.set(true);
        this.membershipLoaded.set(true);
      },
    });
  }

  private loadOccurrences(): void {
    this.status.set('loading');
    const range = this.bookableRange() ?? { from: this.monthRange.from, to: this.monthRange.to };
    (this.isDemoPreview
      ? this.demoPreviewService.listOccurrences(range.from, range.to)
      : this.reservationService.listOccurrences(range.from, range.to)
    ).subscribe({
      next: (occurrences) => {
        this.occurrences.set(occurrences);
        this.status.set('idle');
      },
      error: () => this.status.set('error'),
    });
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

  private loadMyReservations(): void {
    (this.isDemoPreview
      ? this.demoPreviewService.listReservations()
      : this.reservationService.myReservations()
    ).subscribe({
      next: (reservations) => this.myReservations.set(reservations),
      error: () => this.status.set('error'),
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
    const [y, m] = this.todayIso.split('-').map(Number);
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
    return `${reservation.classDate}T${reservation.endTime}` < this.nowChileIso;
  }

  // Resalta la clase que está pasando AHORA — pedido explícito del usuario para que se note (y
  // de paso empuje a marcar asistencia escaneando el QR de la TV, sin el cual Memoria Viva ni
  // Historial cuentan la clase como asistida). Compartido por "Mis reservas" (Reservation) y
  // "Reserva tu bloque" (GymBlockOccurrence) — mismos 3 campos de fecha/hora en los dos.
  private isLiveNow(classDate: string, startTime: string, endTime: string): boolean {
    const start = `${classDate}T${startTime}`;
    const end = `${classDate}T${endTime}`;
    return start <= this.nowChileIso && this.nowChileIso <= end;
  }

  // Misma regla que ReservationService.cancel: solo se puede cancelar si faltan MÁS de
  // `cancellationWindowMinutes` minutos para el inicio (el admin del gym la define, y es un dato
  // DISTINTO al límite para reservar). Antes el botón salía siempre y el backend rechazaba
  // después con un error. Se calcula con "ahora" fresco (no el snapshot de la página) porque
  // ésta puede quedar abierta horas.
  protected canCancel(classDate: string, startTime: string): boolean {
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

  // "2026-02-03" → "3 de febrero"
  private longDateLabel(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('es-CL', { day: 'numeric', month: 'long' });
  }

  private async showMembershipAlert(): Promise<void> {
    // Nunca en la vista demo del admin ("Ver como socio") ni mientras corre el tour guiado.
    if (this.isDemoPreview || this.tourService.isOpen()) {
      return;
    }
    const membership = this.membership();
    const planName = membership.plan?.name;
    const periodEnd = this.membershipPeriodEnd();
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
      key = 'expired';
      header = 'Tu plan venció';
      message = `Tu plan ${planName} se cumplió. Mientras no lo renueves no podrás reservar clases nuevas.`;
    } else if (expiring) {
      key = 'expiring';
      header = days === 1 ? 'Tu plan vence mañana' : `Tu plan vence en ${days} días`;
      message =
        `Tu plan ${planName} vence el ${this.longDateLabel(periodEnd)}. Renuévalo antes para no quedarte sin poder reservar.` +
        (exhausted ? ' Además, ya usaste todas tus clases de este período.' : '');
    } else if (exhausted) {
      key = 'exhausted';
      header = 'Usaste todas tus clases';
      message = `Ya reservaste las clases de tu plan ${planName} de este período. Tu plan sigue vigente hasta el ${this.longDateLabel(periodEnd)}; si cancelas una reserva a tiempo, esa clase se libera.`;
      renew = false;
    } else {
      return;
    }

    // Una vez por día por situación y período: si lo cierra, no se le insiste hasta mañana.
    const storageKey = `mygym.alert.${key}.${periodEnd}`;
    try {
      if (localStorage.getItem(storageKey) === this.todayIso) {
        return;
      }
      localStorage.setItem(storageKey, this.todayIso);
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
            { text: 'Renovar plan', role: 'confirm', handler: () => setTimeout(() => this.scrollToPlans(), 150) },
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

  // Barra ESTÁTICA (pedido explícito): calculada una sola vez contra el mismo snapshot de "ahora"
  // que el resto de esta pantalla, no un timer en vivo — evita tener que mantener un intervalo
  // corriendo mientras la página esté abierta por un dato que ya se ve en texto (el horario).
  protected liveProgressPercent(startTime: string, endTime: string): number {
    const toMinutes = (hms: string) => {
      const [h, m] = hms.split(':').map(Number);
      return h * 60 + m;
    };
    const total = toMinutes(endTime) - toMinutes(startTime);
    if (total <= 0) {
      return 0;
    }
    const nowTime = this.nowChileIso.split('T')[1];
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
