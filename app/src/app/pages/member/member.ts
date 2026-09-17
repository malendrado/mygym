import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { Router } from '@angular/router';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonLabel,
  IonSegment,
  IonSegmentButton,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  calendarOutline,
  checkmarkDoneOutline,
  checkmarkOutline,
  chevronBackOutline,
  chevronForwardOutline,
  flashOutline,
  lockClosedOutline,
  logOutOutline,
  logoInstagram,
  logoWhatsapp,
  sparklesOutline,
  trendingUpOutline,
} from 'ionicons/icons';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { ReservationService } from '../../core/services/reservation.service';
import { GymBlockOccurrence, Reservation } from '../../core/models/reservation.model';
import { GymPhoto, MemberPlan, PublicGym } from '../../core/models/gym.model';
import { AttendeeSummary } from '../../core/models/member.model';
import { deriveSurfaceTint, ensureMinContrastColor } from '../../core/utils/gym-theme';
import { registerClassCategoryIcons, resolveClassCategoryIcon } from '../../core/utils/class-category';

registerClassCategoryIcons();

const FALLBACK_HERO_PHOTO = 'https://images.unsplash.com/photo-1637430308606-86576d8fef3c?q=75&w=1600&h=900&fit=crop&auto=format';

addIcons({
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

/** El plan de precio intermedio se marca "Recomendado" — heurística visual, no una señal del admin. */
function withHighlight(plans: MemberPlan[]): MembershipPlan[] {
  const sorted = [...plans].sort((a, b) => a.priceClp - b.priceClp);
  const highlightIndex = sorted.length >= 3 ? Math.floor(sorted.length / 2) : -1;
  return sorted.map((plan, index) => ({
    id: plan.id,
    name: plan.name,
    priceClp: plan.priceClp,
    monthlyClasses: plan.monthlyClasses,
    highlight: index === highlightIndex,
  }));
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

/** Instant ISO del backend ("2026-09-16T17:35:38Z") → "YYYY-MM-DD" en hora de Chile, mismo formato que `todayIso`. */
function toChileIsoDate(instantIso: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date(instantIso));
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
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

/** "YYYY-MM-DDTHH:mm:ss" en hora de Chile — comparable lexicográficamente contra `classDate + 'T' + endTime`. */
function nowInGymZoneIso(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Santiago',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date());
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
  ],
  templateUrl: './member.html',
  styleUrl: './member.scss',
})
export class MemberPage {
  private readonly authService = inject(AuthService);
  private readonly gymService = inject(GymService);
  private readonly reservationService = inject(ReservationService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly toastController = inject(ToastController);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly section = signal<'reservar' | 'reservas'>('reservar');

  protected readonly status = signal<Status>('idle');
  protected readonly gym = signal<PublicGym | null>(null);
  protected readonly gymLoaded = signal(false);
  protected readonly isRasterLogo = computed(() => (this.gym()?.logoSvg ?? '').startsWith('data:image'));
  protected readonly safeLogo = computed(() => {
    const svg = this.gym()?.logoSvg;
    return svg && !this.isRasterLogo() ? this.sanitizer.bypassSecurityTrustHtml(svg) : null;
  });
  protected readonly occurrences = signal<GymBlockOccurrence[]>([]);
  protected readonly myReservations = signal<Reservation[]>([]);
  protected readonly bookingId = signal<number | null>(null);
  protected readonly cancelingId = signal<number | null>(null);

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
  protected readonly monthLabel = capitalize(
    new Intl.DateTimeFormat('es-CL', { month: 'long', year: 'numeric' }).format(
      new Date(this.monthRange.year, this.monthRange.month - 1, 1),
    ),
  );
  protected readonly selectedDate = signal(this.todayIso);

  protected readonly benefits = BENEFITS;
  // Cargar el mes completo puede tardar unos segundos en gyms con muchos
  // bloques — mejor un mensaje con onda que una grilla vacía y quieta.
  protected readonly occurrencesLoadingMessage =
    OCCURRENCES_LOADING_MESSAGES[Math.floor(Math.random() * OCCURRENCES_LOADING_MESSAGES.length)];
  private readonly fallbackQuote = MOTIVATIONAL_QUOTES[Math.floor(Math.random() * MOTIVATIONAL_QUOTES.length)];
  // El gym puede escribir su propia frase (Gym.tagline) — si no lo hizo, cae a una genérica motivacional.
  protected readonly quote = computed(() => this.gym()?.tagline || this.fallbackQuote);

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
  protected readonly membership = signal<Membership>({
    status: 'none',
    plan: null,
    classesUsed: 0,
    paidAt: null,
  });
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
  protected readonly bookingLocked = computed(() => {
    const status = this.membership().status;
    return status === 'none' || status === 'past_due';
  });

  protected readonly firstName = computed(() => this.authService.currentUser()?.name?.split(' ')[0] ?? 'socio');
  // Only derive a surface tint from a real gym color — falling back to the lime
  // *accent* here (like the header does) would treat lime's hue as a background
  // color source and paint an off-brand olive surface. No gym color yet means
  // no override: the template's --member-surface binding stays unset and CSS
  // falls through to the plain --brand-ink default instead.
  protected readonly themeSurface = computed(() => {
    const color = this.gym()?.themeColor;
    return color ? deriveSurfaceTint(color) : null;
  });
  // El acento libre a veces no llega a 4.5:1 como texto plano (ej. el índigo
  // real de Fortis, ~4.07:1) — solo se usa donde el acento pinta TEXTO
  // (eyebrow del hero, "Tu membresía", "Ilimitado"), nunca fondos sólidos.
  protected readonly accentTextSafe = computed(() => {
    const color = this.gym()?.themeColor;
    const surface = this.themeSurface();
    return color && surface ? ensureMinContrastColor(color, surface.card) : null;
  });

  // Grilla del mes en curso (el socio solo puede navegar el mes calendario
  // actual, no meses futuros/pasados) — celdas nulas al inicio/fin completan
  // la fila para que la grilla quede rectangular (7 columnas, Lun a Dom).
  protected readonly monthGrid = computed<(MonthDayCell | null)[]>(() => {
    const { year, month, daysInMonth } = this.monthRange;
    const occurrenceDates = new Set(this.occurrences().map((o) => o.classDate));
    const leadingBlanks = this.dayOfWeekMonFirst(year, month, 1);
    const cells: (MonthDayCell | null)[] = Array(leadingBlanks).fill(null);
    for (let day = 1; day <= daysInMonth; day++) {
      const iso = `${year}-${pad2(month)}-${pad2(day)}`;
      cells.push({
        iso,
        dayNumber: day,
        isToday: iso === this.todayIso,
        isPast: iso < this.todayIso,
        hasClasses: occurrenceDates.has(iso),
      });
    }
    while (cells.length % 7 !== 0) {
      cells.push(null);
    }
    return cells;
  });

  // La grilla del mes completo (5-6 semanas) empuja demasiado abajo la lista
  // de clases del día — por defecto se ve solo la semana de la fecha
  // seleccionada; "Ver mes completo" expande a las semanas restantes.
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
    this.gymService.getMyBlockAttendees(occurrence.gymBlockId, occurrence.classDate).subscribe({
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
  protected readonly pastReservations = computed(() =>
    this.myReservations()
      .filter((r) => this.isReservationPast(r))
      .slice()
      .reverse(),
  );

  protected readonly selectedDateLabel = computed(() => {
    const [y, m, d] = this.selectedDate().split('-').map(Number);
    const formatted = new Intl.DateTimeFormat('es-CL', { weekday: 'long', day: 'numeric', month: 'long' }).format(
      new Date(y, m - 1, d),
    );
    return capitalize(formatted);
  });

  // Apenas se cumple el mes desde el pago, bloquea (mismo candado que "sin
  // plan") y dispara los emails reales de aviso a socio y admin — una sola
  // vez por sesión, guardado en `expiryNotified` para no repetir el envío
  // en cada re-render mientras el effect sigue vivo.
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
    const plan = current.plan;
    this.membership.update((m) => ({ ...m, status: 'past_due' }));
    this.startBookingDemo();
    if (plan) {
      this.gymService.simulateMyPlanExpiry(plan.id).subscribe({
        error: () => {
          // Best-effort, igual que simulateMyPlanPayment — el bloqueo en la
          // UI ya se aplicó arriba independientemente de si el email sale.
        },
      });
    }
  });

  constructor() {
    this.loadGym();
    this.loadPlans();
    this.loadMembership();
    this.loadPhotos();
    this.loadOccurrences();
    this.loadMyReservations();
    this.startBookingDemo();
  }

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
  }

  protected toggleMonthExpanded(): void {
    this.monthExpanded.update((expanded) => !expanded);
  }

  protected setSection(section: 'reservar' | 'reservas'): void {
    this.section.set(section);
  }

  protected book(occurrence: GymBlockOccurrence): void {
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

  // El checkout en sí sigue siendo MOCK (Flow.cl, Parte B, pendiente) — pasa a
  // "pending" y luego a "active" en el cliente sin crear ninguna suscripción
  // real. Lo que SÍ es real: el POST a simulate-payment dispara los emails de
  // "pago confirmado" a socio y admin (mismo patrón que el alta de socio).
  protected selectPlan(plan: MembershipPlan): void {
    this.membership.update((m) => ({ ...m, status: 'pending' }));
    this.showToast('Redirigiendo a Flow.cl para completar el pago...');
    setTimeout(() => {
      this.membership.set({ status: 'active', plan, classesUsed: 0, paidAt: this.todayIso });
      this.expiryNotified = false;
      this.stopBookingDemo();
      this.showToast(`¡Listo! Ya tienes el plan ${plan.name}.`);
      this.gymService.simulateMyPlanPayment(plan.id).subscribe({
        error: () => {
          // Best-effort: el "pago" del cliente ya se dio por exitoso arriba —
          // si el email de confirmación falla, no tiene sentido revertir la
          // experiencia del socio por eso.
        },
      });
    }, 1500);
  }

  // Desde la tarjeta de "membresía vencida" — vuelve a mostrar las tarjetas
  // de plan (mismo estado que un socio que nunca pagó) para "renovar".
  protected renewPlan(): void {
    this.membership.update((m) => ({ ...m, status: 'none' }));
  }

  protected scrollToPlans(): void {
    document.querySelector('.membership')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected formatClp(amount: number): string {
    return amount.toLocaleString('es-CL');
  }

  // Mismo criterio que gym-admin.ts: un socio que sale vuelve a la página
  // propia de SU gimnasio, no al /login genérico.
  protected async logout(): Promise<void> {
    await this.authService.logout();
    const slug = this.gym()?.slug;
    this.router.navigate([slug ? `/j/${slug}` : '/login']);
  }

  private async showToast(message: string, color: 'success' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 4000, position: 'bottom', color });
    await toast.present();
  }

  private loadPlans(): void {
    this.gymService.getMyMemberPlans().subscribe({
      next: (plans) => {
        this.plans.set(withHighlight(plans));
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
    this.gymService.getMyMembership().subscribe({
      next: (member) => {
        if (!member.planId || !member.paidAt) {
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
      },
      error: () => {
        // Best-effort: sin datos reales, se queda en "none" (el estado por
        // defecto) — el socio puede seguir viendo la pantalla de elegir plan.
      },
    });
  }

  private loadPhotos(): void {
    this.gymService.getMyMemberPhotos().subscribe({
      next: (photos) => this.photos.set(photos),
      error: () => {
        // Best-effort: sin fotos, el hero cae al fondo genérico.
      },
    });
  }

  private loadGym(): void {
    this.gymService.getMyMemberGym().subscribe({
      next: (gym) => {
        this.gym.set(gym);
        this.gymLoaded.set(true);
      },
      error: () => this.gymLoaded.set(true),
    });
  }

  private loadOccurrences(): void {
    this.status.set('loading');
    this.reservationService.listOccurrences(this.monthRange.from, this.monthRange.to).subscribe({
      next: (occurrences) => {
        this.occurrences.set(occurrences);
        this.status.set('idle');
      },
      error: () => this.status.set('error'),
    });
  }

  private loadMyReservations(): void {
    this.reservationService.myReservations().subscribe({
      next: (reservations) => this.myReservations.set(reservations),
      error: () => this.status.set('error'),
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
