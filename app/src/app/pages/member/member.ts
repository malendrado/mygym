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
    const from = new Date();
    const to = new Date();
    to.setDate(to.getDate() + 7);
    this.reservationService.listOccurrences(this.toIsoDate(from), this.toIsoDate(to)).subscribe({
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

  private toIsoDate(date: Date): string {
    return date.toISOString().slice(0, 10);
  }
}
