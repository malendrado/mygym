import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  calendarOutline,
  flashOutline,
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
  renewsOn: string;
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

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

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
  ],
  templateUrl: './member.html',
  styleUrl: './member.scss',
})
export class MemberPage {
  private readonly authService = inject(AuthService);
  private readonly gymService = inject(GymService);
  private readonly reservationService = inject(ReservationService);
  private readonly router = inject(Router);
  private readonly toastController = inject(ToastController);

  protected readonly status = signal<Status>('idle');
  protected readonly gym = signal<PublicGym | null>(null);
  protected readonly gymLoaded = signal(false);
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
  private readonly monthRange = this.currentMonthRange();
  protected readonly weekdayLabels = WEEKDAY_LABELS;
  protected readonly monthLabel = capitalize(
    new Intl.DateTimeFormat('es-CL', { month: 'long', year: 'numeric' }).format(
      new Date(this.monthRange.year, this.monthRange.month - 1, 1),
    ),
  );
  protected readonly selectedDate = signal(this.todayIso);

  protected readonly benefits = BENEFITS;
  private readonly fallbackQuote = MOTIVATIONAL_QUOTES[Math.floor(Math.random() * MOTIVATIONAL_QUOTES.length)];
  // El gym puede escribir su propia frase (Gym.tagline) — si no lo hizo, cae a una genérica motivacional.
  protected readonly quote = computed(() => this.gym()?.tagline || this.fallbackQuote);

  protected readonly photos = signal<GymPhoto[]>([]);
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
    renewsOn: '',
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
  protected readonly quotaPercent = computed(() => {
    const total = this.quotaTotal();
    if (total === null || total === 0) {
      return 0;
    }
    return Math.min((this.membership().classesUsed / total) * 100, 100);
  });
  // r=54 → circunferencia = 2πr ≈ 339.29; el offset "vacía" el anillo en proporción al cupo usado.
  private static readonly QUOTA_RING_CIRCUMFERENCE = 339.29;
  protected readonly quotaRingOffset = computed(
    () => MemberPage.QUOTA_RING_CIRCUMFERENCE * (1 - this.quotaPercent() / 100),
  );

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

  protected readonly dayOccurrences = computed(() =>
    this.occurrences().filter((o) => o.classDate === this.selectedDate()),
  );

  protected readonly selectedDateLabel = computed(() => {
    const [y, m, d] = this.selectedDate().split('-').map(Number);
    const formatted = new Intl.DateTimeFormat('es-CL', { weekday: 'long', day: 'numeric', month: 'long' }).format(
      new Date(y, m - 1, d),
    );
    return capitalize(formatted);
  });

  constructor() {
    this.loadGym();
    this.loadPlans();
    this.loadPhotos();
    this.loadOccurrences();
    this.loadMyReservations();
  }

  protected categoryIcon(category: string | null): string {
    return resolveClassCategoryIcon(category);
  }

  protected selectDay(iso: string): void {
    this.selectedDate.set(iso);
  }

  protected toggleMonthExpanded(): void {
    this.monthExpanded.update((expanded) => !expanded);
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
      this.membership.set({ status: 'active', plan, classesUsed: 0, renewsOn: '3 de octubre' });
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
}
