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
import { calendarOutline, flashOutline, sparklesOutline, trendingUpOutline } from 'ionicons/icons';
import { AuthService } from '../../core/services/auth.service';
import { GymService } from '../../core/services/gym.service';
import { ReservationService } from '../../core/services/reservation.service';
import { GymBlockOccurrence, Reservation } from '../../core/models/reservation.model';
import { PublicGym } from '../../core/models/gym.model';
import { deriveSurfaceTint } from '../../core/utils/gym-theme';

addIcons({
  'flash-outline': flashOutline,
  'calendar-outline': calendarOutline,
  'trending-up-outline': trendingUpOutline,
  'sparkles-outline': sparklesOutline,
});

type Status = 'idle' | 'loading' | 'error';

// ---------------------------------------------------------------------------
// MOCK — sección Membresía. Todavía no existe el backend de planes/pagos
// (Parte B del plan, Flow.cl, pendiente). Esto es una maqueta funcional para
// validar la experiencia antes de construir ese backend: cuando exista,
// reemplazar MOCK_PLANS/mockMembership por llamadas reales a la API y
// selectPlan() por el checkout real de Flow.cl. El resto de la página
// (clases, reservas) ya usa datos reales.
// ---------------------------------------------------------------------------
type MembershipStatus = 'none' | 'pending' | 'active' | 'past_due';

interface MembershipPlan {
  id: string;
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

const MOCK_PLANS: MembershipPlan[] = [
  { id: 'basico', name: 'Básico', priceClp: 19990, monthlyClasses: 8 },
  { id: 'plus', name: 'Plus', priceClp: 29990, monthlyClasses: 16, highlight: true },
  { id: 'ilimitado', name: 'Ilimitado', priceClp: 39990, monthlyClasses: null },
];

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

  protected readonly benefits = BENEFITS;
  protected readonly quote = MOTIVATIONAL_QUOTES[Math.floor(Math.random() * MOTIVATIONAL_QUOTES.length)];

  // MOCK — ver bloque de comentario arriba de MOCK_PLANS.
  protected readonly plans = MOCK_PLANS;
  protected readonly membership = signal<Membership>({
    status: 'active',
    plan: MOCK_PLANS[1],
    classesUsed: 5,
    renewsOn: '3 de octubre',
  });
  protected readonly quotaTotal = computed(() => this.membership().plan?.monthlyClasses ?? null);
  protected readonly classesRemaining = computed(() => {
    const total = this.quotaTotal();
    return total === null ? null : Math.max(total - this.membership().classesUsed, 0);
  });
  protected readonly quotaExhausted = computed(() => this.classesRemaining() === 0);
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

  constructor() {
    this.loadGym();
    this.loadOccurrences();
    this.loadMyReservations();
  }

  protected book(occurrence: GymBlockOccurrence): void {
    this.bookingId.set(occurrence.gymBlockId);
    this.reservationService
      .book({ gymBlockId: occurrence.gymBlockId, classDate: occurrence.classDate })
      .subscribe({
        next: () => {
          this.bookingId.set(null);
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
    this.reservationService.cancel(reservationId).subscribe({
      next: () => {
        this.loadOccurrences();
        this.loadMyReservations();
        this.showToast('Reserva cancelada.');
      },
      error: (err: Error) => this.showToast(err.message || 'No pudimos cancelar esa reserva.', 'danger'),
    });
  }

  // MOCK — simula el checkout de Flow.cl (Parte B, pendiente) mientras no existe
  // el backend real: pasa a "pending" y luego a "active" con el plan elegido.
  protected selectPlan(plan: MembershipPlan): void {
    this.membership.update((m) => ({ ...m, status: 'pending' }));
    this.showToast('Redirigiendo a Flow.cl para completar el pago...');
    setTimeout(() => {
      this.membership.set({ status: 'active', plan, classesUsed: 0, renewsOn: '3 de octubre' });
      this.showToast(`¡Listo! Ya tienes el plan ${plan.name}.`);
    }, 1500);
  }

  protected formatClp(amount: number): string {
    return amount.toLocaleString('es-CL');
  }

  protected logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private async showToast(message: string, color: 'success' | 'danger' = 'success'): Promise<void> {
    const toast = await this.toastController.create({ message, duration: 4000, position: 'bottom', color });
    await toast.present();
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
